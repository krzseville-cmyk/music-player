package com.auss.musicplayer;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.media.MediaDescription;
import android.media.browse.MediaBrowser;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Process;
import android.service.media.MediaBrowserService;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Android Auto browses the same offline library and controls MusicService's session. */
public final class CarBrowserService extends MediaBrowserService {
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private boolean bound;
    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            setSessionToken(((MusicService.LocalBinder)binder).getService().sessionToken());
        }
        @Override public void onServiceDisconnected(ComponentName name) { }
    };
    @Override public void onCreate() {
        super.onCreate(); bound = bindService(new Intent(this,MusicService.class),connection,BIND_AUTO_CREATE);
    }
    @Override public void onDestroy() { if (bound) unbindService(connection); io.shutdown(); super.onDestroy(); }
    @Override public BrowserRoot onGetRoot(String name, int uid, Bundle hints) {
        boolean permitted = uid == Process.myUid() || uid == Process.SYSTEM_UID;
        if (!permitted && (name.equals("com.google.android.projection.gearhead") || name.equals("com.google.android.carassistant") || name.equals("com.android.car.media"))) {
            try { ApplicationInfo app = getPackageManager().getApplicationInfo(name,0); permitted = app.uid == uid; }
            catch (Exception ignored) { }
        }
        if (!permitted) return null;
        Bundle extras = new Bundle();
        extras.putBoolean("android.media.browse.CONTENT_STYLE_SUPPORTED",true);
        return new BrowserRoot("root",extras);
    }
    @Override public void onLoadChildren(String parent, Result<List<MediaBrowser.MediaItem>> result) {
        result.detach();
        io.execute(() -> {
            List<MediaBrowser.MediaItem> items = new ArrayList<>();
            try {
                if (parent.equals("root")) {
                    items.add(item("songs","All songs",null,true));
                    items.add(item("favorites","Favorites",null,true));
                    items.add(item("albums","Albums",null,true));
                    items.add(item("playlists","Playlists",null,true));
                    items.add(item("recent","Recently played",null,true));
                } else {
                    LibraryPreferences preferences = new LibraryPreferences(this);
                    List<Song> visible = new ArrayList<>(); Set<String> excluded = preferences.excluded();
                    for (Song song : LibraryRepository.load(this)) if (!excluded.contains(song.folder)) visible.add(song);
                    PlaylistStore store = new PlaylistStore(this);
                    if (parent.equals("albums")) {
                        Map<Long,Song> albums = new LinkedHashMap<>(); for (Song s : visible) albums.putIfAbsent(s.albumId,s);
                        for (Song s : albums.values()) items.add(item("album:"+s.albumId,s.album,s.artist,true));
                    } else if (parent.equals("playlists")) {
                        for (PlaylistStore.Playlist p : store.getAll()) items.add(item("playlist:"+p.id,p.name,p.songIds.size()+" songs",true));
                    } else {
                        List<Song> songs = new ArrayList<>();
                        if (parent.startsWith("playlist:")) {
                            PlaylistStore.Playlist p = store.get(Long.parseLong(parent.substring(9)));
                            if (p != null) for (long id : p.songIds) for (Song s : visible) if (s.id==id) { songs.add(s); break; }
                        } else {
                            for (Song s : visible) if (parent.equals("songs")
                                    || parent.equals("favorites") && preferences.favorite(s)
                                    || parent.equals("recent") && preferences.last(s)>0
                                    || parent.equals("album:"+s.albumId)) songs.add(s);
                        }
                        if (parent.equals("recent")) Collections.sort(songs,(a,b)->Long.compare(preferences.last(b),preferences.last(a)));
                        for (Song s : songs) items.add(item(s.uri.toString(),s.title,s.artist,false));
                    }
                }
            } catch (Exception ignored) { items.clear(); }
            result.sendResult(items);
        });
    }
    private MediaBrowser.MediaItem item(String id,String title,String subtitle,boolean browsable) {
        return new MediaBrowser.MediaItem(new MediaDescription.Builder().setMediaId(id).setTitle(title).setSubtitle(subtitle).build(),
                browsable ? MediaBrowser.MediaItem.FLAG_BROWSABLE : MediaBrowser.MediaItem.FLAG_PLAYABLE);
    }
}
