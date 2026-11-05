package com.example.sanivend;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.Manifest;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

public class DashboardActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_dashboard);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Notification channels for stock and expiry alerts
        TrayAlerts.createChannels(this);
        
        // Request Notification Permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }

        // Live alerts while the app is running
        TrayAlertMonitor.start(this);

        // Background check every 15 minutes (the shortest Android allows), even when the app is closed.
        // UPDATE replaces the old 1-hour schedule on phones that already have it.
        PeriodicWorkRequest inventoryWorkRequest = new PeriodicWorkRequest.Builder(InventoryWorker.class, 15, TimeUnit.MINUTES)
                .build();

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "InventoryCheck",
                ExistingPeriodicWorkPolicy.UPDATE,
                inventoryWorkRequest
        );

        findViewById(R.id.btn_sales_monitoring).setOnClickListener(v -> {
            startActivity(new Intent(DashboardActivity.this, SalesMonitoringActivity.class));
        });

        findViewById(R.id.btn_inventory_tracking).setOnClickListener(v -> {
            startActivity(new Intent(DashboardActivity.this, InventoryTrackingActivity.class));
        });

        findViewById(R.id.btn_products).setOnClickListener(v -> {
            startActivity(new Intent(DashboardActivity.this, ProductsActivity.class));
        });

        findViewById(R.id.btn_view_graph).setOnClickListener(v -> {
            startActivity(new Intent(DashboardActivity.this, GraphActivity.class));
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 101 && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            TrayAlerts.checkNow(this);   // show any alerts that were waiting for permission
        }
    }
}
