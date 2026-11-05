package com.example.sanivend;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

/**
 * Live alerts: listens to /trays while the app is running, so a sale that
 * empties a tray (or a new expiry date) shows a notification within seconds.
 * When the app is closed, InventoryWorker checks every 15 minutes instead.
 */
public final class TrayAlertMonitor {

    private static ValueEventListener listener;

    private TrayAlertMonitor() {}

    /** Started from the Dashboard; calling it again does nothing. */
    public static synchronized void start(Context context) {
        if (listener != null) return;
        final Context app = context.getApplicationContext();
        listener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                TrayAlerts.check(app, snapshot);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.w("TrayAlertMonitor", "Stopped: " + error.getMessage());
                synchronized (TrayAlertMonitor.class) {
                    listener = null;
                }
            }
        };
        FirebaseDatabase.getInstance().getReference("trays").addValueEventListener(listener);
    }
}
