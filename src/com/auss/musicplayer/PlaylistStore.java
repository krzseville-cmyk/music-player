package com.auss.musicplayer;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** User playlists, stored as song ids in a JSON file in the app's private storage. */
public final class PlaylistStore {

    public static final class Playlist {
        public final long id;
        public String name;
        public final List<Long> songIds = new ArrayList<>();

        Playlist(long id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    private final File file;
    private final List<Playlist> playlists = new ArrayList<>();
    private long nextId = 1;

    public PlaylistStore(Context context) {
        file = new File(context.getFilesDir(), "playlists.json");
        load();
    }

    public List<Playlist> getAll() {
        return Collections.unmodifiableList(playlists);
    }

    public Playlist get(long id) {
        for (Playlist p : playlists) if (p.id == id) return p;
        return null;
    }

    public Playlist create(String name) {
        Playlist p = new Playlist(nextId++, name);
        playlists.add(p);
        save();
        return p;
    }

    public void rename(Playlist p, String name) {
        p.name = name;
        save();
    }

    public void delete(Playlist p) {
        playlists.remove(p);
        save();
    }

    public void addSongs(Playlist p, List<Song> songs) {
        for (Song s : songs) p.songIds.add(s.id);
        save();
    }

    public void removeAt(Playlist p, int index) {
        if (index >= 0 && index < p.songIds.size()) {
            p.songIds.remove(index);
            save();
        }
    }

    /** Swaps two entries; used while dragging so the change is applied step by step. */
    public void swap(Playlist p, int a, int b) {
        Collections.swap(p.songIds, a, b);
    }

    public void save() {
        try {
            JSONArray arr = new JSONArray();
            for (Playlist p : playlists) {
                JSONObject o = new JSONObject();
                o.put("id", p.id);
                o.put("name", p.name);
                JSONArray ids = new JSONArray();
                for (long id : p.songIds) ids.put(id);
                o.put("songs", ids);
                arr.put(o);
            }
            JSONObject root = new JSONObject();
            root.put("nextId", nextId);
            root.put("playlists", arr);
            File tmp = new File(file.getPath() + ".tmp");
            try (OutputStream out = new FileOutputStream(tmp)) {
                out.write(root.toString().getBytes(StandardCharsets.UTF_8));
            }
            if (!tmp.renameTo(file)) {
                file.delete();
                tmp.renameTo(file);
            }
        } catch (Exception ignored) {
        }
    }

    private void load() {
        if (!file.exists()) return;
        try (InputStream in = new FileInputStream(file)) {
            byte[] data = new byte[(int) file.length()];
            int read = 0;
            while (read < data.length) {
                int n = in.read(data, read, data.length - read);
                if (n < 0) break;
                read += n;
            }
            JSONObject root = new JSONObject(new String(data, 0, read, StandardCharsets.UTF_8));
            nextId = root.optLong("nextId", 1);
            JSONArray arr = root.optJSONArray("playlists");
            if (arr == null) return;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Playlist p = new Playlist(o.getLong("id"), o.optString("name", "Playlist"));
                JSONArray ids = o.optJSONArray("songs");
                if (ids != null) for (int j = 0; j < ids.length(); j++) p.songIds.add(ids.getLong(j));
                playlists.add(p);
                nextId = Math.max(nextId, p.id + 1);
            }
        } catch (Exception ignored) {
        }
    }
}
