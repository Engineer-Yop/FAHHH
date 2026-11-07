package com.example.sanivend;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.FirebaseDatabase;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Stock and expiry notifications for the 8 trays.
 *
 * For every machine paired to the signed-in account, reads
 * /devices/{id}/trays/tray_N/stock (written by the ESP32 after each sale) and
 * /devices/{id}/trays/tray_N/expiry (set in the app), then uses AlertRules to decide.
 *
 * Each tray of each machine has its own stock notification and its own expiry
 * notification; the machine's name is shown in the notification header.
 * A notification is only sent again when something changes (one fewer item,
 * one fewer day left, a new date), and it is cleared when the tray is back to OK.
 *
 * Runs from two places:
 *   - TrayAlertMonitor: live, the moment Firebase changes, while the app is running
 *   - InventoryWorker:  every 15 minutes in the background, even when the app is closed
 */
public final class TrayAlerts {

    private static final String TAG = "TrayAlerts";

    public static final String CHANNEL_STOCK  = "stock_alerts";
    public static final String CHANNEL_EXPIRY = "expiry_alerts";

    private static final String PREFS = "tray_alerts";

    /** Tray number → product name, same order as the app's Inventory screen. */
    static final String[] PRODUCTS = {
            "",
            "Sisters Sanitary Napkins",   // 1
            "Charmee Sanitary Napkins",   // 2
            "Kleenfant Wet Wipes",        // 3
            "Giggles Wet Wipes",          // 4
            "Femme Facial Tissues",       // 5
            "Blest Facial Tissues",       // 6
            "Hygienix Hand Sanitizer",    // 7
            "Alcosafe Hand Sanitizer"     // 8
    };
    static final int TRAY_COUNT = 8;
    static final int DEFAULT_CAPACITY = 7;

    private TrayAlerts() {}

    /** Safe to call many times; Android keeps one copy of each channel. */
    public static void createChannels(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm == null) return;

        NotificationChannel stock = new NotificationChannel(CHANNEL_STOCK,
                "Stock alerts", NotificationManager.IMPORTANCE_HIGH);
        stock.setDescription("Low stock and out-of-stock trays");
        nm.createNotificationChannel(stock);

        NotificationChannel expiry = new NotificationChannel(CHANNEL_EXPIRY,
                "Expiry alerts", NotificationManager.IMPORTANCE_HIGH);
        expiry.setDescription("Products expiring within 7 days or already expired");
        nm.createNotificationChannel(expiry);

        CoinAlerts.createChannel(context);
    }

    /** Background check every 15 minutes (the shortest Android allows), even when the app is closed. */
    public static void scheduleBackgroundChecks(Context context) {
        PeriodicWorkRequest request =
                new PeriodicWorkRequest.Builder(InventoryWorker.class, 15, TimeUnit.MINUTES).build();
        // UPDATE replaces the old 1-hour schedule on phones that already have it
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "InventoryCheck", ExistingPeriodicWorkPolicy.UPDATE, request);
    }

    /** Reads every paired machine's trays once and checks them (used after permission is granted). */
    public static void checkNow(Context context) {
        final Context app = context.getApplicationContext();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        FirebaseDatabase.getInstance().getReference("users").child(user.getUid()).child("devices").get()
                .addOnSuccessListener(mine -> {
                    for (DataSnapshot d : mine.getChildren()) {
                        final String id = d.getKey();
                        final String name = machineName(id, d.child("name").getValue());
                        DeviceSession.deviceRef(id).child("trays").get()
                                .addOnSuccessListener(trays -> check(app, id, name, trays));
                        DeviceSession.deviceRef(id).child("coins").get()
                                .addOnSuccessListener(coins -> CoinAlerts.check(app, id, name, coins));
                    }
                })
                .addOnFailureListener(e -> Log.w(TAG, "Couldn't read machines: " + e.getMessage()));
    }

    /** "SaniVend SV-A1B2C3" when the user gave the machine no name. */
    public static String machineName(String deviceId, Object name) {
        String n = name == null ? "" : String.valueOf(name).trim();
        return n.isEmpty() ? "SaniVend " + deviceId : n;
    }

    /** Checks every tray in one machine's /trays snapshot and sends or clears notifications. */
    public static synchronized void check(Context context, String deviceId, String deviceName,
                                          DataSnapshot trays) {
        createChannels(context);
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());

        for (int tray = 1; tray <= TRAY_COUNT; tray++) {
            DataSnapshot t = trays.child("tray_" + tray);
            String product = PRODUCTS[tray];

            Object stockValue = t.child("stock").getValue();
            Object capValue = t.child("capacity").getValue();
            int capacity = capValue != null ? TrayEditor.toInt(capValue) : DEFAULT_CAPACITY;
            Integer stock = stockValue != null ? TrayEditor.toInt(stockValue) : null;
            apply(context, deviceId, deviceName,
                    AlertRules.stock(tray, product, stock, capacity), AlertRules.Kind.STOCK, tray);

            Object expiryValue = t.child("expiry").getValue();
            String expiry = expiryValue != null ? String.valueOf(expiryValue) : null;
            apply(context, deviceId, deviceName,
                    AlertRules.expiry(tray, product, expiry, today), AlertRules.Kind.EXPIRY, tray);
        }
    }

    // ── Sending / clearing ────────────────────────────

    private static void apply(Context context, String deviceId, String deviceName,
                              AlertRules.Alert alert, AlertRules.Kind kind, int tray) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String key = deviceId + "|" + kind.name() + "_" + tray;
        String newState = (alert == null) ? "OK" : alert.state;
        String oldState = prefs.getString(key, "OK");
        if (newState.equals(oldState)) return;                 // nothing new to say

        if (alert == null || !alert.needsNotification()) {
            NotificationManagerCompat.from(context).cancel(notificationId(deviceId, kind, tray));
            prefs.edit().putString(key, "OK").apply();
            return;
        }
        // Only remember it as "sent" if it really was, so it shows once permission is given
        if (send(context, deviceId, deviceName, alert)) {
            prefs.edit().putString(key, newState).apply();
        }
    }

    private static boolean send(Context context, String deviceId, String deviceName, AlertRules.Alert alert) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                   != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Notification permission not granted — skipped: " + alert.title);
            return false;
        }
        boolean isStock = alert.kind == AlertRules.Kind.STOCK;
        NotificationCompat.Builder b = new NotificationCompat.Builder(context,
                isStock ? CHANNEL_STOCK : CHANNEL_EXPIRY)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setSubText(deviceName)
                .setContentTitle(alert.title)
                .setContentText(alert.message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(alert.message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setContentIntent(openScreen(context, deviceId, deviceName, alert))
                .setAutoCancel(true);
        NotificationManagerCompat.from(context).notify(notificationId(deviceId, alert.kind, alert.tray), b.build());
        Log.i(TAG, "Sent: [" + deviceId + "] " + alert.title + " | " + alert.message);
        return true;
    }

    /** Stock → Inventory screen; expiry → that product's category screen, for that machine. Back goes to its Dashboard. */
    private static PendingIntent openScreen(Context context, String deviceId, String deviceName,
                                            AlertRules.Alert alert) {
        Class<?> target;
        if (alert.kind == AlertRules.Kind.STOCK) {
            target = InventoryTrackingActivity.class;
        } else if (alert.tray <= 2) {
            target = ProductNapkinsActivity.class;
        } else if (alert.tray <= 4) {
            target = ProductWipesActivity.class;
        } else if (alert.tray <= 6) {
            target = ProductTissuesActivity.class;
        } else {
            target = ProductSanitizersActivity.class;
        }
        Intent dashboard = new Intent(context, DashboardActivity.class)
                .putExtra(DeviceSession.EXTRA_ID, deviceId).putExtra(DeviceSession.EXTRA_NAME, deviceName);
        Intent screen = new Intent(context, target)
                .putExtra(DeviceSession.EXTRA_ID, deviceId).putExtra(DeviceSession.EXTRA_NAME, deviceName);
        return TaskStackBuilder.create(context)
                .addNextIntent(dashboard)
                .addNextIntent(screen)
                .getPendingIntent(notificationId(deviceId, alert.kind, alert.tray),
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /**
     * One ID per machine + kind + tray, so alerts never replace each other:
     * 20 numbers per machine (stock 1–8, expiry 11–18).
     */
    static int notificationId(String deviceId, AlertRules.Kind kind, int tray) {
        int slot = (deviceId.hashCode() & 0x7fffffff) % 100_000;
        return 1_000_000 + slot * 20 + (kind == AlertRules.Kind.STOCK ? 0 : 10) + tray;
    }
}
