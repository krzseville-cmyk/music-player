package simple.music.player;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.MediaPlayer;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Binder;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.media.PlaybackParams;
import android.media.audiofx.Equalizer;
import android.media.audiofx.BassBoost;
import android.media.audiofx.LoudnessEnhancer;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class MusicService extends Service implements MediaPlayer.OnPreparedListener,
        MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener,
        AudioManager.OnAudioFocusChangeListener {

    public static final String ACTION_TOGGLE = "simple.music.player.TOGGLE";
    public static final String ACTION_NEXT = "simple.music.player.NEXT";
    public static final String ACTION_PREV = "simple.music.player.PREV";
    public static final String ACTION_STOP = "simple.music.player.STOP";
    public static final String EXTRA_OPEN_PLAYER = "simple.music.player.OPEN_PLAYER";

    public static final int REPEAT_OFF = 0;
    public static final int REPEAT_ALL = 1;
    public static final int REPEAT_ONE = 2;

    private static final String CHANNEL_ID = "playback";
    private static final int NOTIFICATION_ID = 1;

    public interface Listener {
        void onPlayerChanged();
    }

    public class LocalBinder extends Binder {
        public MusicService getService() {
            return MusicService.this;
        }
    }

    private final IBinder binder = new LocalBinder();
    private final List<Listener> listeners = new ArrayList<>();
    private final Random random = new Random();
    private final AudioAttributes audioAttributes = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build();

    private MediaPlayer player;
    private MediaSession session;
    private AudioManager audioManager;
    private AudioFocusRequest focusRequest;
    private NotificationManager notificationManager;
    private SharedPreferences prefs;

    /** Play order (shuffled when shuffle is on). */
    private List<Song> queue = new ArrayList<>();
    /** The queue in its original order, restored when shuffle is turned off. */
    private List<Song> original = new ArrayList<>();
    private int index = -1;
    private boolean prepared;
    private boolean preparing;
    private boolean playWhenReady;
    private boolean resumeOnFocusGain;
    private boolean shuffle;
    private int repeatMode = REPEAT_ALL;
    private int consecutiveErrors;
    private boolean isForeground;
    private PlaybackSnapshot snapshot;
    private LibraryPreferences library;
    private final ExecutorService checkpointWriter = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int restorePosition;
    private boolean countOnStart;
    private long sleepDeadline;
    private boolean sleepAtEnd;
    private float speed = 1f;
    private Equalizer equalizer;
    private BassBoost bass;
    private LoudnessEnhancer loudness;
    private float trackVolume = 1f;
    private float duckVolume = 1f;
    private boolean fading;
    private int fadeStart;
    private final Runnable transitionTick = new Runnable() {
        @Override public void run() {
            if (prepared && isPlaying() && upcomingReady && upcoming != null && crossfadeSeconds() > 0 && !sleepAtEnd) {
                try {
                    int remaining = player.getDuration() - player.getCurrentPosition();
                    int duration = Math.min(crossfadeSeconds() * 1000, Math.min(player.getDuration()/2, upcoming.getDuration()/2));
                    duration = Math.max(1, (int)(duration * speed));
                    if (!fading && remaining <= duration && remaining > 0) {
                        fadeStart = player.getCurrentPosition(); fading = true;
                        upcoming.setVolume(0f,0f); upcoming.start();
                        upcoming.setPlaybackParams(new PlaybackParams().setSpeed(speed).setPitch(1f));
                    }
                    if (fading) {
                        float fraction = Math.min(1f, Math.max(0f, (player.getCurrentPosition()-fadeStart)/(float)Math.max(1,player.getDuration()-fadeStart)));
                        player.setVolume(trackVolume*duckVolume*(1-fraction),trackVolume*duckVolume*(1-fraction));
                        upcoming.setVolume(duckVolume*fraction,duckVolume*fraction);
                    }
                } catch (RuntimeException e) { releaseUpcoming(); }
            }
            handler.postDelayed(this,100);
        }
    };
    private MediaPlayer upcoming;
    private int upcomingIndex = -1;
    private boolean upcomingReady;
    private final Runnable checkpoint = new Runnable() {
        @Override public void run() {
            savePlayback();
            if (sleepDeadline > 0 && SystemClock.elapsedRealtime() >= sleepDeadline) {
                sleepDeadline = 0;
                pause();
            }
            handler.postDelayed(this, 1000);
        }
    };
    private int checkpointTicks;
    private Bitmap currentArt;
    private long currentArtSongId = -1;

    private final BroadcastReceiver noisyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Headphones unplugged: pause instead of blasting the speaker.
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) {
                pause();
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        prefs = getSharedPreferences("player", MODE_PRIVATE);
        library = new LibraryPreferences(this);
        snapshot = new PlaybackSnapshot(this);
        snapshot.read();
        queue = snapshot.queue;
        original = snapshot.original;
        index = queue.isEmpty() ? -1 : Math.min(Math.max(0, snapshot.index), queue.size() - 1);
        restorePosition = snapshot.position;
        speed = prefs.getFloat("speed", 1f);
        handler.post(checkpoint);
        handler.post(transitionTick);
        shuffle = prefs.getBoolean("shuffle", false);
        repeatMode = prefs.getInt("repeat", REPEAT_ALL);

        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Playback",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setShowBadge(false);
            notificationManager.createNotificationChannel(channel);
        }

        session = new MediaSession(this, "SimplePlayer");
        session.setCallback(new MediaSession.Callback() {
            @Override public void onPlay() { play(); }
            @Override public void onPlayFromMediaId(String id, android.os.Bundle extras) {
                checkpointWriter.execute(() -> {
                    try {
                        List<Song> songs = LibraryRepository.load(MusicService.this);
                        List<Song> visible = new ArrayList<>();
                        java.util.Set<String> excluded = library.excluded();
                        for (Song song : songs) if (!excluded.contains(song.folder)) visible.add(song);
                        handler.post(() -> {
                            for (int i=0;i<visible.size();i++) if (visible.get(i).uri.toString().equals(id)) { playQueue(visible,i); return; }
                        });
                    } catch (Exception ignored) { }
                });
            }
            @Override public void onPause() { pause(); }
            @Override public void onSkipToNext() { next(); }
            @Override public void onSkipToPrevious() { previous(); }
            @Override public void onStop() { stopPlayback(); }
            @Override public void onSeekTo(long pos) { seekTo((int) pos); }
        });
        session.setSessionActivity(openPlayerIntent());
        session.setActive(true);
        updateSession();
        loadArt();

        registerReceiver(noisyReceiver, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            // Widget clicks may start a cold process through a foreground-service PendingIntent.
            // Publish the restored track before asynchronous preparation starts.
            if (!isForeground && getCurrentSong() != null) {
                if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION_ID,buildNotification(),ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
                else startForeground(NOTIFICATION_ID,buildNotification());
                isForeground = true;
            }
            switch (intent.getAction()) {
                case ACTION_TOGGLE: toggle(); break;
                case ACTION_NEXT: next(); break;
                case ACTION_PREV: previous(); break;
                case ACTION_STOP: stopPlayback(); break;
            }
        }
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        if (!isPlaying()) {
            stopPlayback();
            stopSelf();
        }
    }

    @Override
    public void onDestroy() {
        savePlaybackNow();
        handler.removeCallbacksAndMessages(null);
        checkpointWriter.shutdown();
        releaseUpcoming();
        releaseEffects();
        unregisterReceiver(noisyReceiver);
        abandonFocus();
        if (player != null) {
            player.release();
            player = null;
        }
        session.release();
        stopForeground(true);
        notificationManager.cancel(NOTIFICATION_ID);
        super.onDestroy();
    }

    // ---- Listeners ----

    public void addListener(Listener l) {
        if (!listeners.contains(l)) listeners.add(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    private void dispatch() {
        savePlaybackNow();
        PlayerWidget.update(this, this);
        if (prepared && isPlaying()) prepareUpcoming();
        for (Listener l : new ArrayList<>(listeners)) l.onPlayerChanged();
    }

    private void updateAll() {
        updateSession();
        updateNotification();
        dispatch();
    }

    // ---- Public controls ----

    public void playQueue(List<Song> songs, int start) {
        if (songs.isEmpty()) return;
        original = new ArrayList<>(songs);
        queue = new ArrayList<>(songs);
        index = Math.max(0, Math.min(start, queue.size() - 1));
        if (shuffle) shuffleAroundCurrent();
        consecutiveErrors = 0;
        restorePosition = 0;
        load(index, true);
    }

    /** Turns shuffle on and plays the songs starting from a random one. */
    public void playShuffled(List<Song> songs) {
        if (songs.isEmpty()) return;
        setShuffle(true);
        playQueue(songs, random.nextInt(songs.size()));
    }

    public void playAt(int i) {
        if (i < 0 || i >= queue.size()) return;
        consecutiveErrors = 0;
        restorePosition = 0;
        load(i, true);
    }

    /** Inserts songs right after the current one. */
    public void playNext(List<Song> songs) {
        if (songs.isEmpty()) return;
        if (queue.isEmpty()) {
            playQueue(songs, 0);
            return;
        }
        releaseUpcoming();
        queue.addAll(index + 1, songs);
        int o = indexOfSame(original, getCurrentSong());
        original.addAll(o >= 0 ? o + 1 : original.size(), songs);
        dispatch();
    }

    public void addToQueue(List<Song> songs) {
        if (songs.isEmpty()) return;
        if (queue.isEmpty()) {
            playQueue(songs, 0);
            return;
        }
        releaseUpcoming();
        queue.addAll(songs);
        original.addAll(songs);
        dispatch();
    }

    public void removeFromQueue(int i) {
        if (i < 0 || i >= queue.size()) return;
        releaseUpcoming();
        Song removed = queue.remove(i);
        int o = indexOfSame(original, removed);
        if (o >= 0) original.remove(o);
        if (queue.isEmpty()) {
            index = -1;
            stopPlayback();
            if (player != null) player.reset();
            prepared = false;
            preparing = false;
            dispatch();
        } else if (i == index) {
            boolean wasPlaying = isPlaying() || (preparing && playWhenReady);
            load(Math.min(i, queue.size() - 1), wasPlaying);
        } else {
            if (i < index) index--;
            dispatch();
        }
    }

    public void moveQueueItem(int from, int to) {
        if (from < 0 || to < 0 || from >= queue.size() || to >= queue.size() || from == to) return;
        releaseUpcoming();
        queue.add(to, queue.remove(from));
        if (from == index) index = to;
        else if (from < index && to >= index) index--;
        else if (from > index && to <= index) index++;
        dispatch();
    }

    public void play() {
        if (getCurrentSong() == null) return;
        if (prepared) {
            startPlayback();
        } else if (preparing) {
            playWhenReady = true;
            updateAll();
        } else {
            load(index, true);
        }
    }

    public void pause() {
        playWhenReady = false;
        resumeOnFocusGain = false;
        if (prepared && player.isPlaying()) player.pause();
        if (fading && upcoming != null) { try { upcoming.pause(); } catch (RuntimeException ignored) { } }
        updateAll();
    }

    public void toggle() {
        if (isPlaying() || (preparing && playWhenReady)) pause();
        else play();
    }

    public void next() {
        if (queue.isEmpty()) return;
        load((index + 1) % queue.size(), true);
    }

    public void previous() {
        if (queue.isEmpty()) return;
        if (prepared && player.getCurrentPosition() > 3000) {
            seekTo(0);
            return;
        }
        load((index - 1 + queue.size()) % queue.size(), true);
    }

    public void seekTo(int ms) {
        if (prepared) {
            releaseUpcoming();
            player.seekTo(ms);
            prepareUpcoming();
            savePlaybackNow();
            updateSession();
        }
    }

    public void toggleShuffle() {
        setShuffle(!shuffle);
        dispatch();
    }

    private void setShuffle(boolean on) {
        if (shuffle == on) return;
        releaseUpcoming();
        shuffle = on;
        prefs.edit().putBoolean("shuffle", shuffle).apply();
        if (queue.isEmpty()) return;
        if (shuffle) {
            shuffleAroundCurrent();
        } else {
            Song current = getCurrentSong();
            queue = new ArrayList<>(original);
            index = Math.max(0, indexOfSame(queue, current));
        }
    }

    public void cycleRepeat() {
        releaseUpcoming();
        repeatMode = (repeatMode + 1) % 3;
        prefs.edit().putInt("repeat", repeatMode).apply();
        dispatch();
    }

    public void stopPlayback() {
        pause();
        abandonFocus();
        stopForeground(true);
        isForeground = false;
        notificationManager.cancel(NOTIFICATION_ID);
    }

    // ---- State ----

    public MediaSession.Token sessionToken() { return session.getSessionToken(); }

    public Song getCurrentSong() {
        return (index >= 0 && index < queue.size()) ? queue.get(index) : null;
    }

    public List<Song> getQueue() {
        return Collections.unmodifiableList(queue);
    }

    public int getIndex() {
        return index;
    }

    public boolean isPlaying() {
        return prepared && player.isPlaying();
    }

    /** True while playing or about to play (buffering with play requested). */
    public boolean isActive() {
        return isPlaying() || (preparing && playWhenReady);
    }

    public boolean isShuffle() {
        return shuffle;
    }

    public int getRepeatMode() {
        return repeatMode;
    }

    public int getPosition() {
        return prepared ? player.getCurrentPosition() : restorePosition;
    }

    public int getDuration() {
        if (prepared) return player.getDuration();
        Song s = getCurrentSong();
        return s != null ? (int) s.duration : 0;
    }

    public Bitmap getArt() {
        return currentArt;
    }

    // ---- Player internals ----

    private static int indexOfSame(List<Song> list, Song s) {
        if (s == null) return -1;
        for (int i = 0; i < list.size(); i++) if (list.get(i).uri.equals(s.uri)) return i;
        return -1;
    }

    /** Moves the current song to the front and shuffles everything after it. */
    private void shuffleAroundCurrent() {
        if (index < 0 || index >= queue.size()) return;
        Song current = queue.remove(index);
        Collections.shuffle(queue, random);
        queue.add(0, current);
        index = 0;
    }

    private void load(int i, boolean autoPlay) {
        if (i < 0 || i >= queue.size()) return;
        releaseUpcoming();
        if (index != i) restorePosition = 0;
        index = i;
        countOnStart = true;
        prepared = false;
        preparing = false;
        playWhenReady = autoPlay;
        if (player == null) {
            player = new MediaPlayer();
            player.setAudioAttributes(audioAttributes);
            player.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
            player.setOnPreparedListener(this);
            player.setOnCompletionListener(this);
            player.setOnErrorListener(this);
            player.setOnSeekCompleteListener(seekPlayer -> { updateSession(); savePlaybackNow(); });
            configureEffects();
        } else {
            player.reset();
        }
        loadArt();
        try {
            player.setDataSource(this, queue.get(i).uri);
            player.prepareAsync();
            preparing = true;
        } catch (Exception e) {
            handleError();
            return;
        }
        updateAll();
    }

    private void handleError() {
        prepared = false;
        preparing = false;
        consecutiveErrors++;
        if (queue.size() > 1 && consecutiveErrors < queue.size()) {
            load((index + 1) % queue.size(), true);
        } else {
            playWhenReady = false;
            updateAll();
        }
    }

    private void startPlayback() {
        if (!requestFocus()) return;
        try {
            // Keep the service alive after the activity unbinds.
            startService(new Intent(this, MusicService.class));
        } catch (Exception ignored) {
        }
        player.setVolume(trackVolume * duckVolume, trackVolume * duckVolume);
        player.start();
        if (fading && upcoming != null) upcoming.start();
        applySpeed();
        if (countOnStart) { library.played(getCurrentSong()); countOnStart = false; loadGain(); }
        playWhenReady = true;
        prepareUpcoming();
        updateAll();
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        preparing = false;
        prepared = true;
        consecutiveErrors = 0;
        if (restorePosition > 0) { player.seekTo(restorePosition); restorePosition = 0; }
        if (playWhenReady) startPlayback();
        else updateAll();
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        if (mp != player) return;
        if (sleepAtEnd) {
            sleepAtEnd = false;
            releaseUpcoming();
            pause();
            return;
        }
        if (upcomingReady && upcoming != null) {
            boolean alreadyPlaying = fading || (crossfadeSeconds() == 0 && gapless());
            MediaPlayer old = player;
            releaseEffects();
            player = upcoming;
            // A preloaded player becomes the normal player and may be reset for a later queue.
            player.setOnPreparedListener(this);
            player.setOnErrorListener(this);
            player.setOnSeekCompleteListener(seekPlayer -> { updateSession(); savePlaybackNow(); });
            index = upcomingIndex;
            upcoming = null; upcomingReady = false; upcomingIndex = -1;
            old.release();
            fading = false; trackVolume = 1f;
            configureEffects();
            if (!alreadyPlaying) player.start();
            player.setVolume(duckVolume,duckVolume);
            loadGain();
            prepared = true;
            library.played(getCurrentSong());
            loadArt();
            applySpeed();
            prepareUpcoming();
            updateAll();
            return;
        }
        if (repeatMode == REPEAT_ONE) {
            player.seekTo(0);
            player.start();
            updateAll();
        } else if (repeatMode == REPEAT_OFF && index == queue.size() - 1) {
            playWhenReady = false;
            player.seekTo(0);
            updateAll();
        } else {
            next();
        }
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        handleError();
        return true;
    }

    // ---- Audio focus ----

    private boolean requestFocus() {
        int result;
        if (Build.VERSION.SDK_INT >= 26) {
            if (focusRequest == null) {
                focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(audioAttributes)
                        .setOnAudioFocusChangeListener(this)
                        .build();
            }
            result = audioManager.requestAudioFocus(focusRequest);
        } else {
            result = audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN);
        }
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }

    private void abandonFocus() {
        if (Build.VERSION.SDK_INT >= 26) {
            if (focusRequest != null) audioManager.abandonAudioFocusRequest(focusRequest);
        } else {
            audioManager.abandonAudioFocus(this);
        }
    }

    @Override
    public void onAudioFocusChange(int change) {
        switch (change) {
            case AudioManager.AUDIOFOCUS_LOSS:
                pause();
                abandonFocus();
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                if (isPlaying()) {
                    pause();
                    resumeOnFocusGain = true;
                }
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                duckVolume = .2f;
                if (player != null) player.setVolume(trackVolume * duckVolume, trackVolume * duckVolume);
                if (fading && upcoming != null) upcoming.setVolume(.2f,.2f);
                break;
            case AudioManager.AUDIOFOCUS_GAIN:
                duckVolume = 1f;
                if (player != null) player.setVolume(trackVolume, trackVolume);
                if (resumeOnFocusGain) {
                    resumeOnFocusGain = false;
                    play();
                }
                break;
        }
    }

    private void savePlayback() {
        // Pause/queue edits checkpoint immediately; active position is checkpointed every ten seconds.
        if (++checkpointTicks % 10 == 0 && isPlaying()) savePlaybackNow();
    }

    private void savePlaybackNow() {
        final List<Song> songs = new ArrayList<>(queue);
        final List<Song> order = new ArrayList<>(original);
        final int current = index;
        final int ms = getPosition();
        if (!checkpointWriter.isShutdown()) checkpointWriter.execute(() -> snapshot.save(songs, order, current, ms));
    }

    public void setSleepTimer(int minutes, boolean endOfSong) {
        sleepDeadline = minutes > 0 ? SystemClock.elapsedRealtime() + minutes * 60000L : 0;
        sleepAtEnd = endOfSong;
        releaseUpcoming();
        if (!endOfSong) prepareUpcoming();
        dispatch();
    }
    public String sleepDescription() {
        if (sleepAtEnd) return "Stops at the end of this song";
        if (sleepDeadline == 0) return "Off";
        return "Stops in " + Math.max(1, (sleepDeadline - SystemClock.elapsedRealtime() + 59999) / 60000) + " min";
    }
    public float getSpeed() { return speed; }
    public void setSpeed(float value) {
        releaseUpcoming();
        speed = Math.max(0.5f, Math.min(2f, value));
        prefs.edit().putFloat("speed", speed).apply();
        applySpeed(); updateAll();
    }
    private void applySpeed() {
        try {
            if (prepared) {
                boolean wasPlaying = player.isPlaying();
                player.setPlaybackParams(new PlaybackParams().setSpeed(speed).setPitch(1f));
                if (!wasPlaying) player.pause();
            }
        }
        catch (RuntimeException ignored) { }
    }
    public void clearQueue() {
        releaseUpcoming();
        stopPlayback();
        queue.clear(); original.clear(); index = -1; restorePosition = 0;
        if (player != null) player.reset();
        prepared = false; preparing = false;
        updateAll();
    }
    public void refreshMetadata(List<Song> songs) {
        java.util.Map<String,Song> latest = new java.util.HashMap<>();
        for (Song song : songs) latest.put(song.uri.toString(),song);
        for (List<Song> list : java.util.Arrays.asList(queue,original)) {
            for (int i=0;i<list.size();i++) {
                Song song = latest.get(list.get(i).uri.toString());
                if (song != null) list.set(i,song);
            }
        }
        currentArtSongId = -1;
        loadArt(); updateAll();
    }
    private void releaseEffects() {
        if (equalizer != null) { equalizer.release(); equalizer = null; }
        if (bass != null) { bass.release(); bass = null; }
        if (loudness != null) { loudness.release(); loudness = null; }
    }
    private void configureEffects() {
        releaseEffects();
        try { loudness = new LoudnessEnhancer(player.getAudioSessionId()); }
        catch (RuntimeException ignored) { }
        try {
            equalizer = new Equalizer(0, player.getAudioSessionId());
            int preset = prefs.getInt("eqPreset", -1);
            if (preset >= 0 && preset < equalizer.getNumberOfPresets()) equalizer.usePreset((short) preset);
            equalizer.setEnabled(prefs.getBoolean("eqEnabled", false));
        } catch (RuntimeException ignored) { }
        try {
            bass = new BassBoost(0, player.getAudioSessionId());
            bass.setStrength((short) prefs.getInt("bass", 0));
            bass.setEnabled(prefs.getInt("bass", 0) > 0);
        } catch (RuntimeException ignored) { }
    }
    public String[] equalizerPresets() {
        if (equalizer == null) return new String[0];
        String[] result = new String[equalizer.getNumberOfPresets()];
        for (short i = 0; i < result.length; i++) result[i] = equalizer.getPresetName(i);
        return result;
    }
    public void equalizerPreset(int preset) {
        prefs.edit().putBoolean("eqEnabled", preset >= 0).putInt("eqPreset", preset).apply();
        configureEffects();
        loadGain();
    }
    public void setBass(int value) { prefs.edit().putInt("bass", value).apply(); configureEffects(); loadGain(); }
    public boolean normalization() { return prefs.getBoolean("normalization",false); }
    public void setNormalization(boolean value) { prefs.edit().putBoolean("normalization",value).apply(); loadGain(); }
    private void loadGain() {
        final Song song = getCurrentSong();
        if (song == null || checkpointWriter.isShutdown()) return;
        final boolean enabled = normalization();
        checkpointWriter.execute(() -> {
            float gain = 0;
            if (enabled) try (InputStream in = getContentResolver().openInputStream(song.uri)) { gain = Mp3Metadata.replayGain(in); }
            catch (Exception ignored) { }
            final float db = gain;
            handler.post(() -> {
                if (getCurrentSong() == null || !song.uri.equals(getCurrentSong().uri)) return;
                trackVolume = db < 0 ? (float)Math.pow(10,db/20) : 1f;
                try {
                    if (prepared && !fading) player.setVolume(trackVolume*duckVolume,trackVolume*duckVolume);
                    if (loudness != null) { loudness.setTargetGain(Math.max(0,(int)(db*100))); loudness.setEnabled(db>0); }
                } catch (RuntimeException ignored) { }
            });
        });
    }
    public int crossfadeSeconds() { return prefs.getInt("crossfade",0); }
    public void setCrossfade(int seconds) { prefs.edit().putInt("crossfade",seconds).apply(); releaseUpcoming(); prepareUpcoming(); }
    public boolean gapless() { return prefs.getBoolean("gapless", true); }
    public void setGapless(boolean enabled) {
        prefs.edit().putBoolean("gapless", enabled).apply(); releaseUpcoming(); prepareUpcoming();
    }
    private void releaseUpcoming() {
        if (player != null && prepared) {
            try { player.setNextMediaPlayer(null); } catch (RuntimeException ignored) { }
        }
        if (upcoming != null) { upcoming.release(); upcoming = null; }
        if (fading && player != null && prepared) { try { player.setVolume(trackVolume*duckVolume,trackVolume*duckVolume); } catch (RuntimeException ignored) { } }
        fading = false;
        upcomingIndex = -1; upcomingReady = false;
    }
    private void prepareUpcoming() {
        if ((!gapless() && crossfadeSeconds() == 0) || !prepared || queue.isEmpty() || sleepAtEnd || upcoming != null || repeatMode == REPEAT_ONE) return;
        if (repeatMode == REPEAT_OFF && index == queue.size() - 1) return;
        final int next = (index + 1) % queue.size();
        final MediaPlayer candidate = new MediaPlayer();
        upcoming = candidate; upcomingIndex = next;
        candidate.setAudioAttributes(audioAttributes);
        candidate.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
        candidate.setOnPreparedListener(mp -> {
            if (upcoming != mp || !prepared) return;
            try { if (crossfadeSeconds() == 0 && gapless()) player.setNextMediaPlayer(mp); upcomingReady = true; }
            catch (RuntimeException e) { releaseUpcoming(); }
        });
        candidate.setOnCompletionListener(this);
        candidate.setOnErrorListener((mp, what, extra) -> { if (mp == upcoming) releaseUpcoming(); else if (mp == player) handleError(); return true; });
        try { candidate.setDataSource(this, queue.get(next).uri); candidate.prepareAsync(); }
        catch (Exception e) { releaseUpcoming(); }
    }

    // ---- Art, session, notification ----

    private void loadArt() {
        final Song song = getCurrentSong();
        if (song == null || song.id == currentArtSongId) return;
        currentArtSongId = song.id;
        currentArt = null;
        ArtLoader.load(this, song, bitmap -> {
            if (currentArtSongId == song.id) {
                currentArt = bitmap;
                updateAll();
            }
        });
    }

    private void updateSession() {
        boolean playing = isPlaying();
        int state = playing ? PlaybackState.STATE_PLAYING
                : (preparing && playWhenReady) ? PlaybackState.STATE_BUFFERING
                : PlaybackState.STATE_PAUSED;
        long actions = PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE
                | PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_SKIP_TO_NEXT
                | PlaybackState.ACTION_SKIP_TO_PREVIOUS | PlaybackState.ACTION_SEEK_TO
                | PlaybackState.ACTION_STOP;
        session.setPlaybackState(new PlaybackState.Builder()
                .setActions(actions)
                .setState(state, getPosition(), playing ? speed : 0f)
                .build());

        Song s = getCurrentSong();
        if (s != null) {
            MediaMetadata.Builder m = new MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, s.title)
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, s.artist)
                    .putString(MediaMetadata.METADATA_KEY_ALBUM, s.album)
                    .putLong(MediaMetadata.METADATA_KEY_DURATION, getDuration());
            if (currentArt != null) m.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, currentArt);
            session.setMetadata(m.build());
        }
    }

    private PendingIntent openPlayerIntent() {
        Intent open = new Intent(this, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_OPEN_PLAYER, true);
        return PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private PendingIntent serviceIntent(String action, int requestCode) {
        Intent i = new Intent(this, MusicService.class).setAction(action);
        return PendingIntent.getService(this, requestCode, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private Notification.Action action(int icon, String title, String action, int requestCode) {
        return new Notification.Action.Builder(Icon.createWithResource(this, icon), title,
                serviceIntent(action, requestCode)).build();
    }

    private Notification buildNotification() {
        Song s = getCurrentSong();
        boolean playing = isActive();
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        b.setSmallIcon(R.drawable.ic_music)
                .setContentTitle(s.title)
                .setContentText(s.artist)
                .setLargeIcon(currentArt)
                .setContentIntent(openPlayerIntent())
                .setDeleteIntent(serviceIntent(ACTION_STOP, 4))
                .setOngoing(playing)
                .setShowWhen(false)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .addAction(action(R.drawable.ic_prev, "Previous", ACTION_PREV, 1))
                .addAction(playing
                        ? action(R.drawable.ic_pause, "Pause", ACTION_TOGGLE, 2)
                        : action(R.drawable.ic_play, "Play", ACTION_TOGGLE, 2))
                .addAction(action(R.drawable.ic_next, "Next", ACTION_NEXT, 3))
                .addAction(action(R.drawable.ic_close, "Close", ACTION_STOP, 4))
                .setStyle(new Notification.MediaStyle()
                        .setMediaSession(session.getSessionToken())
                        .setShowActionsInCompactView(0, 1, 2));
        return b.build();
    }

    private void updateNotification() {
        if (getCurrentSong() == null) { notificationManager.cancel(NOTIFICATION_ID); return; }
        Notification n = buildNotification();
        if (isActive()) {
            if (!isForeground) {
                try {
                    if (Build.VERSION.SDK_INT >= 29) {
                        startForeground(NOTIFICATION_ID, n,
                                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
                    } else {
                        startForeground(NOTIFICATION_ID, n);
                    }
                    isForeground = true;
                } catch (Exception e) {
                    notificationManager.notify(NOTIFICATION_ID, n);
                }
            } else {
                notificationManager.notify(NOTIFICATION_ID, n);
            }
        } else {
            if (isForeground) {
                stopForeground(STOP_FOREGROUND_DETACH);
                isForeground = false;
            }
            notificationManager.notify(NOTIFICATION_ID, n);
        }
    }
}
