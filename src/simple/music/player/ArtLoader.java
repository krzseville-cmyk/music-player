package simple.music.player;

import android.content.ContentUris;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.media.MediaMetadataRetriever;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.util.Size;

import java.io.InputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Loads album art in the background and caches it by song id and size. */
public final class ArtLoader {
    public interface Callback {
        void onArt(Bitmap bitmap);
    }

    /** Default size used for the notification / lock screen. */
    public static final int DEFAULT_SIZE = 320;

    private static final LruCache<String, Bitmap> CACHE =
            new LruCache<String, Bitmap>((int) Math.min(Runtime.getRuntime().maxMemory() / 8, 48L * 1024 * 1024)) {
                @Override
                protected int sizeOf(String key, Bitmap value) {
                    return value.getByteCount();
                }
            };
    private static final Set<Long> MISSING = Collections.synchronizedSet(new HashSet<Long>());
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private ArtLoader() {
    }

    public static void clear() { CACHE.evictAll(); MISSING.clear(); }

    public static void load(Context context, Song song, Callback callback) {
        load(context, song, DEFAULT_SIZE, callback);
    }

    public static void load(Context context, final Song song, final int size, final Callback callback) {
        final String key = song.id + ":" + size;
        Bitmap cached = CACHE.get(key);
        if (cached != null || MISSING.contains(song.id)) {
            callback.onArt(cached);
            return;
        }
        final Context app = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            final Bitmap bitmap = loadSync(app, song, size);
            if (bitmap != null) {
                CACHE.put(key, bitmap);
            } else {
                MISSING.add(song.id);
            }
            MAIN.post(() -> callback.onArt(bitmap));
        });
    }

    private static Bitmap loadSync(Context context, Song song, int size) {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                Bitmap thumbnail = context.getContentResolver().loadThumbnail(song.uri, new Size(size, size), null);
                if (thumbnail != null) return thumbnail;
            }
            Uri artUri = ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"), song.albumId);
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = context.getContentResolver().openInputStream(artUri)) {
                if (in == null) return null;
                BitmapFactory.decodeStream(in, null, bounds);
            }
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = 1;
            while (bounds.outWidth / (opts.inSampleSize * 2) >= size) opts.inSampleSize *= 2;
            try (InputStream in = context.getContentResolver().openInputStream(artUri)) {
                if (in == null) return null;
                return BitmapFactory.decodeStream(in, null, opts);
            }
        } catch (Exception ignored) { }
        MediaMetadataRetriever reader = new MediaMetadataRetriever();
        try {
            reader.setDataSource(context,song.uri);
            byte[] cover = reader.getEmbeddedPicture();
            if (cover == null || cover.length > 10*1024*1024) return null;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(cover,0,cover.length,options);
            if (options.outWidth <= 0 || options.outHeight <= 0) return null;
            options.inSampleSize = 1;
            while (Math.max(options.outWidth,options.outHeight)/(options.inSampleSize*2) >= size) options.inSampleSize *= 2;
            options.inJustDecodeBounds = false;
            return BitmapFactory.decodeByteArray(cover,0,cover.length,options);
        } catch (Exception ignored) { return null; }
        finally { try { reader.release(); } catch (Exception ignored) { } }
    }
}
