package com.auss.musicplayer;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

public final class PlayerWidget extends AppWidgetProvider {
    @Override public void onUpdate(Context c, AppWidgetManager manager, int[] ids) { update(c, null); }
    public static void update(Context c, MusicService service) {
        AppWidgetManager manager = AppWidgetManager.getInstance(c);
        for (int id : manager.getAppWidgetIds(new ComponentName(c, PlayerWidget.class))) {
            RemoteViews views = new RemoteViews(c.getPackageName(), R.layout.player_widget);
            Song song = service != null ? service.getCurrentSong() : null;
            if (song == null) {
                PlaybackSnapshot snapshot = new PlaybackSnapshot(c); snapshot.read();
                if (snapshot.index >= 0 && snapshot.index < snapshot.queue.size()) song = snapshot.queue.get(snapshot.index);
            }
            views.setTextViewText(R.id.widget_title, song == null ? "Your music, ready." : song.title);
            views.setTextViewText(R.id.widget_artist, song == null ? "Open your library" : song.artist);
            views.setImageViewResource(R.id.widget_play, service != null && service.isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play);
            views.setOnClickPendingIntent(R.id.widget_open, PendingIntent.getActivity(c, 20,
                    new Intent(c, MainActivity.class).putExtra(MusicService.EXTRA_OPEN_PLAYER, true),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
            relay(c, views, R.id.widget_prev, MusicService.ACTION_PREV, 21, song != null);
            relay(c, views, R.id.widget_play, MusicService.ACTION_TOGGLE, 22, song != null);
            relay(c, views, R.id.widget_next, MusicService.ACTION_NEXT, 23, song != null);
            manager.updateAppWidget(id, views);
        }
    }
    private static void relay(Context c, RemoteViews views, int view, String action, int request, boolean hasSong) {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        if (!hasSong) {
            views.setOnClickPendingIntent(view, PendingIntent.getActivity(c,request,new Intent(c,MainActivity.class),flags));
            return;
        }
        Intent intent = new Intent(c,MusicService.class).setAction(action);
        views.setOnClickPendingIntent(view, android.os.Build.VERSION.SDK_INT >= 26
                ? PendingIntent.getForegroundService(c,request,intent,flags)
                : PendingIntent.getService(c,request,intent,flags));
    }
}
