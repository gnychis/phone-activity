package com.gnychis.PhoneActivity;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

public class ActivityService extends Service implements SensorEventListener {
	
    public static Interface mMainActivity;
    private final boolean DEBUG=true;

	private float mLastX, mLastY, mLastZ;
	private boolean mInitialized;
	private SensorManager mSensorManager;
    private Sensor mAccelerometer;
    private final float NOISE = (float) 0.25;
    
    static ActivityService _this;
    public static final String PREFS_NAME = "PhoneActivityPrefs";
    SharedPreferences settings;
    SharedPreferences.Editor sEditor;
    
    public boolean mDisableWifiAS;
    public int mScansLeft;
    public final int NUM_SCANS=3;
    
    // Used to keep the location of the home so that we know when the user is home.
    // However, we NEVER retrieve this information from the phone.  Your home location
    // is only kept privately on your phone.
    boolean mNextLocIsHome;
    boolean mHaveHomeLoc;
    float mLongCoord=0;
    float mLatCoord=0;

    WifiManager wifi;
    int user_is_home=0;
    List<ScanResult> scan_result;
    String home_ssid;
    
    LocationManager locationManager;
    LocationListener locationListener;
    
    private static Timer mWifiCheck = new Timer(); 
    
    @Override
    public void onCreate() {
    	super.onCreate();
    	_this=this;
    	    	
    	settings = getSharedPreferences(PREFS_NAME, 0);	// Open the application preference settings
        sEditor = settings.edit();						// Get an editable reference
    	    	
        mInitialized = false;	// Related to initializing the sensors
    	mNextLocIsHome=false;	// The next "location" update would be the user's home location
    	mDisableWifiAS=false;	// Initialize "disable wifi after scan"
    	mScansLeft=0;			// Do not initialize with any scans
    	
    	// Set up listeners to detect movement of the phone
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        
        // If we have already determined the location of the user's home (NEVER shared with us, and only
        // stored locally on your phone), then we read it from the application settings.
        if(!settings.getBoolean("haveHomeLoc", false)) {
        	mHaveHomeLoc=false;
        } else {
        	mHaveHomeLoc=true;
        	mLongCoord = settings.getFloat("longCoord",-1);
        	mLatCoord = settings.getFloat("latCoord",-1);
        }
        
        // Setup a location manager to receive location updates.
        wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        
        // Define a listener that receives location updates.  This is for us to know *when* to monitor
        // the phone activity.  We only want to monitor it when your home.  When you're not home, our
        // entire service is basically disabled.  This information is only stored locally on your phone
        // and NEVER sent back to us.  For us to know how often your phone is "in your home" this is necessary.
        locationListener = new LocationListener() {
            public void onLocationChanged(Location location) {
            	if(mNextLocIsHome) {
            		mHaveHomeLoc=true;
            		sEditor.putBoolean("haveHomeLoc", true);
            		sEditor.putFloat("longCoord", (float)location.getLongitude());
            		sEditor.putFloat("latCoord", (float)location.getLatitude());
            		sEditor.commit();
            		Log.d("BLAH", "Got home location and saved it.");
            	}
            }
            public void onStatusChanged(String provider, int status, Bundle extras) {}
            public void onProviderEnabled(String provider) {}
            public void onProviderDisabled(String provider) {}
        };
          
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 15, 0, locationListener);
        
        // Create a broadcast receiver to listen for wifi scan results. We don't invoke them, we only passively
        // listen whenever they become available.
        registerReceiver(new BroadcastReceiver()
        {
            @Override
            public void onReceive(Context c, Intent intent) 
            {
            	if(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(intent.getAction())) {
            	   Log.d("BLAH", "Got incoming scan result...");
	               if((scan_result = wifi.getScanResults())==null)
	            	   return;
	               
	               home_ssid = settings.getString("homeSSID", null);
	               
	               if(mScansLeft-->0) wifi.startScan();
	               
	               // A flag to disable Wifi after a scan result has come in.
	               if(mDisableWifiAS && mScansLeft==0) {
	            	   wifi.setWifiEnabled(false);
	            	   mDisableWifiAS=false;
	               }
	            	   
	               if(home_ssid==null) // If it is still null, then the user still hasn't set it
	            	   return;
	               
	               int homenet_in_list=0;
	               for(ScanResult result : scan_result)
	            	   if(result.SSID.replaceAll("^\"|\"$", "").equals(home_ssid))
	            		   homenet_in_list=1;
	               
	               Log.d("BLAH", "Home net (" + home_ssid + ") is in list (" + scan_result.size() + ") " + homenet_in_list + "  user is home:" + user_is_home);
	               for(ScanResult result : scan_result)
	            	   if(result.SSID.replaceAll("^\"|\"$", "").equals(home_ssid))
	            		   Log.d("BLAH", "... " + result.SSID.replaceAll("^\"|\"$", ""));
	               
	               // The user is now home, we need to register the sensor listener
	               if(user_is_home==0 && homenet_in_list==1) {
	            	   mSensorManager.registerListener(_this, mAccelerometer , SensorManager.SENSOR_DELAY_NORMAL);
	            	   
	            	   // If we don't have the location of the home saved yet (which is NEVER sent back to us, it's
	            	   // only kept locally on the user's phone), then we save it in the application preferences.
	            	   if(!mHaveHomeLoc) {
	            		   Log.d("BLAH", "Do not yet have home location, trying to lock on to it.  Should be canceling scan");
	            		   mNextLocIsHome=true;
	            		   locationManager.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, locationListener, null);
	            		   mWifiCheck.cancel();
	            	   }
	               }
	               
	               // The user has left home, we need to unregister it
	               if(user_is_home==1 && homenet_in_list==0) {
	            	   mSensorManager.unregisterListener(_this);
	            	   if(mMainActivity!=null && DEBUG) mMainActivity.theView.setBackgroundColor(Color.BLACK);
	               }
	               
	               user_is_home=homenet_in_list;	               
            	}
            }
        }, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));  

        // Start the timer for the wifi check if needed
        if(!mHaveHomeLoc)
        	mWifiCheck.scheduleAtFixedRate(new WifiCheck(), 0, 30000);
    }
    
    // This runs when our Wifi check timer expires, this is once every 15 minutes and *only*
    // used if we do not yet know the location of the home.
    private class WifiCheck extends TimerTask
    { 
        public void run() 
        {
            triggerScan(!wifi.isWifiEnabled());	// Trigger a scan
            Log.d("BLAH", "Triggering a scan, home known: " + mHaveHomeLoc);
        }
    }   
    
    // This triggers a wifi scan.  If the wifi is disabled, it enables it for the duration of
    // a single scan and then disables it again.  This likely only takes 200ms total from the time
    // to enable, scan, and get a result, and then disable it again.  If the wifi is already enabled,
    // then it simply triggers the scan.
    public void triggerScan(boolean disable_after_scan) {
    	mDisableWifiAS=disable_after_scan;
        boolean wifi_enabled=wifi.isWifiEnabled();
        if(!wifi_enabled)
        	wifi.setWifiEnabled(true);
        while(!wifi.isWifiEnabled()) {}
        mScansLeft=NUM_SCANS-1;
        wifi.startScan();
    }
    
    @Override
    public void onDestroy() {
    	super.onDestroy();
    	mSensorManager.unregisterListener(this);
    	locationManager.removeUpdates(locationListener);
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
				if(mMainActivity!=null && DEBUG) mMainActivity.theView.setBackgroundColor(Color.RED);
			} else if (deltaY > deltaX) {  // We moved vertically
				if(mMainActivity!=null && DEBUG) mMainActivity.theView.setBackgroundColor(Color.RED);
			} else {
				if(mMainActivity!=null && DEBUG) mMainActivity.theView.setBackgroundColor(Color.BLACK);
			}
		}
	}
}