package com.example.fyp.ui;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class SharedViewModel extends ViewModel {

    // Event for when a Pomodoro Study session completes
    private final MutableLiveData<Boolean> pomodoroStudyCompleted = new MutableLiveData<>();
    // Tracks if any Pomodoro timer (Study or Break) is currently running
    private final MutableLiveData<Boolean> isTimerRunning = new MutableLiveData<>();

    public LiveData<Boolean> getPomodoroStudyCompletedEvent() {
        // Use a wrapper or specific event class for non-sticky events if needed,
        // but for simple increment, this might be okay. Resetting after observation is key.
        return pomodoroStudyCompleted;
    }

    public LiveData<Boolean> getIsTimerRunning() {
        return isTimerRunning;
    }

    // Called from ClockFragment when a study session finishes
    public void triggerPomodoroStudyCompleted() {
        pomodoroStudyCompleted.setValue(true); // Trigger the event
    }

    // Call this to reset the event after it's been observed
    public void doneObservingPomodoroCompletion() {
        pomodoroStudyCompleted.setValue(false);
    }


    // Called from ClockFragment when timer starts or stops
    public void setTimerRunning(boolean running) {
        // Only update if the value changes to avoid unnecessary notifications
        if (isTimerRunning.getValue() == null || isTimerRunning.getValue() != running) {
            isTimerRunning.setValue(running);
        }
    }
}
