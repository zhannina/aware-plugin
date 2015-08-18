package com.aware.plugin.sarsenbayeva;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import com.aware.providers.Light_Provider;
import com.aware.providers.Magnetometer_Provider;
import com.aware.ui.Stream_UI;
import com.aware.utils.IContextCard;

import org.w3c.dom.Text;

public class ContextCard implements IContextCard {

    //Set how often your card needs to refresh if the stream is visible (in milliseconds)
    private int refresh_interval = 1 * 2000; //1 second = 1000 milliseconds

    //DEMO: we are demo'ing a counter incrementing in real-time
    private int counter = 0;

    private Handler uiRefresher = new Handler(Looper.getMainLooper());
    private Runnable uiChanger = new Runnable() {
        @Override
        // Not needed for the plugin but keeping it since it was provided as an example
        public void run() {
            counter++;

            //Modify card's content here once it's initialized
            if( card != null ) {
                //DEMO display the counter value
                counter_txt.setText(""+counter);
            }

            //Reset timer and schedule the next card refresh
            uiRefresher.postDelayed(uiChanger, refresh_interval);
        }
    };

    //Empty constructor used to instantiate this card
    public ContextCard(){};

    //You may use sContext on uiChanger to do queries to databases, etc.
    private Context sContext;

    //Declare here all the UI elements you'll be accessing
    private View card;
    private TextView counter_txt;
    private TextView light_txt;
    private TextView magnet_txt;
    private TextView outside_inside_txt;
    private TextView probability_txt;
    private TextView satellites_txt;

    private static double light_value = 0;
    private static double magnet_value = 0;
    private static double probability = 0;
    private static String outside_inside = "";
    private static double satellites = 0;


    //Used to load your context card
    private LayoutInflater sInflater;

    @Override
    public View getContextCard(Context context) {
        sContext = context;

        //Tell Android that you'll monitor the stream statuses
        IntentFilter filter = new IntentFilter();
        filter.addAction(Stream_UI.ACTION_AWARE_STREAM_OPEN);
        filter.addAction(Stream_UI.ACTION_AWARE_STREAM_CLOSED);
        context.registerReceiver(streamObs, filter);

        //Load card information to memory
        sInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        card = sInflater.inflate(R.layout.card, null);

        //Initialize UI elements from the card
        //DEMO only
        counter_txt = (TextView) card.findViewById(R.id.counter);
        light_txt = (TextView) card.findViewById(R.id.light_sensor_value);
        magnet_txt = (TextView) card.findViewById(R.id.magnetometer_value);
        satellites_txt = (TextView) card.findViewById(R.id.satellites_number);
        outside_inside_txt = (TextView) card.findViewById(R.id.outside_inside_value);
        probability_txt = (TextView) card.findViewById(R.id.probability_value);


        //Begin refresh cycle
        uiRefresher.postDelayed(uiChanger, refresh_interval);

        // cursor to access plugin database
        Cursor cursor = context.getContentResolver().query(Provider.PluginData.CONTENT_URI, null, null, null,
                Provider.PluginData.TIMESTAMP + " DESC LIMIT 1");

        // read sensor values from the database and set the values to the TextView elements of the context card
        if (cursor != null && cursor.moveToFirst()) {
            light_value = cursor.getDouble(cursor.getColumnIndex(Provider.PluginData.LIGHT_VALUE));
            light_txt.setText(light_value + " lux");

            magnet_value = cursor.getDouble(cursor.getColumnIndex(Provider.PluginData.MAGNET_VALUE));
            magnet_txt.setText(magnet_value + " ts");

            outside_inside = cursor.getString(cursor.getColumnIndex(Provider.PluginData.OUTSIDE_INSIDE));
            outside_inside_txt.setText(outside_inside);

            probability = cursor.getDouble(cursor.getColumnIndex(Provider.PluginData.PROBABILITY));
            probability_txt.setText(probability + "");

            satellites = cursor.getInt(cursor.getColumnIndex(Provider.PluginData.SATELLITES));
            satellites_txt.setText(satellites+"");
        }



        //Return the card to AWARE/apps
        if (cursor != null && !cursor.isClosed()) {
            cursor.close();
        }


        return card;
    }

    //This is a BroadcastReceiver that keeps track of stream status. Used to stop the refresh when user leaves the stream and restart again otherwise
    private StreamObs streamObs = new StreamObs();
    public class StreamObs extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if( intent.getAction().equals(Stream_UI.ACTION_AWARE_STREAM_OPEN) ) {
                //start refreshing when user enters the stream
                uiRefresher.postDelayed(uiChanger, refresh_interval);

                //DEMO only, reset the counter every time the user opens the stream
                counter = 0;
            }
            if( intent.getAction().equals(Stream_UI.ACTION_AWARE_STREAM_CLOSED) ) {
                //stop refreshing when user leaves the stream
                uiRefresher.removeCallbacks(uiChanger);
                uiRefresher.removeCallbacksAndMessages(null);
            }
        }
    }
}
