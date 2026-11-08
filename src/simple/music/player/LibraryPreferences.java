package simple.music.player;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class LibraryPreferences {
    final SharedPreferences prefs;
    public LibraryPreferences(Context context) { prefs = context.getSharedPreferences("library", 0); }
    public boolean favorite(Song s) { return prefs.getStringSet("favorites", Collections.emptySet()).contains(s.uri.toString()); }
    public void toggleFavorite(Song s) {
        Set<String> set = new HashSet<>(prefs.getStringSet("favorites", Collections.emptySet()));
        if (!set.remove(s.uri.toString())) set.add(s.uri.toString());
        prefs.edit().putStringSet("favorites", set).apply();
    }
    public void played(Song s) {
        String key = s.uri.toString();
        prefs.edit().putLong("last:" + key, System.currentTimeMillis())
                .putInt("count:" + key, count(s) + 1).apply();
    }
    public long last(Song s) { return prefs.getLong("last:" + s.uri, 0); }
    public int count(Song s) { return prefs.getInt("count:" + s.uri, 0); }
    public Set<String> excluded() { return new HashSet<>(prefs.getStringSet("excluded", Collections.emptySet())); }
    public void exclude(Set<String> folders) { prefs.edit().putStringSet("excluded", new HashSet<>(folders)).apply(); }
    public boolean visible(Song s) { return !excluded().contains(s.folder); }
    public String theme() { return prefs.getString("theme", "ember"); }
    public void theme(String value) { prefs.edit().putString("theme", value).apply(); }
    public int sort() { return prefs.getInt("sort", 0); }
    public void sort(int value) { prefs.edit().putInt("sort", value).apply(); }
    public List<Song> sorted(List<Song> input) {
        List<Song> result = new ArrayList<>(input);
        Comparator<Song> byTitle = (a,b) -> a.title.compareToIgnoreCase(b.title);
        Comparator<Song> compare;
        switch (sort()) {
            case 1: compare = byTitle.reversed(); break;
            case 2: compare = (a,b) -> a.artist.compareToIgnoreCase(b.artist); break;
            case 3: compare = (a,b) -> a.album.compareToIgnoreCase(b.album); break;
            case 4: compare = (a,b) -> Long.compare(b.dateAdded, a.dateAdded); break;
            case 5: compare = (a,b) -> Long.compare(b.duration, a.duration); break;
            default: compare = byTitle;
        }
        Collections.sort(result, compare.thenComparing(byTitle));
        return result;
    }
}
