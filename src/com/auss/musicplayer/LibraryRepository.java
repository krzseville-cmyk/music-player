package com.auss.musicplayer;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import java.util.ArrayList;
import java.util.List;

/** One source of local music for the phone UI and Android Auto. */
public final class LibraryRepository {
    private LibraryRepository() { }
    public static List<Song> load(Context context) {
        List<Song> songs = new ArrayList<>();
        String location = Build.VERSION.SDK_INT >= 29 ? "relative_path" : "_data";
        String[] projection = {"_id", "title", "artist", "album", "album_id", "duration", "track",
                "_display_name", "date_added", location};
        try (Cursor c = context.getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection, "is_music != 0", null, "title COLLATE NOCASE ASC")) {
            if (c == null) return songs;
            while (c.moveToNext()) {
                long id = c.getLong(0);
                Song s = new Song(id, value(c, 1, "Unknown title"), value(c, 2, "Unknown artist"),
                        value(c, 3, ""), c.getLong(4), c.getLong(5), c.getInt(6),
                        Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, "" + id));
                s.fileName = value(c, 7, "");
                s.dateAdded = c.getLong(8);
                s.folder = value(c, 9, "");
                if (Build.VERSION.SDK_INT < 29) {
                    int slash = s.folder.lastIndexOf('/');
                    s.folder = slash >= 0 ? s.folder.substring(0, slash + 1) : "";
                }
                songs.add(s);
            }
        }
        return songs;
    }
    private static String value(Cursor c, int column, String fallback) {
        String s = c.getString(column);
        return s == null || s.isEmpty() || "<unknown>".equals(s) ? fallback : s;
    }
}
