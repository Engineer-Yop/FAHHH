package com.example.sanivend;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
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
 * Live alerts: listens to the trays and orders of every machine paired to the
 * signed-in account, so a sale, a tray that runs low or empty, a refill, a new
 * expiry date or an empty change tube shows a notification within seconds.
 *
 * It keeps running in the background inside MonitorService (the small
 * "Monitoring your machines" notification). If Android stops it anyway,
 * InventoryWorker still checks every 15 minutes.
 */
public final class TrayAlertMonitor {

    private static final String TAG = "TrayAlertMonitor";

    private static Context app;
    private static String uid;
    private static DatabaseReference myDevicesRef;
    private static ValueEventListener myDevicesListener;
    /** machine ID → its trays listener */
    private static final Map<String, ValueEventListener> trayListeners = new HashMap<>();
    /** machine ID → its change-coins listener (IR sensors on the P1/P5/P10 tubes) */
    private static final Map<String, ValueEventListener> coinListeners = new HashMap<>();
    /** machine ID → its new-order listener */
    private static final Map<String, ChildEventListener> orderListeners = new HashMap<>();
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
        for (Map.Entry<String, ValueEventListener> e : coinListeners.entrySet()) {
            coinsRef(e.getKey()).removeEventListener(e.getValue());
        }
        coinListeners.clear();
        for (Map.Entry<String, ChildEventListener> e : orderListeners.entrySet()) {
            ordersRef(e.getKey()).removeEventListener(e.getValue());
        }
        orderListeners.clear();
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
                ValueEventListener c = coinListeners.remove(id);
                if (c != null) coinsRef(id).removeEventListener(c);
                ChildEventListener o = orderListeners.remove(id);
                if (o != null) ordersRef(id).removeEventListener(o);
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

        ValueEventListener c = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String name = names.containsKey(id) ? names.get(id) : TrayAlerts.machineName(id, null);
                CoinAlerts.check(app, id, name, snapshot);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.w(TAG, "Coins of " + id + " stopped: " + error.getMessage());
            }
        };
        coinListeners.put(id, c);
        coinsRef(id).addValueEventListener(c);

        followOrders(id);
    }

    /**
     * New orders: first remember the newest existing order (so old ones never pop up),
     * then listen for every order_N added after it.
     */
    private static void followOrders(final String id) {
        final ChildEventListener l = new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, String previousChildName) {
                String name = names.containsKey(id) ? names.get(id) : TrayAlerts.machineName(id, null);
                OrderAlerts.onOrder(app, id, name, snapshot);
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot snapshot, String previousChildName) {
                // The machine writes an order in one go; a later change is not a new sale.
            }

            @Override
            public void onChildRemoved(@NonNull DataSnapshot snapshot) {}

            @Override
            public void onChildMoved(@NonNull DataSnapshot snapshot, String previousChildName) {}

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.w(TAG, "Orders of " + id + " stopped: " + error.getMessage());
            }
        };
        orderListeners.put(id, l);
        ordersRef(id).get().addOnCompleteListener(task -> {
            synchronized (TrayAlertMonitor.class) {
                if (orderListeners.get(id) != l) return;          // machine removed or signed out meanwhile
                if (task.isSuccessful() && task.getResult() != null) {
                    String name = names.containsKey(id) ? names.get(id) : TrayAlerts.machineName(id, null);
                    // Already seen before: show what came in while the app wasn't listening
                    OrderAlerts.checkAll(app, id, name, task.getResult());
                }
                ordersRef(id).addChildEventListener(l);
            }
        });
    }

    private static DatabaseReference ordersRef(String id) {
        return DeviceSession.deviceRef(id).child("orders");
    }

    private static DatabaseReference coinsRef(String id) {
        return DeviceSession.deviceRef(id).child("coins");
    }

    private static DatabaseReference traysRef(String id) {
        return DeviceSession.deviceRef(id).child("trays");
    }
}
