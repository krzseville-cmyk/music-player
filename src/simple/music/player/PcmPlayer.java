package simple.music.player;

import android.content.Context;
import android.media.*;
import android.net.Uri;
import android.os.*;
import java.nio.*;

/** Local-file decoder with software effects before Android routes PCM to the output device. */
final class PcmPlayer {
    interface OnPreparedListener{void onPrepared(PcmPlayer player);}
    interface OnCompletionListener{void onCompletion(PcmPlayer player);}
    interface OnErrorListener{boolean onError(PcmPlayer player,int what,int extra);}
    interface OnSeekCompleteListener{void onSeekComplete(PcmPlayer player);}
    private final Object state=new Object();
    private final Handler main=new Handler(Looper.getMainLooper());
    private volatile int generation;
    private volatile boolean released,playing,ready,completed;
    private volatile long pendingSeek=-1,baseMs,framesWritten;
    private volatile int duration,sampleRate=44100,sessionId;
    private volatile AudioTrack output;
    private volatile PcmPlayer next;
    private volatile float left=1,right=1;
    private volatile PlaybackParams params=new PlaybackParams().setSpeed(1).setPitch(1);
    private volatile SoftwareEffects.Config config=new SoftwareEffects.Config(-1,0,0);
    private AudioAttributes attributes;
    private Context context;
    private Uri uri;
    private PowerManager.WakeLock wake;
    private OnPreparedListener preparedListener;
    private OnCompletionListener completionListener;
    private OnErrorListener errorListener;
    private OnSeekCompleteListener seekListener;
    void setOnPreparedListener(OnPreparedListener listener){preparedListener=listener;}
    void setOnCompletionListener(OnCompletionListener listener){completionListener=listener;}
    void setOnErrorListener(OnErrorListener listener){errorListener=listener;}
    void setOnSeekCompleteListener(OnSeekCompleteListener listener){seekListener=listener;}
    void setAudioAttributes(AudioAttributes value){attributes=value;}
    void setAudioSessionId(int value){if(ready)throw new IllegalStateException("Already prepared");sessionId=value;}
    int getAudioSessionId(){return sessionId;}
    void setWakeMode(Context context,int mode){
        wake=((PowerManager)context.getSystemService(Context.POWER_SERVICE)).newWakeLock(mode,"SimplePlayer:PCM");wake.setReferenceCounted(false);
    }
    void setDataSource(Context context,Uri uri){this.context=context.getApplicationContext();this.uri=uri;}
    void setEffects(int preset,int bass){SoftwareEffects.Config old=config;config=new SoftwareEffects.Config(preset,bass,old.gainDb);}
    void setGainDb(float gain){SoftwareEffects.Config old=config;config=new SoftwareEffects.Config(old.preset,old.bass,gain);}
    int effectPreset(){return config.preset;}
    int bassStrength(){return config.bass;}
    float gainDb(){return config.gainDb;}
    void prepareAsync(){
        if(uri==null||released)throw new IllegalStateException("Missing source");
        final int request=generation;final Uri source=uri;
        new Thread(()->decode(request,source),"SimplePlayer-decoder").start();
    }
    private void post(int request,Runnable callback){main.post(()->{if(request==generation&&!released)callback.run();});}
    void start(){
        synchronized(state){if(!ready||released)return;completed=false;playing=true;AudioTrack track=output;if(track!=null)track.play();if(wake!=null&&!wake.isHeld())wake.acquire();state.notifyAll();}
    }
    void pause(){synchronized(state){playing=false;AudioTrack track=output;if(track!=null)try{track.pause();}catch(IllegalStateException ignored){}releaseWake();state.notifyAll();}}
    boolean isPlaying(){return playing&&!completed;}
    int getDuration(){return duration;}
    int getCurrentPosition(){
        if(pendingSeek>=0)return (int)pendingSeek;
        if(completed)return duration;
        AudioTrack track=output;long frames=0;
        if(track!=null)try{frames=Integer.toUnsignedLong(track.getPlaybackHeadPosition());}catch(IllegalStateException ignored){}
        return (int)Math.max(0,Math.min(duration,baseMs+frames*1000/Math.max(1,sampleRate)));
    }
    void seekTo(int position){synchronized(state){pendingSeek=Math.max(0,Math.min(duration,position));completed=false;state.notifyAll();}}
    void setVolume(float left,float right){this.left=left;this.right=right;AudioTrack track=output;if(track!=null)track.setStereoVolume(left,right);}
    void setPlaybackParams(PlaybackParams value){params=value;AudioTrack track=output;if(track!=null)track.setPlaybackParams(value);}
    void setNextMediaPlayer(PcmPlayer player){next=player;}
    void reset(){
        synchronized(state){generation++;playing=false;ready=false;completed=false;pendingSeek=-1;baseMs=0;framesWritten=0;duration=0;next=null;
            AudioTrack track=output;output=null;if(track!=null)try{track.pause();track.flush();}catch(IllegalStateException ignored){}
            releaseWake();state.notifyAll();}
    }
    void release(){reset();released=true;}
    private void releaseWake(){if(wake!=null&&wake.isHeld())wake.release();}
    private boolean alive(int request){return request==generation&&!released;}
    private AudioTrack track(MediaFormat format,int request){
        int rate=format.getInteger(MediaFormat.KEY_SAMPLE_RATE),channels=format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        if(rate<8000||channels<1||channels>8)throw new IllegalArgumentException("Unsupported audio format");
        int mask=channels==1?AudioFormat.CHANNEL_OUT_MONO:AudioFormat.CHANNEL_OUT_STEREO;
        int minimum=AudioTrack.getMinBufferSize(rate,mask,AudioFormat.ENCODING_PCM_16BIT);
        if(minimum<=0)throw new IllegalStateException("PCM output unavailable");
        AudioAttributes attrs=attributes==null?new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build():attributes;
        AudioTrack result=new AudioTrack.Builder().setAudioAttributes(attrs).setAudioFormat(new AudioFormat.Builder().setSampleRate(rate).setChannelMask(mask).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setTransferMode(AudioTrack.MODE_STREAM).setBufferSizeInBytes(Math.max(minimum,rate*(channels==1?2:4)/5)).setSessionId(sessionId).build();
        if(result.getState()!=AudioTrack.STATE_INITIALIZED){result.release();throw new IllegalStateException("PCM output unavailable");}
        synchronized(state){
            if(!alive(request)){result.release();return null;}
            sampleRate=rate;sessionId=result.getAudioSessionId();output=result;result.setStereoVolume(left,right);result.setPlaybackParams(params);ready=true;
        }
        post(request,()->{if(preparedListener!=null)preparedListener.onPrepared(this);});return result;
    }
    private void decode(int request,Uri source){
        MediaExtractor extractor=new MediaExtractor();MediaCodec codec=null;AudioTrack track=null;
        try{
            extractor.setDataSource(context,source,null);MediaFormat format=null;
            for(int i=0;i<extractor.getTrackCount();i++){
                MediaFormat found=extractor.getTrackFormat(i);String mime=found.getString(MediaFormat.KEY_MIME);
                if(mime!=null&&mime.startsWith("audio/")){extractor.selectTrack(i);format=found;break;}
            }
            if(format==null)throw new IllegalArgumentException("No decodable audio track");
            synchronized(state){
                if(!alive(request))return;
                if(format.containsKey(MediaFormat.KEY_DURATION))duration=(int)Math.min(Integer.MAX_VALUE,format.getLong(MediaFormat.KEY_DURATION)/1000);
            }
            if(!"audio/raw".equals(format.getString(MediaFormat.KEY_MIME)))format.setInteger(MediaFormat.KEY_PCM_ENCODING,AudioFormat.ENCODING_PCM_16BIT);
            codec=MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME));codec.configure(format,null,null,0);codec.start();
            MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();SoftwareEffects effects=null;
            int channels=0,encoding=AudioFormat.ENCODING_PCM_16BIT;boolean inputEnd=false,outputEnd=false,preroll=true;long seekUs=-1;
            while(alive(request)){
                if(pendingSeek>=0&&track!=null){
                    long target;
                    synchronized(state){target=pendingSeek;pendingSeek=-1;track.pause();track.flush();baseMs=target;framesWritten=0;}
                    extractor.seekTo(target*1000,MediaExtractor.SEEK_TO_PREVIOUS_SYNC);codec.flush();effects.reset();inputEnd=false;outputEnd=false;preroll=true;seekUs=target*1000;
                    if(playing)track.play();
                }
                if(track!=null&&!playing&&!preroll&&pendingSeek<0){synchronized(state){if(alive(request)&&!playing&&pendingSeek<0)state.wait(20);}continue;}
                if(outputEnd){
                    if(Integer.toUnsignedLong(track.getPlaybackHeadPosition())>=framesWritten){
                        synchronized(state){playing=false;completed=true;releaseWake();}
                        PcmPlayer successor=next;if(successor!=null)successor.start();
                        post(request,()->{if(completionListener!=null)completionListener.onCompletion(this);});
                        synchronized(state){while(alive(request)&&pendingSeek<0)state.wait(20);}continue;
                    }
                    Thread.sleep(5);continue;
                }
                if(!inputEnd){
                    int index=codec.dequeueInputBuffer(1000);
                    if(index>=0){ByteBuffer in=codec.getInputBuffer(index);int size=extractor.readSampleData(in,0);
                        if(size<0){codec.queueInputBuffer(index,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);inputEnd=true;}
                        else{codec.queueInputBuffer(index,0,size,extractor.getSampleTime(),0);extractor.advance();}
                    }
                }
                int index=codec.dequeueOutputBuffer(info,1000);
                if(index==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){
                    MediaFormat pcm=codec.getOutputFormat();channels=pcm.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    encoding=pcm.containsKey(MediaFormat.KEY_PCM_ENCODING)?pcm.getInteger(MediaFormat.KEY_PCM_ENCODING):AudioFormat.ENCODING_PCM_16BIT;
                    if(track!=null)throw new IllegalStateException("Mid-track format change is unsupported");
                    track=track(pcm,request);if(track==null)return;effects=new SoftwareEffects(sampleRate,channels==1?1:2);continue;
                }
                if(index<0)continue;
                try{
                    if(info.size>0&&track!=null){
                        ByteBuffer pcm=codec.getOutputBuffer(index).duplicate().order(ByteOrder.LITTLE_ENDIAN);((Buffer)pcm).position(info.offset);((Buffer)pcm).limit(info.offset+info.size);
                        int bytes=encoding==AudioFormat.ENCODING_PCM_FLOAT||encoding==AudioFormat.ENCODING_PCM_32BIT?4:encoding==AudioFormat.ENCODING_PCM_24BIT_PACKED?3:encoding==AudioFormat.ENCODING_PCM_16BIT?2:encoding==AudioFormat.ENCODING_PCM_8BIT?1:0;
                        if(bytes==0)throw new IllegalArgumentException("Unsupported decoder PCM encoding");
                        int frames=info.size/bytes/channels,skip=0;
                        if(seekUs>=0)skip=(int)Math.min(frames,Math.max(0,(seekUs-info.presentationTimeUs)*sampleRate/1000000));
                        ((Buffer)pcm).position(info.offset+skip*channels*bytes);short[] samples=new short[(frames-skip)*(channels==1?1:2)];
                        int cursor=0;
                        for(int frame=skip;frame<frames;frame++){
                            double l=0,r=0;
                            for(int ch=0;ch<channels;ch++){
                                double value;
                                if(encoding==AudioFormat.ENCODING_PCM_FLOAT)value=pcm.getFloat();
                                else if(encoding==AudioFormat.ENCODING_PCM_32BIT)value=pcm.getInt()/2147483648d;
                                else if(encoding==AudioFormat.ENCODING_PCM_24BIT_PACKED){int raw=(pcm.get()&255)|(pcm.get()&255)<<8|pcm.get()<<16;value=raw/8388608d;}
                                else if(encoding==AudioFormat.ENCODING_PCM_8BIT)value=((pcm.get()&255)-128)/128d;
                                else value=pcm.getShort()/32768d;
                                if(!Double.isFinite(value))value=0;
                                if(channels<=2){if(ch==0)l=value;else r=value;}
                                else if(ch==0)l+=value;else if(ch==1)r+=value;else if(ch==2){l+=value*.707;r+=value*.707;}else if(ch==3){l+=value*.5;r+=value*.5;}else if(ch%2==0)l+=value*.707;else r+=value*.707;
                            }
                            double scale=channels>2?.4:1;samples[cursor++]=(short)Math.max(-32768,Math.min(32767,Math.round(l*scale*32768)));
                            if(channels>1)samples[cursor++]=(short)Math.max(-32768,Math.min(32767,Math.round(r*scale*32768)));
                        }
                        effects.configure(config);effects.process(samples,samples.length);
                        int offset=0;
                        while(offset<samples.length&&alive(request)&&pendingSeek<0){
                            if(!playing&&!preroll){synchronized(state){state.wait(10);}continue;}
                            int wrote=track.write(samples,offset,samples.length-offset,AudioTrack.WRITE_NON_BLOCKING);
                            if(wrote<0)throw new IllegalStateException("PCM output write failed: "+wrote);
                            offset+=wrote;framesWritten+=wrote/(channels==1?1:2);
                            if(wrote>0)preroll=false;
                            if(wrote==0)Thread.sleep(5);
                        }
                        if(samples.length>0){preroll=false;if(seekUs>=0){seekUs=-1;post(request,()->{if(seekListener!=null)seekListener.onSeekComplete(this);});}}
                    }
                    if((info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0)outputEnd=true;
                }finally{codec.releaseOutputBuffer(index,false);}
            }
        }catch(Exception error){
            if(alive(request)){
                android.util.Log.e("SimplePlayer","PCM playback failed",error);playing=false;ready=false;releaseWake();
                post(request,()->{if(errorListener!=null)errorListener.onError(this,1,0);});
            }
        }finally{
            if(track!=null){try{track.pause();track.flush();track.release();}catch(RuntimeException ignored){}}
            if(codec!=null){try{codec.stop();}catch(RuntimeException ignored){}codec.release();}extractor.release();
        }
    }
}
