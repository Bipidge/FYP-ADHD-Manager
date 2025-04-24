package com.example.fyp.ui.quests;

import android.content.BroadcastReceiver; // Import
import android.content.Context;
import android.content.Intent; // Import
import android.content.IntentFilter; // Import
import android.content.SharedPreferences;
import android.graphics.drawable.AnimationDrawable;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager; // Import
// Remove ViewModel imports if not used for anything else
// import androidx.lifecycle.Observer;
// import androidx.lifecycle.ViewModelProvider;

import android.os.Handler;
import android.os.Looper;
import android.util.Log; // Import Log
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.example.fyp.R;
import com.example.fyp.services.PomodoroService; // Import service for action constant
// Remove SharedViewModel import if not used
// import com.example.fyp.ui.SharedViewModel;

import java.util.Locale;

public class QuestsFragment extends Fragment{

    // --- Constants ---
    private static final String TAG = "QuestsFragment"; // Tag for logging
    private static final String PREFS_NAME = "PixelPetPrefs";
    private static final String KEY_FOCUS_POINTS = "focusPoints";
    private static final String KEY_POMODOROS = "pomodorosCompleted";
    private static final String KEY_PET_UNLOCKED = "petUnlocked";
    private static final int POMODOROS_FOR_UNLOCK = 5;
    private static final int MAX_TAPS = 5;
    private static final long NAP_DURATION_MS = 5000;
    private static final long HAPPY_ANIM_DURATION_MS = 2000;

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
    // private SharedViewModel sharedViewModel; // Removed unless observing other non-timer states
    private SharedPreferences sharedPreferences;
    private int focusPoints = 0;
    private int pomodorosCompleted = 0;
    private PetState currentPetState = PetState.IDLE;
    private int consecutiveTaps = 0;
    private boolean isTimerCurrentlyRunning = false; // Track timer state locally if needed

    // --- Handlers for timed actions ---
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable napEndRunnable;
    private Runnable happyEndRunnable;
    private Runnable tapResetRunnable;

    // --- Broadcast Receiver ---
    private BroadcastReceiver studyCompleteReceiver;
    private BroadcastReceiver timerUpdateReceiverForPetState; // Optional: for sleep/idle


    public QuestsFragment() { }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // sharedViewModel = new ViewModelProvider(requireActivity()).get(SharedViewModel.class); // Removed
        sharedPreferences = requireActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        setupReceivers(); // Create receiver objects
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
        // REMOVED setupObservers();
        setupTapListener();
        updateUI(); // Initial UI setup
    }

    private void findViews(@NonNull View view) {
        mainLayout = view.findViewById(R.id.layout_quests_main);
        petImageView = view.findViewById(R.id.image_view_pet);
        focusPointsTextView = view.findViewById(R.id.text_view_focus_points);
        unlockLabelTextView = view.findViewById(R.id.text_view_unlock_label);
        unlockProgressBar = view.findViewById(R.id.progress_bar_unlock);
        unlockProgressTextView = view.findViewById(R.id.text_view_unlock_progress);
    }

    private void loadGameState() {
        focusPoints = sharedPreferences.getInt(KEY_FOCUS_POINTS, 0);
        pomodorosCompleted = sharedPreferences.getInt(KEY_POMODOROS, 0);
        // Assume timer is not running initially until first broadcast update
        isTimerCurrentlyRunning = false;
    }

    private void saveGameState() {
        if (sharedPreferences == null) return;
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt(KEY_FOCUS_POINTS, focusPoints);
        editor.putInt(KEY_POMODOROS, pomodorosCompleted);
        editor.apply();
    }

    // --- NEW: Setup Broadcast Receivers ---
    private void setupReceivers() {
        // Receiver for Study Completion
        studyCompleteReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent != null && PomodoroService.ACTION_POMODORO_STUDY_COMPLETE.equals(intent.getAction())) {
                    Log.d(TAG, "Study Complete Broadcast received in QuestsFragment");
                    handlePomodoroCompleted(); // Trigger points/happy animation
                }
            }
        };

        // Optional: Receiver for general Timer Updates (to set sleep/idle state)
        timerUpdateReceiverForPetState = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent != null && PomodoroService.ACTION_TIMER_UPDATE.equals(intent.getAction())) {
                    String stateName = intent.getStringExtra(PomodoroService.EXTRA_TIMER_STATE);
                    PomodoroService.TimerState currentState = PomodoroService.TimerState.IDLE;
                    try {
                        if (stateName != null) currentState = PomodoroService.TimerState.valueOf(stateName);
                    } catch (IllegalArgumentException e) { Log.e(TAG, "Invalid timer state received: "+stateName); }

                    boolean wasRunning = isTimerCurrentlyRunning;
                    isTimerCurrentlyRunning = (currentState == PomodoroService.TimerState.STUDY || currentState == PomodoroService.TimerState.BREAK);

                    // Update pet state ONLY if timer *just* started or stopped,
                    // and pet is not in an uninterruptible state (happy, napping, tapped)
                    if (wasRunning != isTimerCurrentlyRunning &&
                            currentPetState != PetState.HAPPY &&
                            currentPetState != PetState.NAPPING &&
                            currentPetState != PetState.TAPPED) {

                        Log.d(TAG, "Timer state changed relevant to pet: " + isTimerCurrentlyRunning);
                        setPetState(isTimerCurrentlyRunning ? PetState.SLEEPING : PetState.IDLE);
                    }
                }
            }
        };
    }

    private void setupTapListener() {
        if (petImageView == null) return;
        petImageView.setOnClickListener(v -> handlePetTap());
    }

    // --- Game Logic Methods ---

    private void handlePomodoroCompleted() {
        if (currentPetState == PetState.NAPPING) return; // No points if napping

        focusPoints++;
        pomodorosCompleted++;
        saveGameState();
        updateUnlockProgress();
        setPetState(PetState.HAPPY);

        mainHandler.removeCallbacks(happyEndRunnable);
        happyEndRunnable = () -> {
            if (currentPetState == PetState.HAPPY) {
                // Revert to sleep/idle based on remembered timer state AFTER happy anim
                setPetState(isTimerCurrentlyRunning ? PetState.SLEEPING : PetState.IDLE);
            }
        };
        mainHandler.postDelayed(happyEndRunnable, HAPPY_ANIM_DURATION_MS);
    }

    // REMOVED handleTimerStatusChanged (replaced by receiver logic)

    private void handlePetTap() {
        if (currentPetState == PetState.NAPPING || petImageView == null) return;

        mainHandler.removeCallbacks(happyEndRunnable);
        mainHandler.removeCallbacks(tapResetRunnable);

        consecutiveTaps++;
        setPetState(PetState.TAPPED);

        if (consecutiveTaps >= MAX_TAPS) {
            setPetState(PetState.NAPPING);
            consecutiveTaps = 0;

            napEndRunnable = () -> {
                // Revert based on remembered timer state after nap
                setPetState(isTimerCurrentlyRunning ? PetState.SLEEPING : PetState.IDLE);
            };
            mainHandler.postDelayed(napEndRunnable, NAP_DURATION_MS);

        } else {
            tapResetRunnable = () -> {
                if(currentPetState == PetState.TAPPED) {
                    // Revert based on remembered timer state after tap display
                    setPetState(isTimerCurrentlyRunning ? PetState.SLEEPING : PetState.IDLE);
                }
            };
            mainHandler.postDelayed(tapResetRunnable, 500);
            // Reset tap counter if no tap for 2s
            mainHandler.postDelayed(() -> consecutiveTaps = 0, 2000);
        }
    }

    private void setPetState(PetState newState) {
        if (currentPetState == newState && newState != PetState.TAPPED || petImageView == null) return;

        stopCurrentAnimation();
        currentPetState = newState;
        Log.d(TAG, "Setting pet state to: " + newState);

        AnimationDrawable animation;
        try { // Add try-catch for resource loading
            switch (newState) {
                case IDLE:
                    petImageView.setImageResource(R.drawable.anim_pet_idle);
                    animation = (AnimationDrawable) petImageView.getDrawable();
                    if (animation != null) animation.start();
                    break;
                case HAPPY:
                    petImageView.setImageResource(R.drawable.anim_pet_happy);
                    animation = (AnimationDrawable) petImageView.getDrawable();
                    if (animation != null) animation.start();
                    break;
                case SLEEPING:
                    petImageView.setImageResource(R.drawable.anim_pet_sleep);
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting pet drawable resource", e);
            // Maybe set a default placeholder image on error
            // petImageView.setImageResource(R.drawable.pet_placeholder);
        }
        // updateUI(); // Don't call updateUI here to avoid potential loops, call where needed
    }

    private void stopCurrentAnimation() {
        if (petImageView != null && petImageView.getDrawable() instanceof AnimationDrawable) {
            ((AnimationDrawable) petImageView.getDrawable()).stop();
        }
    }

    private void updateUI() {
        if (focusPointsTextView == null) return; // Check if views are ready
        focusPointsTextView.setText(String.format(Locale.getDefault(), "FP: %d", focusPoints));
        updateUnlockProgress();
        // Set initial state based on assumed timer state (will be corrected by broadcast)
        if (currentPetState == PetState.IDLE || currentPetState == PetState.SLEEPING) {
            setPetState(isTimerCurrentlyRunning ? PetState.SLEEPING : PetState.IDLE);
        } else {
            // If currently happy, tapped, napping, let that state persist
            setPetState(currentPetState);
        }
    }

    private void updateUnlockProgress() {
        if (unlockProgressBar == null || unlockProgressTextView == null || unlockLabelTextView == null) return;
        unlockProgressBar.setMax(POMODOROS_FOR_UNLOCK);
        unlockProgressBar.setProgress(Math.min(pomodorosCompleted, POMODOROS_FOR_UNLOCK));
        unlockProgressTextView.setText(String.format(Locale.getDefault(), "%d / %d Pomodoros",
                Math.min(pomodorosCompleted, POMODOROS_FOR_UNLOCK), POMODOROS_FOR_UNLOCK));
        if (pomodorosCompleted >= POMODOROS_FOR_UNLOCK) {
            unlockLabelTextView.setText("Next Pet Unlocked!"); // Example unlock text
        } else {
            unlockLabelTextView.setText("Next Pet:");
        }
    }

    // --- Lifecycle for Receivers ---
    @Override
    public void onResume() {
        super.onResume();
        if (getContext() == null) return;
        // Register receivers
        if (studyCompleteReceiver != null) {
            IntentFilter filter = new IntentFilter(PomodoroService.ACTION_POMODORO_STUDY_COMPLETE);
            LocalBroadcastManager.getInstance(requireContext()).registerReceiver(studyCompleteReceiver, filter);
            Log.d(TAG, "StudyCompleteReceiver registered");
        }
        if (timerUpdateReceiverForPetState != null) {
            IntentFilter filter = new IntentFilter(PomodoroService.ACTION_TIMER_UPDATE);
            LocalBroadcastManager.getInstance(requireContext()).registerReceiver(timerUpdateReceiverForPetState, filter);
            Log.d(TAG, "TimerUpdateReceiverForPetState registered");
        }
        updateUI(); // Refresh UI
    }

    @Override
    public void onPause() {
        super.onPause();
        saveGameState();
        if (getContext() == null) return;
        // Unregister receivers
        try {
            if (studyCompleteReceiver != null) {
                LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(studyCompleteReceiver);
                Log.d(TAG, "StudyCompleteReceiver unregistered");
            }
            if (timerUpdateReceiverForPetState != null) {
                LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(timerUpdateReceiverForPetState);
                Log.d(TAG, "TimerUpdateReceiverForPetState unregistered");
            }
        } catch (IllegalArgumentException e) { Log.w(TAG, "Receiver already unregistered."); }
        // Stop animations etc.
        stopCurrentAnimation();
        mainHandler.removeCallbacksAndMessages(null);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mainHandler.removeCallbacksAndMessages(null); // Clean up handler
    }
}
