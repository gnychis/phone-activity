package com.gnychis.PhoneActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

public class Interface extends Activity {
	
    Spinner netlist;
    Spinner agelist;
    View theView;
    String home_ssid;
    WifiManager wifi;
    
    public static final String PREFS_NAME = "PhoneActivityPrefs";
	 
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
        
        // There are 3 pieces of information that are saved about you.  A random integer
        // so that I can have a unique ID for each participant.  Then, your home network
        // name which is NEVER phoned home to me, but your age range is.
        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
        SharedPreferences.Editor sEditor = settings.edit();
        int sRandClientID = settings.getInt("randClientID",-1);
        String sHomeSSID = settings.getString("homeSSID", null);
        String sAgeRange = settings.getString("ageRange", null);
        
        // So that you remain anonymous, but I can have a unique ID for your data,
        // I generate a random integer and use it as your ID then store it.
        if(sRandClientID==-1) {
        	int randInt = new Random().nextInt(Integer.MAX_VALUE);
        	sEditor.putInt("randClientID", randInt);
        	sEditor.commit();
        	sRandClientID = randInt;
        	Log.d(getClass().getSimpleName(), "Setting the user ID to " + Integer.toString(sRandClientID));
        } else {
        	Log.d(getClass().getSimpleName(), "The user ID is " + Integer.toString(sRandClientID));
        }
        
        // Create the service that runs in the background and captures the activity data
        ActivityService.setMainActivity(this);
        startService(new Intent(this, ActivityService.class));
        
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
    }
    
    public void clickedFinished(View v) {
    	// Get the string representations of the selected items
    	home_ssid = (String) netlist.getSelectedItem();
    	String selected_age = (String) agelist.getSelectedItem();
    	finish();
    }

    protected void onResume() {
        super.onResume();
    }

    protected void onPause() {
        super.onPause();
        
    }

}