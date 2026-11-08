package com.auss.musicplayer;

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
            MainActivity activity = (MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            getTargetContext().bindService(new Intent(getTargetContext(),MusicService.class),connection,Context.BIND_AUTO_CREATE);
            waitFor(()->player!=null,"service binds");
            if (arguments!=null && "restore".equals(arguments.getString("phase"))) {
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
        String[] names={"01 Sunset Drive.mp3","02 Blue Hour.mp3"};
        for(int i=0;i<names.length;i++) {
            File file=new File(getTargetContext().getFilesDir(),names[i]);
            try(InputStream in=getContext().getAssets().open(names[i]);OutputStream out=new FileOutputStream(file)){Mp3Metadata.copy(in,out);}
            Song song=new Song(-100-i,names[i].substring(3,names[i].length()-4),"Studio Sessions","After Hours",-100,18000,i+1,Uri.fromFile(file));
            song.fileName=names[i];song.folder="Test fixtures/";songs.add(song);
        }
        return songs;
    }
    private void preview(MainActivity activity,List<Song> songs,boolean nowPlaying) throws Exception {
        runOnMainSync(()->{
            try {
                java.lang.reflect.Method build=MainActivity.class.getDeclaredMethod("buildLibrary",List.class);build.setAccessible(true);build.invoke(activity,songs);
                java.lang.reflect.Method refresh=MainActivity.class.getDeclaredMethod("refresh");refresh.setAccessible(true);refresh.invoke(activity);
                if(nowPlaying){java.lang.reflect.Method show=MainActivity.class.getDeclaredMethod("showNowPlaying");show.setAccessible(true);show.invoke(activity);}
            }catch(Exception e){throw new RuntimeException(e);}
        });
        Thread.sleep(1500);
        Bitmap bitmap=getUiAutomation().takeScreenshot();
        if(bitmap!=null)try(OutputStream out=new FileOutputStream(new File(getTargetContext().getFilesDir(),nowPlaying?"preview-player.png":"preview-library.png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}
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
