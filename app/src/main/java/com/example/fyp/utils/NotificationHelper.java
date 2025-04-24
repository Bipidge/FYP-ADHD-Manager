package com.example.fyp.utils;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log; // *** ADD THIS IMPORT ***

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat; // *** ADD THIS IMPORT ***

import com.example.fyp.LauncherActivity;
import com.example.fyp.R;

public class NotificationHelper {
    private static final String DAILY_REMINDER_CHANNEL_ID = "daily_reminder_channel";
    private static final String RANDOM_REMINDER_CHANNEL_ID = "random_reminder_channel";
    private static final int DAILY_NOTIFICATION_ID = 1001; // Unique ID for daily
    private static final int RANDOM_NOTIFICATION_ID = 1002; // Unique ID for random

    /**
     * Creates notification channels (required for Android 8.0+).
     * Call this once, e.g., in Application class or MainActivity.
     */
    public static void createNotificationChannels(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence dailyName = "Daily Reminder"; String dailyDescription = "Channel for scheduled daily reminders"; int dailyImportance = NotificationManager.IMPORTANCE_DEFAULT; NotificationChannel dailyChannel = new NotificationChannel(DAILY_REMINDER_CHANNEL_ID, dailyName, dailyImportance); dailyChannel.setDescription(dailyDescription);
            CharSequence randomName = "Productivity Nudges"; String randomDescription = "Channel for occasional reminders"; int randomImportance = NotificationManager.IMPORTANCE_LOW; NotificationChannel randomChannel = new NotificationChannel(RANDOM_REMINDER_CHANNEL_ID, randomName, randomImportance); randomChannel.setDescription(randomDescription);
            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            if (notificationManager != null) { notificationManager.createNotificationChannel(dailyChannel); notificationManager.createNotificationChannel(randomChannel); }
        }
    }

    public static void showDailyReminderNotification(Context context) {
        String title = "Time to Focus!"; String text = "Ready to tackle your tasks? Open the app to get started.";
        showNotification(context, DAILY_REMINDER_CHANNEL_ID, DAILY_NOTIFICATION_ID, title, text);
    }

    public static void showRandomReminderNotification(Context context) {
        String title = "Stay Productive!"; String text = "Just a little nudge to check in on your goals. Open the app!";
        showNotification(context, RANDOM_REMINDER_CHANNEL_ID, RANDOM_NOTIFICATION_ID, title, text);
    }


    private static void showNotification(Context context, String channelId, int notificationId, String title, String text) {
        Intent intent = new Intent(context, LauncherActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_notification_icon)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);

        try {
            // Uses ContextCompat and Log
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    notificationManager.notify(notificationId, builder.build());
                } else {
                    Log.w("NotificationHelper", "POST_NOTIFICATIONS permission not granted. Cannot show notification " + notificationId);
                }
            } else {
                notificationManager.notify(notificationId, builder.build());
            }
        } catch (SecurityException e) {
            // Uses Log
            Log.e("NotificationHelper", "SecurityException showing notification " + notificationId + ". Missing permission?", e);
        }
    }
}
