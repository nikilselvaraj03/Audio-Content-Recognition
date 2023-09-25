package com.stardust.mixmatch;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.design.widget.BottomNavigationView;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.CardView;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import com.acrcloud.rec.sdk.ACRCloudClient;
import com.acrcloud.rec.sdk.ACRCloudConfig;
import com.acrcloud.rec.sdk.IACRCloudListener;
import com.wang.avi.AVLoadingIndicatorView;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import android.Manifest;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;


import java.io.File;

public class MainActivity extends AppCompatActivity implements IACRCloudListener {


    private static final int PERMISSION_CALLBACK_CONSTANT = 100;

    private static final int REQUEST_PERMISSION_SETTING = 101;
    String[] permissionsRequired = new String[]{Manifest.permission.RECORD_AUDIO,
            Manifest.permission.MODIFY_AUDIO_SETTINGS,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,  Manifest.permission.READ_EXTERNAL_STORAGE
    ,  Manifest.permission.READ_PHONE_STATE};
    private SharedPreferences permissionStatus;
    private boolean sentToSettings = false;

    private ACRCloudClient mClient;
    private ACRCloudConfig mConfig;
    private AVLoadingIndicatorView avi;
    private TextView nomatch;

    private TextView  mResult, tv_time;
    private  TextView album1,title1,artist1;
    private boolean mProcessing = false;
    private boolean initState = false;
        private Button done;
    private String path = "";
    private ImageView notfound;
    private long startTime = 0;
    private long stopTime = 0;
private CardView card;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(false);



        permissionStatus = getSharedPreferences("permissionStatus",MODE_PRIVATE);
        path = Environment.getExternalStorageDirectory().toString()
                + "/acrcloud/model";

        File file = new File(path);
        if(!file.exists()){
            file.mkdirs();
        }

nomatch=(TextView)findViewById(R.id.nomatch) ;
        mResult = (TextView) findViewById(R.id.result);
        tv_time = (TextView) findViewById(R.id.time);
        card=(CardView)findViewById(R.id.card);
        avi=(AVLoadingIndicatorView)findViewById(R.id.avi);
        Button startBtn = (Button) findViewById(R.id.start);
        album1=(TextView) findViewById(R.id.album);
        done=(Button) findViewById(R.id.done);
        title1=(TextView) findViewById(R.id.title);
        artist1=(TextView) findViewById(R.id.artist);
        notfound=(ImageView)findViewById(R.id.notfound);

        startBtn.setText(getResources().getString(R.string.start));

        Button stopBtn = (Button) findViewById(R.id.stop);
        stopBtn.setText(getResources().getString(R.string.stop));

        findViewById(R.id.stop).setOnClickListener(
                new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        stop();
                    }
                });

        Button cancelBtn = (Button) findViewById(R.id.cancel);
        cancelBtn.setText(getResources().getString(R.string.cancel));
        findViewById(R.id.done).setOnClickListener(
                new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        card.setVisibility(View.GONE);mResult.setText("");
                    }
                });

        findViewById(R.id.start).setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View arg0) {
                if(ActivityCompat.checkSelfPermission(MainActivity.this, permissionsRequired[0]) != PackageManager.PERMISSION_GRANTED
                        || ActivityCompat.checkSelfPermission(MainActivity.this, permissionsRequired[1]) != PackageManager.PERMISSION_GRANTED
                        || ActivityCompat.checkSelfPermission(MainActivity.this, permissionsRequired[2]) != PackageManager.PERMISSION_GRANTED){
                    if(ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this,permissionsRequired[0])
                            || ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this,permissionsRequired[1])
                            || ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this,permissionsRequired[2])){
                        //Show Information about why you need the permission
                        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                        builder.setTitle("Need Multiple Permissions");
                        builder.setMessage("TThis app needs to access Microphone and File storage permissions.");
                        builder.setPositiveButton("Grant", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.cancel();
                                ActivityCompat.requestPermissions(MainActivity.this,permissionsRequired,PERMISSION_CALLBACK_CONSTANT);
                            }
                        });
                        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.cancel();
                            }
                        });
                        builder.show();
                    } else {
                        //just request the permission
                        ActivityCompat.requestPermissions(MainActivity.this,permissionsRequired,PERMISSION_CALLBACK_CONSTANT);
                    }



                    SharedPreferences.Editor editor = permissionStatus.edit();
                    editor.putBoolean(permissionsRequired[0],true);
                    editor.commit();
                } else {
                    //You already have the permission, just go ahead.
                    start();
                    avi.smoothToShow();
                }

            }
        });

        findViewById(R.id.cancel).setOnClickListener(
                new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        cancel();
                        avi.smoothToHide();
                    }
                });


        this.mConfig = new ACRCloudConfig();
        this.mConfig.acrcloudListener = this;

        // If you implement IACRCloudResultWithAudioListener and override "onResult(ACRCloudResult result)", you can get the Audio data.
        //this.mConfig.acrcloudResultWithAudioListener = this;

        this.mConfig.context = this;
        this.mConfig.host = "identify-eu-west-1.acrcloud.com";
        this.mConfig.dbPath = path; // offline db path, you can change it with other path which this app can access.
        this.mConfig.accessKey = "3d793ad4ce7a8a6836aa3ab9bc24ec11";
        this.mConfig.accessSecret = "83Ld6hApwK2rPgrndICFJgBrSLiUfxiqW5cltwuM";
        this.mConfig.protocol = ACRCloudConfig.ACRCloudNetworkProtocol.PROTOCOL_HTTP; // PROTOCOL_HTTPS
        this.mConfig.reqMode = ACRCloudConfig.ACRCloudRecMode.REC_MODE_REMOTE;
        //this.mConfig.reqMode = ACRCloudConfig.ACRCloudRecMode.REC_MODE_LOCAL;
        //this.mConfig.reqMode = ACRCloudConfig.ACRCloudRecMode.REC_MODE_BOTH;

        this.mClient = new ACRCloudClient();
        // If reqMode is REC_MODE_LOCAL or REC_MODE_BOTH,
        // the function initWithConfig is used to load offline db, and it may cost long time.
        this.initState = this.mClient.initWithConfig(this.mConfig);
        if (this.initState) {
            this.mClient.startPreRecord(3000); //start prerecord, you can call "this.mClient.stopPreRecord()" to stop prerecord.
        }
    }

    public void start() {
        card.setVisibility(View.GONE);
        notfound.setVisibility(View.GONE);
        nomatch.setVisibility(View.GONE);
        if (!this.initState) {
            Toast.makeText(this, "init error", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!mProcessing) {
            mProcessing = true;
            mResult.setText("");
            if (this.mClient == null || !this.mClient.startRecognize()) {
                mProcessing = false;
                mResult.setText("start error!");
            }
            startTime = System.currentTimeMillis();
        }
    }

    protected void stop() {
        if (mProcessing && this.mClient != null) {
            this.mClient.stopRecordToRecognize();
            avi.smoothToHide();
        }
        mProcessing = false;

        stopTime = System.currentTimeMillis();
    }

    protected void cancel() {             card.setVisibility(View.GONE);
        mResult.setText("");
        nomatch.setVisibility(View.GONE);
        notfound.setVisibility(View.GONE);
        avi.hide();
        if (mProcessing && this.mClient != null) {
            mResult.setText("");
            mProcessing = false;
            this.mClient.cancel();
            tv_time.setText("");

        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    public void onResult(String result) {
        title1.setText("");
        artist1.setText("");
        album1.setText("");
        if (this.mClient != null) {
            this.mClient.cancel();
            mProcessing = false;
        }

        String tres = "\n";

        try {
            JSONObject j = new JSONObject(result);
            JSONObject j1 = j.getJSONObject("status");
            int j2 = j1.getInt("code");
            if(j2 == 0){
                JSONObject metadata = j.getJSONObject("metadata");
                //
                avi.smoothToHide();
                card.setVisibility(View.VISIBLE);
                if (metadata.has("humming")) {
                    JSONArray hummings = metadata.getJSONArray("humming");
                    for(int i=0; i<hummings.length(); i++) {
                        JSONObject tt = (JSONObject) hummings.get(i);
                        String title = tt.getString("title");
                        JSONArray artistt = tt.getJSONArray("artists");
                        JSONObject art = (JSONObject) artistt.get(0);
                        String artist = art.getString("name");
                        JSONArray genre = tt.getJSONArray("genres");
                        JSONObject genres = (JSONObject) genre.get(0);
                        String album = "";
                        album+=tt.getString("label")+"\n";
                        album+=genres.getString("name");
                        title1.setText(title);
                        artist1.setText(artist);
                        album1.setText(album);
                        tres = tres + (i+1) + ".  " + title + "\n";

                    }
                }
                if (metadata.has("music")) {
                    JSONArray musics = metadata.getJSONArray("music");
                    for(int i=0; i<musics.length(); i++) {
                        JSONObject tt = (JSONObject) musics.get(i);
                        String title = tt.getString("title");
                        JSONArray artistt = tt.getJSONArray("artists");
                        JSONObject art = (JSONObject) artistt.get(0);
                        String artist = art.getString("name");
                        tres = tres + (i+1) + ".  Title: " + title + "    Artist: " + artist + "\n";
                        JSONArray genre = tt.getJSONArray("genres");
                        JSONObject genres = (JSONObject) genre.get(0);
                        String album = "";
                        album+=tt.getString("label")+"\n";
                        album+=genres.getString("name");
                        title1.setText(title);
                        artist1.setText(artist);
                        album1.setText(album);
                    }
                }
                if (metadata.has("streams")) {
                    JSONArray musics = metadata.getJSONArray("streams");
                    for(int i=0; i<musics.length(); i++) {
                        JSONObject tt = (JSONObject) musics.get(i);
                        String title = tt.getString("title");
                        String channelId = tt.getString("channel_id");
                        tres = tres + (i+1) + ".  Title: " + title + "    Channel Id: " + channelId + "\n";
                        JSONArray genre = tt.getJSONArray("genres");
                        JSONObject genres = (JSONObject) genre.get(0);
                        String album = "";
                        album+=tt.getString("label")+"\n";
                        album+=genres.getString("name");
                        title1.setText(title);
                        album1.setText(album);
                    }
                }
                if (metadata.has("custom_files")) {
                    JSONArray musics = metadata.getJSONArray("custom_files");
                    for(int i=0; i<musics.length(); i++) {
                        JSONObject tt = (JSONObject) musics.get(i);
                        String title = tt.getString("title");
                        tres = tres + (i+1) + ".  Title: " + title + "\n";
                        title1.setText(title);
                        String album = "";
                        album+="Name: "+tt.getString("name")+"\n";
                        album+="Season: "+tt.getString("season")+"\n";
                        album+="Episode: "+tt.getString("episode")+"\n";
                        album+="Link :"+tt.getString("link")+"\n";
                        album1.setText(album);
                    }
                }
                tres = tres + "\n\n" + result;
            }else{avi.smoothToHide();
                tres = result;
                mResult.setVisibility(View.GONE);
                card.setVisibility(View.GONE);
                notfound.setVisibility(View.VISIBLE);
                nomatch.setVisibility(View.VISIBLE);
            }
        } catch (JSONException e) {
            avi.smoothToHide();
            tres = result;
            e.printStackTrace();
        }
        avi.smoothToHide();
        mResult.setText(tres);
    }

    @Override
    public void onVolumeChanged(double v) {

    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.e("MainActivity", "release");
        if (this.mClient != null) {
            this.mClient.release();
            this.initState = false;
            this.mClient = null;
        }
    }

    @Override
    public void onPointerCaptureChanged(boolean hasCapture) {

    }

}