package simple.music.player;

import android.net.Uri;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

public final class FeatureTests {
    static int checks;
    static void check(boolean value,String message) { checks++; if (!value) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        lyrics(); lyricEncoding(); playlists(); mp3(); softwareEffects();
        System.out.println("Passed " + checks + " feature checks");
    }
    static void lyrics() {
        Lyrics lyrics = new Lyrics("[ar:Artist]\n[offset:-500]\n[00:02.25][00:04.250]Repeated\n[00:01.000]First\n[01:00]Last");
        check(lyrics.lines.size()==4,"repeated timestamps are preserved");
        check(lyrics.lines.get(0).text.equals("First"),"timestamps sorted");
        check(lyrics.lines.get(1).time==1750,"fraction and signed offset");
        check(lyrics.active(499)==-1,"before first lyric");
        check(lyrics.active(1750)==1,"exact timestamp becomes active");
        check(lyrics.active(3749)==1,"between timestamps");
        check(lyrics.active(Long.MAX_VALUE)==3,"after final lyric");
        check(new Lyrics("Plain lyrics\nsecond line").lines.isEmpty(),"plain lyrics supported");
    }
    static void lyricEncoding() throws Exception {
        String lrc="[ar:Artist]\n[00:01.00]Golden words\n[00:02.50]Second line";
        check(LyricsText.read(new ByteArrayInputStream(concat(new byte[]{(byte)239,(byte)187,(byte)191},lrc.getBytes(StandardCharsets.UTF_8)))).equals(lrc),"UTF-8 BOM decoded");
        check(LyricsText.read(new ByteArrayInputStream(concat(new byte[]{(byte)255,(byte)254},lrc.getBytes(StandardCharsets.UTF_16LE)))).equals(lrc),"UTF-16 LE LRC decoded");
        check(LyricsText.read(new ByteArrayInputStream(concat(new byte[]{(byte)254,(byte)255},lrc.getBytes(StandardCharsets.UTF_16BE)))).equals(lrc),"UTF-16 BE LRC decoded");
        Lyrics parsed=new Lyrics(lrc);
        check(parsed.plain.equals("Golden words\nSecond line"),"plain text removes timing and file metadata");
        check(new Lyrics("[offset:9999999999999999999999999]\n[99999999999999999999999:01]Bad\n[00:01]Good").lines.size()==1,"invalid numeric metadata cannot break valid lyrics");
        check(new Lyrics("[00:99]Bad\n[00:02]Good").lines.size()==1,"invalid seconds ignored");
        check(new Lyrics("[ar:Artist]\n[ti:Title]").plain.isEmpty(),"metadata is not displayed as lyrics");
    }
    static Song song(long id,String folder,String name) {
        Song s = new Song(id,name,"Artist","Album",1,120000,1,Uri.parse("content://media/external/audio/media/"+id));
        s.folder=folder;s.fileName=name;return s;
    }
    static void playlists() throws Exception {
        Song a=song(1,"Music/Album/","one.mp3"), b=song(2,"Music/Other/","same.mp3"), c=song(3,"Music/Third/","same.mp3");
        List<Song> library=Arrays.asList(a,b,c);
        PlaylistIO.Import roundtrip=PlaylistIO.read(new StringReader(PlaylistIO.export(Arrays.asList(b,a,b))),library);
        check(roundtrip.songs.size()==3,"M3U roundtrip preserves duplicates");
        check(roundtrip.songs.get(0)==b && roundtrip.songs.get(1)==a,"M3U order preserved");
        PlaylistIO.Import windows=PlaylistIO.read(new StringReader("\uFEFF#EXTM3U\r\nC:\\Songs\\one.mp3\r\nsame.mp3\r\nmissing.mp3\r\n"),library);
        check(windows.songs.size()==1 && windows.songs.get(0)==a,"unique filename resolves across devices");
        check(windows.missing==2,"ambiguous and missing filenames reported");
        PlaylistIO.Import android=PlaylistIO.read(new StringReader("/storage/emulated/0/Music/Other/same.mp3\n"),library);
        check(android.songs.size()==1 && android.songs.get(0)==b,"absolute path resolves duplicate names");
        check(PlaylistIO.read(new StringReader("https://example.com/track.mp3\n"),library).songs.isEmpty(),"remote playlists never fetched");
    }
    static void mp3() throws Exception {
        File folder=Files.createTempDirectory("music-tags-test").toFile();
        File source=new File(folder,"source.mp3"), destination=new File(folder,"edited.mp3"), again=new File(folder,"again.mp3");
        byte[] audio=new byte[10000];new Random(42).nextBytes(audio);audio[0]=(byte)255;audio[1]=(byte)251;
        ByteArrayOutputStream body=new ByteArrayOutputStream();
        frame(body,"USLT",concat(new byte[]{3,'e','n','g',0},"Embedded words".getBytes(StandardCharsets.UTF_8)),false);
        frame(body,"TXXX",concat(new byte[]{3},"REPLAYGAIN_TRACK_GAIN\0-7.25 dB".getBytes(StandardCharsets.UTF_8)),false);
        frame(body,"PRIV",new byte[]{1,2,3,4,5},false);
        byte[] initial=tag(body.toByteArray(),3,audio);
        Files.write(source.toPath(),initial);
        check(Mp3Metadata.lyrics(new FileInputStream(source)).equals("Embedded words"),"embedded USLT lyrics read");
        check(Mp3Metadata.replayGain(new FileInputStream(source))==-7.25f,"ReplayGain parsed");
        Mp3Metadata.rewrite(source,destination,"New title","New artist","New album",null);
        byte[] result=Files.readAllBytes(destination.toPath());
        check(Arrays.equals(Arrays.copyOfRange(result,result.length-audio.length,result.length),audio),"audio bytes preserved exactly");
        check(Mp3Metadata.lyrics(new FileInputStream(destination)).equals("Embedded words"),"lyrics survive tag edit");
        check(indexOf(result,new byte[]{'P','R','I','V'})>=0,"unknown frames preserved");
        check(new String(result,StandardCharsets.ISO_8859_1).contains("TIT2"),"title frame written");
        Mp3Metadata.rewrite(destination,again,"Second title","A","B",new byte[]{(byte)137,'P','N','G',1,2});
        check(indexOf(Files.readAllBytes(again.toPath()),new byte[]{'A','P','I','C'})>=0,"artwork frame written");
        check(Arrays.equals(Files.readAllBytes(source.toPath()),initial),"source backup never modified");
        ByteArrayOutputStream v4body=new ByteArrayOutputStream(); frame(v4body,"TXXX",concat(new byte[]{3},"REPLAYGAIN_TRACK_GAIN\0+2.5 dB".getBytes(StandardCharsets.UTF_8)),true);
        Files.write(source.toPath(),tag(v4body.toByteArray(),4,audio));
        Mp3Metadata.rewrite(source,destination,"V4","A","B",null);
        check(Files.readAllBytes(destination.toPath())[3]==4,"ID3v2.4 version preserved");
        check(Mp3Metadata.replayGain(new FileInputStream(destination))==2.5f,"v2.4 ReplayGain preserved");
        byte[] unsupported=initial.clone();unsupported[5]=(byte)128;Files.write(source.toPath(),unsupported);
        boolean rejected=false;try{Mp3Metadata.rewrite(source,destination,"X","Y","Z",null);}catch(IOException e){rejected=true;}
        check(rejected,"unsupported ID3 flags rejected safely");
        check(Arrays.equals(Files.readAllBytes(source.toPath()),unsupported),"rejected source unchanged");
        Files.write(source.toPath(),new byte[]{1,2,3,4});rejected=false;
        try{Mp3Metadata.rewrite(source,destination,"X","Y","Z",null);}catch(IOException e){rejected=true;}
        check(rejected,"non-MP3 files rejected");
        for(File file:folder.listFiles())file.delete();folder.delete();
    }
    static double response(int preset,int bass,int hz,int rate,float gain) {
        SoftwareEffects effects=new SoftwareEffects(rate,1);effects.configure(new SoftwareEffects.Config(preset,bass,gain));
        short[] signal=new short[rate*2];for(int i=0;i<signal.length;i++)signal[i]=(short)(3000*Math.sin(2*Math.PI*hz*i/rate));
        effects.process(signal,signal.length);double sum=0;for(int i=rate;i<signal.length;i++)sum+=(double)signal[i]*signal[i];return Math.sqrt(sum/rate);
    }
    static void softwareEffects() {
        SoftwareEffects dsp=new SoftwareEffects(48000,2);dsp.configure(new SoftwareEffects.Config(-1,0,0));
        short[] original={-32768,32767,-20000,12345,0,1},copy=original.clone();dsp.process(copy,copy.length);check(Arrays.equals(original,copy),"disabled effects preserve PCM exactly");
        dsp.configure(new SoftwareEffects.Config(0,0,0));copy=original.clone();dsp.process(copy,copy.length);check(Arrays.equals(original,copy),"flat preset preserves PCM exactly");
        for(int rate:new int[]{44100,48000,96000}){
            double low=response(-1,1000,60,rate,0),mid=response(-1,1000,1000,rate,0);
            check(low/mid>2.5,"software bass raises low frequencies at "+rate);
            double high=response(2,0,12000,rate,0),lowEq=response(2,0,250,rate,0);
            check(high/lowEq>1.5,"software treble preset changes frequency response at "+rate);
            check(response(-1,0,1000,rate,6)/response(-1,0,1000,rate,0)>1.9,"software ReplayGain boosts PCM at "+rate);
        }
        dsp=new SoftwareEffects(48000,2);dsp.configure(new SoftwareEffects.Config(4,700,0));short[] stereo=new short[96000];
        for(int i=0;i<stereo.length;i+=2)stereo[i]=(short)(8000*Math.sin(2*Math.PI*110*i/96000));dsp.process(stereo,stereo.length);
        boolean isolated=true;for(int i=1;i<stereo.length;i+=2)if(stereo[i]!=0)isolated=false;check(isolated,"stereo filter histories are isolated");
        dsp.reset();short[] silence=new short[9600];dsp.process(silence,silence.length);check(Arrays.equals(silence,new short[silence.length]),"seek reset removes filter history");
        SoftwareEffects.Config bounded=new SoftwareEffects.Config(900,9000,Float.NaN);check(bounded.preset==-1&&bounded.bass==1000&&bounded.gainDb==0,"DSP configuration is bounded");
        for(int preset=0;preset<SoftwareEffects.PRESETS.length;preset++)check(response(preset,1000,250,48000,12)>20,"preset remains stable with maximum bass and gain: "+preset);
    }
    static byte[] concat(byte[] a,byte[] b) { byte[] v=Arrays.copyOf(a,a.length+b.length);System.arraycopy(b,0,v,a.length,b.length);return v; }
    static void frame(OutputStream output,String id,byte[] data,boolean sync) throws IOException {
        DataOutputStream out=new DataOutputStream(output);out.writeBytes(id);
        if(sync)sync(out,data.length);else out.writeInt(data.length);
        out.writeShort(0);out.write(data);
    }
    static void sync(DataOutputStream out,int n)throws IOException{out.writeByte(n>>>21&127);out.writeByte(n>>>14&127);out.writeByte(n>>>7&127);out.writeByte(n&127);}
    static byte[] tag(byte[] body,int version,byte[] audio)throws IOException{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);out.writeBytes("ID3");out.writeByte(version);out.writeByte(0);out.writeByte(0);sync(out,body.length);out.write(body);out.write(audio);return bytes.toByteArray();
    }
    static int indexOf(byte[] haystack,byte[] needle){outer:for(int i=0;i<=haystack.length-needle.length;i++){for(int j=0;j<needle.length;j++)if(haystack[i+j]!=needle[j])continue outer;return i;}return -1;}
}
