package com.gnychis.PhoneActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import com.gnychis.PhoneActivity.R;

public class Interface extends Activity {
	
    Spinner netlist;
    Spinner agelist;
    View theView;
    String home_ssid;
    WifiManager wifi;
	 
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
    }

    protected void onResume() {
        super.onResume();
    }

    protected void onPause() {
        super.onPause();
        
    }

}