package com.gnychis.ActivityService;

import java.util.List;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

public class ActivityService extends Service implements SensorEventListener {
	
    public static Interface mMainActivity;

	private float mLastX, mLastY, mLastZ;
	private boolean mInitialized;
	private SensorManager mSensorManager;
    private Sensor mAccelerometer;
    private final float NOISE = (float) 0.25;

    WifiManager wifi;
    int user_is_home=0;
    List<ScanResult> scan_result;
    String home_ssid;
    
    @Override
    public void onCreate() {
    	super.onCreate();
    	
        mInitialized = false;
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mSensorManager.registerListener(this, mAccelerometer , SensorManager.SENSOR_DELAY_NORMAL);
        
        wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        
        // Create a broadcast receiver to listen for wifi scan results. We don't invoke them, we only passively
        // listen whenever they become available.
        registerReceiver(new BroadcastReceiver()
        {
            @Override
            public void onReceive(Context c, Intent intent) 
            {
            	if(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(intent.getAction())) {
	               scan_result = wifi.getScanResults();
	               if(home_ssid==null)
	            	   return;
	               int homenet_in_list=0;
	               for(ScanResult result : scan_result)
	            	   if(result.SSID.replaceAll("^\"|\"$", "").equals(home_ssid))
	            		   homenet_in_list=1;
	               
	               user_is_home=homenet_in_list;
            	}
            }
        }, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));   
        
        registerReceiver(new BroadcastReceiver()
        {
            @Override
            public void onReceive(Context c, Intent intent) 
            {
            	if("android.intent.action.BOOT_COMPLETED".equals(intent.getAction())) {
            		Toast.makeText(getApplicationContext(), "Got the boot complete...", Toast.LENGTH_LONG).show();	
            	}
            }
        }, new IntentFilter("android.intent.action.BOOT_COMPLETED"));    
        
        if (mMainActivity != null)  Log.d(getClass().getSimpleName(), "ActivityService started");
    }
    
    @Override
    public void onDestroy() {
    	super.onDestroy();
    	mSensorManager.unregisterListener(this);
    	if (mMainActivity != null)  Log.d(getClass().getSimpleName(), "ActivityService stopped");
    }
    
    @Override
    public IBinder onBind(Intent intent) {
    	return null;
    }
	
	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		// can be safely ignored for this demo
	}
	
	public static void setMainActivity(Interface activity) {
		mMainActivity = activity;
	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		float x = event.values[0];
		float y = event.values[1];
		float z = event.values[2];
		if (!mInitialized) {
			mLastX = x;
			mLastY = y;
			mLastZ = z;
			mInitialized = true;
		} else {
			float deltaX = Math.abs(mLastX - x);
			float deltaY = Math.abs(mLastY - y);
			float deltaZ = Math.abs(mLastZ - z);
			if (deltaX < NOISE) deltaX = (float)0.0;
			if (deltaY < NOISE) deltaY = (float)0.0;
			if (deltaZ < NOISE) deltaZ = (float)0.0;
			mLastX = x;
			mLastY = y;
			mLastZ = z;
			
			if (deltaX > deltaY) {  // We moved horizontally
				mMainActivity.theView.setBackgroundColor(Color.RED);
			} else if (deltaY > deltaX) {  // We moved vertically
				mMainActivity.theView.setBackgroundColor(Color.RED);
			} else {
				if(user_is_home==1)
					mMainActivity.theView.setBackgroundColor(Color.BLUE);
				else
					mMainActivity.theView.setBackgroundColor(Color.BLACK);
			}
		}
	}
}
