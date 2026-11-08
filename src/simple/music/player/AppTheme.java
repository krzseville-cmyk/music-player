package simple.music.player;

import android.content.Context;

/** Shared colors and native widget styles for every app-owned screen and dialog. */
final class AppTheme {
    final String id;
    final boolean light;
    final int background,surface,raised,accent,onAccent,text,muted,player,ripple,appStyle,dialogStyle;
    private AppTheme(Context c,String id,boolean light,int appStyle,int dialogStyle,int[] colors){
        this.id=id;this.light=light;this.appStyle=appStyle;this.dialogStyle=dialogStyle;
        background=c.getColor(colors[0]);surface=c.getColor(colors[1]);raised=c.getColor(colors[2]);
        accent=c.getColor(colors[3]);onAccent=c.getColor(colors[4]);text=c.getColor(colors[5]);
        muted=c.getColor(colors[6]);player=c.getColor(colors[7]);ripple=c.getColor(colors[8]);
    }
    static AppTheme from(Context c,String id){
        if("purple".equals(id))return new AppTheme(c,id,false,R.style.MusicTheme_Purple,R.style.MusicDialog_Purple,new int[]{
            R.color.purple_background,R.color.purple_surface,R.color.purple_surface_raised,R.color.purple_accent,R.color.purple_on_accent,
            R.color.purple_text,R.color.purple_muted,R.color.purple_player,R.color.purple_ripple});
        if("light".equals(id))return new AppTheme(c,id,true,R.style.MusicTheme_Light,R.style.MusicDialog_Light,new int[]{
            R.color.light_background,R.color.light_surface,R.color.light_surface_raised,R.color.light_accent,R.color.light_on_accent,
            R.color.light_text,R.color.light_muted,R.color.light_player,R.color.light_ripple});
        return new AppTheme(c,"ember",false,R.style.MusicTheme_Ember,R.style.MusicDialog_Ember,new int[]{
            R.color.ember_background,R.color.ember_surface,R.color.ember_surface_raised,R.color.ember_accent,R.color.ember_on_accent,
            R.color.ember_text,R.color.ember_muted,R.color.ember_player,R.color.ember_ripple});
    }
}
