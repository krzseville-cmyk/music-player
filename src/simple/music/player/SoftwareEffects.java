package simple.music.player;

/** Route-independent PCM filters. Each decoder owns its own filter history. */
final class SoftwareEffects {
    static final String[] PRESETS={"Flat","Bass boost","Treble boost","Vocal","Rock","Pop","Classical","Dance"};
    private static final double[][] GAINS={{0,0,0,0,0},{6,4,0,0,0},{0,0,0,4,6},{-3,-1,4,2,-2},{5,3,-2,3,5},{-1,3,4,2,-1},{3,1,-1,1,3},{5,2,-1,3,4}};
    static final class Config {
        final int preset,bass;final float gainDb;
        Config(int preset,int bass,float gainDb){this.preset=preset>=0&&preset<PRESETS.length?preset:-1;this.bass=Math.max(0,Math.min(1000,bass));this.gainDb=Float.isFinite(gainDb)?Math.max(-30,Math.min(12,gainDb)):0;}
    }
    private final int rate,channels;
    private Config config;
    private Biquad[][] filters;
    private double volume=1,target=1;
    SoftwareEffects(int rate,int channels){if(rate<8000||channels<1||channels>8)throw new IllegalArgumentException("Invalid PCM format");this.rate=rate;this.channels=channels;}
    void configure(Config next){
        if(config!=null&&next.preset==config.preset&&next.bass==config.bass&&next.gainDb==config.gainDb)return;
        config=next;filters=new Biquad[channels][6];double peak=0;
        double[] gains=next.preset<0?GAINS[0]:GAINS[next.preset];double[] hz={63,250,1000,4000,12000};
        for(double gain:gains)peak=Math.max(peak,gain);
        double bass=next.bass*.012;peak+=bass;
        target=Math.pow(10,(next.gainDb-peak)/20);
        for(int ch=0;ch<channels;ch++){
            for(int i=0;i<5;i++)filters[ch][i]=Biquad.peak(Math.min(hz[i],rate*.42),rate,gains[i]);
            filters[ch][5]=Biquad.shelf(120,rate,bass);
        }
    }
    void reset(){if(filters!=null)for(Biquad[] channel:filters)for(Biquad filter:channel)filter.reset();volume=target;}
    void process(short[] samples,int count){
        if(config==null)configure(new Config(-1,0,0));
        boolean bypass=config.preset<=0&&config.bass==0&&config.gainDb==0;
        for(int i=0;i<count;i++){
            if(i%channels==0)volume+=(target-volume)*.002;
            if(bypass&&Math.abs(volume-1)<.00001)continue;
            double value=samples[i]/32768d;
            for(Biquad filter:filters[i%channels])value=filter.apply(value);
            value*=volume;
            // Headroom handles filter boosts; the final limiter bounds hot source material.
            double magnitude=Math.abs(value);
            if(magnitude>.95)value=Math.copySign(.95+.05*(1-Math.exp(-Math.min(100,(magnitude-.95)/.05))),value);
            samples[i]=(short)Math.max(-32768,Math.min(32767,Math.round(value*32768)));
        }
    }
    private static final class Biquad {
        double b0=1,b1,b2,a1,a2,z1,z2;
        double apply(double x){double y=b0*x+z1;z1=b1*x-a1*y+z2;z2=b2*x-a2*y;return y;}
        void reset(){z1=z2=0;}
        void coefficients(double b0,double b1,double b2,double a0,double a1,double a2){this.b0=b0/a0;this.b1=b1/a0;this.b2=b2/a0;this.a1=a1/a0;this.a2=a2/a0;}
        static Biquad peak(double hz,int rate,double db){
            Biquad f=new Biquad();if(db==0)return f;
            double a=Math.pow(10,db/40),w=2*Math.PI*hz/rate,c=Math.cos(w),alpha=Math.sin(w)/(2*.8);
            f.coefficients(1+alpha*a,-2*c,1-alpha*a,1+alpha/a,-2*c,1-alpha/a);return f;
        }
        static Biquad shelf(double hz,int rate,double db){
            Biquad f=new Biquad();if(db==0)return f;
            double a=Math.pow(10,db/40),w=2*Math.PI*hz/rate,c=Math.cos(w),s=Math.sin(w),beta=Math.sqrt(2*a)*s;
            f.coefficients(a*((a+1)-(a-1)*c+beta),2*a*((a-1)-(a+1)*c),a*((a+1)-(a-1)*c-beta),
                (a+1)+(a-1)*c+beta,-2*((a-1)+(a+1)*c),(a+1)+(a-1)*c-beta);return f;
        }
    }
}
