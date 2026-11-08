package simple.music.player;

import java.io.*;
import java.nio.charset.StandardCharsets;

/** Decode common LRC encodings, with a bounded input size. */
public final class LyricsText {
    public static String read(InputStream in) throws IOException {
        if(in==null)throw new IOException("Lyrics file is unavailable");
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;
        while((n=in.read(buffer))!=-1){if(bytes.size()+n>1024*1024)throw new IOException("Lyrics file is larger than 1 MB");bytes.write(buffer,0,n);}
        byte[] data=bytes.toByteArray();String text;
        if(data.length>=2&&data[0]==(byte)0xff&&data[1]==(byte)0xfe)text=new String(data,2,data.length-2,StandardCharsets.UTF_16LE);
        else if(data.length>=2&&data[0]==(byte)0xfe&&data[1]==(byte)0xff)text=new String(data,2,data.length-2,StandardCharsets.UTF_16BE);
        else text=new String(data,StandardCharsets.UTF_8);
        return text.replace("\uFEFF","").replace("\r\n","\n").replace('\r','\n').trim();
    }
}
