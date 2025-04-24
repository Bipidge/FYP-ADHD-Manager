package com.example.fyp.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import com.example.fyp.ui.settings.SettingsFragment;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";
    // Need access to SharedPreferences keys (consider moving keys to a constants class)
    private static final String PREFS_NAME = "AppSettingsPrefs";
    private static final String KEY_REMINDER_ENABLED = "dailyReminderEnabled";
    private static final String KEY_REMINDER_HOUR = "reminderHour";
    private static final String KEY_REMINDER_MINUTE = "reminderMinute";


    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) ||
                Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(intent.getAction())) {

            Log.d(TAG, "Device boot completed, checking for reminder schedule.");

            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            boolean isEnabled = prefs.getBoolean(KEY_REMINDER_ENABLED, false);
            int hour = prefs.getInt(KEY_REMINDER_HOUR, -1);
            int minute = prefs.getInt(KEY_REMINDER_MINUTE, -1);

            if (isEnabled && hour != -1 && minute != -1) {
                Log.d(TAG, "Reminder was enabled, rescheduling alarm for " + hour + ":" + minute);
                // Use the same scheduling logic as in SettingsFragment
                SettingsFragment.scheduleDailyReminder(context, hour, minute); // Make schedule method static
            } else {
                Log.d(TAG, "Reminder not enabled or time not set, no reschedule needed.");
            }
        }
    }
}
