package simple.music.player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Plain lyrics and LRC, including repeated timestamps and a signed global offset. */
public final class Lyrics {
    public static final class Line {
        public final long time;
        public final String text;
        Line(long time,String text){this.time=time;this.text=text;}
    }
    public final List<Line> lines=new ArrayList<>();
    public final String plain;
    private static final Pattern TIME=Pattern.compile("\\[(\\d+):(\\d{2})(?:[.:](\\d{1,3}))?\\]");
    private static final Pattern META=Pattern.compile("(?i)^\\[(?:ar|al|ti|au|by|re|ve|length|offset):.*\\]$");
    public Lyrics(String source){
        source=source==null?"":source.replace("\uFEFF","");long offset=0;
        Matcher offsetMatch=Pattern.compile("\\[offset:([+-]?\\d+)\\]",Pattern.CASE_INSENSITIVE).matcher(source);
        if(offsetMatch.find())try{offset=Math.max(-86400000,Math.min(86400000,Long.parseLong(offsetMatch.group(1))));}catch(NumberFormatException ignored){}
        StringBuilder text=new StringBuilder();
        for(String row:source.split("\\r?\\n")){
            if(META.matcher(row.trim()).matches())continue;
            Matcher m=TIME.matcher(row);List<Long> times=new ArrayList<>();int end=0;
            while(m.find()){
                end=m.end();
                try{
                    long minutes=Long.parseLong(m.group(1));int seconds=Integer.parseInt(m.group(2));
                    if(minutes>Integer.MAX_VALUE/60000||seconds>59)continue;
                    String fraction=m.group(3);long millis=fraction==null?0:Long.parseLong((fraction+"000").substring(0,3));
                    times.add(Math.max(0,minutes*60000+seconds*1000+millis+offset));
                }catch(NumberFormatException ignored){}
            }
            for(long time:times)lines.add(new Line(time,row.substring(end).trim()));
            String plainRow=TIME.matcher(row).replaceAll("").trim();
            if(text.length()>0)text.append('\n');text.append(plainRow);
        }
        plain=text.toString().trim();Collections.sort(lines,(a,b)->Long.compare(a.time,b.time));
    }
    public int active(long position){
        int lo=0,hi=lines.size();while(lo<hi){int mid=(lo+hi)>>>1;if(lines.get(mid).time<=position)lo=mid+1;else hi=mid;}return lo-1;
    }
}
