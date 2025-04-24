package com.example.fyp.ui.clock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.util.Log;
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
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
// Keep ViewModelProvider ONLY if still triggering event for QuestsFragment
import androidx.lifecycle.ViewModelProvider;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.fyp.R;
import com.example.fyp.data.model.Task;
import com.example.fyp.services.PomodoroService;
import com.example.fyp.ui.SharedViewModel; // Keep if needed
import com.prolificinteractive.materialcalendarview.MaterialCalendarView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.prolificinteractive.materialcalendarview.CalendarDay;
import com.prolificinteractive.materialcalendarview.OnDateSelectedListener;

import java.text.SimpleDateFormat; // Using this for date title format now
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class ClockFragment extends Fragment implements OnDateSelectedListener{

    private static final String TAG = "ClockFragment";

    // --- Config Step Enum ---
    private enum ConfigStep { STUDY, BREAK, CYCLES }
    private ConfigStep currentConfigStep = ConfigStep.STUDY;

    // --- UI Elements ---
    private MaterialCalendarView calendarView;
    private FrameLayout pomodoroContentArea;
    private FrameLayout configStepsContainer;
    private LinearLayout studyConfigLayout, breakConfigLayout, cyclesConfigLayout;
    private LinearLayout timerDisplayLayout;
    private LinearLayout selectedDateTasksLayout;
    private RecyclerView selectedDateTasksRecyclerView;
    private TextView selectedDateTitleTextView;
    private TextView noTasksForDateTextView;
    private View pomodoroTaskDivider;

    // Pomodoro Config UI
    private NumberPicker studyHoursPicker, studyMinutesPicker, studySecondsPicker;
    private TextInputEditText studyLabelEditText; private Button studyNextButton;
    private NumberPicker breakHoursPicker, breakMinutesPicker, breakSecondsPicker;
    private TextInputEditText breakLabelEditText; private Button breakBackButton, breakNextButton;
    private NumberPicker cyclesPicker; private Button cyclesBackButton, cyclesDoneStartButton;
    // Pomodoro Timer Display UI
    private TextView phaseLabelTextView, cycleCountTextView, countdownTextView; private Button stopButton;

    // --- Calendar & Task Data ---
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;
    private CollectionReference tasksCollection;
    private final List<Task> allUserTasks = new ArrayList<>();
    private final HashSet<CalendarDay> datesWithTasks = new HashSet<>();
    private CalendarTaskAdapter calendarTaskAdapter;
    private CalendarDay currentlySelectedDate = null;

    // Keep SharedViewModel instance ONLY if triggering completion event for QuestsFragment
    private SharedViewModel sharedViewModel;

    // --- Broadcast Receiver ---
    private BroadcastReceiver timerUpdateReceiver;

    // =========================================================================================
    // Fragment Lifecycle & Setup
    // =========================================================================================

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Keep if needed for QuestsFragment event trigger via Service -> ViewModel
        sharedViewModel = new ViewModelProvider(requireActivity()).get(SharedViewModel.class);
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            tasksCollection = db.collection("users").document(currentUser.getUid()).collection("tasks");
        } else {
            Log.e(TAG, "User not logged in!");
        }
        setupBroadcastReceiver(); // Create the receiver object
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
        setupPomodoroPickers();
        setupPomodoroListeners();
        setupCalendar();
        updateUiForState(PomodoroService.TimerState.IDLE); // Initial UI state

        if (currentUser != null) {
            fetchAllTasksForCalendar();
        }
    }

    private void findViews(@NonNull View view) {
        // Find all UI elements by their IDs...
        calendarView = view.findViewById(R.id.calendar_view);
        pomodoroContentArea = view.findViewById(R.id.pomodoro_content_area);
        configStepsContainer = view.findViewById(R.id.config_steps_container);
        studyConfigLayout = view.findViewById(R.id.layout_config_study);
        breakConfigLayout = view.findViewById(R.id.layout_config_break);
        cyclesConfigLayout = view.findViewById(R.id.layout_config_cycles);
        timerDisplayLayout = view.findViewById(R.id.layout_timer_display);
        selectedDateTasksLayout = view.findViewById(R.id.layout_selected_date_tasks);
        selectedDateTasksRecyclerView = view.findViewById(R.id.recycler_view_selected_date_tasks);
        selectedDateTitleTextView = view.findViewById(R.id.text_view_selected_date_title);
        noTasksForDateTextView = view.findViewById(R.id.text_view_no_tasks_for_date);
        pomodoroTaskDivider = view.findViewById(R.id.divider_pomodoro_tasks);

        studyHoursPicker=view.findViewById(R.id.picker_study_hours); studyMinutesPicker=view.findViewById(R.id.picker_study_minutes); studySecondsPicker=view.findViewById(R.id.picker_study_seconds);
        studyLabelEditText=view.findViewById(R.id.edit_text_study_label); studyNextButton=view.findViewById(R.id.button_study_next);
        breakHoursPicker=view.findViewById(R.id.picker_break_hours); breakMinutesPicker=view.findViewById(R.id.picker_break_minutes); breakSecondsPicker=view.findViewById(R.id.picker_break_seconds);
        breakLabelEditText=view.findViewById(R.id.edit_text_break_label); breakBackButton=view.findViewById(R.id.button_break_back); breakNextButton=view.findViewById(R.id.button_break_next);
        cyclesPicker=view.findViewById(R.id.picker_cycles); cyclesBackButton=view.findViewById(R.id.button_cycles_back); cyclesDoneStartButton=view.findViewById(R.id.button_cycles_done_start);
        phaseLabelTextView=view.findViewById(R.id.text_view_phase_label); cycleCountTextView=view.findViewById(R.id.text_view_cycle_count);
        countdownTextView=view.findViewById(R.id.text_view_countdown); stopButton=view.findViewById(R.id.button_stop_timer);
    }

    private void setupPomodoroPickers() {
        configureTimePickers();
        configureNumberPicker(cyclesPicker, 1, 20, 1); // Default 1 cycle
    }

    private void setupPomodoroListeners() {
        // Basic null checks for safety
        if (studyNextButton == null || breakBackButton == null || breakNextButton == null ||
                cyclesBackButton == null || cyclesDoneStartButton == null || stopButton == null) {
            Log.e(TAG, "One or more Pomodoro buttons are null during setup!");
            return;
        }
        // Step Navigation Listeners
        studyNextButton.setOnClickListener(v -> { currentConfigStep = ConfigStep.BREAK; updateUiForState(PomodoroService.TimerState.IDLE); });
        breakBackButton.setOnClickListener(v -> { currentConfigStep = ConfigStep.STUDY; updateUiForState(PomodoroService.TimerState.IDLE); });
        breakNextButton.setOnClickListener(v -> { currentConfigStep = ConfigStep.CYCLES; updateUiForState(PomodoroService.TimerState.IDLE); });
        cyclesBackButton.setOnClickListener(v -> { currentConfigStep = ConfigStep.BREAK; updateUiForState(PomodoroService.TimerState.IDLE); });
        // Start/Stop Service Listeners
        cyclesDoneStartButton.setOnClickListener(v -> startPomodoroService());
        stopButton.setOnClickListener(v -> stopPomodoroService());
    }

    private void setupCalendar() {
        if (calendarView == null || getContext() == null) return; // Null check
        calendarView.setOnDateChangedListener(this);
        calendarView.setCurrentDate(CalendarDay.today(), false); // Show today's month initially
        calendarTaskAdapter = new CalendarTaskAdapter(requireContext());
        selectedDateTasksRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        selectedDateTasksRecyclerView.setAdapter(calendarTaskAdapter);
        selectedDateTasksRecyclerView.setNestedScrollingEnabled(false);
    }

    // =========================================================================================
    // Broadcast Receiver Setup & Handling
    // =========================================================================================

    private void setupBroadcastReceiver() {
        timerUpdateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent != null && PomodoroService.ACTION_TIMER_UPDATE.equals(intent.getAction())) {
                    String stateName = intent.getStringExtra(PomodoroService.EXTRA_TIMER_STATE);
                    long remaining = intent.getLongExtra(PomodoroService.EXTRA_REMAINING_TIME, 0L);
                    String cycleInfo = intent.getStringExtra(PomodoroService.EXTRA_CYCLE_INFO);
                    String phaseLabel = intent.getStringExtra(PomodoroService.EXTRA_PHASE_LABEL);

                    PomodoroService.TimerState currentState = PomodoroService.TimerState.IDLE;
                    try {
                        if (stateName != null) currentState = PomodoroService.TimerState.valueOf(stateName);
                    } catch (IllegalArgumentException e) { Log.e(TAG, "Invalid timer state received: " + stateName); }

                    Log.d(TAG, "Broadcast received: State=" + currentState + ", Time=" + remaining);

                    // Update UI based on broadcast data
                    updateUiForState(currentState); // Update overall UI visibility
                    if (currentState == PomodoroService.TimerState.STUDY || currentState == PomodoroService.TimerState.BREAK) {
                        updateTimerDisplay(remaining); // Update countdown text
                        if (cycleCountTextView != null) cycleCountTextView.setText(cycleInfo);
                        if (phaseLabelTextView != null) phaseLabelTextView.setText(phaseLabel);
                    } else if (currentState == PomodoroService.TimerState.IDLE) {
                        // Reset timer display text when idle
                        if (countdownTextView != null) countdownTextView.setText(formatTime(0));
                        if (cycleCountTextView != null) cycleCountTextView.setText("");
                        if (phaseLabelTextView != null) phaseLabelTextView.setText("Ready");
                    }
                }
            }
        };
    }

    @Override
    public void onResume() {
        super.onResume();
        // Register receiver
        if (timerUpdateReceiver != null && getContext() != null) {
            IntentFilter filter = new IntentFilter(PomodoroService.ACTION_TIMER_UPDATE);
            LocalBroadcastManager.getInstance(requireContext()).registerReceiver(timerUpdateReceiver, filter);
            Log.d(TAG, "TimerUpdateReceiver registered");
        }
        // Request initial state update or assume IDLE? Assuming IDLE until first broadcast.
        updateUiForState(PomodoroService.TimerState.IDLE);
    }

    @Override
    public void onPause() {
        super.onPause();
        // Unregister receiver
        if (timerUpdateReceiver != null && getContext() != null) {
            try {
                LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(timerUpdateReceiver);
                Log.d(TAG, "TimerUpdateReceiver unregistered");
            } catch (IllegalArgumentException e) {
                // Receiver wasn't registered, ignore.
                Log.w(TAG, "Receiver already unregistered or never registered.");
            }
        }
    }

    // =========================================================================================
    // Calendar & Task List Logic
    // =========================================================================================

    private void fetchAllTasksForCalendar() {
        if (tasksCollection == null) return;
        Log.d(TAG, "Fetching tasks for calendar...");
        tasksCollection.orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    Log.d(TAG, "Fetched " + queryDocumentSnapshots.size() + " tasks for calendar.");
                    allUserTasks.clear(); datesWithTasks.clear(); Calendar cal = Calendar.getInstance();
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        Task task = doc.toObject(Task.class); allUserTasks.add(task);
                        if (task.getTimestamp() != null) {
                            cal.setTime(task.getTimestamp());
                            CalendarDay day = CalendarDay.from(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));
                            datesWithTasks.add(day);
                        }
                    }
                    updateCalendarDecorators(); // Separate method for clarity
                    Log.d(TAG, "Dates with tasks: " + datesWithTasks.size());
                    if(currentlySelectedDate != null) displayTasksForDate(currentlySelectedDate);
                })
                .addOnFailureListener(e -> Log.e(TAG, "Error fetching tasks for calendar", e));
    }

    private void updateCalendarDecorators() {
        if (isAdded() && getContext() != null && calendarView != null) {
            calendarView.removeDecorators(); // Clear existing ones
            if (!datesWithTasks.isEmpty()) {
                int eventColor = ContextCompat.getColor(requireContext(), R.color.purple_500); // Ensure color exists
                calendarView.addDecorator(new EventDecorator(eventColor, datesWithTasks, requireContext()));
            }
        }
    }

    @Override
    public void onDateSelected(@NonNull MaterialCalendarView widget, @NonNull CalendarDay date, boolean selected) {
        if (selected) {
            currentlySelectedDate = date;
            Log.d(TAG, "Date selected: " + date.toString());
            widget.setCurrentDate(date, true); // Ensure calendar navigates
            displayTasksForDate(date);
        } else {
            currentlySelectedDate = null;
            Log.d(TAG, "Date selection cleared.");
        }
        updateUiForState(PomodoroService.TimerState.IDLE); // Update UI based on selection
    }

    private void displayTasksForDate(CalendarDay selectedDay) {
        if (selectedDay == null || getContext() == null || calendarTaskAdapter == null) return; // Safety checks

        List<Task> tasksForDate = new ArrayList<>();
        Calendar calTask = Calendar.getInstance();
        Calendar calSelected = Calendar.getInstance();
        calSelected.set(selectedDay.getYear(), selectedDay.getMonth(), selectedDay.getDay());

        String dateTitleString = "";
        try {
            SimpleDateFormat titleFormat = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());
            dateTitleString = titleFormat.format(calSelected.getTime());
        } catch (Exception e) {
            Log.e(TAG, "Error formatting date title", e);
            dateTitleString = String.format(Locale.getDefault(), "%d/%d/%d", selectedDay.getMonth() + 1, selectedDay.getDay(), selectedDay.getYear());
        }

        if (selectedDateTitleTextView != null) {
            selectedDateTitleTextView.setText("Tasks for " + dateTitleString);
        }

        for (Task task : allUserTasks) {
            if (task.getTimestamp() != null) {
                calTask.setTime(task.getTimestamp());
                if (calTask.get(Calendar.YEAR) == calSelected.get(Calendar.YEAR) &&
                        calTask.get(Calendar.MONTH) == calSelected.get(Calendar.MONTH) &&
                        calTask.get(Calendar.DAY_OF_MONTH) == calSelected.get(Calendar.DAY_OF_MONTH)) {
                    tasksForDate.add(task);
                }
            }
        }

        if (noTasksForDateTextView != null && selectedDateTasksRecyclerView != null) {
            if (tasksForDate.isEmpty()) {
                selectedDateTasksRecyclerView.setVisibility(View.GONE);
                noTasksForDateTextView.setVisibility(View.VISIBLE);
                calendarTaskAdapter.submitList(new ArrayList<>());
            } else {
                noTasksForDateTextView.setVisibility(View.GONE);
                selectedDateTasksRecyclerView.setVisibility(View.VISIBLE);
                calendarTaskAdapter.submitList(tasksForDate);
            }
        }
    }

    // =========================================================================================
    // Pomodoro Interaction Methods
    // =========================================================================================

    private void configureTimePickers() {
        configureNumberPicker(studyHoursPicker, 0, 23, 0); configureNumberPicker(studyMinutesPicker, 0, 59, 0); configureNumberPicker(studySecondsPicker, 0, 59, 5);
        configureNumberPicker(breakHoursPicker, 0, 23, 0); configureNumberPicker(breakMinutesPicker, 0, 59, 0); configureNumberPicker(breakSecondsPicker, 0, 59, 3);
    }
    private void configureNumberPicker(NumberPicker picker, int min, int max, int defaultValue) {
        if (picker == null) return;
        picker.setMinValue(min); picker.setMaxValue(max); picker.setValue(defaultValue); picker.setWrapSelectorWheel(false);
    }

    private void startPomodoroService() {
        if (studyHoursPicker == null || getContext() == null) { if(getContext()!=null)Toast.makeText(getContext(), "Error: UI not ready.", Toast.LENGTH_SHORT).show(); return; }
        long studyH=studyHoursPicker.getValue(); long studyM=studyMinutesPicker.getValue(); long studyS=studySecondsPicker.getValue(); long studyDuration=TimeUnit.HOURS.toMillis(studyH)+TimeUnit.MINUTES.toMillis(studyM)+TimeUnit.SECONDS.toMillis(studyS); long breakH=breakHoursPicker.getValue(); long breakM=breakMinutesPicker.getValue(); long breakS=breakSecondsPicker.getValue(); long breakDuration=TimeUnit.HOURS.toMillis(breakH)+TimeUnit.MINUTES.toMillis(breakM)+TimeUnit.SECONDS.toMillis(breakS); int cycles=cyclesPicker.getValue(); String studyLbl=studyLabelEditText.getText().toString().trim(); if(studyLbl.isEmpty())studyLbl=getString(R.string.default_study_label); String breakLbl=breakLabelEditText.getText().toString().trim(); if(breakLbl.isEmpty())breakLbl=getString(R.string.default_break_label);
        if(studyDuration<=0){Toast.makeText(getContext(),"Study duration must be > 0.",Toast.LENGTH_SHORT).show();currentConfigStep=ConfigStep.STUDY;updateUiForState(PomodoroService.TimerState.IDLE);return;} if(breakDuration<=0){Toast.makeText(getContext(),"Break duration must be > 0.",Toast.LENGTH_SHORT).show();currentConfigStep=ConfigStep.BREAK;updateUiForState(PomodoroService.TimerState.IDLE);return;} if(cycles<=0){Toast.makeText(getContext(),"Cycles must be >= 1.",Toast.LENGTH_SHORT).show();currentConfigStep=ConfigStep.CYCLES;updateUiForState(PomodoroService.TimerState.IDLE);return;}
        Intent serviceIntent = new Intent(getContext(), PomodoroService.class); serviceIntent.setAction(PomodoroService.ACTION_START); serviceIntent.putExtra(PomodoroService.EXTRA_STUDY_DURATION, studyDuration); serviceIntent.putExtra(PomodoroService.EXTRA_BREAK_DURATION, breakDuration); serviceIntent.putExtra(PomodoroService.EXTRA_TOTAL_CYCLES, cycles); serviceIntent.putExtra(PomodoroService.EXTRA_STUDY_LABEL, studyLbl); serviceIntent.putExtra(PomodoroService.EXTRA_BREAK_LABEL, breakLbl);
        Log.d(TAG, "Starting Pomodoro Service..."); ContextCompat.startForegroundService(requireContext(), serviceIntent);
        currentConfigStep = ConfigStep.STUDY; // Reset config step for next time
    }

    private void stopPomodoroService() {
        if (getContext() == null) return;
        Log.d(TAG, "Stopping Pomodoro Service...");
        Intent serviceIntent = new Intent(getContext(), PomodoroService.class);
        serviceIntent.setAction(PomodoroService.ACTION_STOP);
        ContextCompat.startForegroundService(requireContext(), serviceIntent);
        currentConfigStep = ConfigStep.STUDY; // Reset config step
        updateUiForState(PomodoroService.TimerState.IDLE); // Update UI immediately
    }

    private void updateTimerDisplay(long millisUntilFinished) {
        if (countdownTextView != null) {
            countdownTextView.setText(formatTime(millisUntilFinished));
        }
    }
    private String formatTime(long millis) {
        long h=TimeUnit.MILLISECONDS.toHours(millis); long m=TimeUnit.MILLISECONDS.toMinutes(millis)-TimeUnit.HOURS.toMinutes(h); long s=TimeUnit.MILLISECONDS.toSeconds(millis)-TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(millis));
        if(h>0)return String.format(Locale.getDefault(),"%d:%02d:%02d",h,m,s); else return String.format(Locale.getDefault(),"%02d:%02d",m,s);
    }

    // =========================================================================================
    // UI State Update Logic
    // =========================================================================================

    private void updateUiForState(@Nullable PomodoroService.TimerState serviceState) {
        if (serviceState == null) serviceState = PomodoroService.TimerState.IDLE; // Default state

        // Check if view is valid before proceeding
        if (getView() == null || pomodoroContentArea == null || selectedDateTasksLayout == null || pomodoroTaskDivider == null ||
                configStepsContainer == null || timerDisplayLayout == null || studyConfigLayout == null ||
                breakConfigLayout == null || cyclesConfigLayout == null) {
            Log.w(TAG, "updateUiForState called but view or essential children are null.");
            return;
        }

        boolean showSelectedDateTasks = (currentlySelectedDate != null);
        selectedDateTasksLayout.setVisibility(showSelectedDateTasks ? View.VISIBLE : View.GONE);
        pomodoroTaskDivider.setVisibility(showSelectedDateTasks ? View.VISIBLE : View.GONE);

        // Manage Pomodoro internal state based on serviceState parameter
        boolean showConfig = (serviceState == PomodoroService.TimerState.IDLE || serviceState == PomodoroService.TimerState.PAUSED);
        boolean showTimerDisplay = !showConfig;

        configStepsContainer.setVisibility(showConfig ? View.VISIBLE : View.GONE);
        timerDisplayLayout.setVisibility(showTimerDisplay ? View.VISIBLE : View.GONE);

        if (showConfig) {
            // Show the appropriate config step
            studyConfigLayout.setVisibility(currentConfigStep == ConfigStep.STUDY ? View.VISIBLE : View.GONE);
            breakConfigLayout.setVisibility(currentConfigStep == ConfigStep.BREAK ? View.VISIBLE : View.GONE);
            cyclesConfigLayout.setVisibility(currentConfigStep == ConfigStep.CYCLES ? View.VISIBLE : View.GONE);

            // Reset timer text when config is shown
            if (countdownTextView != null) countdownTextView.setText(formatTime(0));
            if (cycleCountTextView != null) cycleCountTextView.setText("");
            if (phaseLabelTextView != null) phaseLabelTextView.setText("Ready");

        }
        // else: Timer display text (countdown, phase, cycle) is updated by the broadcast receiver
    }

    // =========================================================================================
    // Fragment Lifecycle (Cleanup)
    // =========================================================================================

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // No specific timer cleanup needed here anymore
    }
}
