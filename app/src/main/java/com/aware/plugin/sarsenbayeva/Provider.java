package com.aware.plugin.sarsenbayeva;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.os.Environment;
import android.provider.BaseColumns;
import android.util.Log;

import com.aware.Aware;
import com.aware.utils.DatabaseHelper;

import java.util.HashMap;

/**
 * Created by tatoshka87 on 19/03/2015.
 */
public class Provider extends ContentProvider {

    public static final int DATABASE_VERSION = 1;
    public static String AUTHORITY = "com.aware.plugin.sarsenbayeva.provider";
    public static final String DATABASE_NAME = Environment.getExternalStorageDirectory() + "/AWARE/plugin_sarsenbayeva.db";

    private static final int PLUGIN_SARSENBAYEVA = 1;
    private static final int PLUGIN_SARSENBAYEVA_ID = 2;

    public static final String[] DATABASE_TABLES = {
            "plugin_sarsenbayeva",
    };

    public static final String[] TABLES_FIELDS = {
        PluginData._ID + " integer primary key autoincrement," +
            PluginData.TIMESTAMP + " real default 0," +
            PluginData.DEVICE_ID + " text default ''," +
            PluginData.LIGHT_VALUE + " real default 0," +
            PluginData.MAGNET_VALUE + " real default 0," +
            PluginData.SATELLITES + " integer default 0," +
            PluginData.OUTSIDE_INSIDE + " text default ''," +
            PluginData.PROBABILITY + " real default 0," +
            "UNIQUE("+PluginData.TIMESTAMP+","+PluginData.DEVICE_ID+")"


    };

    public static final class PluginData implements BaseColumns {
        private PluginData(){};

        public static final Uri CONTENT_URI = Uri.parse("content://"+AUTHORITY+"/plugin_sarsenbayeva");
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.aware.plugin.sarsenbayeva";
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.aware.plugin.sarsenbayeva";

        public static final String _ID = "_id";
        public static final String TIMESTAMP = "timestamp";
        public static final String DEVICE_ID = "device_id";
        public static final String LIGHT_VALUE = "light_value";
        public static final String MAGNET_VALUE = "magnet_value";
        public static final String SATELLITES = "gps_accuracy";
        public static final String OUTSIDE_INSIDE = "outside_inside";
        public static final String PROBABILITY = "probability";
    }

    private static UriMatcher URIMatcher;
    private static HashMap<String, String> databaseMap;
    private static DatabaseHelper databaseHelper;
    private static SQLiteDatabase database;

    @Override
    public boolean onCreate() {

        //AUTHORITY = getContext().getPackageName() + ".provider.plugin";

        URIMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        URIMatcher.addURI(AUTHORITY, DATABASE_TABLES[0], PLUGIN_SARSENBAYEVA);
        URIMatcher.addURI(AUTHORITY, DATABASE_TABLES[0]+"/#", PLUGIN_SARSENBAYEVA_ID);

        databaseMap = new HashMap<String, String>();
        databaseMap.put(PluginData._ID, PluginData._ID);
        databaseMap.put(PluginData.TIMESTAMP, PluginData.TIMESTAMP);
        databaseMap.put(PluginData.DEVICE_ID, PluginData.DEVICE_ID);
        databaseMap.put(PluginData.LIGHT_VALUE, PluginData.LIGHT_VALUE);
        databaseMap.put(PluginData.MAGNET_VALUE, PluginData.MAGNET_VALUE);
        databaseMap.put(PluginData.SATELLITES, PluginData.SATELLITES);
        databaseMap.put(PluginData.OUTSIDE_INSIDE, PluginData.OUTSIDE_INSIDE);
        databaseMap.put(PluginData.PROBABILITY, PluginData.PROBABILITY);

        return true;
    }

    private boolean initialiseDB(){
        if (databaseHelper == null) {
            databaseHelper = new DatabaseHelper( getContext(), DATABASE_NAME, null, DATABASE_VERSION, DATABASE_TABLES, TABLES_FIELDS );
        }
        if( databaseHelper != null && ( database == null || ! database.isOpen() )) {
            database = databaseHelper.getWritableDatabase();
        }
        return( database != null && databaseHelper != null);
    }

    @Override
    public Cursor query(Uri uri, String[] strings, String s, String[] strings2, String s2) {
        if( ! initialiseDB() ) {
            Log.w(AUTHORITY,"Database unavailable...");
            return null;
        }

        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        switch (URIMatcher.match(uri)) {
            case PLUGIN_SARSENBAYEVA:
                qb.setTables(DATABASE_TABLES[0]);
                qb.setProjectionMap(databaseMap);
                break;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
        try {
            Cursor c = qb.query(database, strings, s, strings2,
                    null, null, s2);
            c.setNotificationUri(getContext().getContentResolver(), uri);
            return c;
        } catch (IllegalStateException e) {
            if (Aware.DEBUG)
                Log.e(Aware.TAG, e.getMessage());

            return null;
        }
    }

    @Override
    public String getType(Uri uri) {

        switch (URIMatcher.match(uri)) {
            case PLUGIN_SARSENBAYEVA:
                return PluginData.CONTENT_TYPE;
            case PLUGIN_SARSENBAYEVA_ID:
                return PluginData.CONTENT_ITEM_TYPE;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
    }

    @Override
    public Uri insert(Uri uri, ContentValues contentValues) {

        if( ! initialiseDB() ) {
            Log.w(AUTHORITY,"Database unavailable...");
            return null;
        }

        ContentValues values = (contentValues != null) ? new ContentValues(
                contentValues) : new ContentValues();

        switch (URIMatcher.match(uri)) {
            case PLUGIN_SARSENBAYEVA:
                long column_id = database.insert(DATABASE_TABLES[0], PluginData.DEVICE_ID, values);

                if (column_id > 0) {
                    Uri new_uri = ContentUris.withAppendedId(
                            PluginData.CONTENT_URI,
                            column_id);
                    getContext().getContentResolver().notifyChange(new_uri,
                            null);
                    return new_uri;
                }
                throw new SQLException("Failed to insert row into " + uri);
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
    }

    @Override
    public int delete(Uri uri, String s, String[] strings) {
        if( ! initialiseDB() ) {
            Log.w(AUTHORITY, "Database unavailable...");
            return 0;
        }

        int count = 0;
        switch (URIMatcher.match(uri)) {
            case PLUGIN_SARSENBAYEVA:
                count = database.delete(DATABASE_TABLES[0], s, strings);
                break;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }

    @Override
    public int update(Uri uri, ContentValues contentValues, String s, String[] strings) {
        if( ! initialiseDB() ) {
            Log.w(AUTHORITY,"Database unavailable...");
            return 0;
        }

        int count = 0;
        switch (URIMatcher.match(uri)) {
            case PLUGIN_SARSENBAYEVA:
                count = database.update(DATABASE_TABLES[0], contentValues, s,
                        strings);
                break;
            default:

                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }
}
