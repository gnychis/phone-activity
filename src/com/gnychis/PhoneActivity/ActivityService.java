package com.gnychis.PhoneActivity;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicHeader;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.protocol.HTTP;
import org.json.JSONObject;

import android.app.PendingIntent;
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
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

public class ActivityService extends Service implements SensorEventListener {
	
    public static Interface mMainActivity;
    static ActivityService _this;
    PowerManager mPowerManager;
    
    PendingIntent mPendingIntent;
    Intent mIntent;

    public final int LOCATION_TOLERANCE=100;		// in meters
    public final int LOCATION_TIMER_RATE=900000;	// in milliseconds (15 minutes)
    private final boolean DEBUG=true;
    
    private final String DATA_FILENAME="pa_data.json";
    private FileOutputStream data_ostream;

	private float mLastX, mLastY, mLastZ;
	private boolean mInitialized;
	private SensorManager mSensorManager;
    private Sensor mAccelerometer;
    private final float NOISE = (float) 0.25;
    
    SharedPreferences settings;
    SharedPreferences.Editor sEditor;
    
    public boolean mDisableWifiAS;
    public int mScansLeft;
    public final int NUM_SCANS=4;
    
    // Used to keep the location of the home so that we know when the user is home.
    // However, we NEVER retrieve this information from the phone.  Your home location
    // is only kept privately on your phone.
    boolean mNextLocIsHome;
    boolean mHaveHomeLoc;
    Location mHomeLoc;

    WifiManager wifi;
    boolean mPhoneIsInTheHome;
    List<ScanResult> scan_result;
    String home_ssid;
    
    LocationManager locationManager;
    LocationListener locationListener;
    
    private static Timer mLocationTimer = new Timer(); 
    
    Map<String,Object> mLastState;
    
    @Override
    public void onCreate() {
    	super.onCreate();
    	_this=this;
    	    	    	    	
    	settings = getSharedPreferences(Interface.PREFS_NAME, 0);	// Open the application preference settings
        sEditor = settings.edit();	// Get an editable reference
    	    	
        mInitialized = false;	// Related to initializing the sensors
    	mNextLocIsHome=false;	// The next "location" update would be the user's home location
    	mDisableWifiAS=false;	// Initialize "disable wifi after scan"
    	mScansLeft=0;			// Do not initialize with any scans
    	mPhoneIsInTheHome=false;		// To detect when the user is home
    	
    	// Set up listeners to detect movement of the phone
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        
        // If we have already determined the location of the user's home (NEVER shared with us, and only
        // stored locally on your phone), then we read it from the application settings.
        mHomeLoc = new Location("Home");
        if(!settings.getBoolean("haveHomeLoc", false)) {
        	mHaveHomeLoc=false;
        } else {
        	mHaveHomeLoc=true;
        	mHomeLoc.setLongitude(settings.getFloat("longCoord",-1));
        	mHomeLoc.setLatitude(settings.getFloat("latCoord",-1));
        }
        
        // Setup a location manager to receive location updates.
        wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        
        // Create a broadcast receiver to listen for wifi scan results. We don't invoke them, we only passively
        // listen whenever they become available.
        registerReceiver(new BroadcastReceiver()
        {
            @Override
            public void onReceive(Context c, Intent intent) 
            {
            	if(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(intent.getAction())) {
	               if((scan_result = wifi.getScanResults())==null)
	            	   return;
	               
	               home_ssid = settings.getString("homeSSID", null);
	               
	               if(--mScansLeft>0) {		// If there are more scans left....
	            	   wifi.startScan();	// scan again.
	               } else {								// There are no scans left.
		               if(mDisableWifiAS) {				// If the user had Wifi disabled, disable it again
		            	   wifi.setWifiEnabled(false);	// Disable it.
		            	   mDisableWifiAS=false;		// Reset the wifi disable state.
		               }
	               }
	            	   
	               if(home_ssid==null) // If it is still null, then the user still hasn't set it
	            	   return;
	               
	               for(ScanResult result : scan_result)
	            	   if(result.SSID.replaceAll("^\"|\"$", "").equals(home_ssid))
	            		   home();	// Their home network is in the list, so they must be home        
            	}
            }
        }, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)); 
        
        registerReceiver(new BroadcastReceiver()
        {
        	@Override
        	public void onReceive(Context context, Intent intent)
        	{
        		cleanup();
        	}
        }, new IntentFilter(Intent.ACTION_SHUTDOWN));
        
        mIntent=new Intent("LOC_OBTAINED");
        mPendingIntent=PendingIntent.getBroadcast(this,0,mIntent,0);
        registerReceiver(new BroadcastReceiver(){
            public void onReceive(Context arg0, Intent arg1)
            {
            	if(arg1.getAction().equals(LocationManager.KEY_LOCATION_CHANGED)) {
	            	Bundle bundle=arg1.getExtras();
	                Location location=(Location)bundle.get(LocationManager.KEY_LOCATION_CHANGED);
	              	onLocationChanged(location);
            	}
            }
           },new IntentFilter("LOC_OBTAINED"));

        mLocationTimer.scheduleAtFixedRate(new LocationCheck(), 0, LOCATION_TIMER_RATE);
        mPowerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        
        try {
        	data_ostream = openFileOutput(DATA_FILENAME, Context.MODE_PRIVATE | Context.MODE_APPEND);
    		JSONObject jstate = new JSONObject();
    		jstate.put("type","state");
    		jstate.put("state","on");
			data_ostream.write(jstate.toString().getBytes());
			data_ostream.write("\n".getBytes()); 
        } catch (Exception e) { }
    }
    
    // The user's phone is in the home, either based on localization information or the fact that their
    // Wifi access point is within range. If we don't have the location of the home saved yet 
    // (which is NEVER sent back to us, it's only kept locally on the user's phone), 
    // then we save it in the application preferences.
    private void home() {
    	Log.d("BLAH", "Marked as being home");
    	if(!mPhoneIsInTheHome) mSensorManager.registerListener(_this, mAccelerometer , SensorManager.SENSOR_DELAY_NORMAL);
    	mPhoneIsInTheHome=true;
    	
    	if(!mHaveHomeLoc) {
 		   mNextLocIsHome=true;
 		   locationManager.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, mPendingIntent);
    	}
    }
    
    // The user's phone is not in the home based on localization information.
    private void notHome() {
    	Log.d("BLAH", "Marked as no longer being home");
    	if(mPhoneIsInTheHome) mSensorManager.unregisterListener(_this);
    	if(mMainActivity!=null && DEBUG) mMainActivity.theView.setBackgroundColor(Color.BLACK);
    	mPhoneIsInTheHome=false;
    }
    
    // This runs when our Wifi check timer expires, this is once every 15 minutes and *only*
    // used if we do not yet know the location of the home.
    private class LocationCheck extends TimerTask
    { 
        public void run() 
        {
        	if(!mHaveHomeLoc) {
        		Log.d("BLAH", "Triggering wifi scan");
        		triggerScan(!wifi.isWifiEnabled());	// Trigger a scan
        	} else {
        		Log.d("BLAH", "Triggering location check");
        		locationManager.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, mPendingIntent);
        	}
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
        mScansLeft=NUM_SCANS;
        wifi.startScan();
    }
	
	// We have an incoming location update.  If we have not yet saved the user's home location (WHICH
	// IS NEVER SHARED WITH US - it is only stored locally on your phone), then we save it.  Otherwise
	// we check the distance of the current location with the home location to detect if the user's
	// phone is in their home.
    public void onLocationChanged(Location location) {
    	Log.d("BLAH", "Location accuracy is: " + location.getAccuracy());
    	if(mNextLocIsHome) {
    		mHaveHomeLoc=true;
    		sEditor.putBoolean("haveHomeLoc", true);
    		sEditor.putFloat("longCoord", (float)location.getLongitude());
    		sEditor.putFloat("latCoord", (float)location.getLatitude());
    		mHomeLoc=location;
    		mNextLocIsHome=false;
    		sEditor.putString("lastUpdate", (new Date()).toString());
    		sEditor.commit();
    		Log.d("BLAH", "Recorded home location as: (" + location.getLatitude() + "," + location.getLongitude() + ")");
    	}
    	
    	if(mHaveHomeLoc) {
    		Log.d("BLAH", "Home Location: (" + mHomeLoc.getLatitude() + "," + mHomeLoc.getLongitude() + ")");
    		Log.d("BLAH", "Current Location: (" + location.getLatitude() + "," + location.getLongitude() + ")");
        	Log.d("BLAH", "Distance is: " + mHomeLoc.distanceTo(location));
    		if(mHomeLoc.distanceTo(location)<=LOCATION_TOLERANCE)
    			home();
    		else
    			notHome();
    	}
    }    

    // We have an update on the sensor data.  We check that the movement of the phone
    // exceeds a threshold, and if so we consider the phone as actively moving, otherwise
    // we consider it to be stable (not moving).
	@Override
	public void onSensorChanged(SensorEvent event) {
		boolean movement=false;
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
				movement=true;
			} else if (deltaY > deltaX) {  // We moved vertically
				if(mMainActivity!=null && DEBUG) mMainActivity.theView.setBackgroundColor(Color.RED);
				movement=true;
			} else {
				if(mMainActivity!=null && DEBUG) mMainActivity.theView.setBackgroundColor(Color.BLACK);
				movement=false;
			}
			
			// Store data about the phone's state
			try {
				// Store the current state
				final Intent batteryIntent = getApplicationContext().registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
				boolean isCharging = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1) == BatteryManager.BATTERY_STATUS_CHARGING;
				Map<String,Object> state = new HashMap<String, Object>();
				state.put("type", "activity");
				state.put("clientID", settings.getInt("randClientID",-1));
				state.put("movement", movement);
				state.put("wifiOn", wifi.isWifiEnabled());
				state.put("screen", mPowerManager.isScreenOn());
				state.put("charging", isCharging);
				if(mLastState==null || !state.equals(mLastState)) {
					mLastState = new HashMap<String,Object>(state);
					state.put("time", new Date());
					state.put("millis", System.currentTimeMillis());
					state.put("wifiStrength", wifi.getConnectionInfo().getRssi());
					data_ostream.write(new JSONObject(state).toString().getBytes());
					data_ostream.write("\n".getBytes());
				}
				
				// If it's been 6 hours since the last update, do it now
				String lu = settings.getString("lastUpdate", null);
				if(lu!=null) {
					@SuppressWarnings("deprecation")
					Date lastUpdate = new Date(lu);
					Date currTime = new Date();
					long timeDiff = TimeUnit.MILLISECONDS.toSeconds(currTime.getTime() - lastUpdate.getTime());
					if(timeDiff>120) {
						sEditor.putString("lastUpdate", (new Date()).toString());
						sEditor.commit();
						sendUpdate();
					}
				}
			} catch(Exception e) {
				Log.e("BLAH", "Exception on time: " + e.toString());
			}	
		}
	}
	
	public void sendUpdate() {
        Thread t = new Thread(){
        public void run() {
                Looper.prepare(); // For Preparing Message Pool for the child Thread
                HttpClient client = new DefaultHttpClient();
                HttpConnectionParams.setConnectionTimeout(client.getParams(), 10000); //Timeout Limit
                HttpResponse response;
                JSONObject json = new JSONObject();
                try{
                    HttpPost post = new HttpPost("http://moo.cmcl.cs.cmu.edu/pastudy/userdata.php");
                    
                    // We only retrieve your random user ID (for uniqueness) age range, and where your phone has been...
                    // Note that your home network name is never sent to us.
                    json.put("clientID", settings.getInt("randClientID",-1));
                    FileInputStream finput = openFileInput(DATA_FILENAME);
                    InputStreamReader inputStreamReader = new InputStreamReader(finput);
                    BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = bufferedReader.readLine()) != null) {
                        sb.append(line);
                    } 
                   json.put("data", sb.toString());
                    
                    StringEntity se = new StringEntity( json.toString());  
                    se.setContentType(new BasicHeader(HTTP.CONTENT_TYPE, "application/json"));
                    post.setEntity(se);
                    response = client.execute(post);
                    if(response!=null) {
                        InputStream in = response.getEntity().getContent();
                        String a = Interface.convertStreamToString(in);
                    }
                } catch(Exception e){}
                Looper.loop(); //Loop in the message queue
            }
        };
        t.start();  
	}

    @Override
    public void onDestroy() {
    	super.onDestroy();
    	cleanup();
    }
    
    private void cleanup() {
    	try {
    		JSONObject jstate = new JSONObject();
    		jstate.put("type","state");
    		jstate.put("state","off");
			data_ostream.write(jstate.toString().getBytes());
			data_ostream.write("\n".getBytes());   		
    		data_ostream.close();
    	} catch(Exception e) {}
    	mSensorManager.unregisterListener(this);
    }
    
    @Override
    public IBinder onBind(Intent intent) { return null; }
	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {}
	public static void setMainActivity(Interface activity) { mMainActivity = activity; }

}
