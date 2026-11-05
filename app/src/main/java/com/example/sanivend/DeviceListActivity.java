package com.example.sanivend;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * My Machines: the first screen after signing in.
 * Lists the machines paired to this account (/users/{uid}/devices), shows if each
 * is online, and pairs new ones with the Machine ID + pairing code from the LCD.
 */
public class DeviceListActivity extends AppCompatActivity {

    private FirebaseUser user;
    private DatabaseReference root;
    private LinearLayout listView;

    /** machine ID → name, in the order they were added */
    private final Map<String, String> devices = new LinkedHashMap<>();
    /** machine ID → last time it checked in (ms) */
    private final Map<String, Long> lastSeen = new HashMap<>();

    private DatabaseReference myDevicesRef;
    private ValueEventListener myDevicesListener;
    private final Map<String, ValueEventListener> seenListeners = new HashMap<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresher = new Runnable() {
        @Override
        public void run() {
            render();                                   // keeps "last seen 3 min ago" current
            handler.postDelayed(this, 30_000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || !user.isEmailVerified()) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_device_list);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left + dp(24), bars.top + dp(24), bars.right + dp(24), bars.bottom + dp(24));
            return insets;
        });

        root = FirebaseDatabase.getInstance().getReference();
        listView = findViewById(R.id.device_list);
        ((TextView) findViewById(R.id.account_text)).setText("Signed in as " + user.getEmail());

        Button add = findViewById(R.id.add_device_button);
        add.setOnClickListener(v -> showAddDialog());
        findViewById(R.id.logout_text).setOnClickListener(v -> confirmLogout());

        askNotificationPermission();
        TrayAlerts.createChannels(this);
        TrayAlertMonitor.start(this);                  // live stock/expiry alerts for all my machines
        TrayAlerts.scheduleBackgroundChecks(this);     // and every 15 min when the app is closed

        listenToMyDevices();
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.removeCallbacks(refresher);
        handler.postDelayed(refresher, 30_000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(refresher);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (myDevicesRef != null && myDevicesListener != null) {
            myDevicesRef.removeEventListener(myDevicesListener);
        }
        for (Map.Entry<String, ValueEventListener> e : seenListeners.entrySet()) {
            seenRef(e.getKey()).removeEventListener(e.getValue());
        }
        seenListeners.clear();
    }

    // ── Loading ────────────────────────────────────

    private void listenToMyDevices() {
        myDevicesRef = root.child("users").child(user.getUid()).child("devices");
        myDevicesListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                devices.clear();
                for (DataSnapshot d : snapshot.getChildren()) {
                    Object name = d.child("name").getValue();
                    devices.put(d.getKey(), name != null ? String.valueOf(name) : "");
                }
                // Follow each machine's "last seen" for the Online label
                for (String id : new ArrayList<>(seenListeners.keySet())) {
                    if (!devices.containsKey(id)) {
                        seenRef(id).removeEventListener(seenListeners.remove(id));
                        lastSeen.remove(id);
                    }
                }
                for (String id : devices.keySet()) {
                    if (!seenListeners.containsKey(id)) followLastSeen(id);
                }
                render();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(DeviceListActivity.this, "Can't load machines: " + error.getMessage(),
                        Toast.LENGTH_LONG).show();
            }
        };
        myDevicesRef.addValueEventListener(myDevicesListener);
    }

    private DatabaseReference seenRef(String id) {
        return DeviceSession.deviceRef(id).child("info").child("last_seen");
    }

    private void followLastSeen(final String id) {
        ValueEventListener l = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Object v = snapshot.getValue();
                lastSeen.put(id, v instanceof Number ? ((Number) v).longValue() : 0L);
                render();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        seenListeners.put(id, l);
        seenRef(id).addValueEventListener(l);
    }

    // ── List ───────────────────────────────────────

    private void render() {
        if (listView == null) return;
        listView.removeAllViews();
        if (devices.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No machines yet.\n\nTap \"Add Machine\" and enter the Machine ID and "
                    + "pairing code shown on the machine's screen.");
            empty.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
            empty.setTextSize(16);
            empty.setGravity(android.view.Gravity.CENTER);
            empty.setPadding(dp(8), dp(32), dp(8), dp(8));
            listView.addView(empty);
            return;
        }
        long now = System.currentTimeMillis();
        for (Map.Entry<String, String> e : devices.entrySet()) {
            final String id = e.getKey();
            final String name = displayName(id, e.getValue());
            View card = getLayoutInflater().inflate(R.layout.item_device, listView, false);
            ((TextView) card.findViewById(R.id.device_name)).setText(name);
            ((TextView) card.findViewById(R.id.device_id)).setText("ID: " + id);

            long seen = lastSeen.containsKey(id) ? lastSeen.get(id) : 0L;
            TextView status = card.findViewById(R.id.device_status);
            status.setText("● " + DeviceIds.statusLabel(seen, now));
            status.setTextColor(DeviceIds.isOnline(seen, now) ? Color.parseColor("#1B873F")
                                                              : Color.parseColor("#7A7A7A"));

            card.setOnClickListener(v -> openDevice(id, name));
            card.setOnLongClickListener(v -> {
                confirmRemove(id, name);
                return true;
            });
            listView.addView(card);
        }
    }

    private static String displayName(String id, String name) {
        return (name == null || name.trim().isEmpty()) ? "SaniVend " + id : name.trim();
    }

    private void openDevice(String id, String name) {
        DeviceSession.select(this, id, name);
        startActivity(new Intent(this, DashboardActivity.class));
    }

    // ── Add machine ────────────────────────────────

    private void showAddDialog() {
        final View form = getLayoutInflater().inflate(R.layout.dialog_add_device, null);
        final EditText idInput = form.findViewById(R.id.input_device_id);
        final EditText codeInput = form.findViewById(R.id.input_pair_code);
        final EditText nameInput = form.findViewById(R.id.input_device_name);

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Add Machine")
                .setView(form)
                .setPositiveButton("Add", null)          // set below so errors keep the dialog open
                .setNegativeButton("Cancel", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String id = DeviceIds.normalize(idInput.getText().toString());
            String code = codeInput.getText().toString().trim();
            String name = nameInput.getText().toString().trim();
            if (id == null) {
                idInput.setError("Use the ID on the machine's screen, e.g. SV-A1B2C3");
                return;
            }
            if (!DeviceIds.isValidCode(code)) {
                codeInput.setError("The pairing code has 6 digits");
                return;
            }
            v.setEnabled(false);
            pair(id, code, name, dialog, v);
        }));
        dialog.show();
    }

    private void pair(final String id, final String code, final String name,
                      final AlertDialog dialog, final View addButton) {
        final String uid = user.getUid();
        final DatabaseReference machine = DeviceSession.deviceRef(id);
        // Read only the small "info" and "owner" entries, not the machine's orders
        machine.child("info").get().addOnSuccessListener(info -> {
            if (!info.exists()) {
                fail(addButton, "No machine with ID " + id + " was found. Make sure it is switched on "
                        + "and connected to WiFi, then try again.");
                return;
            }
            if (!DeviceIds.codeMatches(code, info.child("pair_code").getValue())) {
                fail(addButton, "Wrong pairing code. Restart the machine to see the code again.");
                return;
            }
            machine.child("owner").get().addOnSuccessListener(ownerSnap -> {
                Object owner = ownerSnap.getValue();
                if (!DeviceIds.canClaim(owner == null ? null : String.valueOf(owner), uid)) {
                    fail(addButton, "This machine is already registered to another account.");
                    return;
                }
                Map<String, Object> update = new HashMap<>();
                update.put("devices/" + id + "/owner", uid);
                update.put("users/" + uid + "/devices/" + id + "/name", name);
                update.put("users/" + uid + "/devices/" + id + "/added", ServerValue.TIMESTAMP);
                root.updateChildren(update)
                        .addOnSuccessListener(x -> {
                            dialog.dismiss();
                            Toast.makeText(this, displayName(id, name) + " added.", Toast.LENGTH_LONG).show();
                        })
                        .addOnFailureListener(e -> fail(addButton, "Couldn't add the machine: " + e.getMessage()));
            }).addOnFailureListener(e -> fail(addButton, "Couldn't reach the server: " + e.getMessage()));
        }).addOnFailureListener(e -> fail(addButton, "Couldn't reach the server: " + e.getMessage()));
    }

    private void fail(View addButton, String message) {
        addButton.setEnabled(true);
        new AlertDialog.Builder(this).setMessage(message).setPositiveButton("OK", null).show();
    }

    // ── Remove machine / log out ───────────────────

    private void confirmRemove(final String id, final String name) {
        new AlertDialog.Builder(this)
                .setTitle("Remove " + name + "?")
                .setMessage("It will disappear from your list and stop sending you alerts. "
                        + "You can add it again later with its pairing code.")
                .setPositiveButton("Remove", (d, w) -> {
                    Map<String, Object> update = new HashMap<>();
                    update.put("users/" + user.getUid() + "/devices/" + id, null);
                    update.put("devices/" + id + "/owner", null);
                    root.updateChildren(update);
                    if (id.equals(DeviceSession.id(this))) DeviceSession.clear(this);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmLogout() {
        new AlertDialog.Builder(this)
                .setMessage("Log out of " + user.getEmail() + "?")
                .setPositiveButton("Log out", (d, w) -> logout(this))
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** Signs out, stops alerts for this account and returns to the login screen. */
    static void logout(android.app.Activity activity) {
        TrayAlertMonitor.stop();
        NotificationManagerCompat.from(activity).cancelAll();
        DeviceSession.clear(activity);
        FirebaseAuth.getInstance().signOut();
        Intent i = new Intent(activity, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity.startActivity(i);
        activity.finish();
    }

    // ── Notifications permission (Android 13+) ─────

    private void askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                   != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 101 && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            TrayAlerts.checkNow(this);
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
