package com.example.sanivend;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

/**
 * Remembers which machine the user opened, and points every screen at that
 * machine's data: /devices/{machineId}/trays, /orders, /totals, /notifications.
 */
public final class DeviceSession {

    private static final String PREFS = "device_session";
    private static final String KEY_ID = "device_id";
    private static final String KEY_NAME = "device_name";

    /** Intent extras used by notifications to open a specific machine. */
    public static final String EXTRA_ID = "device_id";
    public static final String EXTRA_NAME = "device_name";

    private DeviceSession() {}

    public static void select(Context context, String deviceId, String name) {
        prefs(context).edit().putString(KEY_ID, deviceId).putString(KEY_NAME, name).apply();
    }

    public static void clear(Context context) {
        prefs(context).edit().clear().apply();
    }

    /** The open machine's ID, or null if none is selected. */
    public static String id(Context context) {
        return prefs(context).getString(KEY_ID, null);
    }

    /** The open machine's display name (falls back to its ID). */
    public static String name(Context context) {
        String n = prefs(context).getString(KEY_NAME, null);
        return (n == null || n.isEmpty()) ? id(context) : n;
    }

    /** /devices/{id} */
    public static DatabaseReference deviceRef(String deviceId) {
        return FirebaseDatabase.getInstance().getReference("devices").child(deviceId);
    }

    /** /devices/{open machine}/{child}, e.g. ref(this, "trays"). Call requireDevice() first. */
    public static DatabaseReference ref(Context context, String child) {
        String id = id(context);
        if (id == null) throw new IllegalStateException("No machine selected");
        return deviceRef(id).child(child);
    }

    /**
     * For screens that show one machine's data. If none is selected (for example
     * after the user logged out), sends the user to My Machines and returns false.
     */
    public static boolean requireDevice(Activity activity) {
        Intent in = activity.getIntent();
        String fromIntent = in != null ? in.getStringExtra(EXTRA_ID) : null;
        if (fromIntent != null) select(activity, fromIntent, in.getStringExtra(EXTRA_NAME));
        if (id(activity) != null) return true;
        activity.startActivity(new Intent(activity, DeviceListActivity.class));
        activity.finish();
        return false;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
