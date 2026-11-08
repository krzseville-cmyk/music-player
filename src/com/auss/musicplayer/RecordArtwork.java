package com.auss.musicplayer;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** A record sleeve for tracks with no cover image; colors follow the selected theme. */
final class RecordArtwork extends Drawable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int surface, accent, text;
    private final String initials;
    RecordArtwork(int surface, int accent, int text, String title) {
        this.surface=surface; this.accent=accent; this.text=text;
        String[] words=title.trim().split("\\s+");
        initials=words.length==0 || title.trim().isEmpty() ? "♪" :
                (words[0].substring(0,1)+(words.length>1 ? words[words.length-1].substring(0,1) : "")).toUpperCase(java.util.Locale.ROOT);
    }
    @Override public void draw(Canvas canvas) {
        Rect b=getBounds(); float w=b.width(),h=b.height(),size=Math.min(w,h),cx=b.exactCenterX(),cy=b.exactCenterY();
        paint.setShader(new LinearGradient(b.left,b.top,b.right,b.bottom,new int[]{surface,blend(surface,accent,.2f),surface},null,Shader.TileMode.CLAMP));
        canvas.drawRect(b,paint);paint.setShader(null);
        paint.setColor(blend(surface,Color.BLACK,.7f));canvas.drawCircle(cx,cy,size*.36f,paint);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Math.max(1,size*.002f));
        for(int i=0;i<9;i++){paint.setColor(blend(surface,accent,.12f+i*.012f));canvas.drawCircle(cx,cy,size*(.22f+i*.015f),paint);}
        paint.setStyle(Paint.Style.FILL);paint.setColor(accent);canvas.drawCircle(cx,cy,size*.125f,paint);
        paint.setColor(surface);paint.setTextAlign(Paint.Align.CENTER);paint.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));paint.setTextSize(size*.055f);
        canvas.drawText(initials,cx,cy+size*.02f,paint);
        if(size>180){
            paint.setColor(text);paint.setTextSize(size*.023f);paint.setTextAlign(Paint.Align.LEFT);
            canvas.drawText("LOCAL COLLECTION",b.left+size*.07f,b.top+size*.10f,paint);
            paint.setColor(accent);canvas.drawCircle(b.right-size*.08f,b.top+size*.09f,size*.008f,paint);
            paint.setColor(blend(surface,text,.5f));canvas.drawRect(b.left+size*.07f,b.bottom-size*.095f,b.right-size*.07f,b.bottom-size*.092f,paint);
        }
    }
    static int blend(int a,int b,float fraction){return Color.rgb((int)(Color.red(a)*(1-fraction)+Color.red(b)*fraction),(int)(Color.green(a)*(1-fraction)+Color.green(b)*fraction),(int)(Color.blue(a)*(1-fraction)+Color.blue(b)*fraction));}
    @Override public void setAlpha(int alpha){paint.setAlpha(alpha);}
    @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);}
    @Override public int getOpacity(){return PixelFormat.OPAQUE;}
}
