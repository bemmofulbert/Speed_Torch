package com.bemsproduction.torche;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.drawable.Icon;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.view.MotionEvent;
import android.view.View;

import androidx.appcompat.widget.SwitchCompat;

import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;

import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Pattern;

import permissions.dispatcher.NeedsPermission;
import permissions.dispatcher.OnPermissionDenied;
import permissions.dispatcher.OnShowRationale;
import permissions.dispatcher.RuntimePermissions;

@RuntimePermissions
public class MainActivity extends AppCompatActivity {


    private ImageButton but_switchTo_Led ;
    private ImageButton but_switchTo_Ecran ;

    private ImageView backImg;

    private SwitchCompat switch_controlLed;
    private SwitchCompat switch_controlEcran;
    private CameraManager cameraManager;
    private String cameraId = null ;
    private boolean stateTorch = false;
    private boolean modeTorchIsEcran = false;

    private TextView text_batlev;

    NotificationManager notificationManager;

    private float luminosite_actuel;
    Toolbar toolbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //initialise
        setContentView(R.layout.activity_main);

        but_switchTo_Led = findViewById(R.id.but_led);
        but_switchTo_Ecran = findViewById(R.id.but_ecran);
        backImg = findViewById(R.id.backImg);
        switch_controlLed = findViewById(R.id.switch_controlLed);
        switch_controlEcran = findViewById(R.id.switch_controlEcran);
        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        text_batlev = findViewById(R.id.text_batLevel);
        findViewById(R.id.switch_controlEcran).setVisibility(View.VISIBLE);
        findViewById(R.id.switch_controlLed).setVisibility(View.GONE);

        File fileState = new File(getFilesDir().getAbsolutePath()+"/state");

        //bare d'outils
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayShowTitleEnabled(true);



        // buttons led  ------------------------------
        but_switchTo_Led.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                backImg.setImageDrawable(getResources().getDrawable(R.drawable.ic_flashlight_torch));
                findViewById(R.id.switch_controlEcran).setVisibility(View.GONE);
                findViewById(R.id.switch_controlLed).setVisibility(View.VISIBLE);
                modeTorchIsEcran = false;
                try {
                    fileState.createNewFile();
                }catch (FileNotFoundException fnfe){
                    fnfe.printStackTrace();
                }catch (IOException ioe){
                    ioe.printStackTrace();
                }
                //ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.WRITE_SETTINGS}, 200);
            }
        });
        // buttons Ecran ------------------------------
        but_switchTo_Ecran.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                backImg.setImageDrawable(getResources().getDrawable(R.drawable.ic_smartphone));
                findViewById(R.id.switch_controlEcran).setVisibility(View.VISIBLE);
                findViewById(R.id.switch_controlLed).setVisibility(View.GONE);

                    fileState.delete();
                modeTorchIsEcran = true;

//                if(!permissionBrigthnessAccepted){
//                    backImg.setImageDrawable(getResources().getDrawable(R.drawable.ic_flashlight_torch));
//                    findViewById(R.id.switch_controlEcran).setVisibility(View.GONE);
//                    findViewById(R.id.switch_controlLed).setVisibility(View.VISIBLE);
//                }
            }
        });


        //Verification de la version d'android pour la camera
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                cameraId = cameraManager.getCameraIdList()[0];
                cameraManager.setTorchMode(cameraId, false);
            }
        }
        catch(CameraAccessException cae) {
            System.out.println("camera indisponible");
            cae.printStackTrace();
        }

        switch_controlLed.setOnCheckedChangeListener((buttonView, isChecked) -> toggleLed());
        switch_controlEcran.setOnCheckedChangeListener((buttonView, isChecked) -> toggleEcran());

        // initialisation de l'afficheur du niveau de batterie
        setNiveauBatterie();

        // creer la notification de l'application pour un controle facile de la torche
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel notifC = new NotificationChannel("457","notif-Torche-Bems", NotificationManager.IMPORTANCE_HIGH);
            //notifC.setDescription("description");
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(notifC);
            Intent notificationIntent = new Intent(getApplicationContext(),MainActivity.class);
            PendingIntent intent = PendingIntent.getActivity(getApplicationContext(),456,notificationIntent,PendingIntent.FLAG_CANCEL_CURRENT);
            Notification notif = new Notification.Builder(getApplicationContext(),notifC.getId()).setContentTitle("Torche").setContentIntent(intent)
                    .setContentText("Cliquez ici pour acceder a Torche").setSmallIcon(R.drawable.ic_flashlight_torch)
                    .setLargeIcon(Icon.createWithResource(getApplicationContext(),R.drawable.ic_flashlight_torch)).setAutoCancel(true).build();
            notificationManager.notify(4,notif);
        }

        //creerNotification();
        File file = new File(getFilesDir().getAbsolutePath()+"/perm");
        if(!file.exists()){
            onShowRationaleForbrigthnesse();
            try {
                file.createNewFile();
            }catch (IOException io){
                io.printStackTrace();
            }
        }
        reloadState();

        //promptSpeechInput();

    }

///////////
    private void promptSpeechInput() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().getDisplayLanguage());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Vous pouvez parler ...");
        try {
            startActivityForResult(intent, 188);
        } catch (ActivityNotFoundException a) {
            Toast.makeText(getApplicationContext(), "Désolé, votre appareil ne supporte pas d'entr",Toast.LENGTH_LONG).show();
        }
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case 188: {
                if (resultCode == RESULT_OK && null != data) {
                    ArrayList<String> buffer = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    String result = buffer.get(0);
                    Pattern ff = Pattern.compile("Final|fantasy");
                    if (ff.matcher(result).find()) {
                            Toast.makeText(this,"entendu",Toast.LENGTH_LONG).show();
                    }
                }
                break;
            }
            default:
                break;
        }
    }
    // assignation du Menu, executer pour la creation d'un menu
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);

        return true;
    }

    public void share(){
        Intent share = new Intent(android.content.Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_SUBJECT, "APP");
        share.putExtra(Intent.EXTRA_TEXT, "Aller sur ce site http://bemsProduction.org/");

        startActivity(Intent.createChooser(share, "Partager Torche a vos amis"));
    }
    public void a_propos(){
        Intent intent = new Intent(getApplicationContext(),A_ProposActivity.class);

        startActivity(intent);
    }

    //executer lorsqu'une option du menu est selectionne
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        //int id = item.getItemId();
        switch (item.getItemId()) {
            case R.id.action_share :
                share();
                return true;
            case R.id.action_a_propos:
                a_propos();
                return true;
        }
        //noinspection SimplifiableIfStatement

        return super.onOptionsItemSelected(item);
    }

    //--------------------------------------------------------------------
    public boolean toggleLed() { // changer l'etat de la torche
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    stateTorch = !stateTorch;
                    cameraManager.setTorchMode(cameraId, stateTorch);// commute l'etat de la torche
                }
            } catch (CameraAccessException cae) {
                cae.printStackTrace();
                return false;
            }
        return true;

    }
    public boolean toggleEcran(){
        MainActivityPermissionsDispatcher.brigthnesseWithPermissionCheck(this);
        //brigthnesse();
        return true;
    }

    //mettre a jour l'etat de la batterie
    public void setNiveauBatterie() {
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = this.getApplicationContext().registerReceiver(null, ifilter);

        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

        float batteryPct = level * 100 / (float)scale;

        text_batlev.setText(""+batteryPct+" %");
    }

    @OnPermissionDenied(Manifest.permission.WRITE_SETTINGS)
    void showDeniedForbrigthnesse(){
        Toast.makeText(this,"vous ne pourrez pas utiliser l'ecran comme torche",Toast.LENGTH_LONG).show();
        switch_controlEcran.setChecked(false);
    }

    @NeedsPermission(Manifest.permission.WRITE_SETTINGS)
    void brigthnesse(){
        Settings.System.putInt(getContentResolver(),Settings.System.SCREEN_BRIGHTNESS_MODE,Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
        WindowManager.LayoutParams lp = getWindow().getAttributes();

        if(switch_controlEcran.isChecked()) {
            luminosite_actuel = lp.screenBrightness;
            float brightness = 1.0f;
            lp.screenBrightness = brightness;
            getWindow().setAttributes(lp);
        }else {
            lp.screenBrightness = luminosite_actuel;
            getWindow().setAttributes(lp);
        }
    }


    @OnShowRationale(Manifest.permission.WRITE_SETTINGS)
    public void onShowRationaleForbrigthnesse(){
        new AlertDialog.Builder(this).setMessage("L'application Torche a besoin de permission pour controler la luminosite de votre telephone")
                .setPositiveButton("Permettre",((dialog, button) -> MainActivityPermissionsDispatcher.proceedBrigthnessePermissionRequest(this) ))
                .setNegativeButton("Ne plus afficher", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                        MainActivityPermissionsDispatcher.cancelBrigthnessePermissionRequest(MainActivity.this);
                    }
                })
                .show();
    }

    public void reloadState(){
            File fileState = new File(getFilesDir().getAbsolutePath()+"/state");

        if(!fileState.exists()) {
            backImg.setImageDrawable(getResources().getDrawable(R.drawable.ic_smartphone));
            findViewById(R.id.switch_controlEcran).setVisibility(View.VISIBLE);
            findViewById(R.id.switch_controlLed).setVisibility(View.GONE);
        }else{
            backImg.setImageDrawable(getResources().getDrawable(R.drawable.ic_flashlight_torch));
            findViewById(R.id.switch_controlEcran).setVisibility(View.GONE);
            findViewById(R.id.switch_controlLed).setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int permsRequestCode, String[] permissions, int[] grantResults){
        super.onRequestPermissionsResult(permsRequestCode,permissions,grantResults);
        MainActivityPermissionsDispatcher.onActivityResult(this,200);
        //onShowRationaleManifest();
        switch(permsRequestCode){
            case 200:
                if(grantResults.length > 0)
                    if (grantResults[0]!=PackageManager.PERMISSION_GRANTED) switch_controlEcran.setChecked(false);
                break;
        }

    }
    @Override
    protected void onResume() {
        super.onResume();
        setNiveauBatterie();

        if(modeTorchIsEcran) {
            backImg.setImageDrawable(getResources().getDrawable(R.drawable.ic_smartphone));
            findViewById(R.id.switch_controlEcran).setVisibility(View.VISIBLE);
            findViewById(R.id.switch_controlLed).setVisibility(View.GONE);
        }else{
            backImg.setImageDrawable(getResources().getDrawable(R.drawable.ic_flashlight_torch));
            findViewById(R.id.switch_controlEcran).setVisibility(View.GONE);
            findViewById(R.id.switch_controlLed).setVisibility(View.VISIBLE);
        }
        reloadState();
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        setNiveauBatterie();

        if(modeTorchIsEcran) {
            backImg.setImageDrawable(getResources().getDrawable(R.drawable.ic_smartphone));
            findViewById(R.id.switch_controlEcran).setVisibility(View.VISIBLE);
            findViewById(R.id.switch_controlLed).setVisibility(View.GONE);
        }else{
            backImg.setImageDrawable(getResources().getDrawable(R.drawable.ic_flashlight_torch));
            findViewById(R.id.switch_controlEcran).setVisibility(View.GONE);
            findViewById(R.id.switch_controlLed).setVisibility(View.VISIBLE);
        }

        reloadState();
    }

    @Override
    protected void onPause() {
        super.onPause();
        setNiveauBatterie();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        setNiveauBatterie();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        setNiveauBatterie();
        return super.onTouchEvent(event);
    }
}
