package com.auss.musicplayer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Activity-owned document pickers and feature dialogs. All document IO runs off the UI thread. */
final class PlayerTools {
    private static final int IMPORT = 40, EXPORT = 41, LRC = 42, MP3 = 43, COVER = 44;
    private final MainActivity host;
    private final LibraryPreferences prefs;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Song lyricsSong;
    private Uri editUri;
    private String editTitle = "", editArtist = "", editAlbum = "";
    private byte[] cover;
    private AlertDialog lyricDialog;
    private AlertDialog tagDialog;
    PlayerTools(MainActivity host, LibraryPreferences prefs) { this.host = host; this.prefs = prefs; }
    void close() { handler.removeCallbacksAndMessages(null); if (lyricDialog != null) lyricDialog.dismiss(); if (tagDialog != null) tagDialog.dismiss(); io.shutdown(); }
    private AlertDialog.Builder dialog(String title) {
        return new AlertDialog.Builder(host, "light".equals(prefs.theme())
                ? android.R.style.Theme_Material_Light_Dialog_Alert : android.R.style.Theme_Material_Dialog_Alert).setTitle(title).setOnDismissListener(d -> host.refreshSettings());
    }
    private void ui(Runnable run) { handler.post(() -> { if (!host.isDestroyed() && !host.isFinishing()) run.run(); }); }
    private void message(String text) { ui(() -> Toast.makeText(host, text, Toast.LENGTH_LONG).show()); }
    private int dp(int value) { return (int)(value * host.getResources().getDisplayMetrics().density + .5f); }
    private LinearLayout column() { LinearLayout v = new LinearLayout(host); v.setOrientation(1); v.setPadding(dp(24), dp(12), dp(24), dp(12)); return v; }
    private TextView label(String text) { TextView v = new TextView(host); v.setText(text); v.setTextSize(15); v.setTextColor(host.textColor()); v.setPadding(0, dp(8), 0, dp(8)); return v; }
    private void pick(int code, String type) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(type)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        if (code == MP3) intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        try { host.startActivityForResult(intent, code); }
        catch (android.content.ActivityNotFoundException e) { message("No document picker is installed on this device."); }
    }
    void settings() { host.showSettings(); }
    void importPlaylist() { pick(IMPORT, "*/*"); }
    void sort() {
        dialog("Sort songs").setSingleChoiceItems(new String[]{"Title · A–Z", "Title · Z–A", "Artist", "Album", "Recently added", "Duration · longest first"}, prefs.sort(), (d, which) -> {
            prefs.sort(which); host.repaint(); d.dismiss();
        }).setNegativeButton("Cancel", null).show();
    }
    void theme() {
        String[] values = {"ember", "purple", "light"};
        int selected = Arrays.asList(values).indexOf(prefs.theme());
        dialog("Theme").setSingleChoiceItems(new String[]{"Dark · Orange", "Dark · Purple", "Light"}, selected, (d, which) -> {
            prefs.theme(values[which]); d.dismiss(); host.recreate();
        }).setNegativeButton("Cancel", null).show();
    }
    void excludeFolders() {
        TreeSet<String> folders = new TreeSet<>();
        for (Song song : host.scannedSongs()) folders.add(song.folder);
        if (folders.isEmpty()) { message("Scan your music library first."); return; }
        String[] paths = folders.toArray(new String[0]);
        String[] names = paths.clone();
        Set<String> excluded = prefs.excluded();
        boolean[] checked = new boolean[paths.length];
        for (int i=0;i<paths.length;i++) { checked[i] = excluded.contains(paths[i]); if (names[i].isEmpty()) names[i] = "Storage root"; }
        dialog("Hide these folders").setMultiChoiceItems(names, checked, (d, which, value) -> {
            if (value) excluded.add(paths[which]); else excluded.remove(paths[which]);
        }).setPositiveButton("Apply", (d,w) -> { prefs.exclude(excluded); host.reloadLibrary(); })
                .setNegativeButton("Cancel", null).show();
    }
    void sleepTimer() {
        MusicService player = host.player();
        if (player == null) { message("The player is connecting. Try again."); return; }
        String[] options = {"Off", "15 minutes", "30 minutes", "45 minutes", "60 minutes", "End of this song"};
        int[] minutes = {0,15,30,45,60,0};
        dialog("Sleep timer · " + player.sleepDescription()).setItems(options, (d, which) -> {
            player.setSleepTimer(minutes[which], which == 5); message(player.sleepDescription());
        }).setNegativeButton("Cancel", null).show();
    }
    void sound() { host.showSoundSettings(); }
    void exportPlaylist(String name, List<Song> songs) {
        prefs.prefs.edit().putString("pendingM3U", PlaylistIO.export(songs)).apply();
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("audio/x-mpegurl").putExtra(Intent.EXTRA_TITLE, name.replaceAll("[/\\\\]", "_") + ".m3u");
        try { host.startActivityForResult(intent, EXPORT); } catch (android.content.ActivityNotFoundException e) { message("No document picker is installed."); }
    }
    void lyrics(Song song) {
        lyricsSong = song;
        prefs.prefs.edit().putString("lyricsTarget", song.uri.toString()).apply();
        io.execute(() -> {
            String text = "";
            try {
                String linked = prefs.prefs.getString("lyrics:" + song.uri, "");
                if (!linked.isEmpty()) {
                    try (InputStream in = host.getContentResolver().openInputStream(Uri.parse(linked))) { text = readText(in, 1024*1024); }
                } else {
                    try (InputStream in = host.getContentResolver().openInputStream(song.uri)) { text = Mp3Metadata.lyrics(in); }
                }
            } catch (Exception ignored) { }
            final String loaded = text;
            ui(() -> showLyrics(song, loaded));
        });
    }
    private void showLyrics(Song song, String source) {
        if (lyricDialog != null) lyricDialog.dismiss();
        Lyrics lyrics;
        try { lyrics = new Lyrics(source); } catch (RuntimeException e) { message("This lyrics file contains invalid timestamps."); return; }
        LinearLayout col = column();
        TextView artist = label(song.artist); artist.setTextColor(host.mutedColor()); col.addView(artist);
        ScrollView scroll = new ScrollView(host); LinearLayout lines = column(); lines.setPadding(0,0,0,0); scroll.addView(lines);
        List<TextView> views = new ArrayList<>();
        if (lyrics.lines.isEmpty()) {
            TextView plain = label(source.isEmpty() ? "Bring the words along.\n\nAttach a .lrc or .txt file, or use embedded MP3 lyrics." : source);
            plain.setTextIsSelectable(true); plain.setTextSize(18); lines.addView(plain);
        } else for (Lyrics.Line line : lyrics.lines) {
            TextView view = label(line.text.isEmpty() ? "♪" : line.text); view.setTextSize(20); view.setPadding(0,dp(12),0,dp(12));
            view.setOnClickListener(v -> { MusicService p = host.player(); if (p != null && p.getCurrentSong() != null && p.getCurrentSong().uri.equals(song.uri)) p.seekTo((int)line.time); });
            views.add(view); lines.addView(view);
        }
        col.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(320)));
        lyricDialog = dialog(song.title).setView(col).setPositiveButton("Done",null)
                .setNeutralButton("Attach lyrics…", (d,w) -> pick(LRC,"*/*"))
                .setNegativeButton("Unlink", (d,w) -> { prefs.prefs.edit().remove("lyrics:" + song.uri).apply(); message("Linked lyrics removed."); }).create();
        final AlertDialog current = lyricDialog;
        Runnable follow = new Runnable() {
            int previous = -2;
            @Override public void run() {
                if (!current.isShowing()) return;
                MusicService player = host.player();
                if (player != null && player.getCurrentSong() != null && player.getCurrentSong().uri.equals(song.uri)) {
                    int active = lyrics.active(player.getPosition());
                    if (active != previous) {
                        for (int i=0;i<views.size();i++) views.get(i).setTextColor(i==active ? host.accentColor() : host.mutedColor());
                        if (active >= 0 && active < views.size()) scroll.smoothScrollTo(0, Math.max(0, views.get(active).getTop() - dp(100)));
                        previous = active;
                    }
                }
                handler.postDelayed(this,250);
            }
        };
        current.setOnDismissListener(d -> handler.removeCallbacks(follow));
        current.show(); handler.post(follow);
    }
    void openAudio(Uri uri) {
        io.execute(() -> {
            try {
                Song song = describe(uri);
                ui(() -> { if (host.player()!=null) host.player().playQueue(Collections.singletonList(song),0); });
            } catch (Exception e) { message("Cannot open this audio file: " + e.getMessage()); }
        });
    }
    private Song describe(Uri uri) throws Exception {
        String title, artist, album; long duration;
        MediaMetadataRetriever reader = new MediaMetadataRetriever();
        try {
            reader.setDataSource(host, uri);
            title = reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
            artist = reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
            album = reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
            String value = reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            duration = value == null ? 0 : Long.parseLong(value);
        } finally { reader.release(); }
        Song song = new Song(-Math.abs((long)uri.toString().hashCode())-1, title == null ? fileName(uri) : title,
                artist == null ? "Unknown artist" : artist, album == null ? "" : album, -1, duration, 0, uri);
        song.fileName = fileName(uri);
        return song;
    }
    private String fileName(Uri uri) {
        try (Cursor c = host.getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)) {
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Exception ignored) { }
        return uri.getLastPathSegment() == null ? "Audio file" : uri.getLastPathSegment();
    }
    void chooseMp3() { pick(MP3,"audio/mpeg"); }
    private void editTags() {
        if (editUri == null) return;
        LinearLayout col = column();
        col.addView(label("Changes are saved to the selected MP3 file."));
        EditText title = field(col,"Title",editTitle), artist = field(col,"Artist",editArtist), album = field(col,"Album",editAlbum);
        Button art = new Button(host); art.setText(cover == null ? "Choose cover artwork…" : "Cover artwork selected");
        art.setOnClickListener(v -> {
            editTitle = title.getText().toString(); editArtist = artist.getText().toString(); editAlbum = album.getText().toString();
            tagDialog.dismiss(); pick(COVER,"image/*");
        }); col.addView(art);
        tagDialog = dialog("Edit MP3 tags").setView(col).setNegativeButton("Cancel",null)
                .setPositiveButton("Save to file",(d,w) -> {
                    final Uri target = editUri; final byte[] image = cover;
                    final String t = title.getText().toString(), a = artist.getText().toString(), al = album.getText().toString();
                    if (t.trim().isEmpty()) { message("A title is required."); return; }
                    if (host.player() != null) host.player().pause();
                    io.execute(() -> saveTags(target,t,a,al,image));
                }).show();
    }
    private EditText field(LinearLayout col,String label,String value) {
        col.addView(label(label)); EditText input = new EditText(host); input.setText(value); input.setTextColor(host.textColor()); input.setSingleLine(); col.addView(input); return input;
    }
    private void saveTags(Uri target, String title, String artist, String album, byte[] image) {
        File backup = new File(host.getFilesDir(), "tag-backup-" + System.currentTimeMillis() + ".mp3");
        File changed = new File(host.getCacheDir(), "tag-edited.mp3");
        boolean writing = false;
        try {
            try (InputStream in = host.getContentResolver().openInputStream(target); OutputStream out = new FileOutputStream(backup)) { Mp3Metadata.copy(in,out); }
            Mp3Metadata.rewrite(backup,changed,title,artist,album,image);
            try (InputStream in = new FileInputStream(changed); OutputStream out = host.getContentResolver().openOutputStream(target,"rwt")) {
                if (out == null) throw new IOException("The provider cannot write this file");
                writing = true; Mp3Metadata.copy(in,out);
            }
            writing = false;
            backup.delete(); changed.delete();
            // Ask the provider to invalidate its metadata; MediaStore scanning can finish asynchronously.
            try { host.getContentResolver().notifyChange(target,null); } catch (RuntimeException ignored) { }
            ArtLoader.clear();
            refreshMetadata(target);
            ui(() -> { host.reloadLibrary(); message("MP3 tags saved. Rescan if your file provider updates metadata later."); });
        } catch (Exception e) {
            if (writing) {
                try (InputStream in = new FileInputStream(backup); OutputStream out = host.getContentResolver().openOutputStream(target,"rwt")) {
                    if (out == null) throw new IOException(); Mp3Metadata.copy(in,out);
                } catch (Exception restore) { message("Write failed. The original backup is retained in app storage; keep the app installed."); return; }
            }
            backup.delete(); changed.delete(); message("Tags were not changed: " + e.getMessage());
        }
    }
    private void refreshMetadata(Uri target) {
        String path = null;
        try {
            if ("com.android.externalstorage.documents".equals(target.getAuthority())) {
                String id = android.provider.DocumentsContract.getDocumentId(target);
                if (id.startsWith("primary:")) path = new File(android.os.Environment.getExternalStorageDirectory(),id.substring(8)).getPath();
            } else {
                try (Cursor c = host.getContentResolver().query(target,new String[]{"_data"},null,null,null)) {
                    if (c != null && c.moveToFirst()) path = c.getString(0);
                }
            }
        } catch (Exception ignored) { }
        if (path != null) MediaScannerConnection.scanFile(host,new String[]{path},new String[]{"audio/mpeg"},
                (p,uri) -> ui(host::reloadLibrary));
    }
    void onResult(int request, int result, Intent data) {
        if (result != Activity.RESULT_OK || data == null || data.getData() == null) { if (request==COVER) editTags(); return; }
        final Uri uri = data.getData();
        try { host.getContentResolver().takePersistableUriPermission(uri, data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION)); }
        catch (SecurityException ignored) { }
        if (request == EXPORT) {
            final String content = prefs.prefs.getString("pendingM3U", "");
            io.execute(() -> {
                try (OutputStream out = host.getContentResolver().openOutputStream(uri,"wt")) {
                    if (content.isEmpty() || out == null) throw new IOException("Playlist export is unavailable. Try again.");
                    out.write(content.getBytes(StandardCharsets.UTF_8)); message("Playlist exported.");
                } catch (Exception e) { message("Export failed: " + e.getMessage()); }
            });
        } else if (request == IMPORT) {
            List<Song> library = host.librarySongs();
            io.execute(() -> {
                try (InputStream in = host.getContentResolver().openInputStream(uri); Reader reader = new InputStreamReader(in,StandardCharsets.UTF_8)) {
                    PlaylistIO.Import imported = PlaylistIO.read(reader,library);
                    if (imported.songs.isEmpty()) { message("No matching local songs found. Scan the library first; duplicate filenames need matching folder paths."); return; }
                    String name = fileName(uri).replaceFirst("(?i)\\.m3u8?$", "");
                    ui(() -> { PlaylistStore.Playlist p = host.playlistStore().create(name); host.playlistStore().addSongs(p,imported.songs); host.repaint(); message("Imported " + imported.songs.size() + " songs · " + imported.missing + " unmatched"); });
                } catch (Exception e) { message("Import failed: " + e.getMessage()); }
            });
        } else if (request == LRC) {
            String target = prefs.prefs.getString("lyricsTarget", "");
            prefs.prefs.edit().putString("lyrics:" + target,uri.toString()).apply();
            if (lyricsSong != null) lyrics(lyricsSong); else message("Lyrics linked. Open the song’s lyrics to view them.");
        } else if (request == MP3) {
            io.execute(() -> {
                try { Song song = describe(uri); ui(() -> { editUri = uri; cover = null; editTitle = song.title; editArtist = song.artist; editAlbum = song.album; editTags(); }); }
                catch (Exception e) { message("Cannot read that MP3 file."); }
            });
        } else if (request == COVER) {
            io.execute(() -> {
                try (InputStream in = host.getContentResolver().openInputStream(uri)) {
                    byte[] bytes = readBytes(in, 5*1024*1024);
                    BitmapFactory.Options options = new BitmapFactory.Options(); options.inJustDecodeBounds = true;
                    BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
                    if (options.outWidth <= 0) throw new IOException("Choose a JPEG or PNG image");
                    if (!"image/jpeg".equals(options.outMimeType) && !"image/png".equals(options.outMimeType)) throw new IOException("Choose a JPEG or PNG image");
                    ui(() -> { cover = bytes; editTags(); });
                } catch (Exception e) { message(e.getMessage()); ui(this::editTags); }
            });
        }
    }
    private static String readText(InputStream in,int limit) throws IOException { return new String(readBytes(in,limit),StandardCharsets.UTF_8).replace("\uFEFF", ""); }
    private static byte[] readBytes(InputStream in,int limit) throws IOException {
        if (in == null) throw new IOException("File is unavailable");
        ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int n;
        while ((n=in.read(buffer))>=0) { if (out.size()+n>limit) throw new IOException("File is too large"); out.write(buffer,0,n); }
        return out.toByteArray();
    }
}
