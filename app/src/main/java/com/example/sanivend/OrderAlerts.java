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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Real-time order notifications: one for every successful sale written by the machine to
 * /devices/{id}/orders/order_N, showing the product, tray, price and time.
 *
 * The highest order number already shown is remembered per machine, so:
 *   - the first time a machine is seen, its existing orders are only counted, never shown;
 *   - after that, every new order_N shows exactly once, from the live listener
 *     (TrayAlertMonitor) or, if the app was asleep, from the 15-minute check (InventoryWorker).
 */
public final class OrderAlerts {

    private static final String TAG = "OrderAlerts";
    public static final String CHANNEL_ORDERS = "order_alerts";
    private static final String PREFS = "order_alerts";

    private OrderAlerts() {}

    public static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm == null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL_ORDERS,
                "New orders", NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("Every successful purchase: product, tray, price and time");
        nm.createNotificationChannel(ch);
    }

    /** Highest order number already handled for this machine, or -1 if never seen. */
    static int lastSeen(Context context, String deviceId) {
        return prefs(context).getInt(deviceId, -1);
    }

    private static void setLastSeen(Context context, String deviceId, int n) {
        prefs(context).edit().putInt(deviceId, n).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /**
     * First look at a machine: remember its newest order without notifying.
     * Does nothing if this machine was seen before.
     */
    public static synchronized void baseline(Context context, String deviceId, DataSnapshot orders) {
        if (lastSeen(context, deviceId) >= 0) return;
        int max = 0;
        for (DataSnapshot o : orders.getChildren()) {
            max = Math.max(max, OrderAlertRules.number(o.getKey()));
        }
        setLastSeen(context, deviceId, max);
        Log.i(TAG, deviceId + ": starting after order_" + max);
    }

    /** One order arrived (live listener). Notifies if it is newer than the last one shown. */
    public static synchronized void onOrder(Context context, String deviceId, String deviceName,
                                            DataSnapshot order) {
        int n = OrderAlertRules.number(order.getKey());
        int last = lastSeen(context, deviceId);
        if (n <= 0 || last < 0 || n <= last) return;
        if (send(context, deviceId, deviceName, n, order)) setLastSeen(context, deviceId, n);
    }

    /** Background check: notifies every order newer than the last one shown (at most 5, then a summary). */
    public static synchronized void checkAll(Context context, String deviceId, String deviceName,
                                             DataSnapshot orders) {
        int last = lastSeen(context, deviceId);
        if (last < 0) {
            baseline(context, deviceId, orders);
            return;
        }
        List<Integer> fresh = new ArrayList<>();
        for (DataSnapshot o : orders.getChildren()) {
            int n = OrderAlertRules.number(o.getKey());
            if (n > last) fresh.add(n);
        }
        if (fresh.isEmpty()) return;
        Collections.sort(fresh);
        int newest = fresh.get(fresh.size() - 1);
        int from = Math.max(0, fresh.size() - OrderAlertRules.MAX_AT_ONCE);   // show the latest 5
        boolean allowed = true;
        for (int i = from; i < fresh.size() && allowed; i++) {
            int n = fresh.get(i);
            allowed = send(context, deviceId, deviceName, n, orders.child("order_" + n));
        }
        if (!allowed) return;                     // no permission yet: try again next time
        if (from > 0) sendMore(context, deviceId, deviceName, from);
        setLastSeen(context, deviceId, newest);
    }

    private static boolean send(Context context, String deviceId, String deviceName, int n, DataSnapshot o) {
        if (!canNotify(context)) return false;
        createChannel(context);
        int tray = TrayEditor.toInt(o.child("tray").getValue());
        int price = TrayEditor.toInt(o.child("price").getValue());
        int paid = TrayEditor.toInt(o.child("amount_paid").getValue());
        int change = TrayEditor.toInt(o.child("change").getValue());
        Object dt = o.child("datetime").getValue();
        Object savedName = o.child("product").getValue();
        String product = (savedName != null && !String.valueOf(savedName).trim().isEmpty())
                ? ProductCatalog.name(tray, savedName)          // name at the time of the sale
                : TrayAlerts.productName(deviceId, tray);

        String title = OrderAlertRules.title(tray, price);
        String text = OrderAlertRules.message(product, tray, price, dt == null ? "" : String.valueOf(dt), paid, change);
        NotificationCompat.Builder b = new NotificationCompat.Builder(context, CHANNEL_ORDERS)
                .setSmallIcon(android.R.drawable.stat_notify_more)
                .setSubText(deviceName)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setGroup("orders_" + deviceId)
                .setContentIntent(openSales(context, deviceId, deviceName, n))
                .setAutoCancel(true);
        NotificationManagerCompat.from(context).notify(notificationId(deviceId, n), b.build());
        Log.i(TAG, "Sent: [" + deviceId + "] order_" + n + " | " + text);
        return true;
    }

    private static void sendMore(Context context, String deviceId, String deviceName, int more) {
        String text = OrderAlertRules.moreText(more);
        NotificationCompat.Builder b = new NotificationCompat.Builder(context, CHANNEL_ORDERS)
                .setSmallIcon(android.R.drawable.stat_notify_more)
                .setSubText(deviceName)
                .setContentTitle("More new orders")
                .setContentText(text)
                .setGroup("orders_" + deviceId)
                .setContentIntent(openSales(context, deviceId, deviceName, 0))
                .setAutoCancel(true);
        NotificationManagerCompat.from(context).notify(notificationId(deviceId, 0), b.build());
    }

    private static boolean canNotify(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                   != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Notification permission not granted — order alert skipped");
            return false;
        }
        return true;
    }

    /** Opens that machine's Sales Monitoring; Back goes to its Dashboard. */
    private static PendingIntent openSales(Context context, String deviceId, String deviceName, int n) {
        Intent dashboard = new Intent(context, DashboardActivity.class)
                .putExtra(DeviceSession.EXTRA_ID, deviceId).putExtra(DeviceSession.EXTRA_NAME, deviceName);
        Intent sales = new Intent(context, SalesMonitoringActivity.class)
                .putExtra(DeviceSession.EXTRA_ID, deviceId).putExtra(DeviceSession.EXTRA_NAME, deviceName);
        return TaskStackBuilder.create(context)
                .addNextIntent(dashboard)
                .addNextIntent(sales)
                .getPendingIntent(notificationId(deviceId, n),
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** 50 IDs per machine (order number mod 50), separate from the tray and coin alerts. */
    static int notificationId(String deviceId, int n) {
        int slot = (deviceId.hashCode() & 0x7fffffff) % 100_000;
        return 40_000_000 + slot * 50 + (n % 50);
    }
}
