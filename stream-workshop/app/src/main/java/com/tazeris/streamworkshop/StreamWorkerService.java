package com.tazeris.streamworkshop;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.os.IBinder;

import org.json.JSONArray;
import org.json.JSONObject;

import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLRequest;
import com.yausername.ffmpeg.FFmpeg;

import kotlin.Unit;
import kotlin.jvm.functions.Function3;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public class StreamWorkerService extends Service {
    public static final String ACTION_PROGRESS="com.tazeris.streamworkshop.PROGRESS";
    public static final String ACTION_START_LINK="com.tazeris.streamworkshop.START_LINK";
    public static final String ACTION_CANCEL="com.tazeris.streamworkshop.CANCEL";
    private static final String API="https://tazeris-stream-clipper-v2-production.up.railway.app";
    private static final String CHANNEL="tazeris_stream_work";
    private static final int NOTIF=4107;
    private static final String UA="Mozilla/5.0 (Linux; Android 16; Mobile) AppleWebKit/537.36 Chrome/153 Safari/537.36";

    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private volatile boolean running=false;
    private volatile boolean cancelled=false;
    private SharedPreferences prefs;
    private NotificationManager nm;

    @Override public void onCreate(){
        super.onCreate();
        prefs=getSharedPreferences("worker_state",MODE_PRIVATE);
        nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel ch=new NotificationChannel(CHANNEL,"TAZERIS video kūrimas",NotificationManager.IMPORTANCE_LOW);
        ch.setDescription("Streamo atsisiuntimas, įkėlimas ir AI video kūrimas");
        nm.createNotificationChannel(ch);
    }

    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null) return START_STICKY;
        String action=intent.getAction();
        if(ACTION_CANCEL.equals(action)){
            cancelled=true;
            try{YoutubeDL.getInstance().destroyProcessById("TAZERIS_STREAM");}catch(Exception ignored){}
            fail("Atšaukta","Užduotį atšaukei pats.");
            return START_NOT_STICKY;
        }
        if(ACTION_START_LINK.equals(action)){
            if(running) return START_STICKY;
            running=true; cancelled=false;
            startForegroundNow("Paleidžiama…",0);
            String url=intent.getStringExtra("url");
            String tiktok=intent.getStringExtra("tiktok");
            String youtube=intent.getStringExtra("youtube");
            String discord=intent.getStringExtra("discord");
            prefs.edit().putString("source_url",url==null?"":url).apply();
            executor.execute(()->process(url,tiktok,youtube,discord));
        }
        return START_STICKY;
    }

    private void process(String url,String tiktok,String youtube,String discord){
        try{
            if(url==null||url.isBlank()) throw new IOException("Tuščia streamo nuoroda");
            update("VARIKLIO PARUOŠIMAS",1,"Ruošiamas yt-dlp telefone…","Gali uždaryti dirbtuvę ir naudoti telefoną kitur");
            initEngine();
            checkCancel();

            telemetry("bg_link_start","Foninis yt-dlp pradėtas",url);
            File dir=new File(getExternalFilesDir(null),"stream-workshop-bg");
            if(!dir.exists()&&!dir.mkdirs()) throw new IOException("Nepavyko sukurti darbo aplanko");
            clearSources(dir);
            String template=new File(dir,"source.%(ext)s").getAbsolutePath();

            Function3<Float,Long,String,Unit> cb=(p,eta,line)->{
                int pct=Math.max(0,Math.min(100,Math.round(p)));
                int appPct=3+(int)(pct*0.44);
                String det=(eta!=null&&eta>0)?("Liko ~"+eta+" s"):"";
                update("ATSISIUNTIMAS TELEFONE",appPct,"Siunčiamas streamas… "+pct+"%",det);
                return Unit.INSTANCE;
            };

            String[] clients={"","tv_simply","android_vr","web_safari"};
            Exception last=null; boolean downloaded=false;
            for(int attempt=0;attempt<clients.length&&!downloaded;attempt++){
                checkCancel(); clearSources(dir);
                String client=clients[attempt];
                update("NUORODOS ANALIZĖ",2+attempt,"YouTube gavimo būdas "+(attempt+1)+"/"+clients.length+"…",client.isEmpty()?"default":client);
                telemetry("bg_ytdlp_attempt","Būdas "+(attempt+1),client.isEmpty()?"default":client);
                try{
                    YoutubeDLRequest r=new YoutubeDLRequest(url);
                    r.addOption("--no-playlist");
                    r.addOption("--no-mtime");
                    r.addOption("--merge-output-format","mp4");
                    r.addOption("--remote-components","ejs:github");
                    r.addOption("-f","bv*[height<=1080]+ba/b[height<=1080]/best[height<=1080]");
                    r.addOption("-o",template);
                    r.addOption("--retries","5");
                    r.addOption("--fragment-retries","5");
                    r.addOption("--socket-timeout","30");
                    if(!client.isEmpty()) r.addOption("--extractor-args","youtube:player_client="+client);
                    YoutubeDL.getInstance().execute(r,"TAZERIS_STREAM",cb);
                    downloaded=true;
                }catch(Exception e){
                    last=e;
                    telemetry("bg_ytdlp_attempt_error","Būdas "+(attempt+1)+" nepavyko",clean(e));
                }
            }
            if(!downloaded) throw last!=null?last:new IOException("Nepavyko gauti YouTube video");

            File[] fs=dir.listFiles((d,n)->n.startsWith("source.")&&!n.endsWith(".part")&&!n.endsWith(".ytdl"));
            if(fs==null||fs.length==0) throw new IOException("Video failas po atsisiuntimo nerastas");
            File video=fs[0]; for(File f:fs) if(f.length()>video.length()) video=f;
            if(video.length()<1024*1024) throw new IOException("Parsisiųstas video failas per mažas");
            telemetry("bg_link_resolved","Video gautas",video.getName()+" • "+mb(video.length()));
            update("ATSISIUNTIMAS BAIGTAS",48,"Streamas gautas telefone",mb(video.length()));

            JSONObject init=new JSONObject();
            init.put("video_name",video.getName()); init.put("video_size",video.length());
            init.put("audio_name",""); init.put("audio_size",0);
            init.put("tiktok",tiktok==null?"":tiktok); init.put("youtube",youtube==null?"":youtube);
            init.put("discord",discord==null?"":discord); init.put("language","lt"); init.put("min_score",7.3);
            update("PERDAVIMAS",49,"Ruošiamas perdavimas į V2…","");
            JSONObject meta=json(API+"/api/mobile/init","POST",init.toString());
            String sid=meta.getString("id"); int chunk=meta.getInt("chunk_size");
            uploadFile(sid,video,chunk);
            checkCancel();
            update("PERDAVIMAS",65,"Video perduotas į V2","Kuriama AI užduotis");
            JSONObject job=json(API+"/api/mobile/"+sid+"/complete","POST","{}");
            String jobId=job.getString("id");
            prefs.edit().putString("job_id",jobId).apply();
            telemetry("bg_server_job_created","V2 užduotis sukurta",jobId);
            poll(jobId);
        }catch(Exception e){
            if(cancelled) fail("Atšaukta","Užduotis atšaukta.");
            else fail("KLAIDA",clean(e));
        }
    }

    private void initEngine() throws Exception{
        Exception last=null;
        for(int i=1;i<=3;i++){
            try{
                YoutubeDL.getInstance().init(this);
                FFmpeg.getInstance().init(this);
                String before=String.valueOf(YoutubeDL.getInstance().versionName(this));
                update("VARIKLIO ATNAUJINIMAS",2,"Atnaujinamas yt-dlp…","Dabartinė: "+before);
                try{YoutubeDL.getInstance().updateYoutubeDL(this,YoutubeDL.UpdateChannel._NIGHTLY);}
                catch(Exception n){
                    telemetry("bg_ytdlp_update_warning","NIGHTLY nepavyko",clean(n));
                    try{YoutubeDL.getInstance().updateYoutubeDL(this,YoutubeDL.UpdateChannel._STABLE);}catch(Exception ignored){}
                }
                String after=String.valueOf(YoutubeDL.getInstance().versionName(this));
                telemetry("bg_engine_ready","yt-dlp paruoštas",after);
                return;
            }catch(Exception e){
                last=e; Thread.sleep(700L*i);
            }
        }
        throw last!=null?last:new IOException("yt-dlp variklis nepasileido");
    }

    private void uploadFile(String sid,File file,int chunk)throws Exception{
        long total=file.length(),sent=0;int idx=0;byte[] buf=new byte[chunk];
        telemetry("bg_upload_start","Pradedamas foninis upload",mb(total));
        try(InputStream in=new BufferedInputStream(new FileInputStream(file))){
            int n;
            while((n=readChunk(in,buf))>0){
                checkCancel();
                putBytes(API+"/api/mobile/"+sid+"/video/"+idx,buf,n);
                sent+=n;idx++;
                int pct=(int)Math.min(100,sent*100/total);
                int app=49+(int)(15*(pct/100.0));
                update("PERDAVIMAS",app,"Keliamas video… "+pct+"%",mb(sent)+" / "+mb(total));
                if(idx==1||idx%25==0) telemetry("bg_upload_progress","Video upload",idx+" dalys • "+mb(sent));
            }
        }
    }

    private void poll(String jobId)throws Exception{
        while(true){
            checkCancel();
            JSONObject j=json(API+"/api/jobs/"+jobId,"GET",null);
            String st=j.optString("status","processing");
            int rp=j.optInt("progress",0);int p=65+(int)(rp*.35);
            update(j.optString("stage","AI ANALIZĖ").toUpperCase(Locale.ROOT),Math.min(100,p),
                    j.optString("message","Apdorojama…"),j.optString("detail",""));
            if("done".equals(st)){
                JSONArray clips=j.optJSONArray("clips");
                String clipsJson=clips==null?"[]":clips.toString();
                prefs.edit().putString("clips",clipsJson).putString("status","done").apply();
                telemetry("bg_job_done","Video paruošti","clips="+(clips==null?0:clips.length()));
                finishSuccess(clips==null?0:clips.length());
                return;
            }
            if("error".equals(st)) throw new IOException(j.optString("message","AI serverio klaida"));
            Thread.sleep(2500);
        }
    }

    private void update(String stage,int pct,String msg,String detail){
        prefs.edit()
                .putString("status","running")
                .putString("stage",stage)
                .putInt("progress",Math.max(0,Math.min(100,pct)))
                .putString("message",msg==null?"":msg)
                .putString("detail",detail==null?"":detail)
                .putLong("updated",System.currentTimeMillis())
                .apply();
        nm.notify(NOTIF,notification(msg,pct,true));
        Intent b=new Intent(ACTION_PROGRESS).setPackage(getPackageName());
        sendBroadcast(b);
    }

    private void finishSuccess(int count){
        running=false;
        prefs.edit().putString("status","done").putString("stage","BAIGTA").putInt("progress",100)
                .putString("message","Paruošta: "+count+" video").putString("detail","Gali grįžti į dirbtuvę ir peržiūrėti.").apply();
        stopForeground(STOP_FOREGROUND_REMOVE);
        nm.notify(NOTIF,notification("Paruošta: "+count+" video",100,false));
        sendBroadcast(new Intent(ACTION_PROGRESS).setPackage(getPackageName()));
        stopSelf();
    }

    private void fail(String stage,String detail){
        running=false;
        prefs.edit().putString("status","error").putString("stage",stage).putInt("progress",100)
                .putString("message","Procesas sustabdytas").putString("detail",detail==null?"":detail).apply();
        stopForeground(STOP_FOREGROUND_REMOVE);
        nm.notify(NOTIF,notification("Procesas sustabdytas",100,false));
        sendBroadcast(new Intent(ACTION_PROGRESS).setPackage(getPackageName()));
        stopSelf();
    }

    private Notification notification(String text,int pct,boolean ongoing){
        Intent open=new Intent(this,MainActivity.class);
        PendingIntent pi=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b=new Notification.Builder(this,CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("TAZERIS Video Dirbtuvė")
                .setContentText(text==null?"Vykdoma…":text)
                .setContentIntent(pi)
                .setOnlyAlertOnce(true)
                .setOngoing(ongoing);
        if(ongoing)b.setProgress(100,Math.max(0,Math.min(100,pct)),false);
        return b.build();
    }

    private void startForegroundNow(String text,int pct){
        startForeground(NOTIF,notification(text,pct,true),ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
    }

    private void checkCancel() throws InterruptedException{
        if(cancelled) throw new InterruptedException("Atšaukta");
    }

    private JSONObject json(String url,String method,String body)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
        c.setRequestMethod(method);c.setConnectTimeout(30000);c.setReadTimeout(120000);
        c.setRequestProperty("User-Agent",UA);c.setRequestProperty("Accept","application/json");
        if(body!=null){c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");byte[] data=body.getBytes(StandardCharsets.UTF_8);c.setFixedLengthStreamingMode(data.length);try(OutputStream o=c.getOutputStream()){o.write(data);}}
        int code=c.getResponseCode();InputStream in=code>=400?c.getErrorStream():c.getInputStream();String s=readText(in);c.disconnect();
        if(code>=400)throw new IOException("Serverio HTTP "+code+": "+s);
        return new JSONObject(s);
    }

    private void putBytes(String url,byte[] b,int n)throws Exception{
        Exception last=null;
        for(int attempt=1;attempt<=8;attempt++){
            HttpURLConnection c=null;
            try{
                c=(HttpURLConnection)new URL(url).openConnection();c.setRequestMethod("PUT");c.setDoOutput(true);
                c.setConnectTimeout(45000);c.setReadTimeout(180000);
                c.setRequestProperty("User-Agent",UA);c.setRequestProperty("Content-Type","application/octet-stream");
                c.setRequestProperty("Connection","close");c.setFixedLengthStreamingMode(n);
                try(OutputStream o=new BufferedOutputStream(c.getOutputStream(),256*1024)){o.write(b,0,n);o.flush();}
                int code=c.getResponseCode();if(code>=200&&code<300){c.disconnect();return;}
                throw new IOException("Upload HTTP "+code+": "+readText(c.getErrorStream()));
            }catch(Exception e){
                last=e;if(c!=null)c.disconnect();
                telemetry("bg_upload_retry","Upload dalis kartojama","bandymas="+attempt+" • "+clean(e));
                if(attempt<8)Thread.sleep(Math.min(8000,500L*(1L<<Math.min(attempt,4))));
            }
        }
        throw last!=null?last:new IOException("Upload nepavyko");
    }

    private void telemetry(String event,String message,String detail){
        try{
            JSONObject x=new JSONObject();
            x.put("event",event);x.put("message",message);x.put("detail",detail);x.put("app_version","1.4.0-background");
            json(API+"/api/mobile/event","POST",x.toString());
        }catch(Exception ignored){}
    }

    private static int readChunk(InputStream in,byte[] b)throws IOException{int off=0,n;while(off<b.length&&(n=in.read(b,off,b.length-off))>0)off+=n;return off;}
    private static String readText(InputStream in)throws IOException{if(in==null)return "";try(BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){StringBuilder b=new StringBuilder();String l;while((l=r.readLine())!=null)b.append(l);return b.toString();}}
    private static String mb(long n){return String.format(Locale.US,"%.1f MB",n/1024.0/1024.0);}
    private static String clean(Throwable e){String s=e.getMessage();return (s==null||s.isBlank())?e.getClass().getSimpleName():s;}
    private static void clearSources(File d){File[] fs=d.listFiles();if(fs!=null)for(File f:fs)if(f.isFile()&&f.getName().startsWith("source."))f.delete();}

    @Override public void onTimeout(int startId,int fgsType){
        fail("LAIKO LIMITAS","Android foninio duomenų perdavimo limitas pasiektas. Atidaryk dirbtuvę ir paleisk dar kartą.");
    }
    @Override public void onDestroy(){executor.shutdownNow();super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}
