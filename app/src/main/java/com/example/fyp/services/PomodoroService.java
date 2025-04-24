package com.example.fyp.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.CountDownTimer;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.lifecycle.ViewModelProvider; // Keep if using for completion event
import androidx.lifecycle.ViewModelStore;    // Keep if using for completion event
import androidx.lifecycle.ViewModelStoreOwner;// Keep if using for completion event
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.example.fyp.MainActivity; // To open app from notification
import com.example.fyp.R;
import com.example.fyp.ui.SharedViewModel; // Keep if using for completion event

import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class PomodoroService extends Service implements ViewModelStoreOwner {

    private static final String TAG = "PomodoroService";
    private static final String CHANNEL_ID = "PomodoroServiceChannel";
    private static final int NOTIFICATION_ID = 1;

    // --- Service Actions (Intents from UI) ---
    public static final String ACTION_START = "com.example.fyp.action.START";
    public static final String ACTION_STOP = "com.example.fyp.action.STOP";
    // Add PAUSE/RESUME actions later if needed

    // --- Intent Extras (Configuration from UI) ---
    public static final String EXTRA_STUDY_DURATION = "extra_study_duration";
    public static final String EXTRA_BREAK_DURATION = "extra_break_duration";
    public static final String EXTRA_TOTAL_CYCLES = "extra_total_cycles";
    public static final String EXTRA_STUDY_LABEL = "extra_study_label";
    public static final String EXTRA_BREAK_LABEL = "extra_break_label";

    // --- Broadcast Actions (Updates TO UI) ---
    public static final String ACTION_TIMER_UPDATE = "com.example.fyp.action.TIMER_UPDATE";
    public static final String EXTRA_TIMER_STATE = "extra_timer_state"; // String name of TimerState enum
    public static final String EXTRA_REMAINING_TIME = "extra_remaining_time"; // long
    public static final String EXTRA_CYCLE_INFO = "extra_cycle_info"; // String
    public static final String EXTRA_PHASE_LABEL = "extra_phase_label"; // String
    // Optional broadcast specifically for study completion
    public static final String ACTION_POMODORO_STUDY_COMPLETE = "com.example.fyp.action.POMODORO_STUDY_COMPLETE";

    // --- Timer State Enum ---
    public enum TimerState { IDLE, STUDY, BREAK, PAUSED }

    // --- Service State Variables ---
    private CountDownTimer currentTimer;
    private TimerState currentState = TimerState.IDLE;
    private long studyDurationMillis = 0;
    private long breakDurationMillis = 0;
    private int totalCycles = 1;
    private int currentCycle = 0;
    private long remainingMillis = 0;
    private String studyLabel = "Study Time";
    private String breakLabel = "Break Time";

    private NotificationManager notificationManager;

    // --- ViewModel related (ONLY if triggering completion event via VM) ---
    private final ViewModelStore viewModelStore = new ViewModelStore();
    private SharedViewModel sharedViewModelForCompletion;
    @NonNull @Override public ViewModelStore getViewModelStore() { return viewModelStore; }
    // --- ---

    // =========================================================================================
    // Service Lifecycle Methods
    // =========================================================================================

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service onCreate");
        // Keep this ONLY if QuestsFragment needs the VM event directly
        sharedViewModelForCompletion = new ViewModelProvider(this).get(SharedViewModel.class);

        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand received");
        if (intent == null) {
            Log.w(TAG, "Intent is null, service possibly restarted by system. Stopping.");
            stopSelf(); // Stop if restarted without explicit command
            return START_NOT_STICKY;
        }

        String action = intent.getAction();
        if (ACTION_START.equals(action)) {
            Log.d(TAG, "Action: START");
            handleStartAction(intent);
        } else if (ACTION_STOP.equals(action)) {
            Log.d(TAG, "Action: STOP");
            stopTimerAndService();
        }

        return START_REDELIVER_INTENT; // Attempt to redeliver the last intent if service is killed
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null; // This is a started service, not bound
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Service onDestroy");
        if (currentTimer != null) {
            currentTimer.cancel();
        }
        // Clear ViewModelStore if it was used
        // viewModelStore.clear();
    }

    // =========================================================================================
    // Timer Logic Methods
    // =========================================================================================

    private void handleStartAction(Intent intent) {
        // Retrieve configuration from Intent extras
        studyDurationMillis = intent.getLongExtra(EXTRA_STUDY_DURATION, TimeUnit.MINUTES.toMillis(25));
        breakDurationMillis = intent.getLongExtra(EXTRA_BREAK_DURATION, TimeUnit.MINUTES.toMillis(5));
        totalCycles = intent.getIntExtra(EXTRA_TOTAL_CYCLES, 4);
        studyLabel = intent.getStringExtra(EXTRA_STUDY_LABEL);
        breakLabel = intent.getStringExtra(EXTRA_BREAK_LABEL);
        if (studyLabel == null || studyLabel.isEmpty()) studyLabel = "Study Time";
        if (breakLabel == null || breakLabel.isEmpty()) breakLabel = "Break Time";

        if (currentState == TimerState.IDLE) { // Only start if idle
            currentCycle = 1; // Start cycle 1
            startPhaseTimer(studyDurationMillis, TimerState.STUDY);
        } else {
            Log.w(TAG, "Start command received but timer already running/paused.");
        }
    }

    private void startPhaseTimer(long durationMillis, TimerState nextState) {
        if (currentTimer != null) {
            currentTimer.cancel();
        }
        currentState = nextState;
        remainingMillis = durationMillis;
        sendTimerUpdateBroadcast(); // Send initial state update

        Log.i(TAG, "Starting phase: " + nextState + " for " + durationMillis / 1000 + "s. Cycle: " + currentCycle + "/" + totalCycles);

        currentTimer = new CountDownTimer(remainingMillis, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                remainingMillis = millisUntilFinished;
                sendTimerUpdateBroadcast(); // Send periodic updates
                updateNotification();       // Update notification text
            }

            @Override
            public void onFinish() {
                currentTimer = null;
                remainingMillis = 0;
                handlePhaseCompletion();    // Move to next state or finish
            }
        }.start();

        startForeground(NOTIFICATION_ID, buildNotification()); // Start foreground service
    }

    private void handlePhaseCompletion() {
        Log.d(TAG, "Phase " + currentState + " completed.");
        playSoundNotification();

        if (currentState == TimerState.STUDY) {
            // Notify observers (e.g., Pet Game) that a study session ended
            Intent completeIntent = new Intent(ACTION_POMODORO_STUDY_COMPLETE);
            LocalBroadcastManager.getInstance(this).sendBroadcast(completeIntent);
            // Fallback / alternative using ViewModel:
            // if (sharedViewModelForCompletion != null) sharedViewModelForCompletion.triggerPomodoroStudyCompleted();

            // Start Break Timer
            startPhaseTimer(breakDurationMillis, TimerState.BREAK);

        } else if (currentState == TimerState.BREAK) {
            // Check if all cycles are done
            if (currentCycle >= totalCycles) {
                Log.i(TAG, "All cycles completed.");
                stopTimerAndService(); // Finished
            } else {
                // Start next Study Timer
                currentCycle++;
                startPhaseTimer(studyDurationMillis, TimerState.STUDY);
            }
        }
    }

    private void stopTimerAndService() {
        Log.i(TAG, "Stopping timer and service.");
        if (currentTimer != null) {
            currentTimer.cancel();
            currentTimer = null;
        }
        currentState = TimerState.IDLE;
        remainingMillis = 0;
        currentCycle = 0;
        sendTimerUpdateBroadcast(); // Send final IDLE state update
        stopForeground(true);      // Remove notification
        stopSelf();                // Stop the service instance
    }

    // =========================================================================================
    // Communication & Notification Methods
    // =========================================================================================

    /**
     * Sends the current timer status via LocalBroadcastManager.
     */
    private void sendTimerUpdateBroadcast() {
        Intent intent = new Intent(ACTION_TIMER_UPDATE);
        intent.putExtra(EXTRA_TIMER_STATE, currentState.name()); // Send enum name as String
        intent.putExtra(EXTRA_REMAINING_TIME, remainingMillis);
        intent.putExtra(EXTRA_CYCLE_INFO, getCycleInfoString());
        intent.putExtra(EXTRA_PHASE_LABEL, getPhaseLabelString());
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Pomodoro Timer Status", // User-visible channel name
                    NotificationManager.IMPORTANCE_LOW // Low importance = no sound/vibration by default
            );
            serviceChannel.setDescription("Displays the active Pomodoro timer");
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(serviceChannel);
            } else {
                Log.e(TAG, "NotificationManager is null, cannot create channel.");
            }
        }
    }

    private Notification buildNotification() {
        // Intent to open MainActivity when notification is tapped
        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.setAction(Intent.ACTION_MAIN);
        notificationIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        notificationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0, notificationIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        String timeText = formatTime(remainingMillis);
        String phaseLabel = getPhaseLabelString();
        String cycleInfo = getCycleInfoString();
        String contentText = phaseLabel + (cycleInfo.isEmpty() ? "" : " | " + cycleInfo);
        if (currentState == TimerState.IDLE) contentText = "Pomodoro Timer"; // Default text before stop

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Pomodoro Timer Active")
                .setContentText(timeText + " - " + contentText)
                .setSmallIcon(R.drawable.ic_clock_notification) // Ensure this drawable exists!
                .setContentIntent(pendingIntent)
                .setOnlyAlertOnce(true) // Don't alert repeatedly for time updates
                .setOngoing(true);      // Make it persistent while service is foreground

        // TODO: Add actions for Pause/Resume/Stop later if needed

        return builder.build();
    }

    private void updateNotification() {
        if (notificationManager != null) {
            notificationManager.notify(NOTIFICATION_ID, buildNotification());
        } else {
            Log.e(TAG, "NotificationManager is null, cannot update notification.");
        }
    }

    // =========================================================================================
    // Utility Methods
    // =========================================================================================

    private String getCycleInfoString() {
        if (currentState == TimerState.IDLE || currentState == TimerState.PAUSED) return "";
        return String.format(Locale.getDefault(), "Cycle %d of %d", Math.max(currentCycle, 1), totalCycles);
    }

    private String getPhaseLabelString() {
        switch (currentState) {
            case STUDY: return studyLabel;
            case BREAK: return breakLabel;
            case PAUSED: return "Paused";
            case IDLE:
            default: return "Ready";
        }
    }

    private String formatTime(long millis) {
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis) - TimeUnit.MINUTES.toSeconds(minutes);
        // Basic MM:SS format
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
    }

    private void playSoundNotification() {
        // Avoid playing sound when service stops itself naturally after completion
        if (currentState == TimerState.IDLE) return;
        try {
            Uri notificationSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            if (notificationSoundUri != null) {
                Ringtone r = RingtoneManager.getRingtone(this, notificationSoundUri);
                if (r != null) {
                    r.play();
                } else {
                    Log.w(TAG, "Could not get Ringtone object for default sound.");
                }
            } else {
                Log.w(TAG, "Default notification sound URI is null.");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error playing notification sound", e);
        }
    }
}
