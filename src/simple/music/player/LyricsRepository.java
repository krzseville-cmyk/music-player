package simple.music.player;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.AtomicFile;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.*;

/** Local lyrics load automatically. Online lookup runs only after an explicit Find action. */
final class LyricsRepository {
    static final class Result {
        final String text,source,message;
        Result(String text,String source,String message){this.text=text;this.source=source;this.message=message;}
    }
    interface Callback { void loaded(Result result); }
    private final Context context;
    private final SharedPreferences prefs;
    private final ExecutorService io=Executors.newFixedThreadPool(2);
    private final Handler main=new Handler(Looper.getMainLooper());
    private volatile boolean closed;
    LyricsRepository(Context context){this.context=context.getApplicationContext();prefs=this.context.getSharedPreferences("library",0);}
    void close(){closed=true;main.removeCallbacksAndMessages(null);io.shutdownNow();}
    void load(Song song,Callback callback){submit(()->local(song),callback);}
    void findOnline(Song song,Callback callback){submit(()->online(song),callback);}
    private interface Work { Result run() throws Exception; }
    private void submit(Work work,Callback callback){
        if(closed)return;
        io.execute(()->{
            Result result;
            try{result=work.run();}catch(Exception e){result=new Result("","","Couldn't load lyrics. Check your connection or add a lyrics file.");}
            final Result loaded=result;
            if(!closed)main.post(()->{if(!closed)callback.loaded(loaded);});
        });
    }
    private Result local(Song song){
        String error="";String linked=prefs.getString("lyrics:"+song.uri,"");
        if(!linked.isEmpty())try(InputStream in=context.getContentResolver().openInputStream(Uri.parse(linked))){
            String text=LyricsText.read(in);if(!text.isEmpty())return new Result(text,"Lyrics file","");
            error="The linked lyrics file is empty. Choose another file.";
        }catch(Exception e){error="The linked lyrics file is unavailable. Add it again.";}
        try(InputStream in=context.getContentResolver().openInputStream(song.uri)){
            String embedded=Mp3Metadata.lyrics(in).replace("\uFEFF","").trim();
            if(!embedded.isEmpty())return new Result(embedded,"Embedded lyrics","");
        }catch(Exception ignored){}
        try(InputStream in=new FileInputStream(cache(song))){
            String saved=LyricsText.read(in);if(!saved.isEmpty())return new Result(saved,"Saved online lyrics","");
        }catch(Exception ignored){}
        return new Result("","",error.isEmpty()?"No lyrics saved for this song.":error);
    }
    private File cache(Song song)throws Exception{
        String key=song.uri+"\n"+song.title+"\n"+song.artist+"\n"+song.duration;
        byte[] hash=MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));StringBuilder name=new StringBuilder();
        for(byte value:hash)name.append(String.format(java.util.Locale.ROOT,"%02x",value&255));
        File folder=new File(context.getFilesDir(),"lyrics");if(!folder.exists()&&!folder.mkdirs())throw new IOException("Cannot save lyrics");
        return new File(folder,name+".txt");
    }
    private Result online(Song song)throws Exception{
        String query="track_name="+encode(song.title)+"&artist_name="+encode(song.artist)+"&duration="+Math.max(0,song.duration/1000);
        if(!song.album.isEmpty())query+="&album_name="+encode(song.album);
        HttpURLConnection connection=(HttpURLConnection)new URL("https://lrclib.net/api/get?"+query).openConnection();
        connection.setConnectTimeout(10000);connection.setReadTimeout(10000);
        connection.setRequestProperty("Accept","application/json");connection.setRequestProperty("User-Agent","SimplePlayer/3.6 (Android)");
        try{
            int status=connection.getResponseCode();
            if(status==404)return new Result("","","No matching lyrics found. Try adding an LRC or text file.");
            if(status!=200)throw new IOException("Lyrics service unavailable");
            JSONObject json;try(InputStream in=connection.getInputStream()){json=new JSONObject(LyricsText.read(in));}
            Result parsed=fromResponse(json);String text=parsed.text;
            if(text.trim().isEmpty())return parsed;
            AtomicFile target=new AtomicFile(cache(song));FileOutputStream out=null;
            try{out=target.startWrite();out.write(text.getBytes(StandardCharsets.UTF_8));target.finishWrite(out);}
            catch(Exception e){if(out!=null)target.failWrite(out);}
            return new Result(text,"LRCLIB","");
        }finally{connection.disconnect();}
    }
    static Result fromResponse(JSONObject json){
        if(json.optBoolean("instrumental",false))return new Result("","Instrumental","This track is listed as instrumental.");
        String text=json.optString("syncedLyrics","");if(text.equals("null"))text="";
        if(text.trim().isEmpty())text=json.optString("plainLyrics","");if(text.equals("null"))text="";
        return text.trim().isEmpty()?new Result("","","No lyrics available for this track. Add your own lyrics file."):new Result(text,"LRCLIB","");
    }
    private static String encode(String text)throws Exception{return URLEncoder.encode(text.length()>512?text.substring(0,512):text,"UTF-8");}
}
