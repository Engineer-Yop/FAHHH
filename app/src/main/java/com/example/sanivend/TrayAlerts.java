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

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.FirebaseDatabase;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Stock and expiry notifications for the 8 trays.
 *
 * Reads /trays/tray_N/stock (written by the ESP32 after each sale) and
 * /trays/tray_N/expiry (set in the app), then uses AlertRules to decide.
 *
 * Each tray has its own stock notification and its own expiry notification.
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
    }

    /** Reads /trays once and checks it (used after the notification permission is granted). */
    public static void checkNow(Context context) {
        final Context app = context.getApplicationContext();
        FirebaseDatabase.getInstance().getReference("trays").get()
                .addOnSuccessListener(snapshot -> check(app, snapshot))
                .addOnFailureListener(e -> Log.w(TAG, "Couldn't read trays: " + e.getMessage()));
    }

    /** Checks every tray in a /trays snapshot and sends or clears notifications. */
    public static synchronized void check(Context context, DataSnapshot trays) {
        createChannels(context);
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());

        for (int tray = 1; tray <= TRAY_COUNT; tray++) {
            DataSnapshot t = trays.child("tray_" + tray);
            String product = PRODUCTS[tray];

            Object stockValue = t.child("stock").getValue();
            Object capValue = t.child("capacity").getValue();
            int capacity = capValue != null ? TrayEditor.toInt(capValue) : DEFAULT_CAPACITY;
            Integer stock = stockValue != null ? TrayEditor.toInt(stockValue) : null;
            apply(context, AlertRules.stock(tray, product, stock, capacity), AlertRules.Kind.STOCK, tray);

            Object expiryValue = t.child("expiry").getValue();
            String expiry = expiryValue != null ? String.valueOf(expiryValue) : null;
            apply(context, AlertRules.expiry(tray, product, expiry, today), AlertRules.Kind.EXPIRY, tray);
        }
    }

    // ── Sending / clearing ────────────────────────────

    private static void apply(Context context, AlertRules.Alert alert, AlertRules.Kind kind, int tray) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String key = kind.name() + "_" + tray;
        String newState = (alert == null) ? "OK" : alert.state;
        String oldState = prefs.getString(key, "OK");
        if (newState.equals(oldState)) return;                 // nothing new to say

        if (alert == null || !alert.needsNotification()) {
            NotificationManagerCompat.from(context).cancel(notificationId(kind, tray));
            prefs.edit().putString(key, "OK").apply();
            return;
        }
        // Only remember it as "sent" if it really was, so it shows once permission is given
        if (send(context, alert)) {
            prefs.edit().putString(key, newState).apply();
        }
    }

    private static boolean send(Context context, AlertRules.Alert alert) {
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
                .setContentTitle(alert.title)
                .setContentText(alert.message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(alert.message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setContentIntent(openScreen(context, alert))
                .setAutoCancel(true);
        NotificationManagerCompat.from(context).notify(notificationId(alert.kind, alert.tray), b.build());
        Log.i(TAG, "Sent: " + alert.title + " | " + alert.message);
        return true;
    }

    /** Stock → Inventory screen; expiry → that product's category screen. Back goes to the Dashboard. */
    private static PendingIntent openScreen(Context context, AlertRules.Alert alert) {
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
        return TaskStackBuilder.create(context)
                .addNextIntent(new Intent(context, DashboardActivity.class))
                .addNextIntent(new Intent(context, target))
                .getPendingIntent(notificationId(alert.kind, alert.tray),
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** Stock alerts use 1001–1008, expiry alerts 2001–2008, so they never replace each other. */
    static int notificationId(AlertRules.Kind kind, int tray) {
        return (kind == AlertRules.Kind.STOCK ? 1000 : 2000) + tray;
    }
}
