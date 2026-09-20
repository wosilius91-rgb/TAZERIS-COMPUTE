package com.tazeris.streamworkshop;

import android.app.Activity;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.content.*;
import android.Manifest;
import android.content.pm.PackageManager;
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
    private final Handler stateHandler=new Handler(Looper.getMainLooper());
    private boolean watching=false;
    private boolean receiverRegistered=false;
    private final BroadcastReceiver workerReceiver=new BroadcastReceiver(){
        @Override public void onReceive(Context context,Intent intent){ syncFromWorkerState(); }
    };

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(buildUi());
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},77);
        }
        executor.execute(() -> {
            try {
                JSONObject h=json(API+"/health","GET",null);
                progress("PARUOŠTA",0,"Dirbtuvė paruošta","V2 "+h.optString("version","?")+" • foninis režimas įjungtas");
            } catch(Exception e) {
                progress("RYŠIO KLAIDA",0,"V2 serverio pasiekti nepavyko",cleanError(e));
            }
        });
        startStateWatcher();
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
        root.addView(text("Nuoroda arba failas. Nuorodos užduotis veikia fone — gali uždaryti dirbtuvę ir naudoti telefoną kitur.",14,Color.rgb(166,177,194),false));

        LinearLayout card = card(); root.addView(card, lpMatch());
        label(card,"Streamo nuoroda");
        urlInput = input("https://youtube.com/live/…"); card.addView(urlInput, lpMatch());
        linkButton = button("PALEISTI KŪRIMĄ FONE"); card.addView(linkButton, lpMatch());
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
        results.removeAllViews();
        Intent svc=new Intent(this,StreamWorkService.class);
        svc.putExtra("url",url);
        svc.putExtra("tiktok",tiktokInput.getText().toString());
        svc.putExtra("youtube",youtubeInput.getText().toString());
        svc.putExtra("discord",discordInput.getText().toString());
        if(Build.VERSION.SDK_INT>=26)startForegroundService(svc);else startService(svc);
        setBusy(true);
        progress("PALEISTA",1,"Darbas perduotas fonui","Gali užgesinti ekraną arba naudoti kitas programas");
        startStateWatcher();
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
                if(idx==1 || idx%25==0) telemetry("upload_progress",kind+" upload",idx+" dalys • "+mb(sent));
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

    private void startStateWatcher(){
        if(watching)return;watching=true;
        stateHandler.post(new Runnable(){
            @Override public void run(){
                if(!watching)return;
                try{
                    SharedPreferences p=getSharedPreferences(StreamWorkService.PREFS,MODE_PRIVATE);
                    String st=p.getString("status","");
                    if(!st.isEmpty()){
                        String stage=p.getString("stage","VEIKIA");
                        int pct=p.getInt("progress",0);
                        String msg=p.getString("message","Apdorojama…");
                        String detail=p.getString("detail","");
                        progress(stage,pct,msg,detail);
                        boolean active="running".equals(st)||"downloading".equals(st)||"uploading".equals(st)||"processing".equals(st);
                        setBusy(active);
                        if("done".equals(st)){
                            String raw=p.getString("results","[]");
                            try{showResults(new JSONArray(raw));}catch(Exception ignored){}
                        }
                    }
                }catch(Exception ignored){}
                stateHandler.postDelayed(this,1000);
            }
        });
    }

    @Override protected void onResume(){
        super.onResume();watching=false;startStateWatcher();
    }
    @Override protected void onPause(){
        watching=false;stateHandler.removeCallbacksAndMessages(null);super.onPause();
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
            x.put("event",event);x.put("message",message);x.put("detail",detail);x.put("app_version","1.4.0-background-ui");
            json(API+"/api/mobile/event","POST",x.toString());
        }catch(Exception ignored){}
    }

    private JSONObject json(String url,String method,String body)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setRequestMethod(method);c.setConnectTimeout(30000);c.setReadTimeout(120000);c.setRequestProperty("User-Agent",UA);c.setRequestProperty("Accept","application/json");
        if(body!=null){c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");byte[] data=body.getBytes(StandardCharsets.UTF_8);c.setFixedLengthStreamingMode(data.length);try(OutputStream o=c.getOutputStream()){o.write(data);}}
        int code=c.getResponseCode();InputStream in=code>=400?c.getErrorStream():c.getInputStream();String s=readText(in);c.disconnect();if(code>=400)throw new IOException("Serverio HTTP "+code+": "+s);return new JSONObject(s);
    }
    private void putBytes(String url,byte[] b,int n)throws Exception{
        Exception last=null;
        for(int attempt=1;attempt<=8;attempt++){
            HttpURLConnection c=null;
            try{
                c=(HttpURLConnection)new URL(url).openConnection();
                c.setRequestMethod("PUT");
                c.setDoOutput(true);
                c.setConnectTimeout(45000);
                c.setReadTimeout(180000);
                c.setRequestProperty("User-Agent",UA);
                c.setRequestProperty("Content-Type","application/octet-stream");
                c.setRequestProperty("Connection","close");
                c.setFixedLengthStreamingMode(n);
                try(OutputStream o=new BufferedOutputStream(c.getOutputStream(),256*1024)){
                    o.write(b,0,n);o.flush();
                }
                int code=c.getResponseCode();
                if(code>=200&&code<300){c.disconnect();return;}
                String s=readText(c.getErrorStream());
                throw new IOException("Upload HTTP "+code+": "+s);
            }catch(Exception e){
                last=e;
                telemetry("upload_retry","Upload dalis kartojama","bandymas="+attempt+" • "+cleanError(e));
                if(c!=null)c.disconnect();
                if(attempt<8)Thread.sleep(Math.min(8000,500L*(1L<<Math.min(attempt,4))));
            }
        }
        throw last!=null?last:new IOException("Upload nepavyko");
    }
    private String readText(InputStream in)throws IOException{if(in==null)return "";try(BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){StringBuilder b=new StringBuilder();String l;while((l=r.readLine())!=null)b.append(l);return b.toString();}}
    private String mb(long n){return String.format(Locale.US,"%.1f MB",n/1024.0/1024.0);}
    private void clearDir(File d){File[] fs=d.listFiles();if(fs!=null)for(File f:fs)if(f.isFile())f.delete();}

    private static final String UA="Mozilla/5.0 (Linux; Android 16; Mobile) AppleWebKit/537.36 Chrome/153 Safari/537.36";

    @Override protected void onDestroy(){watching=false;stateHandler.removeCallbacksAndMessages(null);executor.shutdownNow();super.onDestroy();}
}
