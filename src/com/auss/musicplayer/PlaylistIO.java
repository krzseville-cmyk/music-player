package com.auss.musicplayer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** UTF-8 M3U. Resolve local paths/names only; never fetch a URL from a playlist. */
public final class PlaylistIO {
    public static final class Import {
        public final List<Song> songs = new ArrayList<>();
        public int missing;
    }
    public static String export(List<Song> songs) {
        StringBuilder out = new StringBuilder("#EXTM3U\n");
        for (Song s : songs) {
            out.append("#EXTINF:").append(s.duration / 1000).append(',')
                    .append(clean(s.artist)).append(" - ").append(clean(s.title)).append('\n');
            out.append(clean(s.folder + s.fileName)).append('\n');
        }
        return out.toString();
    }
    private static String clean(String s) { return s.replace('\n', ' ').replace('\r', ' '); }
    public static Import read(Reader input, List<Song> library) throws IOException {
        Map<String, Song> paths = new HashMap<>();
        Map<String, List<Song>> names = new HashMap<>();
        for (Song s : library) {
            paths.put(normalize(s.folder + s.fileName), s);
            paths.put(s.uri.toString(), s);
            names.computeIfAbsent(s.fileName.toLowerCase(java.util.Locale.ROOT), key -> new ArrayList<>()).add(s);
        }
        Import result = new Import();
        BufferedReader reader = new BufferedReader(input);
        String line;
        int total = 0;
        while ((line = reader.readLine()) != null) {
            if (++total > 50000) throw new IOException("Playlist is too large");
            line = line.replace("\uFEFF", "").trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String path = normalize(line);
            Song match = paths.get(path);
            if (match == null) {
                String name = path.substring(path.lastIndexOf('/') + 1).toLowerCase(java.util.Locale.ROOT);
                List<Song> candidates = names.get(name);
                if (candidates != null && candidates.size() == 1) match = candidates.get(0);
                else if (candidates != null) {
                    // Absolute Android paths can have a different storage prefix.
                    for (Song s : candidates) if (path.endsWith("/" + normalize(s.folder + s.fileName))) {
                        if (match != null) { match = null; break; }
                        match = s;
                    }
                }
            }
            if (match != null) result.songs.add(match); else result.missing++;
        }
        return result;
    }
    private static String normalize(String path) { return path.replace('\\', '/').replaceFirst("^file://", "").replaceFirst("^\\./", ""); }
}
