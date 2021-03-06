package it.polito.mad.streamreceiver;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import java.io.IOException;
import java.nio.ByteBuffer;

import it.polito.mad.websocket.WSClientImpl;

public class MainActivity extends AppCompatActivity {

    private WSClientImpl mClient = new WSClientImpl(new WSClientImpl.Listener() {
        @Override
        public void onConnectionEstablished() {
            Toast.makeText(MainActivity.this, "Connected", Toast.LENGTH_LONG).show();
            mClient.sendHello();
            mClient.requestConfigParams();
        }

        @Override
        public void onServerUnreachable(Exception e) {
            Toast.makeText(MainActivity.this, "Can't connect to server: "
                    + e.getClass().getSimpleName()+": "+e.getMessage(), Toast.LENGTH_LONG).show();

        }

        @Override
        public void onConfigParamsReceived(byte[] configParams, int width, int height) {
            Log.d("ACT", "config bytes: "+new String(configParams)+" ; " +
                    "resolution: "+width+"x"+height);
            mWidth = width;
            mHeight = height;
            stopDecoder();
            startDecoder();
            mDecoderTask.setConfigurationBuffer(ByteBuffer.wrap(configParams));
        }

        @Override
        public void onStreamChunkReceived(byte[] chunk, int flags, long timestamp, long sn) {
            //Log.d("ACT", "stream["+chunk.length+"]");
            VideoChunks.Chunk c = new VideoChunks.Chunk(chunk, flags, timestamp);
            c.sn = sn;
            mDecoderTask.submitEncodedData(c);
        }
    });

    private int mWidth = 320, mHeight = 240;
    private Surface mSurface;
    private DecoderThread mDecoderTask;

    private PowerManager.WakeLock wakeLock;
    private SharedPreferences mPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        final SurfaceView outputView = (SurfaceView) findViewById(R.id.output_view);
        outputView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {

            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                mSurface = holder.getSurface();
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {

            }
        });
//        outputView.post(new Runnable() {
//            @Override
//            public void run() {
//                //int w = surfaceView.getMeasuredWidth();
//                int measuredHeight = outputView.getMeasuredHeight();
//                ViewGroup.LayoutParams lp = outputView.getLayoutParams();
//                lp.width = measuredHeight * 4 / 3;
//                outputView.setLayoutParams(lp);
//            }
//        });

        findViewById(R.id.connect_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String ip = mPreferences.getString(getString(R.string.pref_key_server_ip),
                        getString(R.string.pref_server_ip_default_value));
                int port = Integer.parseInt(mPreferences.getString(getString(R.string.pref_key_server_port),
                        getString(R.string.pref_server_port_default_value)));
                mClient.connect(ip, port, 2000);
            }
        });

        outputView.post(new Runnable() {
            @Override
            public void run() {
                //int w = surfaceView.getMeasuredWidth();
                int measuredHeight = outputView.getMeasuredHeight();
                ViewGroup.LayoutParams lp = outputView.getLayoutParams();
                lp.width = measuredHeight * 4 / 3;
                outputView.setLayoutParams(lp);
            }
        });

        mPreferences = PreferenceManager.getDefaultSharedPreferences(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()){
            case R.id.action_settings:
                Intent intent = new Intent(this, SettingsActivity.class);
                startActivity(intent);
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onPause() {
        stopDecoder();
        if (mClient.getSocket() != null){
            mClient.closeConnection();
        }
        wakeLock.release();
        super.onPause();
    }

    private void startDecoder(){
        if (mDecoderTask == null){
            mDecoderTask = new DecoderThread(mWidth, mHeight, null);
            mDecoderTask.setSurface(mSurface);
            mDecoderTask.start();
        }
    }

    private void stopDecoder(){
        if (mDecoderTask != null){
            mDecoderTask.interrupt();
            try{
                mDecoderTask.join();
            }catch(InterruptedException e){}
            mDecoderTask = null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(
                PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE,
                "My wakelook");
        wakeLock.acquire();
    }

}
