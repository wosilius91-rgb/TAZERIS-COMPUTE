package com.tazeris.streamworkshop;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import android.graphics.Typeface;

import org.json.JSONArray;
import org.json.JSONObject;
import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLException;
import com.yausername.youtubedl_android.YoutubeDLRequest;
import com.yausername.ffmpeg.FFmpeg;
import kotlin.Unit;
import kotlin.jvm.functions.Function3;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    private static final String API = "https://tazeris-stream-clipper-v2-production.up.railway.app";
    private static final int PICK_VIDEO = 1001;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private EditText urlInput, tiktokInput, youtubeInput, discordInput;
    private Button linkButton, fileButton;
    private TextView stageText, messageText, detailText, percentText;
    private ProgressBar progressBar;
    private LinearLayout results;
    private volatile boolean busy = false;
    private volatile boolean ytDlpReady = false;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(buildUi());
        setBusy(true);
        executor.execute(() -> {
            try {
                progress("VARIKLIO PARUOŠIMAS",1,"Ruošiamas telefono video variklis…","yt-dlp + FFmpeg");
                YoutubeDL.getInstance().init(this);
                FFmpeg.getInstance().init(this);
                String before=String.valueOf(YoutubeDL.getInstance().versionName(this));
                progress("VARIKLIO ATNAUJINIMAS",2,"Atnaujinamas yt-dlp…","Dabartinė versija: "+before);
                try {
                    YoutubeDL.getInstance().updateYoutubeDL(this, YoutubeDL.UpdateChannel._NIGHTLY);
                } catch(Exception updateError) {
                    telemetry("ytdlp_update_warning","NIGHTLY atnaujinimas nepavyko",cleanError(updateError));
                    try {
                        YoutubeDL.getInstance().updateYoutubeDL(this, YoutubeDL.UpdateChannel._STABLE);
                    } catch(Exception stableError) {
                        telemetry("ytdlp_update_warning","STABLE atnaujinimas nepavyko",cleanError(stableError));
                    }
                }
                String after=String.valueOf(YoutubeDL.getInstance().versionName(this));
                ytDlpReady = true;
                JSONObject h=json(API+"/health","GET",null);
                telemetry("app_start","Dirbtuvė paleista","yt-dlp "+after+" • V2 "+h.optString("version","?"));
                setBusy(false);
                progress("PARUOŠTA",0,"Dirbtuvė paruošta","yt-dlp "+after+" + V2 serveris pasiekiami");
            } catch(Exception e) {
                telemetry("startup_error","Variklio paleidimas nepavyko",cleanError(e));
                setBusy(false);
                progress("VARIKLIO KLAIDA",100,"Telefono video variklis nepasileido",cleanError(e));
            }
        });
    }

    private View buildUi() {
        int pad = dp(16);
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, dp(22), pad, dp(40));
        root.setBackgroundColor(Color.rgb(9,11,16));
        scroll.addView(root);

        TextView brand = text("TAZERIS • VIDEO DIRBTUVĖ", 12, Color.rgb(150,162,181), true);
        root.addView(brand);
        TextView title = text("Stream → profesionalūs Shorts", 30, Color.WHITE, true);
        title.setPadding(0, dp(8), 0, dp(12)); root.addView(title);
        root.addView(text("Nuoroda arba failas. Atsisiuntimas vyksta telefonu, AI analizė ir montažas — serveryje.",14,Color.rgb(166,177,194),false));

        LinearLayout card = card(); root.addView(card, lpMatch());
        label(card,"Streamo nuoroda");
        urlInput = input("https://youtube.com/live/…"); card.addView(urlInput, lpMatch());
        linkButton = button("SUKURTI IŠ NUORODOS"); card.addView(linkButton, lpMatch());
        linkButton.setOnClickListener(v -> startLink());

        fileButton = button("PASIRINKTI VIDEO FAILĄ"); card.addView(fileButton, lpMatch());
        fileButton.setOnClickListener(v -> pickFile());

        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
        EditText tt = input("@tazeris5"), yt = input("@tazeris5");
        tiktokInput=tt; youtubeInput=yt;
        LinearLayout a = new LinearLayout(this); a.setOrientation(LinearLayout.VERTICAL);
        LinearLayout c = new LinearLayout(this); c.setOrientation(LinearLayout.VERTICAL);
        label(a,"TikTok"); a.addView(tt,lpMatch()); label(c,"YouTube"); c.addView(yt,lpMatch());
        row.addView(a,new LinearLayout.LayoutParams(0,-2,1)); row.addView(c,new LinearLayout.LayoutParams(0,-2,1));
        card.addView(row,lpMatch());
        label(card,"Discord"); discordInput=input(""); discordInput.setHint("nuoroda arba pavadinimas"); card.addView(discordInput,lpMatch());

        LinearLayout status = card(); root.addView(status,lpMatch());
        stageText=text("PARUOŠTA",12,Color.rgb(150,162,181),true); status.addView(stageText);
        messageText=text("Įklijuok nuorodą arba pasirink failą",19,Color.WHITE,true); status.addView(messageText);
        detailText=text("",13,Color.rgb(160,171,188),false); status.addView(detailText);
        progressBar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100); progressBar.setProgress(0); status.addView(progressBar,lpMatch());
        percentText=text("0%",22,Color.WHITE,true); status.addView(percentText);
        results=new LinearLayout(this); results.setOrientation(LinearLayout.VERTICAL); status.addView(results,lpMatch());
        return scroll;
    }

    private LinearLayout card() {
        LinearLayout x=new LinearLayout(this); x.setOrientation(LinearLayout.VERTICAL);
        x.setPadding(dp(16),dp(16),dp(16),dp(16));
        x.setBackgroundColor(Color.rgb(18,23,34));
        LinearLayout.LayoutParams p=lpMatch(); p.setMargins(0,dp(14),0,0); x.setLayoutParams(p);
        return x;
    }
    private void label(LinearLayout p,String s){ TextView t=text(s,12,Color.rgb(171,181,197),false); t.setPadding(0,dp(10),0,dp(6)); p.addView(t); }
    private TextView text(String s,int sp,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private EditText input(String val){EditText e=new EditText(this);e.setText(val);e.setTextColor(Color.WHITE);e.setHintTextColor(Color.rgb(100,110,126));e.setSingleLine(true);e.setBackgroundColor(Color.rgb(10,14,21));e.setPadding(dp(14),dp(12),dp(14),dp(12));return e;}
    private Button button(String s){Button b=new Button(this);b.setText(s);LinearLayout.LayoutParams p=lpMatch();p.setMargins(0,dp(12),0,0);b.setLayoutParams(p);return b;}
    private LinearLayout.LayoutParams lpMatch(){return new LinearLayout.LayoutParams(-1,-2);}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}

    private void setBusy(boolean v){busy=v;runOnUiThread(()->{linkButton.setEnabled(!v);fileButton.setEnabled(!v);});}
    private void progress(String stage,int pct,String msg,String detail){
        runOnUiThread(()->{stageText.setText(stage.toUpperCase(Locale.ROOT));progressBar.setProgress(Math.max(0,Math.min(100,pct)));percentText.setText(Math.max(0,Math.min(100,pct))+"%");messageText.setText(msg);detailText.setText(detail==null?"":detail);});
    }
    private void fail(Exception e){
        String msg=cleanError(e);
        telemetry("client_error","Procesas sustabdytas",msg);
        setBusy(false);progress("KLAIDA",100,"Procesas sustabdytas",msg);
    }
    private String cleanError(Throwable e){String s=e.getMessage();return (s==null||s.isBlank())?e.getClass().getSimpleName():s;}

    private void startLink(){
        if(busy)return;
        String url=urlInput.getText().toString().trim();
        if(url.isEmpty()){urlInput.requestFocus();return;}
        if(!ytDlpReady){progress("VARIKLIO KLAIDA",100,"yt-dlp dar neparuoštas","Perkrauk dirbtuvę");return;}
        setBusy(true); results.removeAllViews();
        executor.execute(()->{
            try{
                telemetry("link_start","Pradėtas yt-dlp atsisiuntimas",url);
                progress("Nuorodos analizė",2,"yt-dlp analizuoja streamą…","Naudojamas tavo telefono internetas");
                File dir=new File(getExternalFilesDir(null),"stream-workshop");
                if(!dir.exists()&&!dir.mkdirs())throw new IOException("Nepavyko sukurti darbo aplanko");
                clearDir(dir);

                String template=new File(dir,"source.%(ext)s").getAbsolutePath();

                Function3<Float,Long,String,Unit> cb=(p,eta,line)->{
                    int pct=Math.max(0,Math.min(100,Math.round(p)));
                    int appPct=3+(int)(pct*0.44);
                    String det=(eta!=null&&eta>0)?("Liko ~"+eta+" s"):"";
                    progress("Atsisiuntimas telefone",appPct,"Siunčiamas streamas… "+pct+"%",det);
                    return Unit.INSTANCE;
                };

                String[] clients={"","tv_simply","android_vr","web_safari"};
                Exception lastError=null;
                boolean downloaded=false;
                for(int attempt=0;attempt<clients.length&&!downloaded;attempt++){
                    for(File old:Objects.requireNonNullElse(dir.listFiles(),new File[0])) {
                        if(old.getName().startsWith("source.")) old.delete();
                    }
                    String client=clients[attempt];
                    progress("Nuorodos analizė",2+attempt,"YouTube gavimo būdas "+(attempt+1)+"/"+clients.length+"…",client.isEmpty()?"default":client);
                    telemetry("ytdlp_attempt","YouTube gavimo būdas "+(attempt+1),client.isEmpty()?"default":client);
                    try{
                        YoutubeDLRequest request=new YoutubeDLRequest(url);
                        request.addOption("--no-playlist");
                        request.addOption("--no-mtime");
                        request.addOption("--merge-output-format","mp4");
                        request.addOption("--remote-components","ejs:github");
                        request.addOption("-f","bv*[height<=1080]+ba/b[height<=1080]/best[height<=1080]");
                        request.addOption("-o",template);
                        request.addOption("--retries","5");
                        request.addOption("--fragment-retries","5");
                        request.addOption("--socket-timeout","30");
                        if(!client.isEmpty()) request.addOption("--extractor-args","youtube:player_client="+client);
                        YoutubeDL.getInstance().execute(request,"TAZERIS_STREAM_"+attempt,cb);
                        downloaded=true;
                    }catch(Exception e){
                        lastError=e;
                        telemetry("ytdlp_attempt_error","Būdas "+(attempt+1)+" nepavyko",cleanError(e));
                    }
                }
                if(!downloaded) throw lastError!=null?lastError:new IOException("Nepavyko gauti YouTube video");

                File[] files=dir.listFiles((d,n)->n.startsWith("source.")&&!n.endsWith(".part")&&!n.endsWith(".ytdl"));
                if(files==null||files.length==0)throw new IOException("yt-dlp baigė darbą, bet video failas nerastas");
                File video=files[0];
                for(File f:files)if(f.length()>video.length())video=f;
                if(video.length()<1024*1024)throw new IOException("Parsisiųstas video failas per mažas");
                telemetry("link_resolved","yt-dlp video gautas",video.getName()+" • "+mb(video.length()));
                progress("Atsisiuntimas baigtas",48,"Streamo įrašas gautas telefone",mb(video.length()));
                uploadPair(video,null);
            }catch(Exception e){fail(e);}
        });
    }

    private void uploadPair(File video,File audio)throws Exception{
        JSONObject init=new JSONObject();
        init.put("video_name",video.getName());init.put("video_size",video.length());
        init.put("audio_name",audio==null?"":audio.getName());init.put("audio_size",audio==null?0:audio.length());
        init.put("tiktok",tiktokInput.getText().toString());init.put("youtube",youtubeInput.getText().toString());init.put("discord",discordInput.getText().toString());init.put("language","lt");init.put("min_score",7.3);
        telemetry("upload_start","Pradedamas perdavimas į V2",video.getName()+(audio!=null?" + "+audio.getName():""));
        progress("Perdavimas",49,"Ruošiamas perdavimas į AI serverį…","");
        JSONObject meta=json(API+"/api/mobile/init","POST",init.toString());
        String sid=meta.getString("id");int chunk=meta.getInt("chunk_size");
        uploadFile(sid,"video",video,chunk,49,59);
        if(audio!=null)uploadFile(sid,"audio",audio,chunk,59,64);
        progress("Perdavimas",65,"Vaizdas ir garsas perduoti","Sujungiama be kokybės praradimo");
        JSONObject job=json(API+"/api/mobile/"+sid+"/complete","POST","{}");
        telemetry("server_job_created","V2 užduotis sukurta",job.optString("id",""));
        poll(job.getString("id"));
    }

    private void uploadFile(String sid,String kind,File file,int chunk,int p0,int p1)throws Exception{
        long total=file.length(),sent=0;int idx=0;byte[] buf=new byte[chunk];
        try(InputStream in=new BufferedInputStream(new FileInputStream(file))){
            int n;while((n=readChunk(in,buf))>0){
                putBytes(API+"/api/mobile/"+sid+"/"+kind+"/"+idx,buf,n);sent+=n;idx++;
                int pct=(int)Math.min(100,sent*100/total);int app=p0+(int)((p1-p0)*(pct/100.0));
                progress("Perdavimas",app,(kind.equals("video")?"Keliamas vaizdas":"Keliamas garsas")+"… "+pct+"%",mb(sent)+" / "+mb(total));
            }
        }
    }
    private int readChunk(InputStream in,byte[] b)throws IOException{int off=0,n;while(off<b.length&&(n=in.read(b,off,b.length-off))>0)off+=n;return off;}

    private void poll(String jobId)throws Exception{
        while(true){
            JSONObject j=json(API+"/api/jobs/"+jobId,"GET",null);
            String st=j.optString("status","processing");int rp=j.optInt("progress",0);int p=65+(int)(rp*.35);
            progress(j.optString("stage","AI analizė"),Math.min(100,p),j.optString("message","Apdorojama…"),j.optString("detail",""));
            if("done".equals(st)){telemetry("job_done","Video paruošti","clips="+(j.optJSONArray("clips")==null?0:j.optJSONArray("clips").length()));showResults(j.optJSONArray("clips"));setBusy(false);return;}
            if("error".equals(st))throw new IOException(j.optString("message","AI serverio klaida"));
            Thread.sleep(2000);
        }
    }

    private void showResults(JSONArray clips){
        runOnUiThread(()->{
            results.removeAllViews();
            if(clips==null)return;
            for(int i=0;i<clips.length();i++){
                JSONObject c=clips.optJSONObject(i); if(c==null)continue;
                String hook=c.optString("hook","Video "+(i+1));String u=API+c.optString("url","");
                Button b=button((i+1)+". "+hook+" • ATIDARYTI MP4");
                b.setOnClickListener(v->{try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(u)));}catch(Exception ignored){}});
                results.addView(b,lpMatch());
            }
        });
    }

    private void pickFile(){
        if(busy)return;
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("video/*");i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i,PICK_VIDEO);
    }
    @Override protected void onActivityResult(int r,int c,Intent d){
        super.onActivityResult(r,c,d);if(r!=PICK_VIDEO||c!=RESULT_OK||d==null||d.getData()==null)return;
        Uri uri=d.getData();setBusy(true);results.removeAllViews();
        executor.execute(()->{try{File dir=new File(getExternalFilesDir(null),"stream-workshop");if(!dir.exists())dir.mkdirs();clearDir(dir);File f=new File(dir,"selected.mp4");copyUri(uri,f);uploadPair(f,null);}catch(Exception e){fail(e);}});
    }
    private void copyUri(Uri uri,File out)throws Exception{
        long total=querySize(uri),got=0,last=0;progress("Failo paruošimas",2,"Skaitomas pasirinktas video…","");
        try(InputStream in=getContentResolver().openInputStream(uri);OutputStream o=new BufferedOutputStream(new FileOutputStream(out))){
            if(in==null)throw new IOException("Failo atidaryti nepavyko");byte[] b=new byte[1024*1024];int n;
            while((n=in.read(b))>0){o.write(b,0,n);got+=n;long now=System.currentTimeMillis();if(now-last>500){last=now;int pct=total>0?(int)Math.min(100,got*100/total):0;progress("Failo paruošimas",(int)(pct*.45),"Ruošiamas failas… "+pct+"%",mb(got)+(total>0?" / "+mb(total):""));}}
        }
    }
    private long querySize(Uri uri){try(android.database.Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.SIZE},null,null,null)){if(c!=null&&c.moveToFirst()){int x=c.getColumnIndex(OpenableColumns.SIZE);if(x>=0)return c.getLong(x);}}catch(Exception ignored){}return -1;}

    private void telemetry(String event,String message,String detail){
        try{
            JSONObject x=new JSONObject();
            x.put("event",event);x.put("message",message);x.put("detail",detail);x.put("app_version","1.2.0-yt-dlp-nightly");
            json(API+"/api/mobile/event","POST",x.toString());
        }catch(Exception ignored){}
    }

    private JSONObject json(String url,String method,String body)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setRequestMethod(method);c.setConnectTimeout(30000);c.setReadTimeout(120000);c.setRequestProperty("User-Agent",UA);c.setRequestProperty("Accept","application/json");
        if(body!=null){c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");byte[] data=body.getBytes(StandardCharsets.UTF_8);c.setFixedLengthStreamingMode(data.length);try(OutputStream o=c.getOutputStream()){o.write(data);}}
        int code=c.getResponseCode();InputStream in=code>=400?c.getErrorStream():c.getInputStream();String s=readText(in);c.disconnect();if(code>=400)throw new IOException("Serverio HTTP "+code+": "+s);return new JSONObject(s);
    }
    private void putBytes(String url,byte[] b,int n)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setRequestMethod("PUT");c.setDoOutput(true);c.setConnectTimeout(30000);c.setReadTimeout(120000);c.setRequestProperty("User-Agent",UA);c.setRequestProperty("Content-Type","application/octet-stream");c.setFixedLengthStreamingMode(n);try(OutputStream o=c.getOutputStream()){o.write(b,0,n);}int code=c.getResponseCode();if(code>=400){String s=readText(c.getErrorStream());c.disconnect();throw new IOException("Upload HTTP "+code+": "+s);}c.disconnect();
    }
    private String readText(InputStream in)throws IOException{if(in==null)return "";try(BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){StringBuilder b=new StringBuilder();String l;while((l=r.readLine())!=null)b.append(l);return b.toString();}}
    private String mb(long n){return String.format(Locale.US,"%.1f MB",n/1024.0/1024.0);}
    private void clearDir(File d){File[] fs=d.listFiles();if(fs!=null)for(File f:fs)if(f.isFile())f.delete();}

    private static final String UA="Mozilla/5.0 (Linux; Android 16; Mobile) AppleWebKit/537.36 Chrome/153 Safari/537.36";

    @Override protected void onDestroy(){executor.shutdownNow();super.onDestroy();}
}
