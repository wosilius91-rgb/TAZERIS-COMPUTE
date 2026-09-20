package com.tazeris.streamworkshop;

import android.app.*;
import android.content.*;
import android.os.*;
import androidx.annotation.Nullable;

import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLRequest;
import com.yausername.ffmpeg.FFmpeg;
import kotlin.Unit;
import kotlin.jvm.functions.Function3;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public class StreamWorkService extends Service {
    public static final String API="https://tazeris-stream-clipper-v2-production.up.railway.app";
    public static final String PREFS="tazeris_stream_work";
    private static final String CHANNEL="tazeris_stream_work";
    private static final int NOTIF=4417;
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private volatile boolean running=false;
    private SharedPreferences prefs;
    private PowerManager.WakeLock wakeLock;

    @Override public void onCreate(){
        super.onCreate();
        prefs=getSharedPreferences(PREFS,MODE_PRIVATE);
        NotificationManager nm=getSystemService(NotificationManager.class);
        if(Build.VERSION.SDK_INT>=26){
            NotificationChannel c=new NotificationChannel(CHANNEL,"TAZERIS Video Dirbtuvė",NotificationManager.IMPORTANCE_LOW);
            c.setDescription("Streamo atsisiuntimas, įkėlimas ir AI video kūrimas");
            nm.createNotificationChannel(c);
        }
    }

    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null)return START_REDELIVER_INTENT;
        if("STOP".equals(intent.getAction())){
            running=false;executor.shutdownNow();releaseWake();stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();return START_NOT_STICKY;
        }
        if(running)return START_REDELIVER_INTENT;
        String url=intent.getStringExtra("url");
        if(url==null||url.isBlank())return START_NOT_STICKY;
        String tt=intent.getStringExtra("tiktok");
        String yt=intent.getStringExtra("youtube");
        String dc=intent.getStringExtra("discord");
        String prev=prefs.getString("active_url","");
        String prevStatus=prefs.getString("status","");
        boolean resume=url.equals(prev)&&("running".equals(prevStatus)||"downloading".equals(prevStatus)||"uploading".equals(prevStatus));
        running=true;
        startForeground(NOTIF,notification("Paleista",1,"Ruošiamas darbas…",false));
        acquireWake();
        save("running","PALEISTA",1,"Darbas paleistas fone","Gali užgesinti ekraną ar naudoti kitas programėles",null,url);
        executor.execute(()->runJob(url,tt,yt,dc,resume));
        return START_REDELIVER_INTENT;
    }

    private void runJob(String url,String tt,String yt,String dc,boolean resume){
        try{
            update("running","VARIKLIO PARUOŠIMAS",1,"Ruošiamas yt-dlp…","Telefoną gali naudoti toliau");
            YoutubeDL.getInstance().init(this);
            FFmpeg.getInstance().init(this);
            try{YoutubeDL.getInstance().updateYoutubeDL(this,YoutubeDL.UpdateChannel._NIGHTLY);}
            catch(Exception e){
                telemetry("ytdlp_update_warning","NIGHTLY atnaujinimas nepavyko",err(e));
                try{YoutubeDL.getInstance().updateYoutubeDL(this,YoutubeDL.UpdateChannel._STABLE);}catch(Exception ignored){}
            }
            String ver=String.valueOf(YoutubeDL.getInstance().versionName(this));
            telemetry("app_start","Background service paleistas","yt-dlp "+ver);

            File dir=new File(getExternalFilesDir(null),"stream-workshop-bg");
            if(!dir.exists()&&!dir.mkdirs())throw new IOException("Nepavyko sukurti darbo aplanko");
            if(!resume)clearDir(dir);

            File video=download(url,dir);
            uploadAndProcess(video,tt,yt,dc);
        }catch(Exception e){
            telemetry("client_error","Foninis procesas sustabdytas",err(e));
            update("error","KLAIDA",100,"Procesas sustabdytas",err(e));
        }finally{
            running=false;releaseWake();
            try{stopForeground(STOP_FOREGROUND_DETACH);}catch(Exception ignored){}
            stopSelf();
        }
    }

    private File download(String url,File dir)throws Exception{
        String template=new File(dir,"source.%(ext)s").getAbsolutePath();
        Function3<Float,Long,String,Unit> cb=(p,eta,line)->{
            int pct=Math.max(0,Math.min(100,Math.round(p)));
            int app=3+(int)(pct*.44);
            update("downloading","ATSISIUNTIMAS",app,"Siunčiamas streamas… "+pct+"%",eta!=null&&eta>0?"Liko ~"+eta+" s":"");
            return Unit.INSTANCE;
        };
        String[] clients={"","tv_simply","android_vr","web_safari"};
        Exception last=null;boolean ok=false;
        for(int i=0;i<clients.length&&!ok;i++){
            String client=clients[i];
            telemetry("ytdlp_attempt","YouTube gavimo būdas "+(i+1),client.isEmpty()?"default":client);
            try{
                YoutubeDLRequest r=new YoutubeDLRequest(url);
                r.addOption("--no-playlist");
                r.addOption("--no-mtime");
                r.addOption("--merge-output-format","mp4");
                r.addOption("--remote-components","ejs:github");
                r.addOption("-f","bv*[height<=1080]+ba/b[height<=1080]/best[height<=1080]");
                r.addOption("-o",template);
                r.addOption("--retries","8");
                r.addOption("--fragment-retries","8");
                r.addOption("--socket-timeout","45");
                if(!client.isEmpty())r.addOption("--extractor-args","youtube:player_client="+client);
                YoutubeDL.getInstance().execute(r,"TAZERIS_BG_"+i,cb);
                ok=true;
            }catch(Exception e){
                last=e;telemetry("ytdlp_attempt_error","Būdas "+(i+1)+" nepavyko",err(e));
            }
        }
        if(!ok)throw last!=null?last:new IOException("YouTube video gauti nepavyko");
        File[] fs=dir.listFiles((d,n)->n.startsWith("source.")&&!n.endsWith(".part")&&!n.endsWith(".ytdl"));
        if(fs==null||fs.length==0)throw new IOException("yt-dlp baigė darbą, bet video nerastas");
        File best=fs[0];for(File f:fs)if(f.length()>best.length())best=f;
        if(best.length()<1024*1024)throw new IOException("Parsisiųstas video per mažas");
        telemetry("link_resolved","yt-dlp video gautas",best.getName()+" • "+mb(best.length()));
        update("uploading","PERDAVIMAS",48,"Streamas gautas telefone",mb(best.length()));
        return best;
    }

    private void uploadAndProcess(File video,String tt,String yt,String dc)throws Exception{
        JSONObject init=new JSONObject();
        init.put("video_name",video.getName());init.put("video_size",video.length());
        init.put("audio_name","");init.put("audio_size",0);
        init.put("tiktok",tt==null?"":tt);init.put("youtube",yt==null?"":yt);init.put("discord",dc==null?"":dc);
        init.put("language","lt");init.put("min_score",8.35);
        telemetry("upload_start","Pradedamas perdavimas į V2",video.getName());
        JSONObject meta=json(API+"/api/mobile/init","POST",init.toString());
        String sid=meta.getString("id");int chunk=meta.getInt("chunk_size");
        uploadFile(sid,video,chunk);
        update("processing","AI ANALIZĖ",65,"Video perduotas į serverį","Toliau telefonui nebereikia laikyti video proceso");
        JSONObject job=json(API+"/api/mobile/"+sid+"/complete","POST","{}");
        String jobId=job.getString("id");
        prefs.edit().putString("job_id",jobId).apply();
        telemetry("server_job_created","V2 užduotis sukurta",jobId);
        poll(jobId);
    }

    private void uploadFile(String sid,File file,int chunk)throws Exception{
        long total=file.length(),sent=0;int idx=0;byte[] buf=new byte[chunk];
        try(InputStream in=new BufferedInputStream(new FileInputStream(file))){
            int n;while(running&&(n=readChunk(in,buf))>0){
                putBytes(API+"/api/mobile/"+sid+"/video/"+idx,buf,n);sent+=n;idx++;
                int pct=(int)Math.min(100,sent*100/total);
                update("uploading","PERDAVIMAS",49+(int)(pct*.16),"Keliamas video… "+pct+"%",mb(sent)+" / "+mb(total));
                if(idx==1||idx%25==0)telemetry("upload_progress","video upload",idx+" dalys • "+mb(sent));
            }
        }
        if(!running)throw new InterruptedException("Darbas sustabdytas");
    }

    private void poll(String jobId)throws Exception{
        while(running){
            JSONObject j=json(API+"/api/jobs/"+jobId,"GET",null);
            String st=j.optString("status","processing");
            int rp=j.optInt("progress",0);int p=65+(int)(rp*.35);
            update("processing",j.optString("stage","AI ANALIZĖ"),Math.min(99,p),j.optString("message","Apdorojama…"),j.optString("detail",""));
            if("done".equals(st)){
                JSONArray clips=j.optJSONArray("clips");
                String raw=clips==null?"[]":clips.toString();
                save("done","BAIGTA",100,"Video paruošti","Gali atidaryti rezultatus dirbtuvėje",raw,prefs.getString("active_url",""));
                telemetry("job_done","Video paruošti","clips="+(clips==null?0:clips.length()));
                return;
            }
            if("error".equals(st))throw new IOException(j.optString("message","AI serverio klaida"));
            Thread.sleep(2500);
        }
    }

    private void update(String status,String stage,int pct,String msg,String detail){
        save(status,stage,pct,msg,detail,null,prefs.getString("active_url",""));
    }

    private void save(String status,String stage,int pct,String msg,String detail,String results,String url){
        SharedPreferences.Editor e=prefs.edit().putString("status",status).putString("stage",stage)
            .putInt("progress",Math.max(0,Math.min(100,pct))).putString("message",msg==null?"":msg)
            .putString("detail",detail==null?"":detail).putLong("updated",System.currentTimeMillis());
        if(url!=null)e.putString("active_url",url);
        if(results!=null)e.putString("results",results);
        e.apply();
        getSystemService(NotificationManager.class).notify(NOTIF,notification(stage,pct,msg,"done".equals(status)));
    }

    private Notification notification(String stage,int pct,String msg,boolean done){
        Intent open=new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi=PendingIntent.getActivity(this,1,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);
        b.setContentTitle("TAZERIS Video Dirbtuvė • "+stage).setContentText(msg==null?"":msg)
         .setSmallIcon(done?android.R.drawable.stat_sys_download_done:android.R.drawable.stat_sys_download)
         .setContentIntent(pi).setOngoing(!done).setOnlyAlertOnce(true)
         .setProgress(100,Math.max(0,Math.min(100,pct)),false);
        if(done)b.setAutoCancel(true);
        return b.build();
    }

    private void acquireWake(){
        try{
            PowerManager pm=(PowerManager)getSystemService(POWER_SERVICE);
            wakeLock=pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"TAZERIS:StreamWork");
            wakeLock.setReferenceCounted(false);wakeLock.acquire(6*60*60*1000L);
        }catch(Exception ignored){}
    }
    private void releaseWake(){try{if(wakeLock!=null&&wakeLock.isHeld())wakeLock.release();}catch(Exception ignored){}}

    private JSONObject json(String url,String method,String body)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
        c.setRequestMethod(method);c.setConnectTimeout(45000);c.setReadTimeout(180000);
        c.setRequestProperty("User-Agent",UA);c.setRequestProperty("Accept","application/json");
        if(body!=null){c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");byte[] d=body.getBytes(StandardCharsets.UTF_8);c.setFixedLengthStreamingMode(d.length);try(OutputStream o=c.getOutputStream()){o.write(d);}}
        int code=c.getResponseCode();InputStream in=code>=400?c.getErrorStream():c.getInputStream();String s=readText(in);c.disconnect();
        if(code>=400)throw new IOException("Serverio HTTP "+code+": "+s);return new JSONObject(s);
    }

    private void putBytes(String url,byte[] b,int n)throws Exception{
        Exception last=null;
        for(int a=1;a<=8;a++){
            HttpURLConnection c=null;
            try{
                c=(HttpURLConnection)new URL(url).openConnection();c.setRequestMethod("PUT");c.setDoOutput(true);
                c.setConnectTimeout(45000);c.setReadTimeout(180000);c.setRequestProperty("User-Agent",UA);
                c.setRequestProperty("Content-Type","application/octet-stream");c.setRequestProperty("Connection","close");
                c.setFixedLengthStreamingMode(n);
                try(OutputStream o=new BufferedOutputStream(c.getOutputStream(),256*1024)){o.write(b,0,n);o.flush();}
                int code=c.getResponseCode();if(code>=200&&code<300){c.disconnect();return;}
                throw new IOException("Upload HTTP "+code+": "+readText(c.getErrorStream()));
            }catch(Exception e){last=e;if(c!=null)c.disconnect();telemetry("upload_retry","Upload kartojamas","bandymas="+a+" • "+err(e));if(a<8)Thread.sleep(Math.min(8000,700L*a*a));}
        }
        throw last!=null?last:new IOException("Upload nepavyko");
    }

    private void telemetry(String event,String message,String detail){
        try{
            JSONObject x=new JSONObject();x.put("event",event);x.put("message",message);x.put("detail",detail);x.put("app_version","1.4.0-background");
            json(API+"/api/mobile/event","POST",x.toString());
        }catch(Exception ignored){}
    }

    private int readChunk(InputStream in,byte[] b)throws IOException{int off=0,n;while(off<b.length&&(n=in.read(b,off,b.length-off))>0)off+=n;return off;}
    private String readText(InputStream in)throws IOException{if(in==null)return "";try(BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){StringBuilder b=new StringBuilder();String l;while((l=r.readLine())!=null)b.append(l);return b.toString();}}
    private String err(Throwable e){String s=e.getMessage();return s==null||s.isBlank()?e.getClass().getSimpleName():s;}
    private String mb(long n){return String.format(Locale.US,"%.1f MB",n/1024.0/1024.0);}
    private void clearDir(File d){File[] fs=d.listFiles();if(fs!=null)for(File f:fs)if(f.isFile())f.delete();}
    private static final String UA="Mozilla/5.0 (Linux; Android 16; Mobile) AppleWebKit/537.36 Chrome/153 Safari/537.36";
    @Nullable @Override public android.os.IBinder onBind(Intent intent){return null;}
    @Override public void onDestroy(){running=false;releaseWake();super.onDestroy();}
}
