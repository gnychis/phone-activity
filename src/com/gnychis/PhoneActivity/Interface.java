package com.gnychis.PhoneActivity;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicHeader;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.protocol.HTTP;
import org.json.JSONObject;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.LocationManager;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Looper;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.Spinner;

public class Interface extends Activity {
	
    Spinner netlist, agelist;
    View theView;
    String home_ssid;
    WifiManager wifi;
    SharedPreferences.Editor sEditor;
    SharedPreferences settings;
    CheckBox kitchen, livingRoom, bedroom, bathroom;
    
    public static final String PREFS_NAME = "PhoneActivityPrefs";

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        theView = findViewById(R.id.main_id);
        
        // Check if the user already selected some of this information in the application's
        // preferences.  If so, we put their information back so they don't reselect it.
        settings = getSharedPreferences(PREFS_NAME, 0);
        sEditor = settings.edit();
        int sRandClientID = settings.getInt("randClientID",-1);
        String sHomeSSID = settings.getString("homeSSID", null);
        int sAgeRange = settings.getInt("ageRange", -1);
        
        // So that you remain anonymous, but I can have a unique ID for your data,
        // I generate a random integer and use it as your ID then store it.
        if(sRandClientID==-1) {
        	sRandClientID = new Random().nextInt(Integer.MAX_VALUE);
        	sEditor.putInt("randClientID", sRandClientID);
        	sEditor.commit();
        }
        
        // Create the service that runs in the background and captures the activity data
        ActivityService.setMainActivity(this);
        startService(new Intent(this, ActivityService.class));
        
        // Gets the list of networks on their phone and puts them in to a drop-down menu
        // for them to select their home network from.
        wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        
        // If Wifi is disabled, we need to enable it just to retrieve this list and do a scan
        boolean wifi_enabled=wifi.isWifiEnabled();
        if(!wifi_enabled)
        	wifi.setWifiEnabled(true);
        while(!wifi.isWifiEnabled()) {}
        
        List<WifiConfiguration> cfgNets = wifi.getConfiguredNetworks();
        netlist = (Spinner) findViewById(R.id.network_list);
        ArrayList<String> spinnerArray = new ArrayList<String>();
        Collections.sort(cfgNets,netsort);
        for (WifiConfiguration config: cfgNets)
        	spinnerArray.add(config.SSID.replaceAll("^\"|\"$", ""));
        ArrayAdapter<String> spinnerArrayAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, spinnerArray);
        spinnerArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        netlist.setAdapter(spinnerArrayAdapter);
        
        // Setup the age-range list and put it in a drop-down menu for them to select.
        agelist = (Spinner) findViewById(R.id.age_group);
        ArrayAdapter<CharSequence> ageAdapter = ArrayAdapter.createFromResource(this, R.array.age_ranges, android.R.layout.simple_spinner_item);
        ageAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        agelist.setAdapter(ageAdapter);
        
        // If the user already selected their age range and home network, bring them up so that
        // they don't think they have to reselect them.
        if(sHomeSSID!=null)
        	netlist.setSelection(spinnerArray.indexOf(sHomeSSID));    
        if(sAgeRange!=-1)
        	agelist.setSelection(sAgeRange);
        
        // Set the checkboxes back to what the user had
        ((CheckBox) findViewById(R.id.kitchen)).setChecked((settings.getInt("kitchen",0)==0) ? false : true);
        ((CheckBox) findViewById(R.id.bedroom)).setChecked((settings.getInt("bedroom",0)==0) ? false : true);
        ((CheckBox) findViewById(R.id.livingRoom)).setChecked((settings.getInt("livingRoom",0)==0) ? false : true);
        ((CheckBox) findViewById(R.id.bathroom)).setChecked((settings.getInt("bathroom",0)==0) ? false : true);
        ((CheckBox) findViewById(R.id.everywhere)).setChecked((settings.getInt("everywhere",0)==0) ? false : true);
        
        // If the user puts a "checkmark" in the Everywhere option, we check all other boxes.
        ((CheckBox) findViewById(R.id.everywhere)).setOnCheckedChangeListener(new OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if ( isChecked ) {
                	 ((CheckBox) findViewById(R.id.kitchen)).setChecked(true);
                	 ((CheckBox) findViewById(R.id.bedroom)).setChecked(true);
                	 ((CheckBox) findViewById(R.id.livingRoom)).setChecked(true);
                	 ((CheckBox) findViewById(R.id.bathroom)).setChecked(true);
                }
            }
        });
        
        wifi.setWifiEnabled(wifi_enabled);  // If the user had wifi disabled, re-disable it
    }
    
    // When the user clicks finished, we save some information locally.  The home network name is
    // only saved locally (so that our application can work), and it is never shared back with us.
    public void clickedFinished(View v) {
    	home_ssid = (String) netlist.getSelectedItem();
    	int selected_age_id = (int) agelist.getSelectedItemId();
    	if(home_ssid!=settings.getString("homeSSID", null)) {  // if the user changed their SSID, we need to reset some things
    		sEditor.putBoolean("haveHomeLoc", false);
    		sEditor.remove("longCoord");
    		sEditor.remove("latCoord");
    		sEditor.remove("lastUpdate");
    	}
    	sEditor.putString("homeSSID", home_ssid);
    	sEditor.putInt("ageRange", selected_age_id);
    	sEditor.putInt("kitchen", (((CheckBox) findViewById(R.id.kitchen)).isChecked()==true) ? 1 : 0);
    	sEditor.putInt("bedroom", (((CheckBox) findViewById(R.id.bedroom)).isChecked()==true) ? 1 : 0);
    	sEditor.putInt("livingRoom", (((CheckBox) findViewById(R.id.livingRoom)).isChecked()==true) ? 1 : 0);
    	sEditor.putInt("bathroom", (((CheckBox) findViewById(R.id.bathroom)).isChecked()==true) ? 1 : 0);
    	sEditor.putInt("everywhere", (((CheckBox) findViewById(R.id.everywhere)).isChecked()==true) ? 1 : 0);
    	sEditor.commit();
    	sendUserData();
    	wifi.startScan();
    	finish();
    }
    
    // This sends us your optional "survey" like results.  It does so anonymously by accompanying them
    // with a unique but random ID.  Note that your home network name or location are NOT transmitted
    // back to us.  
    protected void sendUserData() {
        Thread t = new Thread(){
        public void run() {
                Looper.prepare(); // For Preparing Message Pool for the child Thread
                HttpClient client = new DefaultHttpClient();
                HttpConnectionParams.setConnectionTimeout(client.getParams(), 10000); //Timeout Limit
                HttpResponse response;
                JSONObject json = new JSONObject();
                try{
                    HttpPost post = new HttpPost("http://moo.cmcl.cs.cmu.edu/pastudy/survey.php");
                    
                    // We only retrieve your random user ID (for uniqueness) age range, and where your phone has been...
                    // Note that your home network name is never sent to us.
                    json.put("clientID", settings.getInt("randClientID",-1));
                    json.put("ageRange", agelist.getSelectedItemId());
                    json.put("kitchen", (((CheckBox) findViewById(R.id.kitchen)).isChecked()==true) ? 1 : 0);
                    json.put("bedroom", (((CheckBox) findViewById(R.id.bedroom)).isChecked()==true) ? 1 : 0);
                    json.put("livingRoom", (((CheckBox) findViewById(R.id.livingRoom)).isChecked()==true) ? 1 : 0);
                    json.put("bathroom", (((CheckBox) findViewById(R.id.bathroom)).isChecked()==true) ? 1 : 0);
                    json.put("everywhere", (((CheckBox) findViewById(R.id.everywhere)).isChecked()==true) ? 1 : 0);    
                     
                    
                    StringEntity se = new StringEntity( json.toString());  
                    se.setContentType(new BasicHeader(HTTP.CONTENT_TYPE, "application/json"));
                    post.setEntity(se);
                    response = client.execute(post);
                    if(response!=null) {
                        InputStream in = response.getEntity().getContent();
                        String a = convertStreamToString(in);
                    }
                } catch(Exception e){}
                Looper.loop(); //Loop in the message queue
            }
        };
        t.start();      
    }

	 
    // This is a comparator to sort the networks on your phone, so that your home network is
    // more likely to be at the top of the list.
    Comparator<Object> netsort = new Comparator<Object>() {
    	public int compare(Object arg0, Object arg1) {
    		if(((WifiConfiguration)arg0).priority > ((WifiConfiguration)arg1).priority)
    			return 1;
    		else if( ((WifiConfiguration)arg0).priority < ((WifiConfiguration)arg1).priority)
    			return -1;
    		else
    			return 0;
    	}
      };
    
      // For converting an incoming input stream to a string
      public static String convertStreamToString(InputStream is) {
    	    BufferedReader reader = new BufferedReader(new InputStreamReader(is));
    	    StringBuilder sb = new StringBuilder();
    	    String line = null;
    	    try {
    	        while ((line = reader.readLine()) != null) {
    	            sb.append(line + "\n");
    	        }
    	    } catch (IOException e) {
    	        e.printStackTrace();
    	    } finally {
    	        try {
    	            is.close();
    	        } catch (IOException e) {
    	            e.printStackTrace();
    	        }
    	    }
    	    return sb.toString();
    	}

    protected void onResume() {
        super.onResume();
    }

    protected void onPause() {
        super.onPause();
        
    }

}