package simple.music.player;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Conservative ID3v2.3/2.4 reader/writer. Unknown frames and audio bytes are preserved. */
public final class Mp3Metadata {
    static final int MAX_TAG = 16 * 1024 * 1024;
    static final class Frame {
        final String id;
        final byte[] data;
        Frame(String id, byte[] data) { this.id = id; this.data = data; }
    }
    static final class Tag {
        int version = 3;
        long audioStart;
        final List<Frame> frames = new ArrayList<>();
    }
    private static int sync(byte[] b, int offset) {
        return (b[offset] & 127) << 21 | (b[offset+1] & 127) << 14 | (b[offset+2] & 127) << 7 | b[offset+3] & 127;
    }
    private static int int32(byte[] b, int o) { return (b[o]&255)<<24 | (b[o+1]&255)<<16 | (b[o+2]&255)<<8 | b[o+3]&255; }
    private static void size(DataOutputStream out, int value, boolean sync) throws IOException {
        if (!sync) out.writeInt(value);
        else { out.writeByte(value >>> 21 & 127); out.writeByte(value >>> 14 & 127); out.writeByte(value >>> 7 & 127); out.writeByte(value & 127); }
    }
    private static Tag read(InputStream input) throws IOException {
        DataInputStream in = new DataInputStream(input);
        byte[] header = new byte[10];
        int n = 0;
        while (n < 10) { int count = in.read(header, n, 10-n); if (count < 0) break; n += count; }
        Tag tag = new Tag();
        if (n < 10 || header[0] != 'I' || header[1] != 'D' || header[2] != '3') return tag;
        tag.version = header[3];
        if ((tag.version != 3 && tag.version != 4) || header[5] != 0)
            throw new IOException("This MP3 uses an unsupported ID3 variant; its tags were left unchanged.");
        int length = sync(header, 6);
        if (length > MAX_TAG) throw new IOException("ID3 tag exceeds 16 MB");
        byte[] data = new byte[length]; in.readFully(data);
        tag.audioStart = 10L + length;
        for (int o = 0; o + 10 <= data.length;) {
            if (data[o] == 0) break;
            String id = new String(data, o, 4, StandardCharsets.ISO_8859_1);
            if (!id.matches("[A-Z0-9]{4}")) throw new IOException("Invalid ID3 frame");
            int count = tag.version == 4 ? sync(data, o+4) : int32(data, o+4);
            if (count < 0 || count > data.length-o-10 || data[o+8] != 0 || data[o+9] != 0)
                throw new IOException("Unsupported or damaged ID3 frame; no changes were made.");
            tag.frames.add(new Frame(id, Arrays.copyOfRange(data, o+10, o+10+count)));
            o += 10 + count;
        }
        return tag;
    }
    private static Charset encoding(byte type) {
        switch (type) { case 1: return StandardCharsets.UTF_16; case 2: return StandardCharsets.UTF_16BE; case 3: return StandardCharsets.UTF_8; default: return StandardCharsets.ISO_8859_1; }
    }
    public static String lyrics(InputStream in) throws IOException {
        for (Frame f : read(in).frames) if (f.id.equals("USLT") && f.data.length > 4) {
            String value = new String(f.data, 4, f.data.length-4, encoding(f.data[0]));
            int split = value.indexOf('\0');
            return split < 0 ? value : value.substring(split+1).replace("\0", "");
        }
        return "";
    }
    public static float replayGain(InputStream in) throws IOException {
        for (Frame f : read(in).frames) if (f.id.equals("TXXX") && f.data.length > 1) {
            String value = new String(f.data, 1, f.data.length-1, encoding(f.data[0])).replace("\0", " ").trim();
            if (value.toLowerCase(Locale.ROOT).startsWith("replaygain_track_gain")) {
                String gain = value.substring("replaygain_track_gain".length()).trim().replaceAll("(?i)\\s*dB.*$", "");
                try { float result = Float.parseFloat(gain); return Float.isFinite(result) ? Math.max(-30, Math.min(12, result)) : 0; }
                catch (NumberFormatException ignored) { }
            }
        }
        return 0;
    }
    public static void rewrite(File source, File destination, String title, String artist, String album, byte[] cover) throws IOException {
        Tag tag;
        try (InputStream in = new FileInputStream(source)) { tag = read(in); }
        // An MP3 must have either an ID3 tag or an MPEG frame sync at its beginning.
        try (RandomAccessFile in = new RandomAccessFile(source, "r")) {
            in.seek(tag.audioStart);
            if (in.read() != 255 || (in.read() & 224) != 224) throw new IOException("Only MP3 audio files can be edited.");
        }
        List<Frame> frames = new ArrayList<>();
        for (Frame f : tag.frames) if (!f.id.equals("TIT2") && !f.id.equals("TPE1") && !f.id.equals("TALB") && !(cover != null && f.id.equals("APIC"))) frames.add(f);
        frames.add(text("TIT2", title)); frames.add(text("TPE1", artist)); frames.add(text("TALB", album));
        if (cover != null) {
            if (cover.length > 5 * 1024 * 1024) throw new IOException("Cover must be smaller than 5 MB");
            String mime = cover.length > 3 && cover[0] == (byte)137 && cover[1] == 'P' ? "image/png" : "image/jpeg";
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            bytes.write(0); bytes.write(mime.getBytes(StandardCharsets.ISO_8859_1)); bytes.write(0); bytes.write(3); bytes.write(0); bytes.write(cover);
            frames.add(new Frame("APIC", bytes.toByteArray()));
        }
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(body);
        for (Frame f : frames) {
            data.write(f.id.getBytes(StandardCharsets.ISO_8859_1)); size(data, f.data.length, tag.version == 4);
            data.writeShort(0); data.write(f.data);
        }
        if (body.size() > MAX_TAG) throw new IOException("Resulting ID3 tag is too large");
        try (DataOutputStream out = new DataOutputStream(new FileOutputStream(destination)); InputStream in = new FileInputStream(source)) {
            out.writeBytes("ID3"); out.writeByte(tag.version); out.writeByte(0); out.writeByte(0); size(out, body.size(), true);
            body.writeTo(out);
            long skip = tag.audioStart;
            while (skip > 0) { long count = in.skip(skip); if (count == 0) { if (in.read() < 0) throw new EOFException(); count = 1; } skip -= count; }
            copy(in, out);
        }
    }
    private static Frame text(String id, String value) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); out.write(1); out.write(value.getBytes(StandardCharsets.UTF_16));
        return new Frame(id, out.toByteArray());
    }
    public static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[65536]; int count;
        while ((count = in.read(buffer)) >= 0) out.write(buffer, 0, count);
    }
}
