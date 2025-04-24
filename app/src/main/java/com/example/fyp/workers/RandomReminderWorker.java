package com.example.fyp.workers;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.fyp.utils.NotificationHelper;

import java.util.Random;

public class RandomReminderWorker extends Worker {
    private static final String TAG = "RandomReminderWorker";

    public RandomReminderWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "Worker running. Deciding whether to show random reminder.");

        // Simple logic: Show notification roughly 1 out of 3 times the worker runs
        if (new Random().nextInt(3) == 0) {
            Log.d(TAG, "Showing random reminder notification.");
            NotificationHelper.showRandomReminderNotification(getApplicationContext());
        } else {
            Log.d(TAG, "Skipping random reminder this time.");
        }

        // Indicate whether the work finished successfully
        return Result.success();

        // If there was an error, return Result.failure()
        // If the work needs to be retried, return Result.retry()
    }
}
