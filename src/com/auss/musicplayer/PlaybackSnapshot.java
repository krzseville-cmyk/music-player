package com.auss.musicplayer;

import android.content.Context;
import android.net.Uri;
import android.util.AtomicFile;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Atomic checkpoint; restoring never starts playback by itself. */
final class PlaybackSnapshot {
    final List<Song> queue = new ArrayList<>();
    final List<Song> original = new ArrayList<>();
    int index;
    int position;
    private final AtomicFile file;
    PlaybackSnapshot(Context context) { file = new AtomicFile(new File(context.getFilesDir(), "playback.json")); }
    void save(List<Song> songs, List<Song> order, int current, int ms) {
        FileOutputStream out = null;
        try {
            JSONObject root = new JSONObject();
            root.put("queue", encode(songs));
            root.put("original", encode(order));
            root.put("index", current);
            root.put("position", ms);
            out = file.startWrite();
            out.write(root.toString().getBytes(StandardCharsets.UTF_8));
            file.finishWrite(out);
        } catch (Exception e) { if (out != null) file.failWrite(out); }
    }
    private JSONArray encode(List<Song> songs) throws Exception {
        JSONArray array = new JSONArray();
        for (Song s : songs) {
            JSONObject o = new JSONObject();
            o.put("id", s.id).put("title", s.title).put("artist", s.artist).put("album", s.album)
                    .put("albumId", s.albumId).put("duration", s.duration).put("track", s.track)
                    .put("uri", s.uri.toString()).put("folder", s.folder).put("fileName", s.fileName)
                    .put("dateAdded", s.dateAdded);
            array.put(o);
        }
        return array;
    }
    void read() {
        try {
            JSONObject root = new JSONObject(new String(file.readFully(), StandardCharsets.UTF_8));
            decode(root.getJSONArray("queue"), queue);
            decode(root.getJSONArray("original"), original);
            index = root.optInt("index", 0);
            position = root.optInt("position", 0);
        } catch (Exception ignored) { queue.clear(); original.clear(); }
    }
    private void decode(JSONArray a, List<Song> target) throws Exception {
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.getJSONObject(i);
            Song s = new Song(o.getLong("id"), o.getString("title"), o.getString("artist"),
                    o.getString("album"), o.getLong("albumId"), o.getLong("duration"),
                    o.getInt("track"), Uri.parse(o.getString("uri")));
            s.folder = o.optString("folder"); s.fileName = o.optString("fileName"); s.dateAdded = o.optLong("dateAdded");
            target.add(s);
        }
    }
}
