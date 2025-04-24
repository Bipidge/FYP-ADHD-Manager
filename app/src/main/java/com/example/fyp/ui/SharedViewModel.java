package com.example.fyp.ui;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.fyp.services.PomodoroService;

public class SharedViewModel extends ViewModel {

    // Event for when a Pomodoro Study session completes (Triggered by Service)
    private final MutableLiveData<Boolean> pomodoroStudyCompletedEvent = new MutableLiveData<>(false);
    // Tracks if any Pomodoro timer (Study or Break) is currently running (Set by Service)
    private final MutableLiveData<Boolean> isTimerRunning = new MutableLiveData<>(false);
    // Holds the current state (IDLE, STUDY, BREAK, PAUSED) from the Service
    private final MutableLiveData<PomodoroService.TimerState> currentTimerState = new MutableLiveData<>(PomodoroService.TimerState.IDLE);
    // Holds the remaining time from the Service
    private final MutableLiveData<Long> remainingTimeMillis = new MutableLiveData<>(0L);
    // Holds the current cycle info from the Service
    private final MutableLiveData<String> cycleInfo = new MutableLiveData<>(""); // e.g., "Cycle 1 of 4"
    // Holds the current phase label from the Service
    private final MutableLiveData<String> phaseLabel = new MutableLiveData<>("Ready");


    // --- Getters for Fragments to Observe ---
    public LiveData<Boolean> getPomodoroStudyCompletedEvent() { return pomodoroStudyCompletedEvent; }
    public LiveData<Boolean> getIsTimerRunning() { return isTimerRunning; }
    public LiveData<PomodoroService.TimerState> getCurrentTimerState() { return currentTimerState; }
    public LiveData<Long> getRemainingTimeMillis() { return remainingTimeMillis; }
    public LiveData<String> getCycleInfo() { return cycleInfo; }
    public LiveData<String> getPhaseLabel() { return phaseLabel; }

    // --- Setters for Service to Update ---
    public void triggerPomodoroStudyCompleted() { pomodoroStudyCompletedEvent.postValue(true); } // Use postValue if called from background thread in service
    public void doneObservingPomodoroCompletion() { pomodoroStudyCompletedEvent.setValue(false); } // Can use setValue if called from main thread observer
    public void setTimerRunning(boolean running) { isTimerRunning.postValue(running); }
    public void setCurrentTimerState(PomodoroService.TimerState state) { currentTimerState.postValue(state); }
    public void setRemainingTimeMillis(long millis) { remainingTimeMillis.postValue(millis); }
    public void setCycleInfo(String info) { cycleInfo.postValue(info); }
    public void setPhaseLabel(String label) { phaseLabel.postValue(label); }
}
