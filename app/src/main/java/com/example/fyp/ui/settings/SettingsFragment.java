package com.example.fyp.ui.settings;

import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import com.example.fyp.R;
import com.example.fyp.auth.LoginActivity;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.firebase.auth.FirebaseAuth;

import java.util.Calendar;
import java.util.Locale;

public class SettingsFragment extends Fragment {

    // Constants
    private static final String PREFS_NAME = "AppSettingsPrefs";
    private static final String KEY_REMINDER_ENABLED = "dailyReminderEnabled";
    private static final String KEY_REMINDER_HOUR = "reminderHour";
    private static final String KEY_REMINDER_MINUTE = "reminderMinute";
    private static final int DEFAULT_REMINDER_HOUR = 9; // 9 AM
    private static final int DEFAULT_REMINDER_MINUTE = 0; // 00


    // UI Elements
    private RelativeLayout accountRow, reminderTimeRow, languageRow, themesRow, logoutRow;
    private SwitchMaterial reminderSwitch;
    private TextView reminderTimeTextView;

    // State
    private SharedPreferences sharedPreferences;
    private boolean isReminderEnabled;
    private int reminderHour;
    private int reminderMinute;
    private FirebaseAuth mAuth;
    private GoogleSignInClient mGoogleSignInClient;


    public SettingsFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sharedPreferences = requireActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        mAuth = FirebaseAuth.getInstance();

        // Configure GoogleSignInClient for sign-out
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(requireActivity(), gso);
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
    }

    private void findViews(@NonNull View view) {
        accountRow = view.findViewById(R.id.setting_row_account);
        reminderSwitch = view.findViewById(R.id.switch_daily_reminder);
        reminderTimeRow = view.findViewById(R.id.setting_row_reminder_time);
        reminderTimeTextView = view.findViewById(R.id.text_view_reminder_time);
        languageRow = view.findViewById(R.id.setting_row_language);
        themesRow = view.findViewById(R.id.setting_row_themes);
        logoutRow = view.findViewById(R.id.setting_row_logout);
    }

    private void loadSettings() {
        isReminderEnabled = sharedPreferences.getBoolean(KEY_REMINDER_ENABLED, false);
        reminderHour = sharedPreferences.getInt(KEY_REMINDER_HOUR, -1);
        reminderMinute = sharedPreferences.getInt(KEY_REMINDER_MINUTE, -1);

        if (reminderHour == -1 || reminderMinute == -1) {
            final Calendar c = Calendar.getInstance();
            reminderHour = c.get(Calendar.HOUR_OF_DAY);
            reminderMinute = c.get(Calendar.MINUTE);
        }
    }

    private void saveBooleanSetting(String key, boolean value) {
        sharedPreferences.edit().putBoolean(key, value).apply();
    }

    private void saveIntSetting(String key, int value) {
        sharedPreferences.edit().putInt(key, value).apply();
    }


    private void setupListeners() {
        reminderSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            isReminderEnabled = isChecked;
            saveBooleanSetting(KEY_REMINDER_ENABLED, isReminderEnabled);
            updateReminderTimeRowVisibility();
            if(isChecked) {
                System.out.println("Reminder Enabled - Schedule Alarm for " + formatTime(reminderHour, reminderMinute));
            } else {
                System.out.println("Reminder Disabled - Cancel Alarm");
            }
        });

        reminderTimeRow.setOnClickListener(v -> showTimePickerDialog());

        View.OnClickListener placeholderClickListener = v -> {
            String text = "";
            int id = v.getId();
            if (id == R.id.setting_row_account) text = getString(R.string.setting_title_account);
            else if (id == R.id.setting_row_language) text = getString(R.string.setting_title_language);
            else if (id == R.id.setting_row_themes) text = getString(R.string.setting_title_themes);
            Toast.makeText(getContext(), text + ": " + getString(R.string.setting_nav_placeholder_toast), Toast.LENGTH_SHORT).show();
        };
        accountRow.setOnClickListener(placeholderClickListener);
        languageRow.setOnClickListener(placeholderClickListener);
        themesRow.setOnClickListener(placeholderClickListener);

        // Logout Listener
        logoutRow.setOnClickListener(v -> logoutUser());
    }

    // --- CORRECTED LOGOUT METHOD ---
    private void logoutUser() {
        // Sign out from Firebase
        mAuth.signOut();

        // Sign out from Google
        mGoogleSignInClient.signOut().addOnCompleteListener(requireActivity(), task -> {
            // This block executes AFTER Google sign-out attempt completes
            Toast.makeText(getContext(), "Logged Out", Toast.LENGTH_SHORT).show();
            navigateToLogin(); // Navigate AFTER sign-out attempt
        });
        // --- NO NAVIGATION CODE HERE ---
    }
    // --- END OF CORRECTED LOGOUT METHOD ---

    private void navigateToLogin() {
        // Navigate back to Login screen and clear the back stack
        Intent intent = new Intent(getActivity(), LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        if (getActivity() != null) {
            getActivity().finish(); // Close MainActivity
        }
    }

    private void showTimePickerDialog() {
        int currentHour = reminderHour != -1 ? reminderHour : Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        int currentMinute = reminderMinute != -1 ? reminderMinute : Calendar.getInstance().get(Calendar.MINUTE);

        TimePickerDialog timePickerDialog = new TimePickerDialog(
                getContext(),
                (view, hourOfDay, minute) -> {
                    reminderHour = hourOfDay;
                    reminderMinute = minute;
                    saveIntSetting(KEY_REMINDER_HOUR, reminderHour);
                    saveIntSetting(KEY_REMINDER_MINUTE, reminderMinute);
                    updateReminderTimeDisplay();
                    if (isReminderEnabled) {
                        System.out.println("Reminder Time Changed - Reschedule Alarm for " + formatTime(reminderHour, reminderMinute));
                    }
                },
                currentHour,
                currentMinute,
                android.text.format.DateFormat.is24HourFormat(getContext())
        );
        timePickerDialog.show();
    }

    private void updateUI() {
        reminderSwitch.setChecked(isReminderEnabled);
        updateReminderTimeDisplay();
        updateReminderTimeRowVisibility();
    }

    private void updateReminderTimeDisplay() {
        if (sharedPreferences.getInt(KEY_REMINDER_HOUR, -1) != -1) {
            reminderTimeTextView.setText(formatTime(reminderHour, reminderMinute));
        } else {
            reminderTimeTextView.setText(getString(R.string.setting_reminder_time_default));
        }
    }

    private void updateReminderTimeRowVisibility() {
        reminderTimeRow.setEnabled(isReminderEnabled);
        reminderTimeTextView.setEnabled(isReminderEnabled);
        reminderTimeRow.setAlpha(isReminderEnabled ? 1.0f : 0.5f);
    }

    private String formatTime(int hour, int minute) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        java.text.DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(getContext());
        return timeFormat.format(calendar.getTime());
    }
}
