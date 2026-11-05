package com.example.sanivend;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Home screen for one machine: greeting, the machine's status and today's sales,
 * and the four feature buttons. Reached from My Machines (or from a notification).
 */
public class DashboardActivity extends BaseActivity {

    private DatabaseReference seenRef, todayRef;
    private ValueEventListener seenListener, todayListener;
    private long lastSeen = 0;
    private TextView statusView;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresher = new Runnable() {
        @Override
        public void run() {
            showStatus();
            handler.postDelayed(this, 30_000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }
        if (!DeviceSession.requireDevice(this)) return;

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_dashboard);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        String deviceId = DeviceSession.id(this);
        String name = DeviceSession.name(this);
        ((TextView) findViewById(R.id.machine_name)).setText(
                name.equals(deviceId) ? "SaniVend " + deviceId
                        : name.contains(deviceId) ? name : name + "  (" + deviceId + ")");
        statusView = findViewById(R.id.machine_status);

        showGreeting(user);
        followMachine(deviceId);

        // Alerts keep running for all machines on this account
        TrayAlerts.createChannels(this);
        TrayAlertMonitor.start(this);
        TrayAlerts.scheduleBackgroundChecks(this);

        findViewById(R.id.switch_machine).setOnClickListener(v -> {
            if (isTaskRoot()) startActivity(new Intent(this, DeviceListActivity.class));
            finish();
        });
        findViewById(R.id.logout).setOnClickListener(v -> new AlertDialog.Builder(this)
                .setMessage("Log out of " + user.getEmail() + "?")
                .setPositiveButton("Log out", (d, w) -> DeviceListActivity.logout(this))
                .setNegativeButton("Cancel", null)
                .show());

        findViewById(R.id.btn_sales_monitoring).setOnClickListener(v ->
                startActivity(new Intent(DashboardActivity.this, SalesMonitoringActivity.class)));
        findViewById(R.id.btn_inventory_tracking).setOnClickListener(v ->
                startActivity(new Intent(DashboardActivity.this, InventoryTrackingActivity.class)));
        findViewById(R.id.btn_products).setOnClickListener(v ->
                startActivity(new Intent(DashboardActivity.this, ProductsActivity.class)));
        findViewById(R.id.btn_view_graph).setOnClickListener(v ->
                startActivity(new Intent(DashboardActivity.this, GraphActivity.class)));
    }

    /** "Hello, geof!" from the profile; falls back to the part of the email before @. */
    private void showGreeting(FirebaseUser user) {
        final TextView hello = findViewById(R.id.hello_text);
        String email = user.getEmail() != null ? user.getEmail() : "";
        hello.setText("Hello, " + (email.contains("@") ? email.substring(0, email.indexOf('@')) : "there") + "!");
        FirebaseDatabase.getInstance().getReference("users").child(user.getUid()).child("username").get()
                .addOnSuccessListener(s -> {
                    Object u = s.getValue();
                    if (u != null && !String.valueOf(u).trim().isEmpty()) {
                        hello.setText("Hello, " + u + "!");
                    }
                });
    }

    /** Online status (machine checks in every minute) and today's sales total. */
    private void followMachine(String deviceId) {
        DatabaseReference machine = DeviceSession.deviceRef(deviceId);

        seenRef = machine.child("info").child("last_seen");
        seenListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot s) {
                Object v = s.getValue();
                lastSeen = v instanceof Number ? ((Number) v).longValue() : 0;
                showStatus();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError e) {}
        };
        seenRef.addValueEventListener(seenListener);

        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        final TextView todayView = findViewById(R.id.today_sales);
        todayRef = machine.child("totals").child("daily").child(today);
        todayListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot s) {
                int total = TrayEditor.toInt(s.child("total").getValue());
                int orders = TrayEditor.toInt(s.child("orders").getValue());
                todayView.setText(orders == 0 ? "Today: no sales yet"
                        : "Today: ₱" + total + " from " + orders + (orders == 1 ? " item" : " items"));
            }

            @Override
            public void onCancelled(@NonNull DatabaseError e) {}
        };
        todayRef.addValueEventListener(todayListener);
    }

    private void showStatus() {
        if (statusView == null) return;
        long now = System.currentTimeMillis();
        statusView.setText("● " + DeviceIds.statusLabel(lastSeen, now));
        statusView.setTextColor(DeviceIds.isOnline(lastSeen, now) ? Color.parseColor("#1B873F")
                                                                  : Color.parseColor("#7A7A7A"));
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
        if (seenRef != null && seenListener != null) seenRef.removeEventListener(seenListener);
        if (todayRef != null && todayListener != null) todayRef.removeEventListener(todayListener);
    }
}
