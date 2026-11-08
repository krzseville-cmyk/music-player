package simple.music.player;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ScrollView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class MainActivity extends Activity implements MusicService.Listener {

    private static final int REQ_PERMS = 1;

    private int BG = 0xFF141210;
    private int SURFACE = 0xFF211D19;
    private int SURFACE2 = 0xFF322A23;
    private int ACCENT = 0xFFFFB07A;
    private int ON_ACCENT = 0xFF301909;
    private int TEXT = 0xFFFFF7ED;
    private int SUBTEXT = 0xFFBFAF9E;
    private int NP_DEFAULT_TINT = 0xFF493022;
    private AppTheme appTheme;
    AppTheme appTheme(){return appTheme;}

    private static final int LIST_ART_PX = 192;
    private static final int GRID_ART_PX = 480;
    private static final int PLAYER_ART_PX = 1000;

    private static final int TAB_SONGS = 0, TAB_ALBUMS = 1, TAB_ARTISTS = 4, TAB_PLAYLISTS = 3, TAB_FAVORITES = 2, TAB_FOLDERS = 5, TAB_HISTORY = 6;
    private static final String[] TAB_NAMES = {"Songs", "Albums", "Favorites", "Playlists", "Artists", "Folders", "History"};
    private static final int DETAIL_NONE = 0, DETAIL_ALBUM = 1, DETAIL_ARTIST = 2, DETAIL_PLAYLIST = 3;

    // ---- Library model ----

    static final class Album {
        long id;
        String name;
        String artist;
        final List<Song> songs = new ArrayList<>();
    }

    static final class Artist {
        String name;
        int albumCount;
        final List<Song> songs = new ArrayList<>();
    }

    /** A tappable row such as "Shuffle all" or "New playlist". */
    static final class Action {
        final int icon;
        final String label;
        final Runnable run;

        Action(int icon, String label, Runnable run) {
            this.icon = icon;
            this.label = label;
            this.run = run;
        }
    }

    /** A screen in the navigation stack (a tab, or an album / artist / playlist page). */
    static final class Nav {
        final int type;
        final Album album;
        final Artist artist;
        final long playlistId;

        Nav(int type, Album album, Artist artist, long playlistId) {
            this.type = type;
            this.album = album;
            this.artist = artist;
            this.playlistId = playlistId;
        }
    }

    interface NameCallback {
        void onName(String name);
    }

    interface Reorder {
        boolean move(int from, int to);

        void done();
    }

    private final List<Song> allSongs = new ArrayList<>();
    private final List<Album> albums = new ArrayList<>();
    private final List<Artist> artists = new ArrayList<>();
    private final Map<Long, Song> songById = new HashMap<>();
    private final Map<Long, Album> albumById = new HashMap<>();
    private final Map<String, Artist> artistByKey = new HashMap<>();
    private PlaylistStore playlists;
    private LibraryPreferences preferences;
    private PlayerTools tools;
    private LyricsRepository lyricsRepository;
    private NowPlayingLyrics npLyrics;
    private FrameLayout npArtBox;
    private TextView npLyricsToggle,npLyricsHint;
    private boolean lyricsForced;
    private String boundLyricsUri="";
    LyricsRepository lyricsRepository(){return lyricsRepository;}
    void attachLyrics(Song song){tools.attachLyrics(song);}
    void reloadLyricsFor(String uri){
        if(npLyrics!=null&&npLyrics.songUri().equals(uri)){
            lyricsForced=true;npLyrics.reload();showNowPlaying();setLyricsVisible(true);
        }
    }
    void showSongLyrics(Song song){
        if(service==null||service.getCurrentSong()==null||!service.getCurrentSong().uri.equals(song.uri))return;
        showNowPlaying();lyricsForced=true;preferences.prefs.edit().putBoolean("lyricsOnPlayer",true).apply();setLyricsVisible(true);
    }
    void showCover(){lyricsForced=false;preferences.prefs.edit().putBoolean("lyricsOnPlayer",false).apply();setLyricsVisible(false);}
    void onLyricsReady(boolean available){
        if(npLyricsHint!=null)npLyricsHint.setText(available?"LYRICS  ·  Tap to view":"LYRICS  ·  Find or add lyrics");
        if(available&&preferences.prefs.getBoolean("lyricsOnPlayer",true))setLyricsVisible(true);
        else if(!available&&!lyricsForced)setLyricsVisible(false);
    }
    private void setLyricsVisible(boolean visible){
        if(npLyrics==null)return;
        npLyrics.setVisibility(visible?View.VISIBLE:View.GONE);npArt.setVisibility(visible?View.GONE:View.VISIBLE);
        if(npLyricsToggle!=null)npLyricsToggle.setText(visible?"Cover":"Lyrics");
        LinearLayout.LayoutParams params=(LinearLayout.LayoutParams)npArtBox.getLayoutParams();
        params.height=dp(visible?300:180);npArtBox.setLayoutParams(params);
        if(visible)npLyrics.reveal();
    }
    private SettingsScreen settingsScreen;
    private FrameLayout screenRoot;
    private android.window.OnBackInvokedCallback backCallback;
    void showSettings() { openSettings("root",false); }
    void showSoundSettings() { openSettings("sound",false); }
    void showAppearanceSettings() { openSettings("appearance",false); }
    private void openSettings(String page,boolean parent) {
        hideKeyboard();
        settingsScreen.open(page,parent);
        setSettingsAccessibility(true);
    }
    void closeSettings() {
        settingsScreen.setVisibility(View.GONE);
        setSettingsAccessibility(false);
    }
    void refreshSettings() { if(settingsScreen!=null)settingsScreen.refresh(); }
    private void setSettingsAccessibility(boolean visible) {
        if(screenRoot==null)return;
        for(int i=0;i<screenRoot.getChildCount();i++)if(screenRoot.getChildAt(i)!=settingsScreen)
            screenRoot.getChildAt(i).setImportantForAccessibility(visible?View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS:View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
    }
    private final List<Song> scannedSongs = new ArrayList<>();
    private String selectedFolder;
    private boolean mostPlayed;
    private Intent pendingIntent;
    private TextView favoriteButton;
    private TextView timerButton;
    private View overview;
    private TextView overviewTitle, overviewSubtitle, overviewLabel, overviewAction;
    private TextView favoritesShortcut, historyShortcut, sectionTitle, sortLabel;
    private ImageView overviewArt, nextArt;
    private TextView nextTitle, nextSubtitle, playbackInfo;
    private long overviewArtId = Long.MIN_VALUE;
    private long nextArtId = Long.MIN_VALUE;
    MusicService player() { return service; }
    List<Song> librarySongs() { return new ArrayList<>(allSongs); }
    List<Song> scannedSongs() { return new ArrayList<>(scannedSongs); }
    PlaylistStore playlistStore() { return playlists; }
    void reloadLibrary() { loadSongs(); }
    void repaint() { refresh(); onPlayerChanged(); }
    int accentColor() { return ACCENT; }
    int textColor() { return TEXT; }
    int mutedColor() { return SUBTEXT; }
    int surfaceColor() { return SURFACE; }
    int backgroundColor() { return BG; }
    private void applyTheme() {
        appTheme=AppTheme.from(this,preferences.theme());
        BG=appTheme.background;SURFACE=appTheme.surface;SURFACE2=appTheme.raised;ACCENT=appTheme.accent;
        ON_ACCENT=appTheme.onAccent;TEXT=appTheme.text;SUBTEXT=appTheme.muted;NP_DEFAULT_TINT=appTheme.player;
        setTheme(appTheme.appStyle);
    }

    /** Rows currently shown in the main list: Song, Album, Artist, Playlist, Action or String (header). */
    private final List<Object> rows = new ArrayList<>();
    /** For a playlist page: index into the playlist's song ids for each row. */
    private final List<Integer> rowPlaylistIndex = new ArrayList<>();
    private final List<Album> shownAlbums = new ArrayList<>();
    private final List<Nav> navStack = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private int tab = TAB_SONGS;
    private Nav detail = new Nav(DETAIL_NONE, null, null, 0);

    // Main screen views
    private RowAdapter rowAdapter;
    private AlbumAdapter albumAdapter;
    private ListView listView;
    private GridView gridView;
    private TextView emptyView;
    private TextView countView;
    private EditText searchBox;
    private final TextView[] tabViews = new TextView[TAB_NAMES.length];
    private View detailBar;
    private ImageView detailArt;
    private TextView detailTitle;
    private TextView detailSubtitle;
    private DragController listDrag;

    // Mini player
    private View miniPlayer;
    private ImageView miniArt;
    private TextView miniTitle;
    private TextView miniArtist;
    private ImageButton miniPlay;
    private ProgressBar miniProgress;

    // Now Playing
    private View npView;
    private GradientDrawable npBackground;
    private ImageView npArt;
    private TextView npTitle;
    private TextView npArtist;
    private TextView npPosition;
    private TextView npPos;
    private TextView npDur;
    private SeekBar npSeek;
    private ImageButton npPlay;
    private ImageButton npShuffle;
    private ImageButton npRepeat;
    private long npArtSongId = -1;

    // Queue
    private View queueView;
    private ListView queueList;
    private QueueAdapter queueAdapter;
    private TextView queueCount;

    private MusicService service;
    private boolean bound;
    private boolean userSeeking;
    private boolean songsLoaded;
    private boolean emptyActionable;
    private boolean pendingOpenPlayer;
    private String libraryMessage = "";

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            updateProgress();
            handler.postDelayed(this, 500);
        }
    };

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            service = ((MusicService.LocalBinder) binder).getService();
            service.addListener(MainActivity.this);
            onPlayerChanged();
            handlePendingIntent();
            if (pendingOpenPlayer) {
                pendingOpenPlayer = false;
                showNowPlaying();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            service = null;
        }
    };

    // ---- Lifecycle ----

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        preferences = new LibraryPreferences(this);
        applyTheme();
        super.onCreate(savedInstanceState);
        playlists = new PlaylistStore(this);
        lyricsRepository=new LyricsRepository(this);
        tools = new PlayerTools(this, preferences);
        pendingIntent = getIntent();
        View root = buildUi();
        setContentView(root);
        setupWindow(root);
        if(savedInstanceState!=null && savedInstanceState.containsKey("settingsPage"))
            openSettings(savedInstanceState.getString("settingsPage","root"),savedInstanceState.getBoolean("settingsParent",false));
        if(Build.VERSION.SDK_INT>=33) {
            backCallback=this::onBackPressed;
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,backCallback);
        }

        Intent intent = new Intent(this, MusicService.class);
        startService(intent);
        bound = bindService(intent, connection, BIND_AUTO_CREATE);

        pendingOpenPlayer = getIntent().getBooleanExtra(MusicService.EXTRA_OPEN_PLAYER, false);
        checkPermissionsAndLoad();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        pendingIntent = intent;
        handlePendingIntent();
        if (intent.getBooleanExtra(MusicService.EXTRA_OPEN_PLAYER, false)) {
            if (service != null) showNowPlaying();
            else pendingOpenPlayer = true;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Returning from the settings screen after granting the permission.
        if (!songsLoaded && hasAudioPermission()) loadSongs();
        refreshSettings();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        if(settingsScreen!=null && settingsScreen.getVisibility()==View.VISIBLE) {
            state.putString("settingsPage",settingsScreen.page());state.putBoolean("settingsParent",settingsScreen.hasRootParent());
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        handler.post(ticker);
    }

    @Override
    protected void onStop() {
        handler.removeCallbacks(ticker);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (service != null) service.removeListener(this);
        if (bound) unbindService(connection);
        if (tools != null) tools.close();
        if (lyricsRepository!=null)lyricsRepository.close();
        if(Build.VERSION.SDK_INT>=33 && backCallback!=null)getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backCallback);
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if(settingsScreen!=null && settingsScreen.back())return;
        if (queueView.getVisibility() == View.VISIBLE) {
            hideQueue();
        } else if (npView.getVisibility() == View.VISIBLE) {
            hideNowPlaying();
        } else if (detail.type != DETAIL_NONE) {
            navigateBack();
        } else if (query().length() > 0) {
            searchBox.setText("");
        } else {
            // Keep the music playing; just leave the app.
            moveTaskToBack(true);
        }
    }

    private void setupWindow(View root) {
        if ("light".equals(preferences.theme())) {
            int flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            getWindow().getDecorView().setSystemUiVisibility(flags);
        }
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            getWindow().setStatusBarColor(Color.TRANSPARENT);
            getWindow().setNavigationBarColor(Color.TRANSPARENT);
            root.setOnApplyWindowInsetsListener((v, insets) -> {
                Insets i = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime());
                ViewGroup g = (ViewGroup) v;
                for (int c = 0; c < g.getChildCount(); c++) {
                    g.getChildAt(c).setPadding(i.left, i.top, i.right, i.bottom);
                }
                return WindowInsets.CONSUMED;
            });
        } else {
            getWindow().setStatusBarColor(BG);
            getWindow().setNavigationBarColor(BG);
        }
    }

    // ---- Permissions & loading ----

    private String audioPermission() {
        return Build.VERSION.SDK_INT >= 33
                ? Manifest.permission.READ_MEDIA_AUDIO
                : Manifest.permission.READ_EXTERNAL_STORAGE;
    }

    private boolean hasAudioPermission() {
        return checkSelfPermission(audioPermission()) == PackageManager.PERMISSION_GRANTED;
    }

    private void checkPermissionsAndLoad() {
        List<String> needed = new ArrayList<>();
        if (!hasAudioPermission()) needed.add(audioPermission());
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (hasAudioPermission()) {
            loadSongs();
        } else {
            libraryMessage = "Allow access to your music to get started.\n\nTap here to grant permission.";
            refresh();
        }
        if (!needed.isEmpty()) requestPermissions(needed.toArray(new String[0]), REQ_PERMS);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        if (requestCode != REQ_PERMS) return;
        if (hasAudioPermission()) loadSongs();
    }

    private void onEmptyClicked() {
        if (!emptyActionable) return;
        if (hasAudioPermission()) {
            loadSongs();
        } else if (shouldShowRequestPermissionRationale(audioPermission())) {
            requestPermissions(new String[]{audioPermission()}, REQ_PERMS);
        } else {
            // Permanently denied (or first denial on some devices): open app settings.
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", getPackageName(), null));
            startActivity(i);
        }
    }

    private void loadSongs() {
        songsLoaded = true;
        libraryMessage = "Scanning your music…";
        refresh();
        new Thread(() -> {
            final List<Song> songs = querySongs();
            runOnUiThread(() -> {
                scannedSongs.clear(); scannedSongs.addAll(songs);
                List<Song> visible = new ArrayList<>();
                Set<String> excluded = preferences.excluded();
                for (Song song : songs) if (!excluded.contains(song.folder)) visible.add(song);
                buildLibrary(visible);
                npArtSongId = -1;
                if (service != null) service.refreshMetadata(visible);
                libraryMessage = "No music found on this device.\n\nCopy MP3 or other audio files to your phone "
                        + "(e.g. the Music folder), then tap here to rescan.";
                refresh();
            });
        }).start();
    }

    private List<Song> querySongs() {
        try { return LibraryRepository.load(this); }
        catch (Exception e) { return new ArrayList<>(); }
    }

    private void buildLibrary(List<Song> songs) {
        allSongs.clear();
        allSongs.addAll(songs);
        songById.clear();
        albums.clear();
        albumById.clear();
        artists.clear();
        artistByKey.clear();

        Map<Long, Album> albumMap = new LinkedHashMap<>();
        Map<String, Artist> artistMap = new LinkedHashMap<>();
        Map<String, Set<Long>> artistAlbums = new HashMap<>();
        for (Song s : songs) {
            songById.put(s.id, s);
            Album a = albumMap.get(s.albumId);
            if (a == null) {
                a = new Album();
                a.id = s.albumId;
                a.name = s.album.isEmpty() ? "Unknown album" : s.album;
                a.artist = s.artist;
                albumMap.put(s.albumId, a);
            } else if (!a.artist.equalsIgnoreCase(s.artist)) {
                a.artist = "Various artists";
            }
            a.songs.add(s);

            String key = artistKey(s.artist);
            Artist ar = artistMap.get(key);
            if (ar == null) {
                ar = new Artist();
                ar.name = s.artist;
                artistMap.put(key, ar);
                artistAlbums.put(key, new HashSet<Long>());
            }
            ar.songs.add(s);
            artistAlbums.get(key).add(s.albumId);
        }
        for (Album a : albumMap.values()) {
            Collections.sort(a.songs, (x, y) -> x.track != y.track
                    ? Integer.compare(x.track, y.track) : x.title.compareToIgnoreCase(y.title));
            albums.add(a);
        }
        Collections.sort(albums, (x, y) -> x.name.compareToIgnoreCase(y.name));
        albumById.putAll(albumMap);

        for (Map.Entry<String, Artist> e : artistMap.entrySet()) {
            Artist ar = e.getValue();
            ar.albumCount = artistAlbums.get(e.getKey()).size();
            Collections.sort(ar.songs, (x, y) -> {
                int c = x.album.compareToIgnoreCase(y.album);
                if (c != 0) return c;
                return x.track != y.track ? Integer.compare(x.track, y.track) : x.title.compareToIgnoreCase(y.title);
            });
            artists.add(ar);
        }
        Collections.sort(artists, (x, y) -> x.name.compareToIgnoreCase(y.name));
        artistByKey.putAll(artistMap);

        // Refresh pages that point at objects from a previous scan.
        if (detail.type == DETAIL_ALBUM || detail.type == DETAIL_ARTIST) {
            navStack.clear();
            detail = new Nav(DETAIL_NONE, null, null, 0);
        }
    }

    private static String artistKey(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }

    // ---- Navigation ----

    private String query() {
        return searchBox.getText().toString().trim().toLowerCase(Locale.getDefault());
    }

    private static boolean matches(String q, String... fields) {
        if (q.isEmpty()) return true;
        for (String f : fields) {
            if (f != null && f.toLowerCase(Locale.getDefault()).contains(q)) return true;
        }
        return false;
    }

    private void selectTab(int t) {
        selectedFolder = null;
        tab = t;
        navStack.clear();
        detail = new Nav(DETAIL_NONE, null, null, 0);
        resetSearchAndRefresh();
    }

    private void openDetail(Nav nav) {
        if (detail.type != DETAIL_NONE) navStack.add(detail);
        detail = nav;
        resetSearchAndRefresh();
    }

    private void navigateBack() {
        detail = navStack.isEmpty() ? new Nav(DETAIL_NONE, null, null, 0) : navStack.remove(navStack.size() - 1);
        resetSearchAndRefresh();
    }

    private void resetSearchAndRefresh() {
        if (searchBox.getText().length() > 0) searchBox.setText(""); // triggers refresh()
        else refresh();
        listView.setSelection(0);
        gridView.setSelection(0);
    }

    private void openAlbum(Album a) {
        openDetail(new Nav(DETAIL_ALBUM, a, null, 0));
    }

    private void openArtist(Artist a) {
        openDetail(new Nav(DETAIL_ARTIST, null, a, 0));
    }

    private void openPlaylist(PlaylistStore.Playlist p) {
        openDetail(new Nav(DETAIL_PLAYLIST, null, null, p.id));
    }

    private void goToAlbum(Song s) {
        Album a = albumById.get(s.albumId);
        if (a == null) return;
        hideOverlays();
        openAlbum(a);
    }

    private void goToArtist(Song s) {
        Artist a = artistByKey.get(artistKey(s.artist));
        if (a == null) return;
        hideOverlays();
        openArtist(a);
    }

    private void hideOverlays() {
        if (queueView.getVisibility() == View.VISIBLE) hideQueue();
        if (npView.getVisibility() == View.VISIBLE) hideNowPlaying();
    }

    private PlaylistStore.Playlist detailPlaylist() {
        return detail.type == DETAIL_PLAYLIST ? playlists.get(detail.playlistId) : null;
    }

    private List<Song> resolve(PlaylistStore.Playlist p) {
        List<Song> list = new ArrayList<>();
        for (long id : p.songIds) {
            Song s = songById.get(id);
            if (s != null) list.add(s);
        }
        return list;
    }

    /** All songs of the open album / artist / playlist page, ignoring the search filter. */
    private List<Song> detailSongs() {
        switch (detail.type) {
            case DETAIL_ALBUM: return detail.album.songs;
            case DETAIL_ARTIST: return detail.artist.songs;
            case DETAIL_PLAYLIST:
                PlaylistStore.Playlist p = detailPlaylist();
                return p != null ? resolve(p) : new ArrayList<Song>();
            default: return new ArrayList<>();
        }
    }

    // ---- Building the visible list ----

    private void refresh() {
        if (searchBox == null) return;
        String q = query();
        rows.clear();
        rowPlaylistIndex.clear();
        shownAlbums.clear();

        if (detail.type == DETAIL_PLAYLIST && detailPlaylist() == null) {
            detail = new Nav(DETAIL_NONE, null, null, 0);
        }
        updateChrome();
        updateOverview();
        countView.setText(allSongs.size() + (allSongs.size() == 1 ? " song" : " songs")
                + "  •  " + albums.size() + (albums.size() == 1 ? " album" : " albums")
                + "  •  " + artists.size() + (artists.size() == 1 ? " artist" : " artists"));

        if (allSongs.isEmpty() && !(detail.type == DETAIL_NONE && tab == TAB_PLAYLISTS)) {
            showEmpty(libraryMessage, true);
            return;
        }

        boolean grid = false;
        String emptyText = q.isEmpty() ? "Nothing here yet." : "No results for \"" + q + "\"";

        if (detail.type == DETAIL_ALBUM) {
            for (Song s : detail.album.songs) if (matches(q, s.title, s.artist)) rows.add(s);
        } else if (detail.type == DETAIL_ARTIST) {
            if (q.isEmpty()) {
                List<Album> own = new ArrayList<>();
                Set<Long> seen = new HashSet<>();
                for (Song s : detail.artist.songs) {
                    if (seen.add(s.albumId) && albumById.containsKey(s.albumId)) own.add(albumById.get(s.albumId));
                }
                rows.add("Albums");
                rows.addAll(own);
                rows.add("Songs");
            }
            for (Song s : detail.artist.songs) if (matches(q, s.title, s.album)) rows.add(s);
        } else if (detail.type == DETAIL_PLAYLIST) {
            PlaylistStore.Playlist p = detailPlaylist();
            for (int i = 0; i < p.songIds.size(); i++) {
                Song s = songById.get(p.songIds.get(i));
                if (s != null && matches(q, s.title, s.artist, s.album)) {
                    rows.add(s);
                    rowPlaylistIndex.add(i);
                }
            }
            if (q.isEmpty()) emptyText = "This playlist is empty.\n\nTap ⋮ next to any song and choose \"Add to playlist\".";
        } else if (tab == TAB_SONGS) {
            if (q.isEmpty()) rows.add(new Action(R.drawable.ic_shuffle, "Shuffle all", () -> {
                if (service != null) service.playShuffled(allSongs);
            }));
            for (Song s : preferences.sorted(allSongs)) if (matches(q, s.title, s.artist, s.album)) rows.add(s);
        } else if (tab == TAB_ALBUMS) {
            grid = true;
            for (Album a : albums) if (matches(q, a.name, a.artist)) shownAlbums.add(a);
        } else if (tab == TAB_ARTISTS) {
            for (Artist a : artists) if (matches(q, a.name)) rows.add(a);
        } else if (tab == TAB_FAVORITES) {
            for (Song song : preferences.sorted(allSongs)) if (preferences.favorite(song) && matches(q, song.title, song.artist, song.album)) rows.add(song);
            emptyText = "Your favorites belong here.\nTap the heart on a song to save it.";
        } else if (tab == TAB_FOLDERS) {
            if (selectedFolder != null) {
                rows.add(new Action(R.drawable.ic_back, "All folders", () -> { selectedFolder = null; refresh(); }));
                for (Song song : preferences.sorted(allSongs)) if (song.folder.equals(selectedFolder) && matches(q, song.title, song.artist)) rows.add(song);
            } else {
                Map<String, Integer> folders = new java.util.TreeMap<>();
                for (Song song : allSongs) folders.put(song.folder, folders.getOrDefault(song.folder, 0) + 1);
                for (Map.Entry<String, Integer> folder : folders.entrySet()) if (matches(q, folder.getKey())) {
                    String path = folder.getKey();
                    rows.add(new Action(R.drawable.ic_folder, (path.isEmpty() ? "Storage root" : path) + "  ·  " + folder.getValue(), () -> { selectedFolder = path; refresh(); }));
                }
            }
        } else if (tab == TAB_HISTORY) {
            rows.add(new Action(R.drawable.ic_sort, mostPlayed ? "Most played · show recent" : "Recently played · show most played", () -> { mostPlayed = !mostPlayed; refresh(); }));
            List<Song> history = new ArrayList<>();
            for (Song song : allSongs) if (preferences.last(song) > 0 && matches(q, song.title, song.artist, song.album)) history.add(song);
            Collections.sort(history, (a,b) -> mostPlayed ? Integer.compare(preferences.count(b), preferences.count(a)) : Long.compare(preferences.last(b), preferences.last(a)));
            rows.addAll(history);
            if (history.isEmpty()) rows.add("Your listening history will appear here");
        } else {
            if (q.isEmpty()) rows.add(new Action(R.drawable.ic_add, "New playlist", () ->
                    promptName("New playlist", "", "Create", name -> {
                        playlists.create(name);
                        refresh();
                    })));
            for (PlaylistStore.Playlist p : playlists.getAll()) if (matches(q, p.name)) rows.add(p);
            if (q.isEmpty() && playlists.getAll().isEmpty()) rows.add("Your playlists will appear here");
        }

        rowAdapter.notifyDataSetChanged();
        albumAdapter.notifyDataSetChanged();
        boolean empty = grid ? shownAlbums.isEmpty() : rows.isEmpty();
        if (empty) {
            showEmpty(emptyText, false);
        } else {
            emptyView.setVisibility(View.GONE);
            listView.setVisibility(grid ? View.GONE : View.VISIBLE);
            gridView.setVisibility(grid ? View.VISIBLE : View.GONE);
        }
    }

    private void showEmpty(String message, boolean actionable) {
        emptyActionable = actionable;
        emptyView.setText(message);
        emptyView.setVisibility(View.VISIBLE);
        listView.setVisibility(View.GONE);
        gridView.setVisibility(View.GONE);
    }

    /** Updates the tab chips, search hint and the album / artist / playlist header bar. */
    private void updateChrome() {
        if (sectionTitle != null) sectionTitle.setText(detail.type == DETAIL_NONE
                ? TAB_NAMES[tab].toUpperCase(Locale.ROOT) + "  /  " + sectionCount()
                : "TRACKLIST");
        if (sortLabel != null) {
            String[] labels={"Title A–Z", "Title Z–A", "Artist", "Album", "Date added", "Duration"};
            sortLabel.setText(labels[Math.max(0,Math.min(labels.length-1,preferences.sort()))] + "  ↓");
            sortLabel.setVisibility(detail.type == DETAIL_NONE && (tab == TAB_SONGS || tab == TAB_FAVORITES || tab == TAB_FOLDERS) ? View.VISIBLE : View.GONE);
        }
        for (int i = 0; i < tabViews.length; i++) styleTab(tabViews[i], i == tab);
        switch (detail.type) {
            case DETAIL_ALBUM: searchBox.setHint("Search in this album"); break;
            case DETAIL_ARTIST: searchBox.setHint("Search this artist's songs"); break;
            case DETAIL_PLAYLIST: searchBox.setHint("Search in this playlist"); break;
            default:
                searchBox.setHint(tab == TAB_SONGS ? "Search songs, artists, albums"
                        : "Search " + TAB_NAMES[tab].toLowerCase(Locale.ROOT));
        }
        if (detail.type == DETAIL_NONE) {
            detailBar.setVisibility(View.GONE);
            return;
        }
        detailBar.setVisibility(View.VISIBLE);
        List<Song> songs = detailSongs();
        String count = songs.size() + (songs.size() == 1 ? " song" : " songs") + " • " + formatTotal(songs);
        Song first = songs.isEmpty() ? null : songs.get(0);
        if (detail.type == DETAIL_ALBUM) {
            detailTitle.setText(detail.album.name);
            detailSubtitle.setText(detail.album.artist + " • " + count);
            detailArt.setBackground(rounded(SURFACE2, 10));
            bindArt(detailArt, first, LIST_ART_PX, R.drawable.ic_album, dp(16));
        } else if (detail.type == DETAIL_ARTIST) {
            detailTitle.setText(detail.artist.name);
            detailSubtitle.setText(detail.artist.albumCount + (detail.artist.albumCount == 1 ? " album • " : " albums • ") + count);
            detailArt.setBackground(oval(SURFACE2));
            bindArt(detailArt, first, LIST_ART_PX, R.drawable.ic_person, dp(16));
        } else {
            detailTitle.setText(detailPlaylist().name);
            detailSubtitle.setText(count);
            detailArt.setBackground(rounded(SURFACE2, 10));
            bindArt(detailArt, first, LIST_ART_PX, R.drawable.ic_queue, dp(16));
        }
    }

    // ---- Actions & menus ----

    private void play(List<Song> songs, int start) {
        if (service != null && !songs.isEmpty()) service.playQueue(songs, start);
    }

    private void shuffle(List<Song> songs) {
        if (service != null && !songs.isEmpty()) service.playShuffled(songs);
    }

    private void onRowClicked(int position) {
        if (position < 0 || position >= rows.size()) return;
        Object o = rows.get(position);
        if (o instanceof Song) {
            List<Song> songs = new ArrayList<>();
            int start = 0;
            for (int i = 0; i < rows.size(); i++) {
                if (!(rows.get(i) instanceof Song)) continue;
                if (i == position) start = songs.size();
                songs.add((Song) rows.get(i));
            }
            play(songs, start);
        } else if (o instanceof Album) {
            openAlbum((Album) o);
        } else if (o instanceof Artist) {
            openArtist((Artist) o);
        } else if (o instanceof PlaylistStore.Playlist) {
            openPlaylist((PlaylistStore.Playlist) o);
        } else if (o instanceof Action) {
            ((Action) o).run.run();
        }
    }

    private void showRowMenu(View anchor, int position) {
        if (position < 0 || position >= rows.size()) return;
        Object o = rows.get(position);
        if (o instanceof Song) {
            showSongMenu(anchor, (Song) o, detail.type == DETAIL_PLAYLIST ? position : -1);
        } else if (o instanceof Album) {
            Album a = (Album) o;
            showCollectionMenu(anchor, a.songs, null);
        } else if (o instanceof Artist) {
            showCollectionMenu(anchor, ((Artist) o).songs, null);
        } else if (o instanceof PlaylistStore.Playlist) {
            PlaylistStore.Playlist p = (PlaylistStore.Playlist) o;
            showCollectionMenu(anchor, resolve(p), p);
        }
    }

    private void showSongMenu(View anchor, final Song s, final int playlistRow) {
        PopupMenu m = new PopupMenu(this, anchor);
        Menu menu = m.getMenu();
        menu.add(0, 1, 0, "Play next");
        menu.add(0, 2, 1, "Add to queue");
        menu.add(0, 3, 2, "Add to playlist…");
        if (detail.type != DETAIL_ALBUM) menu.add(0, 4, 3, "Go to album");
        if (detail.type != DETAIL_ARTIST) menu.add(0, 5, 4, "Go to artist");
        menu.add(0, 10, 6, preferences.favorite(s) ? "Remove favorite" : "Add to favorites");
        menu.add(0, 11, 7, "Lyrics");
        menu.add(0, 12, 8, "Edit MP3 tags…");
        if (playlistRow >= 0) menu.add(0, 6, 5, "Remove from playlist");
        m.setOnMenuItemClickListener(item -> {
            List<Song> one = Collections.singletonList(s);
            switch (item.getItemId()) {
                case 1: playNext(one); break;
                case 2: addToQueue(one); break;
                case 3: addToPlaylist(one); break;
                case 4: goToAlbum(s); break;
                case 5: goToArtist(s); break;
                case 6: removeFromPlaylist(playlistRow); break;
                case 10: preferences.toggleFavorite(s); repaint(); break;
                case 11: tools.lyrics(s); break;
                case 12: tools.chooseMp3(); break;
            }
            return true;
        });
        m.show();
    }

    private void showCollectionMenu(View anchor, final List<Song> songs, final PlaylistStore.Playlist playlist) {
        PopupMenu m = new PopupMenu(this, anchor);
        Menu menu = m.getMenu();
        menu.add(0, 1, 0, "Play");
        menu.add(0, 2, 1, "Shuffle");
        menu.add(0, 3, 2, "Play next");
        menu.add(0, 4, 3, "Add to queue");
        menu.add(0, 5, 4, "Add to playlist…");
        if (playlist != null) {
            menu.add(0, 6, 5, "Rename");
            menu.add(0, 7, 6, "Delete playlist");
            menu.add(0, 8, 7, "Export M3U…");
        }
        m.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1: play(songs, 0); break;
                case 2: shuffle(songs); break;
                case 3: playNext(songs); break;
                case 4: addToQueue(songs); break;
                case 5: addToPlaylist(songs); break;
                case 6: renamePlaylist(playlist); break;
                case 7: deletePlaylist(playlist); break;
                case 8: tools.exportPlaylist(playlist.name, songs); break;
            }
            return true;
        });
        m.show();
    }

    private void showPlayerMenu(View anchor) {
        if (service == null || service.getCurrentSong() == null) return;
        final Song s = service.getCurrentSong();
        PopupMenu m = new PopupMenu(this, anchor);
        Menu menu = m.getMenu();
        menu.add(0, 1, 0, "Add to playlist…");
        menu.add(0, 2, 1, "Go to album");
        menu.add(0, 3, 2, "Go to artist");
        menu.add(0, 4, 3, "Queue");
        menu.add(0, 5, 4, "Lyrics");
        menu.add(0, 6, 5, "Sleep timer · " + service.sleepDescription());
        menu.add(0, 7, 6, "Sound & playback");
        menu.add(0, 8, 7, "Edit MP3 tags…");
        m.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1: addToPlaylist(Collections.singletonList(s)); break;
                case 2: goToAlbum(s); break;
                case 3: goToArtist(s); break;
                case 4: showQueue(); break;
                case 5: tools.lyrics(s); break;
                case 6: tools.sleepTimer(); break;
                case 7: tools.sound(); break;
                case 8: tools.chooseMp3(); break;
            }
            return true;
        });
        m.show();
    }

    private void playNext(List<Song> songs) {
        if (service == null || songs.isEmpty()) return;
        boolean wasEmpty = service.getCurrentSong() == null;
        service.playNext(songs);
        if (!wasEmpty) toast(songs.size() == 1 ? "Playing next" : songs.size() + " songs playing next");
    }

    private void addToQueue(List<Song> songs) {
        if (service == null || songs.isEmpty()) return;
        boolean wasEmpty = service.getCurrentSong() == null;
        service.addToQueue(songs);
        if (!wasEmpty) toast(songs.size() == 1 ? "Added to queue" : songs.size() + " songs added to queue");
    }

    private void addToPlaylist(final List<Song> songs) {
        if (songs.isEmpty()) return;
        final List<PlaylistStore.Playlist> all = new ArrayList<>(playlists.getAll());
        String[] items = new String[all.size() + 1];
        items[0] = "+  New playlist";
        for (int i = 0; i < all.size(); i++) items[i + 1] = all.get(i).name;
        ThemeDialogs.builder(this)
                .setTitle("Add to playlist")
                .setItems(items, (d, which) -> {
                    if (which == 0) {
                        promptName("New playlist", "", "Create", name -> {
                            PlaylistStore.Playlist p = playlists.create(name);
                            playlists.addSongs(p, songs);
                            toast("Added to " + p.name);
                            refresh();
                        });
                    } else {
                        PlaylistStore.Playlist p = all.get(which - 1);
                        playlists.addSongs(p, songs);
                        toast("Added to " + p.name);
                        refresh();
                    }
                })
                .show();
    }

    private void removeFromPlaylist(int row) {
        PlaylistStore.Playlist p = detailPlaylist();
        if (p == null || row < 0 || row >= rowPlaylistIndex.size()) return;
        playlists.removeAt(p, rowPlaylistIndex.get(row));
        refresh();
    }

    private void renamePlaylist(final PlaylistStore.Playlist p) {
        if (p == null) return;
        promptName("Rename playlist", p.name, "Rename", name -> {
            playlists.rename(p, name);
            refresh();
        });
    }

    private void deletePlaylist(final PlaylistStore.Playlist p) {
        if (p == null) return;
        ThemeDialogs.builder(this)
                .setTitle("Delete playlist?")
                .setMessage("\"" + p.name + "\" will be deleted. Your music files are not affected.")
                .setPositiveButton("Delete", (d, w) -> {
                    playlists.delete(p);
                    if (detail.type == DETAIL_PLAYLIST && detail.playlistId == p.id) navigateBack();
                    else refresh();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void promptName(String title, String initial, String positive, final NameCallback callback) {
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(initial);
        input.setSelection(initial.length());
        input.setHint("Playlist name");
        FrameLayout box = new FrameLayout(this);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        box.addView(input);
        AlertDialog dialog = ThemeDialogs.builder(this)
                .setTitle(title)
                .setView(box)
                .setPositiveButton(positive, (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) name = "My playlist";
                    callback.onName(name);
                })
                .setNegativeButton("Cancel", null)
                .create();
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        dialog.show();
        input.requestFocus();
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(searchBox.getWindowToken(), 0);
        searchBox.clearFocus();
    }

    // ---- Player updates ----

    @Override
    public void onPlayerChanged() {
        refreshSettings();
        if (service == null) return;
        rowAdapter.notifyDataSetChanged();
        albumAdapter.notifyDataSetChanged();
        queueAdapter.notifyDataSetChanged();
        int qs = service.getQueue().size();
        queueCount.setText(qs + (qs == 1 ? " song" : " songs"));

        Song s = service.getCurrentSong();
        updateOverview();
        if (s == null) {
            if(npLyrics!=null)npLyrics.bind(null);boundLyricsUri="";
            miniPlayer.setVisibility(View.GONE);
            hideOverlays();
            return;
        }
        int playIcon = service.isActive() ? R.drawable.ic_pause : R.drawable.ic_play;

        // Mini player
        miniPlayer.setVisibility(View.VISIBLE);
        miniTitle.setText(s.title);
        miniArtist.setText(s.artist);
        miniPlay.setImageResource(playIcon);
        Bitmap art = service.getArt();
        if (art != null) showBitmap(miniArt, art);
        else setRecordPlaceholder(miniArt,s);

        // Bind before asynchronous loading so stale results cannot follow a song change.
        if(!boundLyricsUri.equals(s.uri.toString())){
            boundLyricsUri=s.uri.toString();lyricsForced=false;
            npLyricsHint.setText("LYRICS  ·  Loading…");setLyricsVisible(false);
        }
        npLyrics.bind(s);
        // Now Playing
        npTitle.setText(s.title);
        npArtist.setText(s.album.isEmpty() ? s.artist : s.artist + " • " + s.album);
        npPosition.setText("YOUR QUEUE  ·  " + (service.getIndex() + 1) + " / " + qs);
        if (playbackInfo != null) playbackInfo.setText("ON THIS DEVICE   ·   " + service.getSpeed() + "× SPEED");
        updateNextTrack();
        npPlay.setImageResource(playIcon);
        npPlay.setContentDescription(service.isActive() ? "Pause" : "Play");
        miniPlay.setContentDescription(service.isActive() ? "Pause" : "Play");
        npShuffle.setImageTintList(ColorStateList.valueOf(service.isShuffle() ? ACCENT : SUBTEXT));
        int repeat = service.getRepeatMode();
        npRepeat.setImageResource(repeat == MusicService.REPEAT_ONE ? R.drawable.ic_repeat_one : R.drawable.ic_repeat);
        npRepeat.setImageTintList(ColorStateList.valueOf(repeat == MusicService.REPEAT_OFF ? SUBTEXT : ACCENT));
        if (npArtSongId != s.id) {
            npArtSongId = s.id;
            setRecordPlaceholder(npArt,s);
            setPlayerTint(NP_DEFAULT_TINT);
            final long id = s.id;
            ArtLoader.load(this, s, PLAYER_ART_PX, bmp -> {
                if (bmp == null || npArtSongId != id) return;
                showBitmap(npArt, bmp);
                setPlayerTint(dominantColor(bmp));
            });
        }
        if (favoriteButton != null) {
            favoriteButton.setText(preferences.favorite(s) ? "♥ Saved" : "♡ Favorite");
            favoriteButton.setTextColor(preferences.favorite(s) ? TEXT : SUBTEXT);
        }
        if (tab == TAB_HISTORY || tab == TAB_FAVORITES) refresh();
        updateProgress();
    }

    private void updateProgress() {
        refreshSettings();
        if (service == null || service.getCurrentSong() == null) return;
        if (timerButton != null) timerButton.setText("Off".equals(service.sleepDescription()) ? "Sleep timer" : service.sleepDescription());
        int dur = Math.max(service.getDuration(), 1);
        int pos = service.getPosition();
        miniProgress.setMax(dur);
        miniProgress.setProgress(pos);
        if (!userSeeking) {
            npSeek.setMax(dur);
            npSeek.setProgress(pos);
            npPos.setText(formatTime(pos));
        }
        npDur.setText(formatTime(service.getDuration()));
        if(npLyrics!=null)npLyrics.tick();
    }

    private void setPlayerTint(int color) {
        npBackground.setColors(new int[]{color, BG, BG});
    }

    private int dominantColor(Bitmap bmp) {
        try {
            Bitmap one = Bitmap.createScaledBitmap(bmp, 1, 1, true);
            int c = one.getPixel(0, 0);
            float[] hsv = new float[3];
            Color.colorToHSV(c, hsv);
            hsv[1] = Math.min(hsv[1], 0.6f);
            hsv[2] = "light".equals(preferences.theme()) ? Math.max(hsv[2], 0.9f) : Math.min(hsv[2], 0.42f);
            if ("light".equals(preferences.theme())) hsv[1] = Math.min(hsv[1], 0.15f);
            return Color.HSVToColor(hsv);
        } catch (Exception e) {
            return NP_DEFAULT_TINT;
        }
    }

    private void showNowPlaying() {
        if(settingsScreen!=null && settingsScreen.getVisibility()==View.VISIBLE)closeSettings();
        if (service == null || service.getCurrentSong() == null) return;
        hideKeyboard();
        if (npView.getVisibility() == View.VISIBLE) return;
        npView.setVisibility(View.VISIBLE);
        npView.setAlpha(0f);
        npView.setTranslationY(dp(48));
        npView.animate().alpha(1f).translationY(0).setDuration(220).start();
    }

    private void hideNowPlaying() {
        npView.animate().alpha(0f).translationY(dp(48)).setDuration(180)
                .withEndAction(() -> npView.setVisibility(View.GONE)).start();
    }

    private void showQueue() {
        if (service == null) return;
        hideKeyboard();
        queueAdapter.notifyDataSetChanged();
        queueView.setVisibility(View.VISIBLE);
        queueView.setAlpha(0f);
        queueView.setTranslationY(dp(48));
        queueView.animate().alpha(1f).translationY(0).setDuration(220).start();
        queueList.setSelection(Math.max(0, service.getIndex() - 1));
    }

    private void hideQueue() {
        queueView.animate().alpha(0f).translationY(dp(48)).setDuration(180)
                .withEndAction(() -> queueView.setVisibility(View.GONE)).start();
    }

    private static String formatTime(long ms) {
        long total = ms / 1000;
        long h = total / 3600, m = (total % 3600) / 60, s = total % 60;
        return h > 0
                ? String.format(Locale.US, "%d:%02d:%02d", h, m, s)
                : String.format(Locale.US, "%d:%02d", m, s);
    }

    private static String formatTotal(List<Song> songs) {
        long ms = 0;
        for (Song s : songs) ms += s.duration;
        long min = Math.round(ms / 60000.0);
        if (min < 60) return min + " min";
        return (min / 60) + " hr " + (min % 60) + " min";
    }

    private TextView actionChip(String label, Runnable action) {
        TextView chip = text(13, SUBTEXT, true);
        chip.setText(label); chip.setGravity(Gravity.CENTER);
        chip.setBackground(outlined(SURFACE,RecordArtwork.blend(SURFACE,TEXT,.10f),14));
        chip.setOnClickListener(v -> action.run());
        return chip;
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (tools != null) tools.onResult(request, result, data);
    }

    private void handlePendingIntent() {
        if (service == null || pendingIntent == null) return;
        Intent intent = pendingIntent; pendingIntent = null;
        String action = intent.getAction();
        if (MusicService.ACTION_TOGGLE.equals(action)) service.toggle();
        else if (MusicService.ACTION_NEXT.equals(action)) service.next();
        else if (MusicService.ACTION_PREV.equals(action)) service.previous();
        else if (Intent.ACTION_VIEW.equals(action) && intent.getData() != null) {
            Uri uri = intent.getData();
            if (!"content".equals(uri.getScheme()) && !"file".equals(uri.getScheme())) return;
            tools.openAudio(uri);
        }
    }

    private GradientDrawable outlined(int fill,int stroke,float radius) {
        GradientDrawable drawable=rounded(fill,radius);
        drawable.setStroke(dp(1),stroke);
        return drawable;
    }

    private int sectionCount() {
        if (tab==TAB_ALBUMS) return albums.size();
        if (tab==TAB_ARTISTS) return artists.size();
        if (tab==TAB_PLAYLISTS) return playlists.getAll().size();
        if (tab==TAB_FOLDERS) { Set<String> folders=new HashSet<>();for(Song s:allSongs)folders.add(s.folder);return folders.size(); }
        if (tab==TAB_FAVORITES || tab==TAB_HISTORY) {
            int count=0;for(Song s:allSongs)if(tab==TAB_FAVORITES ? preferences.favorite(s) : preferences.last(s)>0)count++;
            return count;
        }
        return allSongs.size();
    }

    @Override public void onConfigurationChanged(android.content.res.Configuration configuration) {
        super.onConfigurationChanged(configuration);
        if (searchBox!=null) updateOverview();
    }

    private void setRecordPlaceholder(ImageView view,Song song) {
        view.setImageTintList(null); view.setPadding(0,0,0,0);
        view.setScaleType(ImageView.ScaleType.CENTER_CROP);
        view.setImageDrawable(new RecordArtwork(SURFACE2,ACCENT,TEXT,song.title));
    }

    private void addActionCell(LinearLayout row,View child) {
        LinearLayout.LayoutParams cell=new LinearLayout.LayoutParams(0,dp(46),1f);
        cell.setMargins(dp(3),0,dp(3),0); row.addView(child,cell);
    }

    private View buildOverview() {
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20),dp(8),dp(20),dp(4));
        LinearLayout card=new LinearLayout(this);card.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable gradient=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{RecordArtwork.blend(SURFACE,ACCENT,.16f),SURFACE});
        gradient.setCornerRadius(dp(22));gradient.setStroke(dp(1),RecordArtwork.blend(SURFACE,ACCENT,.25f));
        card.setBackground(gradient);card.setPadding(dp(16),dp(14),dp(14),dp(14));
        LinearLayout words=new LinearLayout(this);words.setOrientation(LinearLayout.VERTICAL);
        overviewLabel=text(10,ACCENT,true);overviewLabel.setLetterSpacing(.12f);
        overviewTitle=text(19,TEXT,true);overviewTitle.setPadding(0,dp(5),0,dp(3));
        overviewSubtitle=text(12,SUBTEXT,false);
        overviewAction=text(12,ON_ACCENT,true);overviewAction.setGravity(Gravity.CENTER);overviewAction.setBackground(rounded(ACCENT,10));
        overviewAction.setOnClickListener(v -> openOverview());
        words.addView(overviewLabel);words.addView(overviewTitle);words.addView(overviewSubtitle);
        LinearLayout.LayoutParams button=new LinearLayout.LayoutParams(dp(128),dp(36));button.topMargin=dp(10);words.addView(overviewAction,button);
        card.addView(words,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
        overviewArt=new ImageView(this);overviewArt.setClipToOutline(true);overviewArt.setBackground(rounded(SURFACE2,16));
        LinearLayout.LayoutParams art=new LinearLayout.LayoutParams(dp(100),dp(100));art.leftMargin=dp(12);card.addView(overviewArt,art);
        box.addView(card);card.setOnClickListener(v -> openOverview());
        LinearLayout shortcuts=new LinearLayout(this);shortcuts.setPadding(0,dp(8),0,0);
        favoritesShortcut=actionChip("Favorites",() -> selectTab(TAB_FAVORITES));
        historyShortcut=actionChip("Recently played",() -> selectTab(TAB_HISTORY));
        addActionCell(shortcuts,favoritesShortcut);addActionCell(shortcuts,historyShortcut);box.addView(shortcuts);
        overview=box;return box;
    }

    private void openOverview() {
        if (service==null) return;
        if (service.getCurrentSong()!=null) { if (!service.isActive()) service.play(); showNowPlaying(); }
        else if (!allSongs.isEmpty()) { play(preferences.sorted(allSongs),0);showNowPlaying(); }
    }

    private void updateOverview() {
        if (overview==null) return;
        boolean show=detail.type==DETAIL_NONE && tab==TAB_SONGS && query().isEmpty()
                && getResources().getConfiguration().screenHeightDp>=680 && !allSongs.isEmpty();
        overview.setVisibility(show ? View.VISIBLE : View.GONE);
        if (!show) return;
        Song current=service==null ? null : service.getCurrentSong();
        Song featured=current==null ? preferences.sorted(allSongs).get(0) : current;
        overviewLabel.setText(current==null ? "FROM YOUR COLLECTION" : "CONTINUE LISTENING");
        overviewTitle.setText(featured.title);overviewSubtitle.setText(featured.artist);
        overviewAction.setText(current==null ? "Play collection  ›" : service.isActive() ? "Open player  ›" : "Resume  ›");
        if (overviewArtId!=featured.id) { overviewArtId=featured.id;bindArt(overviewArt,featured,GRID_ART_PX,R.drawable.ic_album,0); }
        int favorites=0,recent=0;
        for(Song song:allSongs){if(preferences.favorite(song)) favorites++;if(preferences.last(song)>0)recent++;}
        favoritesShortcut.setText("♥  Favorites  ·  "+favorites);
        historyShortcut.setText("↻  Recent  ·  "+recent);
    }

    private View buildNextTrack() {
        LinearLayout card=new LinearLayout(this);card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(10),dp(8),dp(8),dp(8));
        card.setBackground(outlined(SURFACE,RecordArtwork.blend(SURFACE,TEXT,.10f),16));
        LinearLayout.LayoutParams layout=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(64));layout.topMargin=dp(10);card.setLayoutParams(layout);
        nextArt=new ImageView(this);nextArt.setBackground(rounded(SURFACE2,10));nextArt.setClipToOutline(true);
        card.addView(nextArt,new LinearLayout.LayoutParams(dp(42),dp(42)));
        LinearLayout words=new LinearLayout(this);words.setOrientation(LinearLayout.VERTICAL);words.setPadding(dp(12),0,0,0);
        nextSubtitle=text(9,ACCENT,true);nextSubtitle.setLetterSpacing(.1f);nextSubtitle.setText("UP NEXT");
        nextTitle=text(13,TEXT,true);words.addView(nextSubtitle);words.addView(nextTitle);
        card.addView(words,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
        ImageButton queue=iconButton(R.drawable.ic_queue,44,SUBTEXT);queue.setOnClickListener(v -> showQueue());card.addView(queue);
        card.setOnClickListener(v -> showQueue());return card;
    }

    private void updateNextTrack() {
        if (nextTitle==null || service==null) return;
        List<Song> queue=service.getQueue();
        int next=service.getIndex()+1;
        if(service.getRepeatMode()==MusicService.REPEAT_ONE)next=service.getIndex();
        else if(next>=queue.size() && service.getRepeatMode()==MusicService.REPEAT_ALL)next=0;
        if(next<0 || next>=queue.size()){
            nextTitle.setText("End of your queue");nextSubtitle.setText("ADD SOMETHING NEXT");
            nextArt.setImageResource(R.drawable.ic_queue);nextArt.setImageTintList(ColorStateList.valueOf(SUBTEXT));nextArtId=Long.MIN_VALUE;return;
        }
        Song song=queue.get(next);nextTitle.setText(song.title+"  ·  "+song.artist);
        nextSubtitle.setText(service.getRepeatMode()==MusicService.REPEAT_ONE ? "REPEATING THIS TRACK" : "UP NEXT");
        if(nextArtId!=song.id){nextArtId=song.id;bindArt(nextArt,song,LIST_ART_PX,R.drawable.ic_music,0);}
    }

    // ---- UI helpers ----

    private int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private GradientDrawable rounded(int color, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    private static GradientDrawable oval(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        return d;
    }

    private TextView text(float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color == ACCENT ? TEXT : color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    private ImageButton iconButton(int icon, int sizeDp, int tint) {
        ImageButton b = new ImageButton(this);
        b.setImageResource(icon);
        String description = icon == R.drawable.ic_play ? "Play" : icon == R.drawable.ic_pause ? "Pause"
                : icon == R.drawable.ic_prev ? "Previous" : icon == R.drawable.ic_next ? "Next"
                : icon == R.drawable.ic_shuffle ? "Shuffle" : icon == R.drawable.ic_repeat ? "Repeat"
                : icon == R.drawable.ic_back ? "Back" : icon == R.drawable.ic_expand ? "Collapse player"
                : icon == R.drawable.ic_queue ? "Queue" : icon == R.drawable.ic_close ? "Close"
                : icon == R.drawable.ic_playlist_add ? "Add to playlist" : "More options";
        b.setContentDescription(description);
        b.setImageTintList(ColorStateList.valueOf(tint));
        b.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        TypedValue tv = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, tv, true);
        b.setBackgroundResource(tv.resourceId);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp));
        lp.setMargins(dp(4), 0, dp(4), 0);
        b.setLayoutParams(lp);
        return b;
    }

    private void setRipple(View v) {
        TypedValue tv = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
        v.setForeground(getDrawable(tv.resourceId));
    }

    private void setPlaceholder(ImageView v, int icon, int padding) {
        v.setImageResource(icon);
        v.setImageTintList(ColorStateList.valueOf(SUBTEXT));
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        v.setPadding(padding, padding, padding, padding);
    }

    private void showBitmap(ImageView v, Bitmap bmp) {
        v.setImageTintList(null);
        v.setScaleType(ImageView.ScaleType.CENTER_CROP);
        v.setPadding(0, 0, 0, 0);
        v.setImageBitmap(bmp);
    }

    /** Shows the song's album art in the view (placeholder first), ignoring results for recycled views. */
    private void bindArt(final ImageView v, Song s, int sizePx, int placeholder, int padding) {
        if (s != null) setRecordPlaceholder(v,s); else setPlaceholder(v, placeholder, padding);
        if (s == null) {
            v.setTag(null);
            return;
        }
        final String key = s.id + ":" + sizePx;
        v.setTag(key);
        ArtLoader.load(this, s, sizePx, bmp -> {
            if (bmp != null && key.equals(v.getTag())) showBitmap(v, bmp);
        });
    }

    private void styleTab(TextView t, boolean selected) {
        t.setBackground(selected ? outlined(ACCENT,ACCENT,14) : outlined(SURFACE,RecordArtwork.blend(SURFACE,TEXT,.08f),14));
        t.setTextColor(selected ? ON_ACCENT : TEXT);
        t.setTypeface(selected ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
    }

    /** An ImageView that stays square, fitting inside the space it is given. */
    static final class SquareImageView extends ImageView {
        SquareImageView(Context c) {
            super(c);
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            int s = MeasureSpec.getSize(widthSpec);
            if (MeasureSpec.getMode(heightSpec) != MeasureSpec.UNSPECIFIED) {
                s = Math.min(s, MeasureSpec.getSize(heightSpec));
            }
            setMeasuredDimension(s, s);
        }
    }

    // ---- UI construction ----

    private View buildUi() {
        FrameLayout root = new FrameLayout(this);
        screenRoot=root;
        root.setBackgroundColor(BG);
        root.addView(buildMain(), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(buildNowPlaying(), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(buildQueue(), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        settingsScreen=new SettingsScreen(this,tools,preferences);
        root.addView(settingsScreen,new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));
        return root;
    }

    private View buildMain() {
        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);

        // Header
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(20), dp(12), dp(20), dp(4));

        TextView title = text(28, TEXT, true);
        TextView eyebrow = text(11, ACCENT, true);
        eyebrow.setText("YOUR OFFLINE COLLECTION");
        eyebrow.setLetterSpacing(0.15f);
        eyebrow.setPadding(0, 0, 0, dp(8));
        header.addView(eyebrow);
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        title.setText("Your library.");
        title.setTextSize(34);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        titleRow.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        ImageButton settings = iconButton(R.drawable.ic_settings, 48, TEXT);
        settings.setBackground(rounded(SURFACE, 16));
        settings.setContentDescription("Library settings");
        settings.setOnClickListener(v -> tools.settings());
        titleRow.addView(settings);
        header.addView(titleRow);

        countView = text(13, SUBTEXT, false);
        countView.setPadding(0, dp(4), 0, dp(4));
        header.addView(countView);

        searchBox = new EditText(this);
        searchBox.setHintTextColor(SUBTEXT);
        searchBox.setTextColor(TEXT);
        searchBox.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        searchBox.setSingleLine(true);
        searchBox.setBackground(outlined(SURFACE,RecordArtwork.blend(SURFACE,TEXT,.08f),16));
        searchBox.setPadding(dp(16), dp(10), dp(16), dp(10));
        searchBox.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_search, 0, 0, 0);
        searchBox.setCompoundDrawableTintList(ColorStateList.valueOf(SUBTEXT));
        searchBox.setCompoundDrawablePadding(dp(10));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.topMargin = dp(12);
        header.addView(searchBox, sp);
        main.addView(header);

        // Tabs
        HorizontalScrollView tabScroll = new HorizontalScrollView(this);
        tabScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setPadding(dp(16), dp(10), dp(16), dp(6));
        for (int i = 0; i < TAB_NAMES.length; i++) {
            final int t = i;
            TextView chip = text(14, TEXT, false);
            chip.setText(TAB_NAMES[i]);
            chip.setPadding(dp(16), dp(8), dp(16), dp(8));
            chip.setOnClickListener(v -> selectTab(t));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(dp(4), 0, dp(4), 0);
            tabs.addView(chip, lp);
            tabViews[i] = chip;
        }
        tabScroll.addView(tabs);
        main.addView(tabScroll);
        main.addView(buildOverview());
        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setPadding(dp(20), dp(2), dp(16), dp(2));
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        TextView offline = text(11, SUBTEXT, true);
        sectionTitle=offline;
        offline.setText("SONGS"); offline.setLetterSpacing(0.1f);
        toolbar.addView(offline, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView sort = text(12, ACCENT, true);
        sortLabel=sort;
        sort.setText("Sort  ↓"); sort.setGravity(Gravity.CENTER); sort.setMinHeight(dp(48)); sort.setMinWidth(dp(64));
        sort.setOnClickListener(v -> tools.sort());
        toolbar.addView(sort); main.addView(toolbar);

        main.addView(buildDetailBar());

        // Content: list, album grid, empty message
        FrameLayout content = new FrameLayout(this);
        listView = new ListView(this);
        listView.setDivider(null);
        listView.setFastScrollEnabled(true);
        listView.setCacheColorHint(Color.TRANSPARENT);
        listView.setClipToPadding(false);
        listView.setPadding(0, dp(4), 0, dp(8));
        rowAdapter = new RowAdapter();
        listView.setAdapter(rowAdapter);
        listView.setOnItemClickListener((parent, view, position, id) -> onRowClicked(position));
        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            showRowMenu(view, position);
            return true;
        });
        listDrag = new DragController(listView, new Reorder() {
            @Override
            public boolean move(int from, int to) {
                PlaylistStore.Playlist p = detailPlaylist();
                if (p == null || to < 0 || to >= rows.size()) return false;
                Collections.swap(rows, from, to);
                playlists.swap(p, rowPlaylistIndex.get(from), rowPlaylistIndex.get(to));
                return true;
            }

            @Override
            public void done() {
                playlists.save();
            }
        }, () -> rowAdapter.notifyDataSetChanged());
        content.addView(listView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        gridView = new GridView(this);
        gridView.setNumColumns(GridView.AUTO_FIT);
        gridView.setColumnWidth(dp(150));
        gridView.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        gridView.setHorizontalSpacing(dp(12));
        gridView.setVerticalSpacing(dp(16));
        gridView.setPadding(dp(16), dp(8), dp(16), dp(16));
        gridView.setClipToPadding(false);
        gridView.setSelector(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        gridView.setFastScrollEnabled(true);
        albumAdapter = new AlbumAdapter();
        gridView.setAdapter(albumAdapter);
        gridView.setOnItemClickListener((parent, view, position, id) -> openAlbum(shownAlbums.get(position)));
        gridView.setOnItemLongClickListener((parent, view, position, id) -> {
            showCollectionMenu(view, shownAlbums.get(position).songs, null);
            return true;
        });
        gridView.setVisibility(View.GONE);
        content.addView(gridView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        emptyView = new TextView(this);
        emptyView.setTextColor(SUBTEXT);
        emptyView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setPadding(dp(32), dp(32), dp(32), dp(32));
        emptyView.setOnClickListener(v -> onEmptyClicked());
        content.addView(emptyView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        main.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        main.addView(buildMiniPlayer());

        // Attach the search listener last so refresh() sees every view.
        searchBox.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) { refresh(); }
        });
        return main;
    }

    private View buildDetailBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8), dp(8), dp(12), dp(8));
        bar.setVisibility(View.GONE);

        ImageButton back = iconButton(R.drawable.ic_back, 44, TEXT);
        back.setOnClickListener(v -> navigateBack());
        bar.addView(back);

        detailArt = new ImageView(this);
        detailArt.setClipToOutline(true);
        bar.addView(detailArt, new LinearLayout.LayoutParams(dp(56), dp(56)));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(dp(12), 0, dp(4), 0);
        detailTitle = text(18, TEXT, true);
        detailSubtitle = text(13, SUBTEXT, false);
        texts.addView(detailTitle);
        texts.addView(detailSubtitle);
        bar.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ImageButton shuffleBtn = iconButton(R.drawable.ic_shuffle, 42, TEXT);
        shuffleBtn.setBackground(oval(SURFACE2));
        shuffleBtn.setOnClickListener(v -> shuffle(detailSongs()));
        bar.addView(shuffleBtn);

        ImageButton playBtn = iconButton(R.drawable.ic_play, 46, ON_ACCENT);
        playBtn.setBackground(oval(ACCENT));
        playBtn.setOnClickListener(v -> play(detailSongs(), 0));
        bar.addView(playBtn);

        ImageButton more = iconButton(R.drawable.ic_more, 36, SUBTEXT);
        more.setOnClickListener(v -> showCollectionMenu(v, detailSongs(), detailPlaylist()));
        bar.addView(more);

        detailBar = bar;
        return bar;
    }

    private View buildMiniPlayer() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(outlined(SURFACE2,RecordArtwork.blend(SURFACE2,ACCENT,.22f),22));
        card.setElevation(dp(6));
        card.setClipToOutline(true);
        card.setVisibility(View.GONE);
        card.setOnClickListener(v -> showNowPlaying());
        setRipple(card);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(8), dp(6), dp(8));

        miniArt = new ImageView(this);
        miniArt.setBackground(rounded(SURFACE2, 8));
        miniArt.setClipToOutline(true);
        row.addView(miniArt, new LinearLayout.LayoutParams(dp(44), dp(44)));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(dp(12), 0, dp(4), 0);
        miniTitle = text(15, TEXT, true);
        miniArtist = text(13, SUBTEXT, false);
        titles.addView(miniTitle);
        titles.addView(miniArtist);
        row.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        miniPlay = iconButton(R.drawable.ic_play, 44, ON_ACCENT);
        miniPlay.setBackground(oval(ACCENT));
        miniPlay.setOnClickListener(v -> { if (service != null) service.toggle(); });
        row.addView(miniPlay);
        ImageButton miniNext = iconButton(R.drawable.ic_next, 44, TEXT);
        miniNext.setOnClickListener(v -> { if (service != null) service.next(); });
        row.addView(miniNext);
        card.addView(row);

        miniProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        miniProgress.setProgressTintList(ColorStateList.valueOf(ACCENT));
        miniProgress.setProgressBackgroundTintList(ColorStateList.valueOf(SURFACE));
        miniProgress.setIndeterminate(false);
        card.addView(miniProgress, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3)));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(16), dp(6), dp(16), dp(12));
        card.setLayoutParams(lp);
        miniPlayer = card;
        return card;
    }

    private View buildNowPlaying() {
        FrameLayout outer = new FrameLayout(this);
        npBackground = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{NP_DEFAULT_TINT, BG, BG});
        outer.setBackground(npBackground);
        outer.setClickable(true); // swallow touches meant for the screen underneath
        outer.setVisibility(View.GONE);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(20), dp(8), dp(20), dp(12));

        // Top bar
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        ImageButton collapse = iconButton(R.drawable.ic_expand, 44, TEXT);
        collapse.setOnClickListener(v -> hideNowPlaying());
        top.addView(collapse);
        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.VERTICAL);
        heading.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView label = text(12, SUBTEXT, true);
        label.setText("NOW PLAYING");
        label.setLetterSpacing(0.12f);
        npPosition = text(12, SUBTEXT, false);
        heading.addView(label);
        heading.addView(npPosition);
        top.addView(heading, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        ImageButton more = iconButton(R.drawable.ic_more, 44, TEXT);
        more.setOnClickListener(this::showPlayerMenu);
        top.addView(more);
        col.addView(top);

        // Artwork
        FrameLayout artBox = new FrameLayout(this);
        npArtBox=artBox;
        artBox.setPadding(dp(8), dp(14), dp(8), dp(14));
        npArt = new SquareImageView(this);
        npArt.setBackground(outlined(SURFACE2,RecordArtwork.blend(SURFACE2,ACCENT,.25f),28));
        npArt.setClipToOutline(true);
        npArt.setElevation(dp(8));
        artBox.addView(npArt, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));
        npLyrics=new NowPlayingLyrics(this,lyricsRepository);npLyrics.setVisibility(View.GONE);
        artBox.addView(npLyrics,new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));
        col.addView(artBox, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(180), 1f));

        playbackInfo=text(10,ACCENT,true);
        playbackInfo.setLetterSpacing(.12f);
        playbackInfo.setPadding(0,0,0,dp(8));
        playbackInfo.setOnClickListener(v -> tools.sound());
        playbackInfo.setContentDescription("Playback speed and sound settings");
        col.addView(playbackInfo);

        // Titles
        npTitle = text(26, TEXT, true);
        npTitle.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        npTitle.setMarqueeRepeatLimit(-1);
        npTitle.setSelected(true);
        col.addView(npTitle);
        npArtist = text(15, SUBTEXT, false);
        npArtist.setPadding(0, dp(2), 0, 0);
        npArtist.setOnClickListener(v -> {
            if (service != null && service.getCurrentSong() != null) goToArtist(service.getCurrentSong());
        });
        col.addView(npArtist);
        npLyricsHint=text(11,TEXT,true);npLyricsHint.setText("LYRICS  ·  Find or add lyrics");
        npLyricsHint.setPadding(0,dp(6),0,dp(2));npLyricsHint.setOnClickListener(v->{if(service!=null&&service.getCurrentSong()!=null)showSongLyrics(service.getCurrentSong());});
        col.addView(npLyricsHint);

        // Seek bar
        npSeek = new SeekBar(this);
        npSeek.setProgressTintList(ColorStateList.valueOf(ACCENT));
        npSeek.setThumbTintList(ColorStateList.valueOf(ACCENT));
        npSeek.setProgressBackgroundTintList(ColorStateList.valueOf(RecordArtwork.blend(SURFACE,TEXT,.18f)));
        npSeek.setPadding(dp(4), dp(16), dp(4), dp(8));
        npSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser) npPos.setText(formatTime(progress));
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
                userSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                userSeeking = false;
                if (service != null) service.seekTo(bar.getProgress());
            }
        });
        col.addView(npSeek);

        LinearLayout times = new LinearLayout(this);
        times.setOrientation(LinearLayout.HORIZONTAL);
        times.setPadding(dp(4), 0, dp(4), 0);
        npPos = text(12, SUBTEXT, false);
        npDur = text(12, SUBTEXT, false);
        times.addView(npPos, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        times.addView(npDur);
        col.addView(times);

        // Transport controls
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER);
        controls.setPadding(0, dp(12), 0, dp(8));
        controls.setWeightSum(5);
        npShuffle = iconButton(R.drawable.ic_shuffle, 48, SUBTEXT);
        ImageButton prev = iconButton(R.drawable.ic_prev, 56, TEXT);
        npPlay = iconButton(R.drawable.ic_play, 72, ON_ACCENT);
        npPlay.setBackground(oval(ACCENT));
        ImageButton next = iconButton(R.drawable.ic_next, 56, TEXT);
        npRepeat = iconButton(R.drawable.ic_repeat, 48, SUBTEXT);
        npShuffle.setOnClickListener(v -> { if (service != null) service.toggleShuffle(); });
        prev.setOnClickListener(v -> { if (service != null) service.previous(); });
        npPlay.setOnClickListener(v -> { if (service != null) service.toggle(); });
        next.setOnClickListener(v -> { if (service != null) service.next(); });
        npRepeat.setOnClickListener(v -> { if (service != null) service.cycleRepeat(); });
        for (View b : new View[]{npShuffle, prev, npPlay, next, npRepeat}) {
            LinearLayout.LayoutParams transport = new LinearLayout.LayoutParams(0, dp(72), 1f);
            transport.setMargins(dp(2), 0, dp(2), 0);
            controls.addView(b, transport);
        }
        col.addView(controls);

        LinearLayout bottom = new LinearLayout(this);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        favoriteButton = actionChip("♡ Favorite", () -> {
            if (service != null && service.getCurrentSong() != null) {
                preferences.toggleFavorite(service.getCurrentSong()); repaint();
            }
        });
        addActionCell(bottom,favoriteButton);
        npLyricsToggle=actionChip("Lyrics",()->{
            if(npLyrics.getVisibility()==View.VISIBLE)showCover();
            else if(service!=null&&service.getCurrentSong()!=null)showSongLyrics(service.getCurrentSong());
        });
        addActionCell(bottom,npLyricsToggle);
        addActionCell(bottom,actionChip("Queue", this::showQueue));
        col.addView(bottom);
        timerButton = actionChip("Sleep timer", () -> tools.sleepTimer());
        LinearLayout.LayoutParams timerLayout=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(40));
        timerLayout.topMargin=dp(6);
        col.addView(timerButton,timerLayout);
        col.addView(buildNextTrack());

        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(col,new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        outer.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        npView = outer;
        return outer;
    }

    private View buildQueue() {
        FrameLayout outer = new FrameLayout(this);
        outer.setBackgroundColor(BG);
        outer.setClickable(true);
        outer.setVisibility(View.GONE);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(12), dp(8), dp(20), dp(8));
        ImageButton close = iconButton(R.drawable.ic_expand, 44, TEXT);
        close.setOnClickListener(v -> hideQueue());
        top.addView(close);
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(dp(8), 0, 0, 0);
        TextView title = text(22, TEXT, true);
        title.setText("Queue");
        queueCount = text(13, SUBTEXT, false);
        titles.addView(title);
        titles.addView(queueCount);
        top.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        col.addView(top);

        TextView hint = text(12, SUBTEXT, false);
        hint.setText("Drag ≡ to reorder • tap a song to play it");
        hint.setPadding(dp(20), 0, dp(20), dp(6));
        col.addView(hint);

        queueList = new ListView(this);
        queueList.setDivider(null);
        queueList.setCacheColorHint(Color.TRANSPARENT);
        queueList.setClipToPadding(false);
        queueList.setPadding(0, 0, 0, dp(16));
        queueAdapter = new QueueAdapter();
        queueList.setAdapter(queueAdapter);
        queueList.setOnItemClickListener((parent, view, position, id) -> {
            if (service != null) service.playAt(position);
        });
        final DragController drag = new DragController(queueList, new Reorder() {
            @Override
            public boolean move(int from, int to) {
                if (service == null || to < 0 || to >= service.getQueue().size()) return false;
                service.moveQueueItem(from, to);
                return true;
            }

            @Override
            public void done() {
            }
        }, () -> queueAdapter.notifyDataSetChanged());
        queueAdapter.drag = drag;
        col.addView(queueList, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        outer.addView(col, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        queueView = outer;
        return outer;
    }

    // ---- Drag to reorder ----

    /** Lets a row's handle be dragged up and down to reorder a ListView. */
    private final class DragController {
        final ListView list;
        final Reorder reorder;
        final Runnable changed;
        int dragging = -1;

        DragController(ListView list, Reorder reorder, Runnable changed) {
            this.list = list;
            this.reorder = reorder;
            this.changed = changed;
        }

        void attach(View handle, final Holder h) {
            handle.setOnTouchListener((v, ev) -> {
                switch (ev.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        dragging = h.position;
                        list.requestDisallowInterceptTouchEvent(true);
                        changed.run();
                        return true;
                    case MotionEvent.ACTION_MOVE: {
                        if (dragging < 0) return false;
                        int[] loc = new int[2];
                        list.getLocationOnScreen(loc);
                        int y = (int) ev.getRawY() - loc[1];
                        int edge = dp(64);
                        if (y < edge) list.smoothScrollBy(-dp(24), 50);
                        else if (y > list.getHeight() - edge) list.smoothScrollBy(dp(24), 50);
                        int target = list.pointToPosition(list.getWidth() / 2,
                                Math.max(0, Math.min(y, list.getHeight() - 1)));
                        if (target != AdapterView.INVALID_POSITION && target != dragging) {
                            int step = target > dragging ? 1 : -1;
                            while (dragging != target && reorder.move(dragging, dragging + step)) {
                                dragging += step;
                            }
                            changed.run();
                        }
                        return true;
                    }
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (dragging >= 0) {
                            dragging = -1;
                            reorder.done();
                            changed.run();
                        }
                        return true;
                }
                return false;
            });
        }
    }

    // ---- Adapters ----

    private static final class Holder {
        int position;
        ImageView drag;
        ImageView art;
        TextView title;
        TextView subtitle;
        TextView number, duration;
        ImageButton action;
    }

    /** A list row: [drag handle] [art] [title / subtitle] [action button]. */
    private View newItemRow(ViewGroup parent) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(82));

        Holder h = new Holder();
        h.drag = new ImageView(this);
        h.drag.setImageResource(R.drawable.ic_drag);
        h.drag.setImageTintList(ColorStateList.valueOf(SUBTEXT));
        h.drag.setScaleType(ImageView.ScaleType.CENTER);
        row.addView(h.drag, new LinearLayout.LayoutParams(dp(44), dp(56)));

        h.number=text(11,SUBTEXT,false);
        h.number.setGravity(Gravity.CENTER);
        row.addView(h.number,new LinearLayout.LayoutParams(dp(24),dp(48)));
        h.duration=text(11,SUBTEXT,false);
        h.duration.setGravity(Gravity.CENTER);
        h.art = new ImageView(this);
        h.art.setClipToOutline(true);
        row.addView(h.art, new LinearLayout.LayoutParams(dp(52), dp(52)));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(dp(14), 0, dp(4), 0);
        h.title = text(15, TEXT, false);
        h.subtitle = text(13, SUBTEXT, false);
        texts.addView(h.title);
        texts.addView(h.subtitle);
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        row.addView(h.duration,new LinearLayout.LayoutParams(dp(42),dp(48)));
        h.action = iconButton(R.drawable.ic_more, 44, SUBTEXT);
        h.action.setFocusable(false);
        row.addView(h.action);

        row.setTag(h);
        return row;
    }

    private class RowAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return rows.size();
        }

        @Override
        public Object getItem(int position) {
            return rows.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public int getViewTypeCount() {
            return 2;
        }

        @Override
        public int getItemViewType(int position) {
            return rows.get(position) instanceof String ? 1 : 0;
        }

        @Override
        public boolean isEnabled(int position) {
            return !(rows.get(position) instanceof String);
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            Object o = rows.get(position);
            if (o instanceof String) {
                TextView t = convertView instanceof TextView ? (TextView) convertView : text(13, SUBTEXT, true);
                t.setPadding(dp(20), dp(16), dp(20), dp(6));
                t.setAllCaps(true);
                t.setLetterSpacing(0.08f);
                t.setText((String) o);
                return t;
            }
            View row = convertView != null ? convertView : newItemRow(parent);
            final Holder h = (Holder) row.getTag();
            h.position = position;

            boolean draggable = detail.type == DETAIL_PLAYLIST && query().isEmpty() && o instanceof Song;
            h.drag.setVisibility(draggable ? View.VISIBLE : View.GONE);
            row.setPadding(draggable ? dp(0) : dp(20), dp(4), dp(8), dp(4));
            if (draggable) listDrag.attach(h.drag, h);
            row.setBackgroundColor(draggable && listDrag.dragging == position ? SURFACE2 : Color.TRANSPARENT);

            h.number.setVisibility(View.GONE);
            h.duration.setVisibility(View.GONE);
            h.subtitle.setVisibility(View.VISIBLE);
            h.action.setVisibility(View.VISIBLE);
            h.action.setOnClickListener(v -> showRowMenu(v, position));
            h.title.setTextColor(TEXT);
            h.title.setTypeface(Typeface.DEFAULT);
            h.art.setBackground(rounded(SURFACE, 10));

            if (o instanceof Song) {
                Song s = (Song) o;
                Song current = service != null ? service.getCurrentSong() : null;
                boolean active = current != null && current.uri.equals(s.uri);
                boolean numbered=detail.type==DETAIL_ALBUM || detail.type==DETAIL_PLAYLIST || (detail.type==DETAIL_NONE && (tab==TAB_SONGS || tab==TAB_FAVORITES));
                h.number.setVisibility(numbered ? View.VISIBLE : View.GONE);
                h.number.setText(active && service.isActive() ? "Ⅱ" : String.format(Locale.US,"%02d",position+1-(detail.type==DETAIL_NONE && tab==TAB_SONGS && query().isEmpty()?1:0)));
                h.number.setTextColor(active ? TEXT : SUBTEXT);
                h.number.setContentDescription(active && service.isActive() ? "Playing" : "Track number");
                h.duration.setVisibility(View.VISIBLE);
                h.duration.setText(formatTime(s.duration));
                if (active) row.setBackground(outlined(RecordArtwork.blend(BG,ACCENT,.08f),RecordArtwork.blend(BG,ACCENT,.18f),14));
                h.title.setText((preferences.favorite(s) ? "♥  " : "") + s.title);
                h.title.setTextColor(TEXT);
                h.title.setTypeface(active ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
                String sub = detail.type == DETAIL_ARTIST
                        ? (s.album.isEmpty() ? "Unknown album" : s.album) : s.artist;
                if (detail.type == DETAIL_ALBUM && s.track > 0) sub = (s.track % 1000) + ".  " + sub;
                h.subtitle.setText(sub + (s.album.isEmpty() || detail.type==DETAIL_ALBUM || detail.type==DETAIL_ARTIST ? "" : "  ·  " + s.album));
                bindArt(h.art, s, LIST_ART_PX, R.drawable.ic_music, dp(12));
            } else if (o instanceof Album) {
                Album a = (Album) o;
                h.title.setText(a.name);
                h.subtitle.setText(a.songs.size() + (a.songs.size() == 1 ? " song" : " songs"));
                bindArt(h.art, a.songs.get(0), LIST_ART_PX, R.drawable.ic_album, dp(12));
            } else if (o instanceof Artist) {
                Artist a = (Artist) o;
                h.title.setText(a.name);
                h.subtitle.setText(a.albumCount + (a.albumCount == 1 ? " album" : " albums") + "  •  "
                        + a.songs.size() + (a.songs.size() == 1 ? " song" : " songs"));
                h.art.setBackground(oval(SURFACE));
                bindArt(h.art, a.songs.get(0), LIST_ART_PX, R.drawable.ic_person, dp(12));
            } else if (o instanceof PlaylistStore.Playlist) {
                PlaylistStore.Playlist p = (PlaylistStore.Playlist) o;
                List<Song> songs = resolve(p);
                h.title.setText(p.name);
                h.subtitle.setText(songs.size() + (songs.size() == 1 ? " song" : " songs"));
                bindArt(h.art, songs.isEmpty() ? null : songs.get(0), LIST_ART_PX, R.drawable.ic_queue, dp(12));
            } else if (o instanceof Action) {
                Action a = (Action) o;
                h.title.setText(a.label);
                h.title.setTextColor(TEXT);
                h.title.setTypeface(Typeface.DEFAULT_BOLD);
                h.subtitle.setVisibility(View.GONE);
                h.action.setVisibility(View.GONE);
                h.art.setTag(null);
                h.art.setBackground(oval(SURFACE));
                setPlaceholder(h.art, a.icon, dp(12));
                h.art.setImageTintList(ColorStateList.valueOf(ACCENT));
            }
            return row;
        }
    }

    private class QueueAdapter extends BaseAdapter {
        DragController drag;

        @Override
        public int getCount() {
            return service != null ? service.getQueue().size() : 0;
        }

        @Override
        public Object getItem(int position) {
            return service.getQueue().get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            View row = convertView != null ? convertView : newItemRow(parent);
            Holder h = (Holder) row.getTag();
            h.position = position;
            row.setPadding(0, dp(4), dp(8), dp(4));
            drag.attach(h.drag, h);

            Song s = service.getQueue().get(position);
            boolean current = position == service.getIndex();
            boolean dragged = drag.dragging == position;
            row.setBackgroundColor(dragged ? SURFACE2 : current ? RecordArtwork.blend(BG,ACCENT,.10f) : Color.TRANSPARENT);
            h.number.setVisibility(View.GONE);
            h.duration.setVisibility(View.VISIBLE);
            h.duration.setText(formatTime(s.duration));
            h.title.setText(s.title);
            h.title.setTextColor(TEXT);
            h.title.setTypeface(current ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            h.subtitle.setText((current ? "Now playing  •  " : "") + s.artist);
            h.art.setBackground(rounded(SURFACE, 10));
            bindArt(h.art, s, LIST_ART_PX, R.drawable.ic_music, dp(12));
            h.action.setImageResource(R.drawable.ic_close);
            h.action.setContentDescription("Remove from queue");
            h.action.setOnClickListener(v -> { if (service != null) service.removeFromQueue(position); });
            return row;
        }
    }

    private class AlbumAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return shownAlbums.size();
        }

        @Override
        public Object getItem(int position) {
            return shownAlbums.get(position);
        }

        @Override
        public long getItemId(int position) {
            return shownAlbums.get(position).id;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout cell;
            if (convertView == null) {
                cell = new LinearLayout(MainActivity.this);
                cell.setOrientation(LinearLayout.VERTICAL);
                ImageView art = new SquareImageView(MainActivity.this);
                art.setBackground(rounded(SURFACE, 14));
                art.setClipToOutline(true);
                setRipple(art);
                cell.addView(art, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                TextView title = text(14, TEXT, true);
                title.setPadding(dp(2), dp(8), dp(2), 0);
                cell.addView(title);
                TextView sub = text(12, SUBTEXT, false);
                sub.setPadding(dp(2), dp(1), dp(2), 0);
                cell.addView(sub);
            } else {
                cell = (LinearLayout) convertView;
            }
            Album a = shownAlbums.get(position);
            ImageView art = (ImageView) cell.getChildAt(0);
            TextView title = (TextView) cell.getChildAt(1);
            TextView sub = (TextView) cell.getChildAt(2);
            Song current = service != null ? service.getCurrentSong() : null;
            title.setText(a.name);
            title.setTextColor(TEXT);
            sub.setText(a.songs.size() + " tracks  ·  " + a.artist);
            bindArt(art, a.songs.get(0), GRID_ART_PX, R.drawable.ic_album, dp(40));
            return cell;
        }
    }
}
