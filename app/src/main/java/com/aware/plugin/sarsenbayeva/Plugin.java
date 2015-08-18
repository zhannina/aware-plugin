package com.aware.plugin.sarsenbayeva;

import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.location.Location;
import android.location.LocationManager;
import android.util.Log;
import android.widget.Toast;

import com.aware.Aware;
import com.aware.Aware_Preferences;
import com.aware.Light;
import com.aware.Magnetometer;
import com.aware.providers.Light_Provider;
import com.aware.providers.Magnetometer_Provider;
import com.aware.utils.Aware_Plugin;

public class Plugin extends Aware_Plugin {

    private static final int TWO_MINUTES = 1000 * 60 * 2;
    public static final String ACTION_AWARE_PLUGIN_SARSENBAYEVA = "ACTION_AWARE_PLUGIN_SARSENBAYEVA";
    public static final String EXTRA_AVG_SARSENBAYEVA = "avg_plugin";
    public static final String EXTRA_OVER_THRESHOLD = "over_threshold";

    private static boolean over_threshold;
    private static int avg = 0;

    private SensorDataReceiver dataReceiver = new SensorDataReceiver();
    private boolean outside_light = false;
    private double light_value = 0;
    private double light_weight = 0.4;

    private double magnetometer_value_x = 0;
    private double magnetometer_value_y = 0;
    private double magnetometer_value_z = 0;
    private double magnetometer_value = 0;
    private boolean outside_magnet = false;
    private double magnet_weight = 0.3;

    private int satellites_count = 0;
    private boolean outside_satellite = false;
    private double gps_weight = 0.3;
    private LocationManager location_manager;
    Location location;

    private boolean outside = false;
    private double probability = 0;
    private double probability_outside = 0;
    private double probability_inside = 0;
    private String outside_inside = "inside";


    // GPS listener to count satellites
    //but it counts all the existing satellites, not the visible ones
//    private final GpsStatus.Listener gps_status_listener = new GpsStatus.Listener() {
//        @Override
//        public void onGpsStatusChanged(int event) {
//            Log.d("listener-zhanna", "inside listener");
//            Log.d("In onGpsStatusChanged event: ", String.valueOf(event));
//            GpsStatus gpsStatus = location_manager.getGpsStatus(null);
//            if(gpsStatus != null) {
//                Iterable<GpsSatellite> satellites = gpsStatus.getSatellites();
//                Iterator<GpsSatellite> sat = satellites.iterator();
//                String lSatellites = null;
//                int i = 0;
//                while (sat.hasNext()) {
//                    GpsSatellite s = (GpsSatellite) sat.next();
//                    if (!s.usedInFix()) {
//                        ++i;
//                    }
//                }
//                satellites_count = i;
//                Toast.makeText(getApplicationContext(), "Satellites: " + satellites_count, Toast.LENGTH_LONG).show();
//                Log.d("Satellites: ", String.valueOf(i));
//            }
//        }
//    };


    @Override
    public void onCreate() {
        super.onCreate();
        if( DEBUG ) Log.d(TAG, "Template plugin running");

        //Initialize our plugin's settings
        if( Aware.getSetting(this, Settings.STATUS_PLUGIN_TEMPLATE).length() == 0 ) {
            Aware.setSetting(this, Settings.STATUS_PLUGIN_TEMPLATE, true);
        }

        //Activate any sensors/plugins you need here
        //...
        Aware.setSetting(this, Aware_Preferences.STATUS_LIGHT, true);
        Aware.setSetting(this, Aware_Preferences.STATUS_MAGNETOMETER, true);
        Aware.setSetting(this, Aware_Preferences.STATUS_LOCATION_GPS, true);

        Intent applySettings = new Intent(Aware.ACTION_AWARE_REFRESH);
        getApplicationContext().sendBroadcast(applySettings);

        Intent refresh = new Intent(Aware.ACTION_AWARE_REFRESH);
        sendBroadcast(refresh);
        IntentFilter filter = new IntentFilter();
        filter.addAction(Light.ACTION_AWARE_LIGHT);
        filter.addAction(Magnetometer.ACTION_AWARE_MAGNETOMETER);

        location_manager = (LocationManager) getApplicationContext().getSystemService(Context.LOCATION_SERVICE);
        location = location_manager.getLastKnownLocation(LocationManager.GPS_PROVIDER);

        if (location != null) {
            satellites_count = (int) location.getExtras().get("satellites");
        }
        else {
            satellites_count = 0;
        }

        registerReceiver(dataReceiver, filter);
//        location_manager.addGpsStatusListener(gps_status_listener);


        //Any active plugin/sensor shares its overall context using broadcasts
        CONTEXT_PRODUCER = new ContextProducer() {
            @Override
            public void onContext() {
                Intent context_data = new Intent();
                context_data.setAction(ACTION_AWARE_PLUGIN_SARSENBAYEVA);
                context_data.putExtra(EXTRA_AVG_SARSENBAYEVA, avg);
                context_data.putExtra(EXTRA_OVER_THRESHOLD, over_threshold);
                sendBroadcast(context_data);
            }
        };

        //To sync data to the server, you'll need to set this variables from your ContentProvider
        //DATABASE_TABLES =
        //TABLES_FIELDS =
        //CONTEXT_URIS = new Uri[]{ }

        //Ask AWARE to apply your settings
        sendBroadcast(new Intent(Aware.ACTION_AWARE_REFRESH));


    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        //This function gets called every 5 minutes by AWARE to make sure this plugin is still running.
        TAG = "Template";
        DEBUG = Aware.getSetting(this, Aware_Preferences.DEBUG_FLAG).equals("true");

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if( DEBUG ) Log.d(TAG, "Template plugin terminated");
        Aware.setSetting(this, Settings.STATUS_PLUGIN_TEMPLATE, false);

        //Deactivate any sensors/plugins you activated here
        //...

        //Ask AWARE to apply your settings
        sendBroadcast(new Intent(Aware.ACTION_AWARE_REFRESH));
//        location_manager.removeGpsStatusListener(gps_status_listener);
    }



    private class SensorDataReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {

            // initialise cursors to read sensor data
            Cursor light_cursor = context.getContentResolver().query(Light_Provider.Light_Data.CONTENT_URI, null, null, null,
                    Light_Provider.Light_Data.TIMESTAMP + " DESC LIMIT 1");

            Cursor magnetometer_cursor = context.getContentResolver().query(Magnetometer_Provider.Magnetometer_Data.CONTENT_URI,
                    null, null, null, Magnetometer_Provider.Magnetometer_Data.TIMESTAMP + " DESC LIMIT 1");


            if (intent.getAction().equals(Light.ACTION_AWARE_LIGHT)) {
                // read light sensor value
                if( light_cursor != null && light_cursor.moveToFirst() ) {
                    light_value = light_cursor.getDouble(light_cursor.getColumnIndex(Light_Provider.Light_Data.LIGHT_LUX));
                    // detect if the light intensity is strong or weak
                    if( light_value < 250 ) {
                        outside_light = false;
                    } else {
                        outside_light = true;
                    }
                }
            }

            // read magnetometer values
            if (intent.getAction().equals(Magnetometer.ACTION_AWARE_MAGNETOMETER)){
                if (magnetometer_cursor != null && magnetometer_cursor.moveToFirst()){
                    magnetometer_value_x = magnetometer_cursor.getDouble(magnetometer_cursor.getColumnIndex(Magnetometer_Provider.Magnetometer_Data.VALUES_0));
                    magnetometer_value_y = magnetometer_cursor.getDouble(magnetometer_cursor.getColumnIndex(Magnetometer_Provider.Magnetometer_Data.VALUES_1));
                    magnetometer_value_z = magnetometer_cursor.getDouble(magnetometer_cursor.getColumnIndex(Magnetometer_Provider.Magnetometer_Data.VALUES_2));

                    // calculate the magnetometer value from 3 readings
                    magnetometer_value = Math.sqrt(magnetometer_value_x * magnetometer_value_x +
                            magnetometer_value_y * magnetometer_value_y +
                            magnetometer_value_z * magnetometer_value_z);

                    magnetometer_value = Math.round(magnetometer_value * 100.00)/100.0;
                    // decide if the magnetometer is measuring fields outside or inside the building
                    // outside the building magnetic fields are weak
                    if (magnetometer_value < 60){
                        outside_magnet = true;
                    }
                    else {
                        outside_magnet = false;

                    }
                }
            }

            if (satellites_count >= 6){
                outside_satellite = true;
            }
            else {
                outside_satellite = false;
            }

//            Log.d("outside_light", ""+outside_light);
//            Log.d("outside_magnet", ""+outside_magnet);
//            Log.d("outside_satellite", ""+outside_satellite);

            //Algorithm

            if (outside_light==true && outside_magnet==true && outside_satellite==true){
                outside = true;
                probability_outside = light_weight + magnet_weight + gps_weight;
                outside_inside = "outside";
                probability = probability_outside;
            }
            else if (outside_light==false && outside_magnet==true && outside_satellite==true){
                outside = true;
                probability_outside = magnet_weight + gps_weight;
                outside_inside = "outside";
                probability = probability_outside;
            }
            else if (outside_light==true && outside_magnet==true && outside_satellite==false){
                outside = true;
                probability_outside = light_weight + magnet_weight;
                outside_inside = "outside";
                probability = probability_outside;
            }
            else if (outside_light==true && outside_satellite==true && outside_magnet==false){
                outside = true;
                probability_outside = light_weight + gps_weight;
                outside_inside = "outside";
                probability = probability_outside;
            }
            else if (outside_light==true && outside_magnet==false && outside_satellite==false){
                outside = false;
                probability_inside = 1 - light_weight;
                outside_inside = "inside";
                probability = probability_inside;
            }
            else if (outside_light==false && outside_magnet == false && outside_satellite==true){
                outside = false;
                probability_inside = 1 - gps_weight;
                outside_inside = "inside";
                probability = probability_inside;
            }
            else if (outside_light==false && outside_magnet==true && outside_satellite==false){
                outside = false;
                probability_inside = 1 - magnet_weight;
                outside_inside = "inside";
                probability = probability_inside;
            }
            else if (outside_magnet==false && outside_satellite==false && outside_light==false){
                outside = false;
                probability_inside = light_weight + magnet_weight + gps_weight;
                outside_inside = "inside";
                probability = probability_inside;
            }


//            Log.d("probability_inside", ""+probability_inside);
//            Log.d("probability_outside", ""+probability_outside);
//            Log.d("probability", ""+probability);
//            Log.d("outside_inside", ""+outside_inside);
//            Toast.makeText(getApplicationContext(), "Satellites: " + satellites_count, Toast.LENGTH_LONG).show();
//            Log.d("Satellites: ", String.valueOf(satellites_count));

            // insert data into the database
            ContentValues data = new ContentValues();
            data.put(Provider.PluginData.TIMESTAMP, System.currentTimeMillis());
            data.put(Provider.PluginData.DEVICE_ID, Aware_Preferences.DEVICE_ID);
            data.put(Provider.PluginData.LIGHT_VALUE, light_value);
            data.put(Provider.PluginData.MAGNET_VALUE, magnetometer_value);
            data.put(Provider.PluginData.SATELLITES, satellites_count);
            data.put(Provider.PluginData.OUTSIDE_INSIDE, outside_inside);
            data.put(Provider.PluginData.PROBABILITY, probability);
            getContentResolver().insert(Provider.PluginData.CONTENT_URI, data);
            CONTEXT_PRODUCER.onContext();


            if( light_cursor != null && !light_cursor.isClosed() ) {
                light_cursor.close();
            }

            if( magnetometer_cursor != null && !magnetometer_cursor.isClosed() ) {
                magnetometer_cursor.close();
            }

        }
    }
}