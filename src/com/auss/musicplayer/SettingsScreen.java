package com.auss.musicplayer;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.provider.Settings;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Full-screen settings with grouped rows, live values, and a separate sound page. */
final class SettingsScreen extends FrameLayout {
    private final MainActivity host;
    private final PlayerTools tools;
    private final LibraryPreferences preferences;
    private final List<Runnable> bindings=new ArrayList<>();
    private LinearLayout sections;
    private ScrollView scroll;
    private String page="root";
    private boolean rootParent;
    private boolean refreshing;

    SettingsScreen(MainActivity host,PlayerTools tools,LibraryPreferences preferences) {
        super(host);this.host=host;this.tools=tools;this.preferences=preferences;
        setBackgroundColor(host.backgroundColor());setClickable(true);setVisibility(GONE);
    }
    String page(){return page;}
    boolean hasRootParent(){return rootParent;}
    void open(String page,boolean parent){this.page=page;rootParent=parent;setVisibility(VISIBLE);render();}
    boolean back(){
        if(getVisibility()!=VISIBLE)return false;
        if(!page.equals("root")&&rootParent){open("root",false);return true;}
        host.closeSettings();return true;
    }
    void refresh(){
        if(getVisibility()!=VISIBLE)return;
        refreshing=true;
        try{for(Runnable binding:bindings)binding.run();}finally{refreshing=false;}
    }
    private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private SharedPreferences audio(){return host.getSharedPreferences("player",0);}
    private TextView text(String value,float size,int color,boolean bold){
        TextView view=new TextView(host);view.setText(value);view.setTextSize(size);view.setTextColor(color);
        if(bold)view.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));return view;
    }
    private GradientDrawable shape(int fill,int stroke,int radius){
        GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(radius));
        if(stroke!=0)d.setStroke(dp(1),stroke);return d;
    }
    private void update(TextView view,String value){if(!view.getText().toString().equals(value))view.setText(value);}
    private void render(){
        removeAllViews();bindings.clear();
        LinearLayout layout=new LinearLayout(host);layout.setOrientation(LinearLayout.VERTICAL);
        LinearLayout bar=new LinearLayout(host);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding(dp(12),dp(8),dp(20),dp(8));
        ImageButton back=new ImageButton(host);back.setImageResource(R.drawable.ic_back);back.setImageTintList(ColorStateList.valueOf(host.textColor()));
        back.setBackground(ripple(shape(host.surfaceColor(),0,16)));back.setContentDescription("Back");back.setOnClickListener(v->back());
        bar.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));
        TextView title=text(page.equals("sound")?"Sound & playback":page.equals("appearance")?"Appearance":"Settings",26,host.textColor(),true);
        title.setPadding(dp(16),0,0,0);if(Build.VERSION.SDK_INT>=28)title.setAccessibilityHeading(true);
        bar.addView(title,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        bar.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{RecordArtwork.blend(host.backgroundColor(),host.accentColor(),.045f),host.backgroundColor()}));
        layout.addView(bar);
        View divider=new View(host);divider.setBackgroundColor(RecordArtwork.blend(host.backgroundColor(),host.textColor(),.08f));
        layout.addView(divider,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(1)));
        scroll=new ScrollView(host);scroll.setFillViewport(true);scroll.setClipToPadding(false);scroll.setVerticalScrollBarEnabled(false);
        sections=new LinearLayout(host);sections.setOrientation(LinearLayout.VERTICAL);sections.setPadding(dp(20),dp(8),dp(20),dp(28));
        TextView introduction=text(page.equals("sound")?"Fine-tune how your music plays.":page.equals("appearance")?"One theme, across your whole player.":"Your music. Your preferences.",14,host.mutedColor(),false);
        introduction.setPadding(dp(2),dp(14),0,dp(6));sections.addView(introduction);
        if(page.equals("sound"))soundPage();else if(page.equals("appearance"))appearancePage();else rootPage();
        scroll.addView(sections);layout.addView(scroll,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));
        addView(layout,new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));
        refresh();
    }
    private LinearLayout group(String label){
        TextView heading=text(label,11,host.accentColor(),true);heading.setLetterSpacing(.12f);heading.setPadding(dp(4),dp(24),0,dp(10));
        if(Build.VERSION.SDK_INT>=28)heading.setAccessibilityHeading(true);sections.addView(heading);
        LinearLayout card=new LinearLayout(host);card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(shape(host.surfaceColor(),RecordArtwork.blend(host.surfaceColor(),host.textColor(),.08f),20));
        card.setClipToOutline(true);sections.addView(card,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));return card;
    }
    private LinearLayout row(LinearLayout group,int icon,String title,String summary){
        if(group.getChildCount()>0){View line=new View(host);line.setBackgroundColor(RecordArtwork.blend(host.surfaceColor(),host.textColor(),.07f));
            LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(1));params.leftMargin=dp(62);group.addView(line,params);}
        LinearLayout row=new LinearLayout(host);row.setGravity(Gravity.CENTER_VERTICAL);row.setMinimumHeight(dp(78));row.setPadding(dp(14),dp(12),dp(14),dp(12));
        ImageView image=new ImageView(host);image.setImageResource(icon);image.setImageTintList(ColorStateList.valueOf(host.accentColor()));image.setPadding(dp(9),dp(9),dp(9),dp(9));
        image.setBackground(shape(RecordArtwork.blend(host.surfaceColor(),host.accentColor(),.08f),0,12));image.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(image,new LinearLayout.LayoutParams(dp(38),dp(38)));
        LinearLayout words=new LinearLayout(host);words.setOrientation(LinearLayout.VERTICAL);words.setPadding(dp(12),0,dp(8),0);
        words.addView(text(title,15,host.textColor(),true));
        if(summary!=null&&!summary.isEmpty()){TextView note=text(summary,12,host.mutedColor(),false);note.setPadding(0,dp(4),0,0);words.addView(note);}
        row.addView(words,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));group.addView(row);return row;
    }
    private void action(LinearLayout group,int icon,String title,String summary,Supplier<String> value,Runnable action){
        LinearLayout row=row(group,icon,title,summary);
        TextView current=text("",12,host.mutedColor(),false);current.setMaxWidth(dp(105));current.setGravity(Gravity.END);
        row.addView(current,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView chevron=text("›",24,host.mutedColor(),false);chevron.setPadding(dp(10),0,0,0);chevron.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);row.addView(chevron);
        row.setTag(title);row.setFocusable(true);row.setBackground(ripple(null));row.setOnClickListener(v->action.run());
        bindings.add(()->{String selected=value==null?"":value.get();update(current,selected);row.setContentDescription(title+(selected.isEmpty()?"":", "+selected)+(summary==null?"":", "+summary));});
    }
    private void toggle(LinearLayout group,int icon,String title,Supplier<String> description,BooleanSupplier checked,BooleanSupplier enabled,Consumer<Boolean> changed){
        LinearLayout row=row(group,icon,title,null);LinearLayout words=(LinearLayout)row.getChildAt(1);
        TextView summary=text("",12,host.mutedColor(),false);summary.setPadding(0,dp(4),0,0);words.addView(summary);
        Switch control=new Switch(host);control.setShowText(false);control.setContentDescription(title);control.setTag(title);
        int[][] states={new int[]{android.R.attr.state_checked},new int[]{}};
        control.setThumbTintList(new ColorStateList(states,new int[]{host.accentColor(),host.mutedColor()}));
        control.setTrackTintList(new ColorStateList(states,new int[]{RecordArtwork.blend(host.surfaceColor(),host.accentColor(),.45f),RecordArtwork.blend(host.surfaceColor(),host.textColor(),.18f)}));
        row.addView(control);row.setFocusable(true);row.setTag(title+" row");
        control.setOnCheckedChangeListener((button,value)->{if(!refreshing){changed.accept(value);refresh();host.repaint();}});
        row.setBackground(ripple(null));
        row.setOnClickListener(v->{if(control.isEnabled())control.setChecked(!control.isChecked());});
        bindings.add(()->{update(summary,description.get());boolean available=enabled.getAsBoolean();control.setEnabled(available);row.setEnabled(available);row.setAlpha(available?1f:.55f);control.setChecked(checked.getAsBoolean());});
    }
    private RippleDrawable ripple(android.graphics.drawable.Drawable background){
        return new RippleDrawable(ColorStateList.valueOf(host.appTheme().ripple),background,shape(android.graphics.Color.WHITE,0,16));
    }
    private void appearancePage(){
        String[] ids={"ember","purple","light"};
        String[] names={"Ember","Amethyst","Daylight"};
        String[] details={"Dark · Orange","Dark · Purple","Light · Warm neutrals"};
        for(int i=0;i<ids.length;i++){
            final String id=ids[i];AppTheme palette=AppTheme.from(host,id);boolean selected=palette.id.equals(host.appTheme().id);
            LinearLayout card=new LinearLayout(host);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(10),dp(10),dp(10),dp(8));
            GradientDrawable fill=shape(host.surfaceColor(),selected?host.accentColor():RecordArtwork.blend(host.surfaceColor(),host.textColor(),.10f),22);
            if(selected)fill.setStroke(dp(2),host.accentColor());card.setBackground(ripple(fill));card.setClipToOutline(true);
            View preview=new ThemePreview(host,palette);preview.setBackground(shape(palette.background,0,14));preview.setClipToOutline(true);
            preview.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);card.addView(preview,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(76)));
            LinearLayout description=new LinearLayout(host);description.setGravity(Gravity.CENTER_VERTICAL);description.setPadding(dp(6),dp(10),dp(2),dp(2));
            LinearLayout words=new LinearLayout(host);words.setOrientation(LinearLayout.VERTICAL);words.addView(text(names[i],16,host.textColor(),true));words.addView(text(details[i],12,host.mutedColor(),false));
            description.addView(words,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
            RadioButton radio=new RadioButton(host);radio.setContentDescription("Use "+names[i]+" theme");radio.setChecked(selected);
            radio.setButtonTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{host.accentColor(),host.mutedColor()}));
            Runnable choose=()->{if(!id.equals(preferences.theme())){preferences.theme(id);host.recreate();}};
            radio.setOnClickListener(v->choose.run());card.setOnClickListener(v->choose.run());card.setFocusable(true);card.setTag("theme-"+id);
            description.addView(radio);card.addView(description);LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
            params.topMargin=dp(14);sections.addView(card,params);
        }
        TextView note=text("Your choice applies to the library, player, settings and dialogs.",12,host.mutedColor(),false);
        note.setPadding(dp(4),dp(18),dp(4),0);sections.addView(note);
    }
    private static final class ThemePreview extends View {
        private final AppTheme palette;private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        ThemePreview(android.content.Context context,AppTheme palette){super(context);this.palette=palette;}
        @Override protected void onDraw(Canvas canvas){
            float w=getWidth(),h=getHeight(),u=h/76f;
            paint.setColor(palette.background);canvas.drawRect(0,0,w,h,paint);
            paint.setColor(palette.accent);canvas.drawRoundRect(14*u,12*u,76*u,17*u,2*u,2*u,paint);
            paint.setColor(palette.surface);canvas.drawRoundRect(12*u,27*u,w-12*u,h-11*u,10*u,10*u,paint);
            paint.setColor(palette.raised);canvas.drawRoundRect(20*u,35*u,42*u,57*u,5*u,5*u,paint);
            paint.setColor(palette.accent);canvas.drawCircle(31*u,46*u,6*u,paint);
            paint.setColor(palette.text);canvas.drawRoundRect(52*u,36*u,w*.56f,41*u,2*u,2*u,paint);
            paint.setColor(palette.muted);canvas.drawRoundRect(52*u,49*u,w*.43f,52*u,1*u,1*u,paint);
            paint.setColor(palette.accent);canvas.drawCircle(w-33*u,46*u,12*u,paint);
            paint.setColor(palette.onAccent);Path play=new Path();play.moveTo(w-36*u,41*u);play.lineTo(w-36*u,51*u);play.lineTo(w-28*u,46*u);play.close();canvas.drawPath(play,paint);
        }
    }

    private String themeName(){return "purple".equals(preferences.theme())?"Dark · Purple":"light".equals(preferences.theme())?"Light":"Dark · Orange";}
    private String sortName(){String[] labels={"Title A–Z","Title Z–A","Artist","Album","Date added","Duration"};return labels[Math.max(0,Math.min(labels.length-1,preferences.sort()))];}
    private void rootPage(){
        LinearLayout appearance=group("APPEARANCE");
        action(appearance,R.drawable.ic_theme,"Theme","Colors and appearance",this::themeName,()->open("appearance",true));
        LinearLayout playback=group("PLAYBACK");
        action(playback,R.drawable.ic_settings,"Sound & playback","Speed, equalizer and transitions",null,()->open("sound",true));
        action(playback,R.drawable.ic_timer,"Sleep timer","Stop music automatically",()->host.player()==null?"Off":host.player().sleepDescription(),tools::sleepTimer);
        LinearLayout library=group("MUSIC LIBRARY");
        action(library,R.drawable.ic_sort,"Sort songs","Default order in your library",this::sortName,tools::sort);
        action(library,R.drawable.ic_folder,"Excluded folders","Choose folders to hide",()->preferences.excluded().isEmpty()?"None":preferences.excluded().size()+" hidden",tools::excludeFolders);
        action(library,R.drawable.ic_refresh,"Rescan library","Find music added to your device",()->host.librarySongs().size()+" songs",()->{host.reloadLibrary();Toast.makeText(host,"Scanning your music…",Toast.LENGTH_SHORT).show();});
        action(library,R.drawable.ic_playlist_add,"Import playlist","Choose an M3U or M3U8 file",null,tools::importPlaylist);
        action(library,R.drawable.ic_edit,"Edit MP3 tags","Title, artist, album and cover",null,tools::chooseMp3);
        LinearLayout about=group("ABOUT");
        action(about,R.drawable.ic_info,"App permissions","Manage audio and notifications",null,()->host.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+host.getPackageName()))));
        LinearLayout app=row(about,R.drawable.ic_music,"Music Player","Offline music, on your device");
        String version="";try{version=host.getPackageManager().getPackageInfo(host.getPackageName(),0).versionName;}catch(Exception ignored){}
        app.addView(text("Version "+version,12,host.mutedColor(),false));
    }
    private MusicService player(){return host.player();}
    private void soundPage(){
        LinearLayout playback=group("PLAYBACK");
        action(playback,R.drawable.ic_play,"Playback speed","Adjust tempo, keep the pitch",()->(player()==null?audio().getFloat("speed",1):player().getSpeed())+"×",this::speed);
        action(playback,R.drawable.ic_timer,"Sleep timer","Stop music automatically",()->player()==null?"Off":player().sleepDescription(),tools::sleepTimer);
        LinearLayout transitions=group("TRACK TRANSITIONS");
        toggle(transitions,R.drawable.ic_next,"Gapless playback",()->player()!=null&&player().crossfadeSeconds()>0?"Turn off crossfade to use gapless.":"Play tracks without a pause between them.",
                ()->player()!=null&&player().gapless()&&player().crossfadeSeconds()==0,()->player()!=null&&player().crossfadeSeconds()==0,value->player().setGapless(value));
        action(transitions,R.drawable.ic_crossfade,"Crossfade","Blend the end of one track into the next",()->{int seconds=player()==null?audio().getInt("crossfade",0):player().crossfadeSeconds();return seconds==0?"Off":seconds+" seconds";},this::crossfade);
        LinearLayout effects=group("AUDIO EFFECTS");
        action(effects,R.drawable.ic_equalizer,"Equalizer","Choose a sound preset",()->{
            if(!audio().getBoolean("eqEnabled",false))return "Off";
            String[] presets=player()==null?new String[0]:player().equalizerPresets();int selected=audio().getInt("eqPreset",-1);
            return selected>=0&&selected<presets.length?presets[selected]:"Enabled";
        },this::equalizer);
        action(effects,R.drawable.ic_bass,"Bass boost","Adjust low-frequency strength",()->{int strength=audio().getInt("bass",0)/10;return strength==0?"Off":strength+"%";},this::bass);
        toggle(effects,R.drawable.ic_volume,"ReplayGain",()->"Balance volume using embedded track-gain tags.",()->player()!=null&&player().normalization(),()->player()!=null,value->player().setNormalization(value));
        TextView note=text("Audio effects depend on your device. Tracks without ReplayGain tags keep their original volume.",12,host.mutedColor(),false);
        note.setPadding(dp(4),dp(16),dp(4),0);sections.addView(note);
    }
    private AlertDialog.Builder dialog(String title){return ThemeDialogs.builder(host).setTitle(title).setOnDismissListener(d->refresh());}
    private boolean connected(){if(player()!=null)return true;Toast.makeText(host,"The player is connecting. Try again.",Toast.LENGTH_SHORT).show();return false;}
    private void speed(){
        if(!connected())return;float[] speeds={.5f,.75f,1f,1.25f,1.5f,2f};String[] labels={"0.5×","0.75×","1×","1.25×","1.5×","2×"};int selected=2;
        for(int i=0;i<speeds.length;i++)if(Math.abs(player().getSpeed()-speeds[i])<.01f)selected=i;
        dialog("Playback speed").setSingleChoiceItems(labels,selected,(d,i)->{player().setSpeed(speeds[i]);d.dismiss();}).setNegativeButton("Cancel",null).show();
    }
    private void crossfade(){
        if(!connected())return;int[] seconds={0,3,5,8};String[] labels={"Off","3 seconds","5 seconds","8 seconds"};int selected=0;
        for(int i=0;i<seconds.length;i++)if(player().crossfadeSeconds()==seconds[i])selected=i;
        dialog("Crossfade").setSingleChoiceItems(labels,selected,(d,i)->{player().setCrossfade(seconds[i]);d.dismiss();}).setNegativeButton("Cancel",null).show();
    }
    private void equalizer(){
        if(!connected())return;String[] presets=player().equalizerPresets();
        if(presets.length==0){Toast.makeText(host,"Play a song first. Equalizer support depends on your device.",Toast.LENGTH_LONG).show();return;}
        String[] labels=new String[presets.length+1];labels[0]="Off";System.arraycopy(presets,0,labels,1,presets.length);
        int selected=audio().getBoolean("eqEnabled",false)?audio().getInt("eqPreset",-1)+1:0;
        dialog("Equalizer").setSingleChoiceItems(labels,selected,(d,i)->{player().equalizerPreset(i-1);d.dismiss();}).setNegativeButton("Cancel",null).show();
    }
    private void bass(){
        if(!connected())return;LinearLayout body=new LinearLayout(host);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(24),dp(12),dp(24),dp(8));
        TextView value=text("",18,host.accentColor(),true);body.addView(value);
        SeekBar slider=new SeekBar(host);slider.setMax(100);slider.setProgress(audio().getInt("bass",0)/10);value.setText(slider.getProgress()+"%");
        slider.setProgressTintList(ColorStateList.valueOf(host.accentColor()));slider.setThumbTintList(ColorStateList.valueOf(host.accentColor()));
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar bar,int progress,boolean user){value.setText(progress+"%");}public void onStartTrackingTouch(SeekBar bar){}public void onStopTrackingTouch(SeekBar bar){}});
        body.addView(slider);dialog("Bass boost").setView(body).setPositiveButton("Apply",(d,i)->player().setBass(slider.getProgress()*10)).setNegativeButton("Cancel",null).show();
    }
}
