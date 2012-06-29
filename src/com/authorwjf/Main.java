package com.authorwjf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import android.app.Activity;
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
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Toast;

import com.gnychis.PhoneActivity.R;

public class Main extends Activity implements SensorEventListener {
	
	private float mLastX, mLastY, mLastZ;
	private boolean mInitialized;
	private SensorManager mSensorManager;
    private Sensor mAccelerometer;
    private final float NOISE = (float) 0.25;
    WifiManager wifi;
    Spinner netlist;
    Spinner agelist;
    View theView;
    List<ScanResult> scan_result;
    int user_is_home=0;
    String home_ssid;
	 
    Comparator<Object> netsort = new Comparator<Object>() {
    	public int compare(Object arg0, Object arg1) {
    		if(((WifiConfiguration)arg0).priority < ((WifiConfiguration)arg1).priority)
    			return 1;
    		else if( ((WifiConfiguration)arg0).priority > ((WifiConfiguration)arg1).priority)
    			return -1;
    		else
    			return 0;
    	}
      };
    
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        theView = findViewById(R.id.main_id);
        mInitialized = false;
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mSensorManager.registerListener(this, mAccelerometer , SensorManager.SENSOR_DELAY_NORMAL);
        
        // Get the list of configured networks and add them to the spinner
        wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        List<WifiConfiguration> cfgNets = wifi.getConfiguredNetworks();
        netlist = (Spinner) findViewById(R.id.network_list);
        ArrayList<String> spinnerArray = new ArrayList<String>();
        Collections.sort(cfgNets,netsort);
        for (WifiConfiguration config: cfgNets)
        	spinnerArray.add(config.SSID.replaceAll("^\"|\"$", ""));
        ArrayAdapter<String> spinnerArrayAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, spinnerArray);
        spinnerArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        netlist.setAdapter(spinnerArrayAdapter);
        
        // Setup the age list
        agelist = (Spinner) findViewById(R.id.age_group);
        ArrayAdapter<CharSequence> ageAdapter = ArrayAdapter.createFromResource(this, R.array.age_ranges, android.R.layout.simple_spinner_item);
        ageAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        agelist.setAdapter(ageAdapter);
        
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
    }
    
    public void clickedFinished(View v) {
    	
    	// Get the string representations of the selected items
    	home_ssid = (String) netlist.getSelectedItem();
    	String selected_age = (String) agelist.getSelectedItem();

    }

    protected void onResume() {
        super.onResume();
        mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
    }

    protected void onPause() {
        super.onPause();
        mSensorManager.unregisterListener(this);
    }

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		// can be safely ignored for this demo
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
				theView.setBackgroundColor(Color.RED);
			} else if (deltaY > deltaX) {  // We moved vertically
				theView.setBackgroundColor(Color.RED);
			} else {
				if(user_is_home==1)
					theView.setBackgroundColor(Color.BLUE);
				else
					theView.setBackgroundColor(Color.BLACK);
			}
		}
	}
}