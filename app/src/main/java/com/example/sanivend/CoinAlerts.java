package com.example.sanivend;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
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

/**
 * Change-coin notifications.
 *
 * ESP32 #1 has an IR sensor on each change tube (P1, P5, P10) and writes
 * /devices/{id}/coins = { p1: "ok" | "empty", p5: ..., p10: ..., updated }.
 *
 * When a tube becomes "empty" the owner gets one notification for that tube
 * ("P5 coins are out"); it is cleared once the tube reads "ok" again.
 * Called from the same two places as TrayAlerts:
 *   - TrayAlertMonitor (live, while the app is running)
 *   - InventoryWorker  (every 15 minutes, even when the app is closed)
 */
public final class CoinAlerts {

    private static final String TAG = "CoinAlerts";
    public static final String CHANNEL_COINS = "coin_alerts";
    private static final String PREFS = "coin_alerts";

    static final String[] KEYS  = {"p1", "p5", "p10"};
    static final int[]    PESOS = {1, 5, 10};

    private CoinAlerts() {}

    public static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm == null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL_COINS,
                "Change coin alerts", NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("A change tube (P1, P5 or P10) has run out of coins");
        nm.createNotificationChannel(ch);
    }

    /** True when the snapshot says this tube is empty ("empty", or true for older firmware). */
    static boolean isEmpty(DataSnapshot coins, String key) {
        Object v = coins.child(key).getValue();
        if (v == null) return false;                      // no sensor data yet → don't alarm
        String s = String.valueOf(v).trim();
        return s.equalsIgnoreCase("empty") || s.equalsIgnoreCase("true");
    }

    /** Checks one machine's /coins snapshot and sends or clears the three notifications. */
    public static synchronized void check(Context context, String deviceId, String deviceName,
                                          DataSnapshot coins) {
        createChannel(context);
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        for (int i = 0; i < KEYS.length; i++) {
            String key = deviceId + "|" + KEYS[i];
            boolean empty = isEmpty(coins, KEYS[i]);
            boolean wasEmpty = prefs.getBoolean(key, false);
            if (empty == wasEmpty) continue;              // nothing new to say

            if (!empty) {                                 // refilled
                NotificationManagerCompat.from(context).cancel(notificationId(deviceId, i));
                prefs.edit().putBoolean(key, false).apply();
            } else if (send(context, deviceId, deviceName, i)) {
                prefs.edit().putBoolean(key, true).apply();
            }
        }
    }

    private static boolean send(Context context, String deviceId, String deviceName, int i) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                   != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Notification permission not granted — skipped P" + PESOS[i] + " alert");
            return false;
        }
        String title = "₱" + PESOS[i] + " coins are out";
        String message = "The ₱" + PESOS[i] + " change tube is empty. Refill it so the machine "
                + "can give change. Until then it gives change with the other coins when it can.";

        Intent open = new Intent(context, DashboardActivity.class)
                .putExtra(DeviceSession.EXTRA_ID, deviceId)
                .putExtra(DeviceSession.EXTRA_NAME, deviceName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(context, notificationId(deviceId, i), open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder b = new NotificationCompat.Builder(context, CHANNEL_COINS)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setSubText(deviceName)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setContentIntent(pi)
                .setAutoCancel(true);
        NotificationManagerCompat.from(context).notify(notificationId(deviceId, i), b.build());
        Log.i(TAG, "Sent: [" + deviceId + "] " + title);
        return true;
    }

    /** Separate range from TrayAlerts (1,000,000+) and OrderAlerts (40,000,000+): 3 numbers per machine. */
    static int notificationId(String deviceId, int i) {
        int slot = (deviceId.hashCode() & 0x7fffffff) % 100_000;
        return 30_000_000 + slot * 3 + i;
    }
}
