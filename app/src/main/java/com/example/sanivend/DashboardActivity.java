package com.example.sanivend;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.Manifest;

import androidx.activity.EdgeToEdge;
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

        // Initialize Notification Channel
        InventoryNotificationUtils.INSTANCE.createNotificationChannel(this);
        
        // Request Notification Permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }

        // Schedule Inventory Check Worker (Every 1 hour for testing, can be 12h for production)
        PeriodicWorkRequest inventoryWorkRequest = new PeriodicWorkRequest.Builder(InventoryWorker.class, 1, TimeUnit.HOURS)
                .build();

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "InventoryCheck",
                ExistingPeriodicWorkPolicy.KEEP,
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
}
