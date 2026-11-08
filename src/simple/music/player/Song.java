package simple.music.player;

import android.net.Uri;

public class Song {
    public String folder = "";
    public String fileName = "";
    public long dateAdded;
    public final long id;
    public final long albumId;
    public final long duration;
    public final int track;
    public final String title;
    public final String artist;
    public final String album;
    public final Uri uri;

    public Song(long id, String title, String artist, String album, long albumId, long duration, int track, Uri uri) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.albumId = albumId;
        this.duration = duration;
        this.track = track;
        this.uri = uri;
    }
}
