package simple.music.player;

import android.app.Instrumentation;
import android.content.*;
import android.media.browse.MediaBrowser;
import android.os.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.io.*;
import android.net.Uri;
import android.graphics.Bitmap;
import android.view.View;
import android.widget.TextView;

/** Run against an emulator populated with at least two 18-second local music fixtures. */
public final class PlayerIntegrationTests extends Instrumentation {
    private MusicService player;
    private Bundle arguments;
    private int checks;
    @Override public void onCreate(Bundle args) { arguments=args; start(); }
    private interface Query { boolean get(); }
    private boolean main(Query query) {
        AtomicReference<Boolean> value = new AtomicReference<>();
        runOnMainSync(() -> value.set(query.get())); return value.get();
    }
    private void waitFor(Query query,String description) throws Exception {
        long end=SystemClock.elapsedRealtime()+20000;
        while (SystemClock.elapsedRealtime()<end) { if (main(query)) { checks++;return; } Thread.sleep(100); }
        throw new AssertionError(description);
    }
    private void check(Query query,String description) { if(!main(query))throw new AssertionError(description);checks++; }
    @Override public void onStart() {
        Bundle result=new Bundle();
        ServiceConnection connection = new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName n,IBinder b) { player=((MusicService.LocalBinder)b).getService(); }
            @Override public void onServiceDisconnected(ComponentName n) { player=null; }
        };
        try {
            if(arguments!=null&&"themes".equals(arguments.getString("phase")))new LibraryPreferences(getTargetContext()).theme("ember");
            if(arguments!=null&&"lyrics".equals(arguments.getString("phase"))){
                android.content.SharedPreferences prefs=getTargetContext().getSharedPreferences("library",0);
                prefs.edit().remove("lyrics:"+Uri.fromFile(new File(getTargetContext().getFilesDir(),"01 Sunset Drive.mp3")))
                    .remove("lyrics:"+Uri.fromFile(new File(getTargetContext().getFilesDir(),"02 Blue Hour.mp3"))).commit();
            }
            MainActivity activity = (MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            getTargetContext().bindService(new Intent(getTargetContext(),MusicService.class),connection,Context.BIND_AUTO_CREATE);
            waitFor(()->player!=null,"service binds");
            if (arguments!=null && "lyrics".equals(arguments.getString("phase"))) {
                lyricsPreview(activity);
            } else if (arguments!=null && "identity".equals(arguments.getString("phase"))) {
                identity(activity);
            } else if (arguments!=null && "themes".equals(arguments.getString("phase"))) {
                themePreview(activity);
            } else if (arguments!=null && "settings".equals(arguments.getString("phase"))) {
                settingsPreview(activity);
            } else if (arguments!=null && "design".equals(arguments.getString("phase"))) {
                designPreview(activity);
            } else if (arguments!=null && "restore".equals(arguments.getString("phase"))) {
                check(()->player.getQueue().size()==2,"queue restored");
                check(()->player.getIndex()==1,"current index restored");
                check(()->!player.isPlaying(),"restoration is paused");
                check(()->player.getPosition()>=2500 && player.getPosition()<5000,"position restored");
                check(()->new LibraryPreferences(getTargetContext()).sort()==4,"sort survives process restart");
            } else {
                List<Song> songs=LibraryRepository.load(getTargetContext());
                if(songs.size()<2) songs=privateFixtures();
                songs=new ArrayList<>(songs.subList(0,2)); final List<Song> queue=songs;
                LibraryPreferences prefs=new LibraryPreferences(getTargetContext());
                boolean original=prefs.favorite(queue.get(0));prefs.toggleFavorite(queue.get(0));
                check(()->prefs.favorite(queue.get(0))!=original,"favorite toggles");
                prefs.toggleFavorite(queue.get(0));prefs.sort(4);
                runOnMainSync(()->{player.setCrossfade(0);player.setSpeed(1f);player.playQueue(queue,0);});
                waitFor(()->player.isPlaying(),"audio starts on Android 35");
                check(()->prefs.count(queue.get(0))>0,"history recorded");
                runOnMainSync(()->player.seekTo(3000));waitFor(()->player.getPosition()>=2900,"seek works");
                runOnMainSync(()->player.pause());
                int position=player.getPosition();Thread.sleep(300);
                check(()->Math.abs(player.getPosition()-position)<200,"pause freezes position");
                runOnMainSync(()->player.toggleShuffle());
                check(()->player.getCurrentSong().uri.equals(queue.get(0).uri),"shuffle preserves current song");
                runOnMainSync(()->player.toggleShuffle());
                check(()->player.getQueue().get(1).uri.equals(queue.get(1).uri),"unshuffle restores original order");
                runOnMainSync(()->player.playNext(Collections.singletonList(queue.get(1))));
                check(()->player.getQueue().size()==3,"play next inserts a track");
                runOnMainSync(()->player.removeFromQueue(1));
                check(()->player.getQueue().size()==2,"queue removal works");
                runOnMainSync(()->{player.setSpeed(1.25f);player.setBass(0);player.setNormalization(true);player.setSpeed(1f);});
                check(()->player.getSpeed()==1f && player.normalization(),"playback settings apply");
                check(()->!player.isPlaying(),"changing speed keeps a paused track paused");
                runOnMainSync(()->{player.setSleepTimer(0,true);player.play();player.seekTo(player.getDuration()-700);});
                waitFor(()->!player.isPlaying() && "Off".equals(player.sleepDescription()),"end-of-song timer stops playback");
                check(()->player.getIndex()==0,"end-of-song timer does not advance queue");
                runOnMainSync(()->{player.setCrossfade(3);player.playQueue(queue,0);});
                waitFor(()->player.isPlaying(),"crossfade source starts");
                runOnMainSync(()->player.seekTo(player.getDuration()-4000));
                waitFor(()->player.getIndex()==1,"crossfade advances track");
                check(()->player.getPosition()>1500,"incoming track played during crossfade");
                runOnMainSync(()->{player.setCrossfade(0);player.setGapless(true);player.playQueue(queue,0);});
                waitFor(()->player.isPlaying(),"gapless source starts");
                runOnMainSync(()->player.seekTo(player.getDuration()-1500));
                waitFor(()->player.getIndex()==1 && player.isPlaying(),"gapless transition keeps playback active");
                browser();
                runOnMainSync(()->player.seekTo(3000));waitFor(()->player.getPosition()>=2900,"final seek");
                runOnMainSync(()->player.pause());Thread.sleep(500);
                if (arguments != null && "preview".equals(arguments.getString("phase"))) {
                    preview(activity,queue,false);
                    preview(activity,queue,true);
                }
            }
            result.putString("stream","Passed "+checks+" Android integration checks\n");
            finish(-1,result);
        } catch(Throwable error) {
            result.putString("stream", "FAIL: "+error+"\n"+android.util.Log.getStackTraceString(error));finish(0,result);
        } finally { try{getTargetContext().unbindService(connection);}catch(Exception ignored){} }
    }
    private List<Song> privateFixtures() throws Exception {
        List<Song> songs=new ArrayList<>();
        String[] names={"01 Sunset Drive.mp3","02 Blue Hour.mp3","03 Somewhere Slow.mp3","04 Golden.mp3","05 Moonlit.mp3","06 Soft Focus.mp3"};
        for(int i=0;i<names.length;i++) {
            File file=new File(getTargetContext().getFilesDir(),names[i]);
            try(InputStream in=getContext().getAssets().open(names[i]);OutputStream out=new FileOutputStream(file)){Mp3Metadata.copy(in,out);}
            Song song=new Song(-100-i,names[i].substring(3,names[i].length()-4),"Studio Sessions",i<2 ? "After Hours" : i<4 ? "Slow Sunday" : "Daydream",-100-i/2,18000,i+1,Uri.fromFile(file));
            song.fileName=names[i];song.folder="Test fixtures/";songs.add(song);
        }
        return songs;
    }
    private Object field(MainActivity activity,String name) {
        try { java.lang.reflect.Field field=MainActivity.class.getDeclaredField(name);field.setAccessible(true);return field.get(activity); }
        catch(Exception e){throw new RuntimeException(e);}
    }
    private boolean hasText(View parent,String text){java.util.ArrayList<View> matches=new java.util.ArrayList<>();parent.findViewsWithText(matches,text,View.FIND_VIEWS_WITH_TEXT);return !matches.isEmpty();}
    private View textView(View parent,String text){java.util.ArrayList<View> matches=new java.util.ArrayList<>();parent.findViewsWithText(matches,text,View.FIND_VIEWS_WITH_TEXT);if(matches.isEmpty())throw new AssertionError("Missing "+text);return matches.get(0);}
    private void lyricsPreview(MainActivity activity) throws Exception {
        List<Song> songs=privateFixtures();Song a=songs.get(0),b=songs.get(1);
        String lrc="[ar:Studio Sessions]\n[00:00.00]A little light across the room\n[00:02.00]We let the quiet carry through\n[00:04.00]Gold on the edge of something new\n[00:06.00]And all the roads are leading home\n[00:10.00]The night turns into morning blue\n[00:14.00]One final note to follow through";
        File file=new File(getTargetContext().getFilesDir(),"test-lyrics.lrc");
        try(OutputStream out=new FileOutputStream(file)){out.write(new byte[]{(byte)255,(byte)254});out.write(lrc.getBytes(java.nio.charset.StandardCharsets.UTF_16LE));}
        android.content.SharedPreferences prefs=getTargetContext().getSharedPreferences("library",0);
        prefs.edit().putString("lyrics:"+a.uri,Uri.fromFile(file).toString()).remove("lyrics:"+b.uri).putBoolean("lyricsOnPlayer",true).commit();
        runOnMainSync(()->{player.setCrossfade(0);player.playQueue(songs,0);});waitFor(()->player.isPlaying(),"lyrics fixture plays");runOnMainSync(player::pause);
        NowPlayingLyrics panel=(NowPlayingLyrics)field(activity,"npLyrics");
        runOnMainSync(()->invoke(activity,"showNowPlaying"));
        waitFor(panel::hasLyrics,"UTF-16 linked lyrics load");
        check(()->panel.getVisibility()==View.VISIBLE,"lyrics automatically display inside Now Playing");
        check(()->((View)field(activity,"npArt")).getVisibility()==View.GONE,"lyrics share the cover area");
        check(()->hasText(panel,"A little light across the room"),"actual lyric text is displayed");
        runOnMainSync(()->player.seekTo(5500));waitFor(()->panel.activeLine()==2,"highlight follows playback seek");
        capture("preview-now-playing-lyrics.png");
        runOnMainSync(()->textView(panel,"We let the quiet carry through").performClick());
        waitFor(()->player.getPosition()>=1900&&player.getPosition()<3500&&panel.activeLine()==1,"tapping a line seeks to its timestamp");
        check(()->!player.isPlaying(),"line seek keeps paused audio paused");
        runOnMainSync(activity::showCover);
        check(()->panel.getVisibility()==View.GONE&&((View)field(activity,"npArt")).getVisibility()==View.VISIBLE,"cover toggle restores artwork");
        runOnMainSync(()->activity.showSongLyrics(a));
        check(()->panel.getVisibility()==View.VISIBLE,"lyrics toggle returns inline");
        runOnMainSync(()->player.playAt(1));waitFor(()->player.isPlaying()&&panel.songUri().equals(b.uri.toString()),"lyrics follow track changes");runOnMainSync(player::pause);
        runOnMainSync(()->activity.showSongLyrics(b));
        waitFor(()->hasText(panel,"No lyrics saved for this song."),"missing lyrics show an actionable empty state");
        check(()->!hasText(panel,"A little light across the room")&&!panel.hasLyrics(),"previous song lyrics are cleared");
        check(()->hasText(panel,"Find online")&&hasText(panel,"Add file"),"empty state offers lookup and file attachment");
        PlayerTools tools=(PlayerTools)field(activity,"tools");
        prefs.edit().putString("lyricsTarget",a.uri.toString()).remove("lyrics:"+a.uri).commit();
        runOnMainSync(()->tools.onResult(42,android.app.Activity.RESULT_OK,new Intent().setData(Uri.fromFile(file)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)));
        waitFor(()->prefs.getString("lyrics:"+a.uri,"").equals(Uri.fromFile(file).toString()),"file result is associated with its requested song");
        check(()->panel.songUri().equals(b.uri.toString())&&!panel.hasLyrics(),"attaching a different song does not overwrite current lyrics");
        prefs.edit().putString("lyrics:"+b.uri,"file:///missing-simple-player-lyrics.lrc").commit();
        runOnMainSync(()->activity.reloadLyricsFor(b.uri.toString()));
        waitFor(()->hasText(panel,"The linked lyrics file is unavailable."),"unavailable file gives a clear recovery message");
        File plain=new File(getTargetContext().getFilesDir(),"plain-lyrics.txt");try(OutputStream out=new FileOutputStream(plain)){out.write("Plain words without timing\nA second line to read".getBytes(java.nio.charset.StandardCharsets.UTF_8));}
        prefs.edit().putString("lyrics:"+b.uri,Uri.fromFile(plain).toString()).commit();runOnMainSync(()->activity.reloadLyricsFor(b.uri.toString()));
        waitFor(()->panel.hasLyrics()&&hasText(panel,"Plain words without timing"),"plain lyrics display without timed metadata");
        check(()->hasText(panel,"Plain lyrics"),"plain lyrics are labelled accurately");capture("preview-now-playing-plain-lyrics.png");
        org.json.JSONObject json=new org.json.JSONObject().put("syncedLyrics","[00:01]Fixture words").put("plainLyrics","Fallback words");
        check(()->LyricsRepository.fromResponse(json).text.startsWith("[00:01]"),"online response prefers synced lyrics");
        org.json.JSONObject fallback=new org.json.JSONObject().put("syncedLyrics",org.json.JSONObject.NULL).put("plainLyrics","Fallback words");
        check(()->LyricsRepository.fromResponse(fallback).text.equals("Fallback words"),"online response falls back to plain lyrics");
        org.json.JSONObject instrumental=new org.json.JSONObject().put("instrumental",true);
        check(()->LyricsRepository.fromResponse(instrumental).text.isEmpty()&&LyricsRepository.fromResponse(instrumental).message.contains("instrumental"),"instrumental tracks have an explicit result");
    }
    private void identity(MainActivity activity) throws Exception {
        check(()->getTargetContext().getPackageName().equals("simple.music.player"),"new application package");
        check(()->getTargetContext().getApplicationInfo().loadLabel(getTargetContext().getPackageManager()).toString().equals("Simple Player"),"launcher label is Simple Player");
        check(()->MainActivity.class.getName().equals("simple.music.player.MainActivity"),"launcher class moved");
        check(()->R.class.getPackage().getName().equals("simple.music.player"),"resource namespace moved");
        android.content.pm.PackageManager manager=getTargetContext().getPackageManager();
        check(()->{
            try{return manager.getActivityInfo(new ComponentName(getTargetContext(),MainActivity.class),0).exported;}catch(Exception e){return false;}
        },"launcher component resolves");
        check(()->{
            try{return manager.getServiceInfo(new ComponentName(getTargetContext(),MusicService.class),0)!=null;}catch(Exception e){return false;}
        },"playback service resolves");
        check(()->{
            try{return manager.getServiceInfo(new ComponentName(getTargetContext(),CarBrowserService.class),0)!=null;}catch(Exception e){return false;}
        },"media browser service resolves");
        check(()->{
            try{return manager.getReceiverInfo(new ComponentName(getTargetContext(),PlayerWidget.class),0).loadLabel(manager).toString().equals("Simple Player");}catch(Exception e){return false;}
        },"widget resolves with new label");
        check(()->MusicService.ACTION_TOGGLE.equals("simple.music.player.TOGGLE")&&MusicService.ACTION_NEXT.equals("simple.music.player.NEXT")&&MusicService.EXTRA_OPEN_PLAYER.equals("simple.music.player.OPEN_PLAYER"),"playback action namespace moved");
        runOnMainSync(activity::showSettings);
        check(()->{
            java.util.ArrayList<View> matches=new java.util.ArrayList<>();
            ((View)field(activity,"settingsScreen")).findViewsWithText(matches,"Simple Player",View.FIND_VIEWS_WITH_TEXT);return !matches.isEmpty();
        },"settings branding is Simple Player");
        runOnMainSync(activity::closeSettings);
        List<Song> fixtures=privateFixtures();
        runOnMainSync(()->player.playQueue(fixtures,0));waitFor(()->player.isPlaying(),"renamed package plays audio");
        runOnMainSync(player::pause);check(()->!player.isPlaying(),"renamed package pauses audio");
        browser();
    }
    private void themePreview(MainActivity initial) throws Exception {
        MainActivity current=initial;
        String[] ids={"ember","purple","light"};
        String[] names={"Ember","Amethyst","Daylight"};
        for(int i=0;i<ids.length;i++){
            final MainActivity host=current;final String id=ids[i];AppTheme palette=host.appTheme();
            check(()->host.appTheme().id.equals(id),"theme applies after recreation: "+id);
            check(()->contrast(palette.text,palette.surface)>=4.5,"primary text contrast: "+id);
            check(()->contrast(palette.muted,palette.surface)>=4.5,"secondary text contrast: "+id);
            check(()->contrast(palette.accent,palette.surface)>=4.5,"accent text contrast: "+id);
            check(()->contrast(palette.onAccent,palette.accent)>=4.5,"button text contrast: "+id);
            android.util.TypedValue resolved=new android.util.TypedValue();
            host.getTheme().resolveAttribute(android.R.attr.colorAccent,resolved,true);
            check(()->resolved.data==palette.accent,"native widget accent matches app: "+id);
            android.view.ContextThemeWrapper dialogContext=new android.view.ContextThemeWrapper(host,palette.dialogStyle);
            android.util.TypedValue dialogAccent=new android.util.TypedValue();dialogContext.getTheme().resolveAttribute(android.R.attr.colorAccent,dialogAccent,true);
            check(()->dialogAccent.data==palette.accent,"native dialog accent matches app: "+id);
            runOnMainSync(host::showSettings);
            SettingsScreen screen=(SettingsScreen)field(host,"settingsScreen");
            check(()->screen.findViewWithTag("Theme").getBackground() instanceof android.graphics.drawable.RippleDrawable,"settings rows use themed ripple: "+id);
            capture("preview-theme-"+id+"-settings.png");
            runOnMainSync(()->screen.findViewWithTag("Sound & playback").performClick());
            android.widget.Switch toggle=(android.widget.Switch)screen.findViewWithTag("ReplayGain");
            check(()->toggle.getThumbTintList().getColorForState(new int[]{android.R.attr.state_checked},0)==palette.accent,"switch accent matches theme: "+id);
            capture("preview-theme-"+id+"-sound.png");
            runOnMainSync(()->screen.findViewWithTag("Playback speed").performClick());
            capture("preview-theme-"+id+"-picker.png");clickText("Cancel");
            runOnMainSync(host::onBackPressed);
            runOnMainSync(()->screen.findViewWithTag("Theme").performClick());
            check(()->screen.page().equals("appearance"),"appearance page opens: "+id);
            check(()->screen.findViewWithTag("theme-"+id)!=null,"selected theme has a preview card: "+id);
            capture("preview-theme-"+id+"-appearance.png");
            int next=(i+1)%ids.length;
            ActivityMonitor monitor=addMonitor(MainActivity.class.getName(),null,false);
            try{
                clickText(names[next]);
                MainActivity replacement=(MainActivity)monitor.waitForActivityWithTimeout(45000);
                if(replacement==null)throw new AssertionError("Theme change did not recreate Activity");checks++;
                current=replacement;
            }finally{removeMonitor(monitor);}
            final MainActivity replacement=current;
            check(()->replacement.appTheme().id.equals(ids[next]),"selected palette applied: "+ids[next]);
            check(()->{
                SettingsScreen restored=(SettingsScreen)field(replacement,"settingsScreen");
                return restored.getVisibility()==View.VISIBLE&&restored.page().equals("appearance")&&restored.hasRootParent();
            },"appearance navigation restored across theme change");
            check(()->!player.isPlaying(),"theme change keeps paused playback paused");
        }
        final MainActivity last=current;runOnMainSync(last::closeSettings);
    }
    private static double contrast(int a,int b){double x=luminance(a),y=luminance(b);return (Math.max(x,y)+.05)/(Math.min(x,y)+.05);}
    private static double luminance(int color){return .2126*channel(android.graphics.Color.red(color))+.7152*channel(android.graphics.Color.green(color))+.0722*channel(android.graphics.Color.blue(color));}
    private static double channel(int value){double x=value/255.0;return x<=.04045?x/12.92:Math.pow((x+.055)/1.055,2.4);}
    private void settingsPreview(MainActivity activity) throws Exception {
        runOnMainSync(activity::showSettings);
        SettingsScreen screen=(SettingsScreen)field(activity,"settingsScreen");
        check(()->screen.getVisibility()==View.VISIBLE&&screen.page().equals("root"),"dedicated settings screen opens");
        capture("preview-settings.png");
        runOnMainSync(()->screen.findViewWithTag("Sound & playback").performClick());
        check(()->screen.page().equals("sound")&&screen.hasRootParent(),"sound settings opens as a nested page");
        boolean originalGain=player.normalization(),originalGapless=player.gapless();float originalSpeed=player.getSpeed();int originalFade=player.crossfadeSeconds();
        runOnMainSync(()->{player.setCrossfade(0);screen.refresh();});
        android.widget.Switch gain=(android.widget.Switch)screen.findViewWithTag("ReplayGain");
        runOnMainSync(gain::toggle);
        check(()->player.normalization()!=originalGain,"ReplayGain switch changes player preference");
        check(()->getTargetContext().getSharedPreferences("player",0).getBoolean("normalization",false)==player.normalization(),"ReplayGain setting persists");
        runOnMainSync(gain::toggle);
        android.widget.Switch gapless=(android.widget.Switch)screen.findViewWithTag("Gapless playback");
        runOnMainSync(gapless::toggle);
        check(()->player.gapless()!=originalGapless,"gapless switch updates the player");
        runOnMainSync(gapless::toggle);
        runOnMainSync(()->screen.findViewWithTag("Playback speed").performClick());
        clickText("1.25×");waitFor(()->player.getSpeed()==1.25f,"speed selection applies");
        check(()->!player.isPlaying(),"changing a setting does not start playback");
        runOnMainSync(()->screen.findViewWithTag("Crossfade").performClick());
        clickText("3 seconds");waitFor(()->player.crossfadeSeconds()==3,"crossfade selection applies");
        check(()->!gapless.isEnabled()&&!gapless.isChecked(),"gapless is unavailable while crossfade is active");
        runOnMainSync(()->{player.setCrossfade(0);screen.refresh();});
        check(()->gapless.isEnabled()&&gapless.isChecked()==originalGapless,"gapless state returns when crossfade is off");
        capture("preview-settings-sound.png");
        runOnMainSync(()->{
            android.widget.ScrollView scroll=findScroll(screen);scroll.fullScroll(View.FOCUS_DOWN);
        });Thread.sleep(500);capture("preview-settings-effects.png");
        runOnMainSync(activity::onBackPressed);
        check(()->screen.getVisibility()==View.VISIBLE&&screen.page().equals("root"),"back from sound returns to settings");
        runOnMainSync(activity::onBackPressed);
        check(()->screen.getVisibility()==View.GONE,"back from settings returns to music");
        runOnMainSync(activity::showSoundSettings);runOnMainSync(activity::onBackPressed);
        check(()->screen.getVisibility()==View.GONE,"direct sound entry returns to its caller");
        runOnMainSync(()->{player.setSpeed(originalSpeed);player.setCrossfade(originalFade);player.setGapless(originalGapless);player.setNormalization(originalGain);});
    }
    private android.widget.ScrollView findScroll(View view){
        if(view instanceof android.widget.ScrollView)return (android.widget.ScrollView)view;
        if(view instanceof android.view.ViewGroup)for(int i=0;i<((android.view.ViewGroup)view).getChildCount();i++){
            android.widget.ScrollView found=findScroll(((android.view.ViewGroup)view).getChildAt(i));if(found!=null)return found;
        }return null;
    }
    private void clickText(String label) throws Exception {
        dismissEmulatorAnr();long end=SystemClock.elapsedRealtime()+20000;
        while(SystemClock.elapsedRealtime()<end){
            android.view.accessibility.AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();
            List<android.view.accessibility.AccessibilityNodeInfo> matches=new ArrayList<>();
            if(root!=null){matches.addAll(root.findAccessibilityNodeInfosByText(label));matches.addAll(root.findAccessibilityNodeInfosByText(label.toUpperCase(java.util.Locale.ROOT)));}
            for(android.view.accessibility.AccessibilityNodeInfo node:matches){
                if(node.getText()==null||!node.getText().toString().equalsIgnoreCase(label))continue;
                while(node!=null&&!node.isClickable())node=node.getParent();
                if(node!=null&&node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))return;
            }Thread.sleep(100);
        }throw new AssertionError("Cannot select "+label);
    }
    private void capture(String name) throws Exception {
        Thread.sleep(1000);dismissEmulatorAnr();Thread.sleep(300);
        Bitmap image=getUiAutomation().takeScreenshot();if(image==null)throw new AssertionError("Screenshot unavailable");
        try(OutputStream out=new FileOutputStream(new File(getTargetContext().getFilesDir(),name))){image.compress(Bitmap.CompressFormat.PNG,100,out);}
    }
    private void invoke(MainActivity activity,String name) {
        try {java.lang.reflect.Method method=MainActivity.class.getDeclaredMethod(name);method.setAccessible(true);method.invoke(activity);}
        catch(Exception e){throw new RuntimeException(e);}
    }
    private void designPreview(MainActivity activity) throws Exception {
        List<Song> songs=privateFixtures();
        runOnMainSync(()->{player.setCrossfade(0);player.playQueue(songs,0);});
        waitFor(()->player.isPlaying(),"design fixture starts");
        runOnMainSync(()->player.pause());
        CountDownLatch artReady=new CountDownLatch(1);AtomicReference<Bitmap> art=new AtomicReference<>();
        runOnMainSync(()->ArtLoader.load(getTargetContext(),songs.get(0),320,bitmap->{art.set(bitmap);artReady.countDown();}));
        if(!artReady.await(30,TimeUnit.SECONDS)||art.get()==null)throw new AssertionError("embedded MP3 cover loads through fallback");checks++;
        preview(activity,songs,false);
        check(()->((View)field(activity,"overview")).getVisibility()==View.VISIBLE,"continue-listening card visible");
        check(()->((TextView)field(activity,"overviewTitle")).getText().toString().equals(songs.get(0).title),"continue card shows actual track");
        check(()->((TextView)field(activity,"nextTitle")).getText().toString().contains(songs.get(1).title),"up-next panel matches queue");
        runOnMainSync(()->((View)field(activity,"overviewAction")).performClick());
        waitFor(()->player.isPlaying(),"resume card resumes playback");
        check(()->((View)field(activity,"npView")).getVisibility()==View.VISIBLE,"resume opens player");
        runOnMainSync(()->{player.pause();invoke(activity,"hideOverlays");((View)field(activity,"favoritesShortcut")).performClick();});
        check(()->((Integer)field(activity,"tab"))==2,"favorites shortcut navigates");
        check(()->((View)field(activity,"overview")).getVisibility()==View.GONE,"overview hidden in favorites");
        runOnMainSync(()->{
            try {java.lang.reflect.Method select=MainActivity.class.getDeclaredMethod("selectTab",int.class);select.setAccessible(true);select.invoke(activity,0);}
            catch(Exception e){throw new RuntimeException(e);}
        });
        preview(activity,songs,false);preview(activity,songs,true);
    }
    private void preview(MainActivity activity,List<Song> songs,boolean nowPlaying) throws Exception {
        dismissEmulatorAnr();
        runOnMainSync(()->{
            try {
                java.lang.reflect.Method build=MainActivity.class.getDeclaredMethod("buildLibrary",List.class);build.setAccessible(true);build.invoke(activity,songs);
                java.lang.reflect.Method refresh=MainActivity.class.getDeclaredMethod("refresh");refresh.setAccessible(true);refresh.invoke(activity);
                if(nowPlaying){java.lang.reflect.Method show=MainActivity.class.getDeclaredMethod("showNowPlaying");show.setAccessible(true);show.invoke(activity);}
            }catch(Exception e){throw new RuntimeException(e);}
        });
        Thread.sleep(1500);
        dismissEmulatorAnr();
        Bitmap bitmap=getUiAutomation().takeScreenshot();
        if(bitmap!=null)try(OutputStream out=new FileOutputStream(new File(getTargetContext().getFilesDir(),nowPlaying?"preview-player.png":"preview-library.png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}
    }
    private void dismissEmulatorAnr() throws Exception {
        android.view.accessibility.AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();
        if(root==null)return;
        List<android.view.accessibility.AccessibilityNodeInfo> nodes=root.findAccessibilityNodeInfosByText("Wait");
        for(android.view.accessibility.AccessibilityNodeInfo node:nodes){
            android.view.accessibility.AccessibilityNodeInfo clickable=node;
            while(clickable!=null&&!clickable.isClickable())clickable=clickable.getParent();
            if(clickable!=null&&clickable.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)){Thread.sleep(1000);return;}
        }
    }
    private void browser() throws Exception {
        CountDownLatch connected=new CountDownLatch(1), loaded=new CountDownLatch(1);
        AtomicReference<MediaBrowser> reference=new AtomicReference<>();
        AtomicReference<Integer> children=new AtomicReference<>(0);
        runOnMainSync(()->{
            MediaBrowser browser=new MediaBrowser(getTargetContext(),new ComponentName(getTargetContext(),CarBrowserService.class),new MediaBrowser.ConnectionCallback(){
                @Override public void onConnected(){connected.countDown();}
            },null);reference.set(browser);browser.connect();
        });
        if(!connected.await(20,TimeUnit.SECONDS))throw new AssertionError("media browser connects");checks++;
        runOnMainSync(()->reference.get().subscribe("root",new MediaBrowser.SubscriptionCallback(){
            @Override public void onChildrenLoaded(String id,List<MediaBrowser.MediaItem> items){children.set(items.size());loaded.countDown();}
        }));
        if(!loaded.await(20,TimeUnit.SECONDS)||children.get()!=5)throw new AssertionError("media browser exposes all categories");checks++;
        runOnMainSync(()->reference.get().disconnect());
    }
}
