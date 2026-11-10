package com.example.sanivend;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;

/**
 * Keeps the live alerts (TrayAlertMonitor) running after the app is closed, on the free
 * Firebase plan where no server can push notifications. Android requires a small ongoing
 * notification for this: "SaniVend is monitoring your machines".
 *
 * Started after sign-in and again after the phone restarts (BootReceiver); stopped on log out.
 */
public class MonitorService extends Service {

    private static final String TAG = "MonitorService";
    static final String CHANNEL_MONITOR = "monitor";
    private static final int NOTIFICATION_ID = 7;

    /** Starts the service if someone is signed in (safe to call often). */
    public static void start(Context context) {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) return;
        try {
            ContextCompat.startForegroundService(context, new Intent(context, MonitorService.class));
        } catch (RuntimeException e) {
            // Android 12+ refuses to start it from the background; the 15-minute worker still runs
            Log.w(TAG, "Couldn't start monitoring: " + e.getMessage());
        }
    }

    public static void stop(Context context) {
        context.stopService(new Intent(context, MonitorService.class));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel(this);
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                ? ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE : 0;
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        TrayAlerts.createChannels(this);
        TrayAlertMonitor.start(this);
        return START_STICKY;                 // Android restarts it if it has to kill it
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private android.app.Notification buildNotification() {
        Intent open = new Intent(this, DeviceListActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, NOTIFICATION_ID, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_MONITOR)
                .setSmallIcon(android.R.drawable.ic_popup_sync)
                .setContentTitle("SaniVend is monitoring your machines")
                .setContentText("Orders, stock, refills and expiry dates")
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setOngoing(true)
                .setShowWhen(false)
                .setContentIntent(pi)
                .build();
    }

    static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm == null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL_MONITOR,
                "Background monitoring", NotificationManager.IMPORTANCE_MIN);
        ch.setDescription("Keeps SaniVend alerts working while the app is closed");
        ch.setShowBadge(false);
        nm.createNotificationChannel(ch);
    }
}
