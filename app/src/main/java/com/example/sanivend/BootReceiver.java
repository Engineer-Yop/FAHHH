package com.example.sanivend;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * After the phone restarts (or the app is updated), starts the alerts again for the
 * signed-in account: the background monitor and the 15-minute check.
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) return;
        TrayAlerts.createChannels(context);
        TrayAlerts.scheduleBackgroundChecks(context);
        MonitorService.start(context);
    }
}
