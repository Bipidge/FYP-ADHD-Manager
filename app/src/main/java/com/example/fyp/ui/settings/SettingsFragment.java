package com.example.fyp.ui.settings;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.example.fyp.R;
import com.example.fyp.auth.AccountActivity;
import com.example.fyp.auth.LoginActivity;
import com.example.fyp.receivers.AlarmReceiver;
import com.example.fyp.workers.RandomReminderWorker;
import com.google.android.gms.auth.api.signin.GoogleSignIn; // Import for Logout
import com.google.android.gms.auth.api.signin.GoogleSignInClient; // Import for Logout
import com.google.android.gms.auth.api.signin.GoogleSignInOptions; // Import for Logout
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.firebase.auth.FirebaseAuth;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class SettingsFragment extends Fragment {

    private static final String TAG = "SettingsFragment";

    // --- Constants ---
    private static final String PREFS_NAME = "AppSettingsPrefs";
    private static final String KEY_REMINDER_ENABLED = "dailyReminderEnabled";
    private static final String KEY_REMINDER_HOUR = "reminderHour";
    private static final String KEY_REMINDER_MINUTE = "reminderMinute";
    private static final int DEFAULT_REMINDER_HOUR = 9;
    private static final int DEFAULT_REMINDER_MINUTE = 0;
    private static final int ALARM_REQUEST_CODE = 123;
    private static final String RANDOM_REMINDER_WORK_TAG = "random_reminder_work";

    // --- UI Elements ---
    private RelativeLayout accountRow, reminderTimeRow, /*languageRow,*/ themesRow, logoutRow; // languageRow removed
    private SwitchMaterial reminderSwitch;
    private TextView reminderTimeTextView;

    // --- State ---
    private SharedPreferences sharedPreferences;
    private boolean isReminderEnabled;
    private int reminderHour;
    private int reminderMinute;
    private FirebaseAuth mAuth;
    private GoogleSignInClient mGoogleSignInClient; // For logout

    public SettingsFragment() { }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sharedPreferences = requireActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        mAuth = FirebaseAuth.getInstance();
        // Configure GoogleSignInClient for sign-out
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id)) // Use the same client ID
                .requestEmail().build();
        if (getActivity() != null) {
            mGoogleSignInClient = GoogleSignIn.getClient(getActivity(), gso);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        findViews(view);
        loadSettings();
        setupListeners();
        updateUI();
        // scheduleRandomReminders(requireContext(), true); // Decide if you want this enabled by default
    }

    private void findViews(@NonNull View view) {
        accountRow = view.findViewById(R.id.setting_row_account);
        reminderSwitch = view.findViewById(R.id.switch_daily_reminder);
        reminderTimeRow = view.findViewById(R.id.setting_row_reminder_time);
        reminderTimeTextView = view.findViewById(R.id.text_view_reminder_time);
        // languageRow = view.findViewById(R.id.setting_row_language); // Removed
        themesRow = view.findViewById(R.id.setting_row_themes);
        logoutRow = view.findViewById(R.id.setting_row_logout);
    }

    private void loadSettings() {
        isReminderEnabled = sharedPreferences.getBoolean(KEY_REMINDER_ENABLED, false);
        reminderHour = sharedPreferences.getInt(KEY_REMINDER_HOUR, -1);
        reminderMinute = sharedPreferences.getInt(KEY_REMINDER_MINUTE, -1);
        if (reminderHour == -1 || reminderMinute == -1) {
            final Calendar c = Calendar.getInstance();
            reminderHour = DEFAULT_REMINDER_HOUR;
            reminderMinute = DEFAULT_REMINDER_MINUTE;
        }
    }

    private void saveBooleanSetting(String key, boolean value) { if(sharedPreferences != null) sharedPreferences.edit().putBoolean(key, value).apply(); }
    private void saveIntSetting(String key, int value) { if(sharedPreferences != null) sharedPreferences.edit().putInt(key, value).apply(); }

    private void setupListeners() {
        // Reminder Toggle
        reminderSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!checkExactAlarmPermission()) {
                buttonView.setChecked(false);
                return;
            }
            isReminderEnabled = isChecked;
            saveBooleanSetting(KEY_REMINDER_ENABLED, isReminderEnabled);
            updateReminderTimeRowVisibility();
            if (isChecked) {
                scheduleDailyReminder(requireContext(), reminderHour, reminderMinute);
                Log.d(TAG,"Daily reminder enabled & scheduled.");
            } else {
                cancelDailyReminder(requireContext());
                Log.d(TAG,"Daily reminder disabled & cancelled.");
            }
        });

        // Reminder Time Picker
        reminderTimeRow.setOnClickListener(v -> {
            if (!isReminderEnabled) {
                Toast.makeText(getContext(), "Enable daily reminder first", Toast.LENGTH_SHORT).show();
                return;
            }
            showTimePickerDialog();
        });

        // Placeholder Navigation Listeners
        View.OnClickListener placeholderClickListener = v -> {
            int id = v.getId();
            if (id == R.id.setting_row_account) {
                Intent intent = new Intent(getActivity(), AccountActivity.class);
                startActivity(intent);
            }
            // --- Removed languageRow handling ---
            else {
                String text = "";
                if (id == R.id.setting_row_themes) {
                    text = getString(R.string.setting_title_themes);
                }
                if (!text.isEmpty()) {
                    Toast.makeText(getContext(), text + ": " + getString(R.string.setting_nav_placeholder_toast), Toast.LENGTH_SHORT).show();
                }
            }
        };
        accountRow.setOnClickListener(placeholderClickListener);
        // languageRow.setOnClickListener(placeholderClickListener); // Removed
        themesRow.setOnClickListener(placeholderClickListener);

        // Logout Listener
        logoutRow.setOnClickListener(v -> logoutUser());
    }

    private void showTimePickerDialog() {
        int currentHour = sharedPreferences.getInt(KEY_REMINDER_HOUR, DEFAULT_REMINDER_HOUR);
        int currentMinute = sharedPreferences.getInt(KEY_REMINDER_MINUTE, DEFAULT_REMINDER_MINUTE);

        TimePickerDialog timePickerDialog = new TimePickerDialog(getContext(), (view, hourOfDay, minute) -> {
            reminderHour = hourOfDay;
            reminderMinute = minute;
            saveIntSetting(KEY_REMINDER_HOUR, reminderHour);
            saveIntSetting(KEY_REMINDER_MINUTE, reminderMinute);
            updateReminderTimeDisplay();
            if (isReminderEnabled) {
                if (checkExactAlarmPermission()) {
                    scheduleDailyReminder(requireContext(), reminderHour, reminderMinute);
                    Log.d(TAG,"Daily reminder rescheduled for new time.");
                } else {
                    reminderSwitch.setChecked(false); isReminderEnabled = false; saveBooleanSetting(KEY_REMINDER_ENABLED, false);
                }
            }
        }, currentHour, currentMinute, android.text.format.DateFormat.is24HourFormat(getContext()));
        timePickerDialog.show();
    }

    private void updateUI() {
        reminderSwitch.setChecked(isReminderEnabled); updateReminderTimeDisplay(); updateReminderTimeRowVisibility();
    }
    private void updateReminderTimeDisplay() { if(sharedPreferences.getInt(KEY_REMINDER_HOUR,-1)!=-1){reminderTimeTextView.setText(formatTime(reminderHour,reminderMinute));}else{reminderTimeTextView.setText(getString(R.string.setting_reminder_time_default));}}
    private void updateReminderTimeRowVisibility() { reminderTimeRow.setEnabled(isReminderEnabled);reminderTimeTextView.setEnabled(isReminderEnabled);reminderTimeRow.setAlpha(isReminderEnabled?1.0f:0.5f); }
    private String formatTime(int hour, int minute) { Calendar c=Calendar.getInstance();c.set(Calendar.HOUR_OF_DAY,hour);c.set(Calendar.MINUTE,minute);SimpleDateFormat timeFormat=new SimpleDateFormat("h:mm a",Locale.getDefault());return timeFormat.format(c.getTime());}

    private void logoutUser() {
        if (mAuth == null || mGoogleSignInClient == null || getActivity() == null) return;

        // Sign out from Firebase
        mAuth.signOut();

        // Sign out from Google
        mGoogleSignInClient.signOut().addOnCompleteListener(requireActivity(), task -> {
            // Regardless of Google sign-out success/failure, proceed to LoginActivity
            Toast.makeText(getContext(), "Logged Out", Toast.LENGTH_SHORT).show();
            navigateToLogin();
        });
    }

    private void navigateToLogin() {
        if (getActivity() == null) return;
        Intent intent = new Intent(getActivity(), LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        getActivity().finish(); // Close MainActivity
    }


    // --- Exact Alarm Permission Handling ---
    private boolean checkExactAlarmPermission() {
        if (getContext() == null) return false; // Need context
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager alarmManager = (AlarmManager) requireContext().getSystemService(Context.ALARM_SERVICE);
            if (alarmManager != null && !alarmManager.canScheduleExactAlarms()) {
                Log.w(TAG, "Exact alarm permission not granted.");
                new AlertDialog.Builder(requireContext())
                        .setTitle("Permission Required")
                        .setMessage("To set precise daily reminders, this app needs permission to schedule exact alarms. Please grant this in the app settings.")
                        .setPositiveButton("Go to Settings", (dialog, which) -> {
                            try {
                                Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                                startActivity(intent);
                            } catch (Exception e) {
                                Log.e(TAG, "Could not open exact alarm settings", e);
                                Toast.makeText(getContext(), "Could not open settings.", Toast.LENGTH_SHORT).show();
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
                return false;
            }
        }
        return true;
    }

    // --- Alarm Scheduling/Cancelling (Static for BootReceiver) ---
    public static void scheduleDailyReminder(Context context, int hour, int minute) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, AlarmReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, ALARM_REQUEST_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (alarmManager == null) { Log.e(TAG, "AlarmManager is null, cannot schedule."); return; }
        Calendar calendar = Calendar.getInstance(); calendar.setTimeInMillis(System.currentTimeMillis()); calendar.set(Calendar.HOUR_OF_DAY, hour); calendar.set(Calendar.MINUTE, minute); calendar.set(Calendar.SECOND, 0); calendar.set(Calendar.MILLISECOND, 0);
        if (calendar.getTimeInMillis() <= System.currentTimeMillis()) { calendar.add(Calendar.DAY_OF_YEAR, 1); }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                Log.d(TAG, "Scheduling exact alarm for: " + calendar.getTime()); alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), pendingIntent);
            } else { Log.w(TAG, "Cannot schedule exact alarm, permission denied."); Toast.makeText(context, "Exact alarm permission needed for daily reminder.", Toast.LENGTH_LONG).show(); }
        } else {
            Log.d(TAG, "Scheduling repeating alarm (pre-Android S) for: " + calendar.getTime()); alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), AlarmManager.INTERVAL_DAY, pendingIntent);
        }
    }

    public static void cancelDailyReminder(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, AlarmReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, ALARM_REQUEST_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (alarmManager != null) { alarmManager.cancel(pendingIntent); pendingIntent.cancel(); Log.d(TAG, "Cancelled daily reminder alarm."); }
    }


    // --- WorkManager Scheduling/Cancelling (Static optional) ---
    public static void scheduleRandomReminders(Context context, boolean enable) {
        WorkManager workManager = WorkManager.getInstance(context);
        if (enable) {
            PeriodicWorkRequest randomWorkRequest = new PeriodicWorkRequest.Builder(RandomReminderWorker.class, 12, TimeUnit.HOURS).setInitialDelay(6, TimeUnit.HOURS).addTag(RANDOM_REMINDER_WORK_TAG).build();
            workManager.enqueueUniquePeriodicWork(RANDOM_REMINDER_WORK_TAG, ExistingPeriodicWorkPolicy.KEEP, randomWorkRequest);
            Log.d(TAG, "Random reminder work enqueued.");
        } else {
            workManager.cancelUniqueWork(RANDOM_REMINDER_WORK_TAG);
            Log.d(TAG, "Random reminder work cancelled.");
        }
    }
}
