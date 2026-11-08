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
        Line(long time, String text) { this.time = time; this.text = text; }
    }
    public final List<Line> lines = new ArrayList<>();
    public final String plain;
    private static final Pattern TIME = Pattern.compile("\\[(\\d+):(\\d{2})(?:[.:](\\d{1,3}))?\\]");
    public Lyrics(String source) {
        plain = source;
        Matcher offsetMatch = Pattern.compile("\\[offset:([+-]?\\d+)\\]", Pattern.CASE_INSENSITIVE).matcher(source);
        long offset = offsetMatch.find() ? Long.parseLong(offsetMatch.group(1)) : 0;
        for (String row : source.split("\\r?\\n")) {
            Matcher m = TIME.matcher(row);
            List<Long> times = new ArrayList<>();
            int end = 0;
            while (m.find()) {
                String fraction = m.group(3);
                long millis = fraction == null ? 0 : Long.parseLong((fraction + "000").substring(0, 3));
                times.add(Long.parseLong(m.group(1)) * 60000 + Long.parseLong(m.group(2)) * 1000 + millis + offset);
                end = m.end();
            }
            for (long time : times) lines.add(new Line(Math.max(0, time), row.substring(end).trim()));
        }
        Collections.sort(lines, (a,b) -> Long.compare(a.time, b.time));
    }
    public int active(long position) {
        int lo = 0, hi = lines.size();
        while (lo < hi) { int mid = (lo + hi) >>> 1; if (lines.get(mid).time <= position) lo = mid + 1; else hi = mid; }
        return lo - 1;
    }
}
