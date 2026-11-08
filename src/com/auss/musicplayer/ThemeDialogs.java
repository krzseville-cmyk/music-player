package com.auss.musicplayer;

import android.app.AlertDialog;
import android.graphics.drawable.GradientDrawable;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;

/** Native choice lists and editable dialogs use the same palette as their parent screen. */
final class ThemeDialogs {
    static AlertDialog.Builder builder(MainActivity host){return new Builder(host);}
    private static final class Builder extends AlertDialog.Builder {
        private final MainActivity host;
        Builder(MainActivity host){super(host,host.appTheme().dialogStyle);this.host=host;}
        @Override public AlertDialog create(){
            AlertDialog dialog=super.create();
            dialog.setOnShowListener(d->{
                AppTheme theme=host.appTheme();float density=host.getResources().getDisplayMetrics().density;
                Window window=dialog.getWindow();
                if(window!=null){
                    GradientDrawable background=new GradientDrawable();background.setColor(theme.surface);
                    background.setCornerRadius(24*density);background.setStroke(Math.max(1,(int)density),RecordArtwork.blend(theme.surface,theme.text,.12f));
                    window.setBackgroundDrawable(background);window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                    window.setDimAmount(theme.light?.28f:.6f);
                    int width=host.getResources().getDisplayMetrics().widthPixels-(int)(40*density);
                    window.setLayout(Math.min(width,(int)(520*density)),WindowManager.LayoutParams.WRAP_CONTENT);
                }
                for(int which:new int[]{AlertDialog.BUTTON_POSITIVE,AlertDialog.BUTTON_NEGATIVE,AlertDialog.BUTTON_NEUTRAL}){
                    Button button=dialog.getButton(which);if(button!=null)button.setTextColor(theme.accent);
                }
            });return dialog;
        }
    }
}
