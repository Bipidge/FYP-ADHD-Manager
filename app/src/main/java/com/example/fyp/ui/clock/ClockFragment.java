package com.example.fyp.ui.clock;

import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider; // Import ViewModelProvider

import com.example.fyp.R;
import com.example.fyp.ui.SharedViewModel; // Import SharedViewModel
import com.google.android.material.textfield.TextInputEditText;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class ClockFragment extends Fragment{

    // --- Add SharedViewModel ---
    private SharedViewModel sharedViewModel;

    // --- Configuration Steps ---
    private enum ConfigStep { STUDY, BREAK, CYCLES }
    // --- Timer States ---
    private enum TimerState { IDLE, STUDY, BREAK }

    // --- UI Elements (Keep as before) ---
    private FrameLayout configStepsContainer;
    private LinearLayout studyConfigLayout, breakConfigLayout, cyclesConfigLayout, timerDisplayLayout;
    private NumberPicker studyHoursPicker, studyMinutesPicker, studySecondsPicker;
    private TextInputEditText studyLabelEditText;
    private Button studyNextButton;
    private NumberPicker breakHoursPicker, breakMinutesPicker, breakSecondsPicker;
    private TextInputEditText breakLabelEditText;
    private Button breakBackButton, breakNextButton;
    private NumberPicker cyclesPicker;
    private Button cyclesBackButton, cyclesDoneStartButton;
    private TextView phaseLabelTextView, cycleCountTextView, countdownTextView;
    private Button stopButton;

    // --- State Variables (Keep as before) ---
    private ConfigStep currentConfigStep = ConfigStep.STUDY;
    private TimerState currentTimerState = TimerState.IDLE;
    private CountDownTimer currentTimer;
    private long studyDurationMillis = 0, breakDurationMillis = 0;
    private int totalCycles = 1, currentCycle = 0;
    private long remainingMillis = 0;
    private String studyLabel = "", breakLabel = "";


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // --- Initialize SharedViewModel ---
        // Scope it to the Activity so QuestsFragment can access the same instance
        sharedViewModel = new ViewModelProvider(requireActivity()).get(SharedViewModel.class);
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_clock, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        findViews(view);
        configureTimePickers();
        configureNumberPicker(cyclesPicker, 1, 20, 1);
        setNavigationListeners();
        stopButton.setOnClickListener(v -> stopTimer());
        updateUI();
    }

    // --- findViews, configureTimePickers, configureNumberPicker, setNavigationListeners remain the same ---
    private void findViews(@NonNull View view) {
        configStepsContainer = view.findViewById(R.id.config_steps_container);
        studyConfigLayout = view.findViewById(R.id.layout_config_study);
        breakConfigLayout = view.findViewById(R.id.layout_config_break);
        cyclesConfigLayout = view.findViewById(R.id.layout_config_cycles);
        timerDisplayLayout = view.findViewById(R.id.layout_timer_display);

        // Study
        studyHoursPicker = view.findViewById(R.id.picker_study_hours);
        studyMinutesPicker = view.findViewById(R.id.picker_study_minutes);
        studySecondsPicker = view.findViewById(R.id.picker_study_seconds);
        studyLabelEditText = view.findViewById(R.id.edit_text_study_label);
        studyNextButton = view.findViewById(R.id.button_study_next);

        // Break
        breakHoursPicker = view.findViewById(R.id.picker_break_hours);
        breakMinutesPicker = view.findViewById(R.id.picker_break_minutes);
        breakSecondsPicker = view.findViewById(R.id.picker_break_seconds);
        breakLabelEditText = view.findViewById(R.id.edit_text_break_label);
        breakBackButton = view.findViewById(R.id.button_break_back);
        breakNextButton = view.findViewById(R.id.button_break_next);

        // Cycles
        cyclesPicker = view.findViewById(R.id.picker_cycles);
        cyclesBackButton = view.findViewById(R.id.button_cycles_back);
        cyclesDoneStartButton = view.findViewById(R.id.button_cycles_done_start);

        // Timer Display
        phaseLabelTextView = view.findViewById(R.id.text_view_phase_label);
        cycleCountTextView = view.findViewById(R.id.text_view_cycle_count);
        countdownTextView = view.findViewById(R.id.text_view_countdown);
        stopButton = view.findViewById(R.id.button_stop_timer);
    }

    private void configureTimePickers() {
        // Study Time Defaults: 0h 0m 5s (short for testing)
        configureNumberPicker(studyHoursPicker, 0, 23, 0);
        configureNumberPicker(studyMinutesPicker, 0, 59, 0);
        configureNumberPicker(studySecondsPicker, 0, 59, 5); // Short for testing

        // Break Time Defaults: 0h 0m 3s (short for testing)
        configureNumberPicker(breakHoursPicker, 0, 23, 0);
        configureNumberPicker(breakMinutesPicker, 0, 59, 0);
        configureNumberPicker(breakSecondsPicker, 0, 59, 3); // Short for testing
    }

    private void configureNumberPicker(NumberPicker picker, int min, int max, int defaultValue) {
        picker.setMinValue(min);
        picker.setMaxValue(max);
        picker.setValue(defaultValue);
        picker.setWrapSelectorWheel(false);
    }

    private void setNavigationListeners() {
        // Study -> Break
        studyNextButton.setOnClickListener(v -> {
            currentConfigStep = ConfigStep.BREAK;
            updateUI();
        });

        // Break -> Study (Back)
        breakBackButton.setOnClickListener(v -> {
            currentConfigStep = ConfigStep.STUDY;
            updateUI();
        });

        // Break -> Cycles
        breakNextButton.setOnClickListener(v -> {
            currentConfigStep = ConfigStep.CYCLES;
            updateUI();
        });

        // Cycles -> Break (Back)
        cyclesBackButton.setOnClickListener(v -> {
            currentConfigStep = ConfigStep.BREAK;
            updateUI();
        });

        // Cycles -> Done & Start Timer
        cyclesDoneStartButton.setOnClickListener(v -> {
            finalizeConfigurationAndStart();
        });
    }

    private void finalizeConfigurationAndStart() {
        // ... (reading config values remains the same) ...
        long studyH = studyHoursPicker.getValue();
        long studyM = studyMinutesPicker.getValue();
        long studyS = studySecondsPicker.getValue();
        studyDurationMillis = TimeUnit.HOURS.toMillis(studyH)
                + TimeUnit.MINUTES.toMillis(studyM)
                + TimeUnit.SECONDS.toMillis(studyS);

        long breakH = breakHoursPicker.getValue();
        long breakM = breakMinutesPicker.getValue();
        long breakS = breakSecondsPicker.getValue();
        breakDurationMillis = TimeUnit.HOURS.toMillis(breakH)
                + TimeUnit.MINUTES.toMillis(breakM)
                + TimeUnit.SECONDS.toMillis(breakS);

        totalCycles = cyclesPicker.getValue();

        studyLabel = studyLabelEditText.getText().toString().trim();
        if (studyLabel.isEmpty()) studyLabel = getString(R.string.default_study_label);
        breakLabel = breakLabelEditText.getText().toString().trim();
        if (breakLabel.isEmpty()) breakLabel = getString(R.string.default_break_label);

        // Validation remains the same...
        if (studyDurationMillis <= 0) {
            Toast.makeText(getContext(), "Study duration must be greater than 0.", Toast.LENGTH_SHORT).show();
            currentConfigStep = ConfigStep.STUDY; // Go back to study config
            updateUI();
            return;
        }
        if (breakDurationMillis <= 0) {
            Toast.makeText(getContext(), "Break duration must be greater than 0.", Toast.LENGTH_SHORT).show();
            currentConfigStep = ConfigStep.BREAK; // Go back to break config
            updateUI();
            return;
        }
        if (totalCycles <= 0) { // Should be prevented by picker min value, but good practice
            Toast.makeText(getContext(), "Number of cycles must be at least 1.", Toast.LENGTH_SHORT).show();
            currentConfigStep = ConfigStep.CYCLES;
            updateUI();
            return;
        }


        // 2. Reset and Start the Timer Sequence
        currentCycle = 1; // Start with cycle 1
        currentTimerState = TimerState.IDLE; // Mark as idle before starting
        startTimer(studyDurationMillis, TimerState.STUDY); // Start the first study phase
    }

    private void handlePhaseCompletion() {
        playSoundNotification(); // Play sound at the end of each phase

        if (currentTimerState == TimerState.STUDY) {
            // --- Notify ViewModel about Study Completion ---
            sharedViewModel.triggerPomodoroStudyCompleted();
            // --- End Notify ---
            startTimer(breakDurationMillis, TimerState.BREAK); // Always start break

        } else if (currentTimerState == TimerState.BREAK) {
            if (currentCycle >= totalCycles) {
                finishPomodoro(); // All cycles done
            } else {
                currentCycle++; // Increment cycle count *after* break finishes
                startTimer(studyDurationMillis, TimerState.STUDY); // Start next study phase
            }
        }
    }

    private void startTimer(long durationMillis, TimerState nextState) {
        if (currentTimer != null) {
            currentTimer.cancel();
        }

        currentTimerState = nextState;
        remainingMillis = durationMillis;
        updateUI();

        // --- Notify ViewModel about Timer Status ---
        sharedViewModel.setTimerRunning(true);
        // --- End Notify ---

        currentTimer = new CountDownTimer(remainingMillis, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                remainingMillis = millisUntilFinished;
                updateTimerDisplay(remainingMillis);
            }

            @Override
            public void onFinish() {
                currentTimer = null;
                remainingMillis = 0;
                handlePhaseCompletion();
            }
        }.start();
    }

    private void stopTimer() {
        if (currentTimer != null) {
            currentTimer.cancel();
            currentTimer = null;
        }
        currentTimerState = TimerState.IDLE;
        currentConfigStep = ConfigStep.STUDY;
        currentCycle = 0;
        remainingMillis = 0;
        updateUI();

        // --- Notify ViewModel about Timer Status ---
        sharedViewModel.setTimerRunning(false);
        // --- End Notify ---
    }

    private void finishPomodoro() {
        Toast.makeText(getContext(), "Pomodoro Sequence Completed!", Toast.LENGTH_LONG).show();
        stopTimer(); // This will also notify ViewModel that timer stopped
    }

    // --- updateUI, updateTimerDisplay, formatTime, playSoundNotification remain the same ---
    private void updateUI() {
        // Timer Running States
        if (currentTimerState == TimerState.STUDY || currentTimerState == TimerState.BREAK) {
            configStepsContainer.setVisibility(View.GONE); // Hide all config
            timerDisplayLayout.setVisibility(View.VISIBLE);

            // Update Timer Display specifics
            phaseLabelTextView.setText(currentTimerState == TimerState.STUDY ? studyLabel : breakLabel);
            // Ensure cycle count is valid even during the very first study phase
            int displayCycle = Math.max(currentCycle, 1); // Show at least 1
            cycleCountTextView.setText(String.format(Locale.getDefault(), "Cycle %d of %d", displayCycle, totalCycles));
            updateTimerDisplay(remainingMillis); // Show current time for the phase

        }
        // Configuration States (Timer is IDLE)
        else {
            timerDisplayLayout.setVisibility(View.GONE); // Hide timer display
            configStepsContainer.setVisibility(View.VISIBLE); // Show config area

            // Show only the current config step layout
            studyConfigLayout.setVisibility(currentConfigStep == ConfigStep.STUDY ? View.VISIBLE : View.GONE);
            breakConfigLayout.setVisibility(currentConfigStep == ConfigStep.BREAK ? View.VISIBLE : View.GONE);
            cyclesConfigLayout.setVisibility(currentConfigStep == ConfigStep.CYCLES ? View.VISIBLE : View.GONE);
            countdownTextView.setText(formatTime(0)); // Reset timer display text when idle
        }
    }

    private void updateTimerDisplay(long millisUntilFinished) {
        countdownTextView.setText(formatTime(millisUntilFinished));
    }

    private String formatTime(long millis) {
        // Format as H:MM:SS if hours > 0, else MM:SS
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) - TimeUnit.HOURS.toMinutes(hours);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis) - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(millis)); // Correct calculation

        if (hours > 0) {
            return String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
        }
    }

    private void playSoundNotification() {
        // Avoid playing sound immediately on start if coming from IDLE
        if (currentTimerState == TimerState.IDLE) return;

        try {
            Uri notificationSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            Ringtone r = RingtoneManager.getRingtone(getContext(), notificationSoundUri);
            if (r != null) {
                r.play();
            } else {
                // Handle case where default sound is null
                // Toast.makeText(getContext(), "Could not find default notification sound.", Toast.LENGTH_SHORT).show();
                System.err.println("Could not find default notification sound."); // Log instead of toast potentially
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(getContext(), "Could not play sound.", Toast.LENGTH_SHORT).show();
        }
    }

    // --- Lifecycle methods remain the same ---
    @Override
    public void onPause() {
        super.onPause();
        if (currentTimer != null) {
            currentTimer.cancel();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        updateUI();
        if(currentTimerState != TimerState.IDLE && currentTimer == null && remainingMillis > 0){
            updateTimerDisplay(remainingMillis);
        } else if (currentTimerState == TimerState.IDLE) {
            updateUI();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (currentTimer != null) {
            currentTimer.cancel();
            currentTimer = null;
        }
    }
}
