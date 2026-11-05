package com.example.sanivend;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Live alerts: while the app is running, listens to the trays of every machine
 * paired to the signed-in account, so a sale that empties a tray (or a new
 * expiry date) shows a notification within seconds.
 * When the app is closed, InventoryWorker checks every 15 minutes instead.
 */
public final class TrayAlertMonitor {

    private static final String TAG = "TrayAlertMonitor";

    private static Context app;
    private static String uid;
    private static DatabaseReference myDevicesRef;
    private static ValueEventListener myDevicesListener;
    /** machine ID → its trays listener */
    private static final Map<String, ValueEventListener> trayListeners = new HashMap<>();
    /** machine ID → display name */
    private static final Map<String, String> names = new HashMap<>();

    private TrayAlertMonitor() {}

    /** Started after sign-in; calling it again for the same account does nothing. */
    public static synchronized void start(Context context) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        if (user.getUid().equals(uid) && myDevicesListener != null) return;
        stop();                                       // a different account signed in

        app = context.getApplicationContext();
        uid = user.getUid();
        myDevicesRef = FirebaseDatabase.getInstance().getReference("users").child(uid).child("devices");
        myDevicesListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                syncMachines(snapshot);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.w(TAG, "Stopped: " + error.getMessage());
            }
        };
        myDevicesRef.addValueEventListener(myDevicesListener);
    }

    /** Called on log out. */
    public static synchronized void stop() {
        if (myDevicesRef != null && myDevicesListener != null) {
            myDevicesRef.removeEventListener(myDevicesListener);
        }
        for (Map.Entry<String, ValueEventListener> e : trayListeners.entrySet()) {
            traysRef(e.getKey()).removeEventListener(e.getValue());
        }
        trayListeners.clear();
        names.clear();
        myDevicesRef = null;
        myDevicesListener = null;
        uid = null;
    }

    private static synchronized void syncMachines(DataSnapshot mine) {
        Set<String> current = new HashSet<>();
        for (DataSnapshot d : mine.getChildren()) {
            String id = d.getKey();
            current.add(id);
            names.put(id, TrayAlerts.machineName(id, d.child("name").getValue()));
            if (!trayListeners.containsKey(id)) follow(id);
        }
        for (String id : new HashSet<>(trayListeners.keySet())) {
            if (!current.contains(id)) {               // machine removed from this account
                traysRef(id).removeEventListener(trayListeners.remove(id));
                names.remove(id);
            }
        }
    }

    private static void follow(final String id) {
        ValueEventListener l = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String name = names.containsKey(id) ? names.get(id) : TrayAlerts.machineName(id, null);
                TrayAlerts.check(app, id, name, snapshot);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.w(TAG, "Trays of " + id + " stopped: " + error.getMessage());
            }
        };
        trayListeners.put(id, l);
        traysRef(id).addValueEventListener(l);
    }

    private static DatabaseReference traysRef(String id) {
        return DeviceSession.deviceRef(id).child("trays");
    }
}
