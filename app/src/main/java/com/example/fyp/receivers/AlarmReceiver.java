package com.example.fyp.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.example.fyp.utils.NotificationHelper;

public class AlarmReceiver extends BroadcastReceiver {

    private static final String TAG = "AlarmReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Daily reminder alarm received.");

        // Show the daily reminder notification
        NotificationHelper.showDailyReminderNotification(context);

        // IMPORTANT: Reschedule for the next day (if using inexact repeating, this isn't needed)
        // Since we'll likely use setExactAndAllowWhileIdle, we need to reschedule manually here.
        // We need the original time to reschedule. This means the SettingsFragment
        // needs to schedule the *next* alarm when the user sets the time.
        // Re-scheduling here can be complex. A simpler approach is to let SettingsFragment
        // handle the *initial* scheduling of the repeating alarm.
        // Let's assume SettingsFragment sets a *repeating* alarm initially.
        // If using setExactAndAllowWhileIdle, call scheduleDailyReminder from SettingsFragment again here.
    }

}
