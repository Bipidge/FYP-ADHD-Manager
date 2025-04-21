package com.example.fyp.ui.quests;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.AnimationDrawable;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast; // For testing

import com.example.fyp.R;
import com.example.fyp.ui.SharedViewModel; // Import SharedViewModel

import java.util.Locale;

public class QuestsFragment extends Fragment{

    // --- Constants ---
    private static final String PREFS_NAME = "PixelPetPrefs";
    private static final String KEY_FOCUS_POINTS = "focusPoints";
    private static final String KEY_POMODOROS = "pomodorosCompleted";
    private static final String KEY_PET_UNLOCKED = "petUnlocked"; // Example key if needed
    private static final int POMODOROS_FOR_UNLOCK = 5; // Example unlock requirement
    private static final int MAX_TAPS = 5;
    private static final long NAP_DURATION_MS = 5000; // 5 seconds nap
    private static final long HAPPY_ANIM_DURATION_MS = 2000; // Show happy anim for 2 sec

    // --- Pet States ---
    private enum PetState { IDLE, HAPPY, SLEEPING, TAPPED, NAPPING }

    // --- UI Elements ---
    private ConstraintLayout mainLayout;
    private ImageView petImageView;
    private TextView focusPointsTextView;
    private TextView unlockLabelTextView;
    private ProgressBar unlockProgressBar;
    private TextView unlockProgressTextView;

    // --- Game State ---
    private SharedViewModel sharedViewModel;
    private SharedPreferences sharedPreferences;
    private int focusPoints = 0;
    private int pomodorosCompleted = 0;
    private PetState currentPetState = PetState.IDLE;
    private int consecutiveTaps = 0;

    // --- Handlers for timed actions ---
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable napEndRunnable;
    private Runnable happyEndRunnable;
    private Runnable tapResetRunnable;


    public QuestsFragment() { } // Required empty public constructor

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sharedViewModel = new ViewModelProvider(requireActivity()).get(SharedViewModel.class);
        sharedPreferences = requireActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_quests, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        findViews(view);
        loadGameState();
        setupObservers();
        setupTapListener();
        updateUI(); // Initial UI setup based on loaded state
    }

    private void findViews(@NonNull View view) {
        mainLayout = view.findViewById(R.id.layout_quests_main);
        petImageView = view.findViewById(R.id.image_view_pet);
        focusPointsTextView = view.findViewById(R.id.text_view_focus_points);
        unlockLabelTextView = view.findViewById(R.id.text_view_unlock_label); // Can customize later
        unlockProgressBar = view.findViewById(R.id.progress_bar_unlock);
        unlockProgressTextView = view.findViewById(R.id.text_view_unlock_progress);
    }

    private void loadGameState() {
        focusPoints = sharedPreferences.getInt(KEY_FOCUS_POINTS, 0);
        pomodorosCompleted = sharedPreferences.getInt(KEY_POMODOROS, 0);
        // Load unlock status if needed
    }

    private void saveGameState() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt(KEY_FOCUS_POINTS, focusPoints);
        editor.putInt(KEY_POMODOROS, pomodorosCompleted);
        // Save unlock status if needed
        editor.apply();
    }

    private void setupObservers() {
        // Observer for Pomodoro Completion
        sharedViewModel.getPomodoroStudyCompletedEvent().observe(getViewLifecycleOwner(), completed -> {
            if (completed != null && completed) {
                handlePomodoroCompleted();
                sharedViewModel.doneObservingPomodoroCompletion(); // Reset the event
            }
        });

        // Observer for Timer Status
        sharedViewModel.getIsTimerRunning().observe(getViewLifecycleOwner(), isRunning -> {
            if (isRunning != null) {
                handleTimerStatusChanged(isRunning);
            }
        });
    }

    private void setupTapListener() {
        // Listen for taps on the pet
        petImageView.setOnClickListener(v -> handlePetTap());
        // Optionally listen on the whole layout if pet is small
        // mainLayout.setOnClickListener(v -> handlePetTap());
    }

    private void handlePomodoroCompleted() {
        if (currentPetState == PetState.NAPPING) return; // No points if napping

        focusPoints++;
        pomodorosCompleted++;
        // Toast.makeText(getContext(), "+1 FP! Pomodoros: " + pomodorosCompleted, Toast.LENGTH_SHORT).show(); // For testing
        saveGameState(); // Save progress
        updateUnlockProgress(); // Update UI for unlocks
        setPetState(PetState.HAPPY); // Show happy animation

        // Schedule return to IDLE after happy animation duration
        mainHandler.removeCallbacks(happyEndRunnable); // Remove previous if any
        happyEndRunnable = () -> {
            // Only go back to idle if still happy (hasn't started sleeping etc)
            if (currentPetState == PetState.HAPPY) {
                // Re-evaluate state based on timer AFTER happy animation finishes
                Boolean isTimerRunning = sharedViewModel.getIsTimerRunning().getValue();
                setPetState(isTimerRunning != null && isTimerRunning ? PetState.SLEEPING : PetState.IDLE);
            }
        };
        mainHandler.postDelayed(happyEndRunnable, HAPPY_ANIM_DURATION_MS);
    }

    private void handleTimerStatusChanged(boolean isRunning) {
        // Don't interrupt HAPPY, NAPPING, or TAPPED states immediately
        if (currentPetState == PetState.HAPPY || currentPetState == PetState.NAPPING || currentPetState == PetState.TAPPED) {
            return;
        }
        setPetState(isRunning ? PetState.SLEEPING : PetState.IDLE);
    }

    private void handlePetTap() {
        if (currentPetState == PetState.NAPPING) return; // Can't interact while napping

        // Cancel any pending return to IDLE from HAPPY state if tapped
        mainHandler.removeCallbacks(happyEndRunnable);
        // Cancel any pending tap reset
        mainHandler.removeCallbacks(tapResetRunnable);

        consecutiveTaps++;
        setPetState(PetState.TAPPED); // Show tapped visual

        if (consecutiveTaps >= MAX_TAPS) {
            setPetState(PetState.NAPPING);
            consecutiveTaps = 0; // Reset count

            // Schedule end of nap
            napEndRunnable = () -> {
                // Re-evaluate state based on timer AFTER nap finishes
                Boolean isTimerRunning = sharedViewModel.getIsTimerRunning().getValue();
                setPetState(isTimerRunning != null && isTimerRunning ? PetState.SLEEPING : PetState.IDLE);
            };
            mainHandler.postDelayed(napEndRunnable, NAP_DURATION_MS);

        } else {
            // If not napping, schedule a reset back to previous state (or evaluate) after short delay
            tapResetRunnable = () -> {
                // Re-evaluate state based on timer AFTER tap animation/display finishes
                Boolean isTimerRunning = sharedViewModel.getIsTimerRunning().getValue();
                // Important: Only revert if still in TAPPED state
                if(currentPetState == PetState.TAPPED) {
                    setPetState(isTimerRunning != null && isTimerRunning ? PetState.SLEEPING : PetState.IDLE);
                }
            };
            mainHandler.postDelayed(tapResetRunnable, 500); // Show tapped state for 0.5s

            // Also schedule tap counter reset if user pauses tapping
            mainHandler.postDelayed(() -> consecutiveTaps = 0, 2000); // Reset taps if no tap for 2s
        }
    }


    private void setPetState(PetState newState) {
        if (currentPetState == newState && newState != PetState.TAPPED) return; // No change needed unless repeating tap

        // Stop previous animation if it was running
        stopCurrentAnimation();

        currentPetState = newState;

        // Update ImageView based on state
        AnimationDrawable animation;
        switch (newState) {
            case IDLE:
                petImageView.setImageResource(R.drawable.anim_pet_idle);
                animation = (AnimationDrawable) petImageView.getDrawable();
                animation.start();
                break;
            case HAPPY:
                petImageView.setImageResource(R.drawable.anim_pet_happy);
                animation = (AnimationDrawable) petImageView.getDrawable();
                animation.start();
                break;
            case SLEEPING:
                petImageView.setImageResource(R.drawable.anim_pet_sleep); // Static image
                animation = (AnimationDrawable) petImageView.getDrawable();
                animation.start();
                break;
        }
        updateUI(); // Update other UI elements if needed based on state
    }

    private void stopCurrentAnimation() {
        if (petImageView.getDrawable() instanceof AnimationDrawable) {
            AnimationDrawable currentAnim = (AnimationDrawable) petImageView.getDrawable();
            currentAnim.stop();
        }
    }


    private void updateUI() {
        focusPointsTextView.setText(String.format(Locale.getDefault(), "FP: %d", focusPoints));
        updateUnlockProgress();
        // Set initial pet state based on loaded data and current timer status
        if (currentPetState == PetState.IDLE) { // Only check on initial load or after state change
            Boolean isRunning = sharedViewModel.getIsTimerRunning().getValue();
            setPetState(isRunning != null && isRunning ? PetState.SLEEPING : PetState.IDLE);
        }
    }

    private void updateUnlockProgress() {
        // Example: Unlock next pet at 5 pomodoros
        unlockProgressBar.setMax(POMODOROS_FOR_UNLOCK);
        unlockProgressBar.setProgress(Math.min(pomodorosCompleted, POMODOROS_FOR_UNLOCK)); // Cap progress at max
        unlockProgressTextView.setText(String.format(Locale.getDefault(), "%d / %d Pomodoros",
                Math.min(pomodorosCompleted, POMODOROS_FOR_UNLOCK), POMODOROS_FOR_UNLOCK));

        // Check for actual unlock
        if (pomodorosCompleted >= POMODOROS_FOR_UNLOCK) {
            // TODO: Handle unlocking logic (e.g., show message, enable selection)
            // Maybe change label: unlockLabelTextView.setText("New Pet Unlocked!");
        } else {
            unlockLabelTextView.setText("Next Pet:"); // Default label
        }
    }


    @Override
    public void onPause() {
        super.onPause();
        saveGameState(); // Save state when fragment is paused
        // Stop animations and handlers when view is not visible
        stopCurrentAnimation();
        mainHandler.removeCallbacks(napEndRunnable);
        mainHandler.removeCallbacks(happyEndRunnable);
        mainHandler.removeCallbacks(tapResetRunnable);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Clean up handlers to prevent memory leaks
        mainHandler.removeCallbacksAndMessages(null);
    }
}
