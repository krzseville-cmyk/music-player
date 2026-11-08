package simple.music.player;

import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import java.util.ArrayList;
import java.util.List;

/** Song-scoped lyrics inside the artwork area; transport controls stay on Now Playing. */
final class NowPlayingLyrics extends LinearLayout {
    private final MainActivity host;
    private final LyricsRepository repository;
    private final TextView status,notice,follow,find,remove;
    private final LinearLayout lines;
    private final ScrollView scroll;
    private final List<TextView> timed=new ArrayList<>();
    private Song song;
    private String linkedUri="";
    private Lyrics lyrics;
    private int generation,active=-2;
    private boolean following=true,loaded;
    private float touchStart;
    NowPlayingLyrics(MainActivity host,LyricsRepository repository){
        super(host);this.host=host;this.repository=repository;setOrientation(VERTICAL);
        GradientDrawable bg=new GradientDrawable();bg.setColor(host.surfaceColor());bg.setCornerRadius(dp(24));
        bg.setStroke(dp(1),RecordArtwork.blend(host.surfaceColor(),host.textColor(),.13f));setBackground(bg);setPadding(dp(16),dp(12),dp(16),dp(10));
        LinearLayout heading=new LinearLayout(host);heading.setGravity(Gravity.CENTER_VERTICAL);
        status=text("LYRICS",10,true);status.setLetterSpacing(.1f);heading.addView(status,new LinearLayout.LayoutParams(0,LayoutParams.WRAP_CONTENT,1));
        follow=button("Follow",()->{following=true;active=-2;tick();});heading.addView(follow);follow.setVisibility(GONE);
        heading.addView(button("Cover",host::showCover));addView(heading);
        scroll=new ScrollView(host);scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);
        scroll.setOnTouchListener((v,event)->{
            if(event.getActionMasked()==MotionEvent.ACTION_DOWN){touchStart=event.getY();v.getParent().requestDisallowInterceptTouchEvent(true);}
            if(event.getActionMasked()==MotionEvent.ACTION_MOVE&&Math.abs(event.getY()-touchStart)>dp(8)){following=false;follow.setVisibility(lyrics!=null&&!lyrics.lines.isEmpty()?VISIBLE:GONE);}
            if(event.getActionMasked()==MotionEvent.ACTION_UP||event.getActionMasked()==MotionEvent.ACTION_CANCEL)v.getParent().requestDisallowInterceptTouchEvent(false);
            return false;
        });
        lines=new LinearLayout(host);lines.setOrientation(VERTICAL);scroll.addView(lines);addView(scroll,new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT,0,1));
        notice=text("",11,false);notice.setTextColor(host.mutedColor());notice.setPadding(0,dp(4),0,dp(4));addView(notice);
        LinearLayout actions=new LinearLayout(host);actions.setGravity(Gravity.CENTER_VERTICAL);
        find=button("Find online",this::findOnline);actions.addView(find);
        actions.addView(button("Add file",()->{if(song!=null)host.attachLyrics(song);}));
        remove=button("Unlink",this::unlink);actions.addView(remove);addView(actions);
    }
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private TextView text(String value,int size,boolean bold){TextView v=new TextView(host);v.setText(value);v.setTextSize(size);v.setTextColor(host.textColor());if(bold)v.setTypeface(Typeface.DEFAULT_BOLD);return v;}
    private TextView button(String label,Runnable action){TextView v=text(label,11,true);v.setPadding(dp(9),dp(10),dp(9),dp(10));v.setMinHeight(dp(44));v.setGravity(Gravity.CENTER);v.setOnClickListener(w->action.run());return v;}
    void bind(Song next){
        if(next==null){generation++;song=null;lyrics=null;timed.clear();lines.removeAllViews();return;}
        String linked=host.getSharedPreferences("library",0).getString("lyrics:"+next.uri,"");
        if(song!=null&&song.uri.equals(next.uri)&&song.title.equals(next.title)&&song.artist.equals(next.artist)&&song.album.equals(next.album)&&song.duration==next.duration&&linkedUri.equals(linked))return;
        song=next;reload();
    }
    String songUri(){return song==null?"":song.uri.toString();}
    boolean hasLyrics(){return loaded&&lyrics!=null&&(!lyrics.plain.trim().isEmpty()||!lyrics.lines.isEmpty());}
    int activeLine(){return active;}
    void reveal(){following=true;active=-2;tick();}
    void reload(){
        if(song==null)return;final int request=++generation;final Song target=song;
        linkedUri=host.getSharedPreferences("library",0).getString("lyrics:"+song.uri,"");
        loading("Loading lyrics…");repository.load(target,result->{if(request==generation&&song!=null&&song.uri.equals(target.uri))display(result);});
    }
    private void loading(String message){
        loaded=false;lyrics=null;active=-2;following=true;timed.clear();lines.removeAllViews();status.setText("LYRICS");
        TextView wait=text(message,18,true);wait.setPadding(0,dp(18),0,dp(18));lines.addView(wait);
        notice.setText("");find.setEnabled(false);find.setAlpha(.5f);remove.setVisibility(GONE);follow.setVisibility(GONE);
    }
    private void display(LyricsRepository.Result result){
        loaded=true;lines.removeAllViews();timed.clear();following=true;active=-2;scroll.scrollTo(0,0);
        try{lyrics=new Lyrics(result.text);}catch(RuntimeException e){lyrics=new Lyrics("");}
        boolean synced=!lyrics.lines.isEmpty();boolean present=!lyrics.plain.trim().isEmpty()||synced;status.setText(synced?"SYNCED LYRICS":"LYRICS");
        if(!present){
            TextView empty=text(result.message.isEmpty()?"No lyrics saved for this song.":result.message,18,true);
            empty.setPadding(0,dp(18),0,dp(14));lines.addView(empty);
            notice.setText("Find online uses this song’s title and artist.");
        }else if(!synced){
            TextView plain=text(lyrics.plain,19,false);plain.setTextIsSelectable(true);plain.setLineSpacing(dp(6),1f);plain.setPadding(0,dp(12),0,dp(12));lines.addView(plain);
            notice.setText(result.source+" · Plain lyrics");
        }else{
            for(Lyrics.Line line:lyrics.lines){
                TextView view=text(line.text.isEmpty()?"♪":line.text,20,false);view.setTextColor(host.mutedColor());view.setPadding(0,dp(10),0,dp(10));
                view.setOnClickListener(v->{MusicService player=host.player();if(player!=null&&player.getCurrentSong()!=null&&song!=null&&player.getCurrentSong().uri.equals(song.uri)){following=true;active=-2;player.seekTo((int)Math.min(Integer.MAX_VALUE,line.time));tick();}});
                timed.add(view);lines.addView(view);
            }
            notice.setText(result.source+" · Tap a line to seek");
        }
        find.setEnabled(true);find.setAlpha(1f);find.setVisibility(present?GONE:VISIBLE);
        remove.setVisibility(song!=null&&!host.getSharedPreferences("library",0).getString("lyrics:"+song.uri,"").isEmpty()?VISIBLE:GONE);
        host.onLyricsReady(hasLyrics());tick();
    }
    private void findOnline(){
        if(song==null)return;final int request=++generation;final Song target=song;
        loading("Finding lyrics…");repository.findOnline(target,result->{if(request==generation&&song!=null&&song.uri.equals(target.uri))display(result);});
    }
    private void unlink(){
        if(song==null)return;host.getSharedPreferences("library",0).edit().remove("lyrics:"+song.uri).apply();reload();
    }
    void tick(){
        if(lyrics==null||lyrics.lines.isEmpty()||song==null)return;
        MusicService player=host.player();if(player==null||player.getCurrentSong()==null||!player.getCurrentSong().uri.equals(song.uri))return;
        int selected=lyrics.active(player.getPosition());if(selected==active)return;active=selected;
        for(int i=0;i<timed.size();i++){TextView v=timed.get(i);v.setTextColor(i==selected?host.textColor():host.mutedColor());v.setTypeface(i==selected?Typeface.DEFAULT_BOLD:Typeface.DEFAULT);v.setAlpha(i==selected?1f:.78f);}
        follow.setVisibility(following?GONE:VISIBLE);
        if(following&&selected<0&&getVisibility()==VISIBLE)scroll.smoothScrollTo(0,0);
        if(following&&selected>=0&&getVisibility()==VISIBLE)scroll.post(()->{if(active>=0&&active<timed.size())scroll.smoothScrollTo(0,Math.max(0,timed.get(active).getTop()-scroll.getHeight()/3));});
    }
}
