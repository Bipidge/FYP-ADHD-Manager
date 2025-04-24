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

import java.text.SimpleDateFormat;
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
    private NumberPicker studyHoursPicker, studyMinutesPicker, studySecondsPicker;
    private TextInputEditText studyLabelEditText; private Button studyNextButton;
    private NumberPicker breakHoursPicker, breakMinutesPicker, breakSecondsPicker;
    private TextInputEditText breakLabelEditText; private Button breakBackButton, breakNextButton;
    private NumberPicker cyclesPicker; private Button cyclesBackButton, cyclesDoneStartButton;
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
        sharedViewModel = new ViewModelProvider(requireActivity()).get(SharedViewModel.class);
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            tasksCollection = db.collection("users").document(currentUser.getUid()).collection("tasks");
        } else { Log.e(TAG, "User not logged in!"); }
        setupBroadcastReceiver();
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
        updateUiForState(PomodoroService.TimerState.IDLE); // Initial state

        if (currentUser != null) {
            fetchAllTasksForCalendar();
        }
    }

    private void findViews(@NonNull View view) {
        // ... findViewById calls ... (Assume correct based on last fix)
        calendarView = view.findViewById(R.id.calendar_view); pomodoroContentArea = view.findViewById(R.id.pomodoro_content_area); configStepsContainer = view.findViewById(R.id.config_steps_container); studyConfigLayout = view.findViewById(R.id.layout_config_study); breakConfigLayout = view.findViewById(R.id.layout_config_break); cyclesConfigLayout = view.findViewById(R.id.layout_config_cycles); timerDisplayLayout = view.findViewById(R.id.layout_timer_display); selectedDateTasksLayout = view.findViewById(R.id.layout_selected_date_tasks); selectedDateTasksRecyclerView = view.findViewById(R.id.recycler_view_selected_date_tasks); selectedDateTitleTextView = view.findViewById(R.id.text_view_selected_date_title); noTasksForDateTextView = view.findViewById(R.id.text_view_no_tasks_for_date); pomodoroTaskDivider = view.findViewById(R.id.divider_pomodoro_tasks); studyHoursPicker=view.findViewById(R.id.picker_study_hours); studyMinutesPicker=view.findViewById(R.id.picker_study_minutes); studySecondsPicker=view.findViewById(R.id.picker_study_seconds); studyLabelEditText=view.findViewById(R.id.edit_text_study_label); studyNextButton=view.findViewById(R.id.button_study_next); breakHoursPicker=view.findViewById(R.id.picker_break_hours); breakMinutesPicker=view.findViewById(R.id.picker_break_minutes); breakSecondsPicker=view.findViewById(R.id.picker_break_seconds); breakLabelEditText=view.findViewById(R.id.edit_text_break_label); breakBackButton=view.findViewById(R.id.button_break_back); breakNextButton=view.findViewById(R.id.button_break_next); cyclesPicker=view.findViewById(R.id.picker_cycles); cyclesBackButton=view.findViewById(R.id.button_cycles_back); cyclesDoneStartButton=view.findViewById(R.id.button_cycles_done_start); phaseLabelTextView=view.findViewById(R.id.text_view_phase_label); cycleCountTextView=view.findViewById(R.id.text_view_cycle_count); countdownTextView=view.findViewById(R.id.text_view_countdown); stopButton=view.findViewById(R.id.button_stop_timer);
    }

    private void setupPomodoroPickers() {
        configureTimePickers();
        configureNumberPicker(cyclesPicker, 1, 20, 1);
    }

    private void setupPomodoroListeners() {
        if (studyNextButton == null || breakBackButton == null || breakNextButton == null ||
                cyclesBackButton == null || cyclesDoneStartButton == null || stopButton == null) {
            Log.e(TAG, "One or more Pomodoro buttons are null during setup!"); return; }
        studyNextButton.setOnClickListener(v -> { currentConfigStep = ConfigStep.BREAK; updateUiForState(PomodoroService.TimerState.IDLE); });
        breakBackButton.setOnClickListener(v -> { currentConfigStep = ConfigStep.STUDY; updateUiForState(PomodoroService.TimerState.IDLE); });
        breakNextButton.setOnClickListener(v -> { currentConfigStep = ConfigStep.CYCLES; updateUiForState(PomodoroService.TimerState.IDLE); });
        cyclesBackButton.setOnClickListener(v -> { currentConfigStep = ConfigStep.BREAK; updateUiForState(PomodoroService.TimerState.IDLE); });
        cyclesDoneStartButton.setOnClickListener(v -> startPomodoroService());
        stopButton.setOnClickListener(v -> stopPomodoroService());
    }

    private void setupCalendar() {
        if (calendarView == null || getContext() == null) return;
        calendarView.setOnDateChangedListener(this); calendarView.setCurrentDate(CalendarDay.today(), false);
        calendarTaskAdapter = new CalendarTaskAdapter(requireContext()); selectedDateTasksRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext())); selectedDateTasksRecyclerView.setAdapter(calendarTaskAdapter); selectedDateTasksRecyclerView.setNestedScrollingEnabled(false);
    }

    // =========================================================================================
    // Broadcast Receiver Setup & Handling
    // =========================================================================================

    private void setupBroadcastReceiver() {
        timerUpdateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent != null && PomodoroService.ACTION_TIMER_UPDATE.equals(intent.getAction())) {
                    String stateName = intent.getStringExtra(PomodoroService.EXTRA_TIMER_STATE); long remaining = intent.getLongExtra(PomodoroService.EXTRA_REMAINING_TIME, 0L); String cycleInfo = intent.getStringExtra(PomodoroService.EXTRA_CYCLE_INFO); String phaseLabel = intent.getStringExtra(PomodoroService.EXTRA_PHASE_LABEL);
                    PomodoroService.TimerState currentState = PomodoroService.TimerState.IDLE; try { if (stateName != null) currentState = PomodoroService.TimerState.valueOf(stateName); } catch (IllegalArgumentException e) { Log.e(TAG, "Invalid timer state: " + stateName); }
                    Log.d(TAG, "Broadcast received: State=" + currentState + ", Time=" + remaining);
                    updateUiForState(currentState);
                    if (currentState == PomodoroService.TimerState.STUDY || currentState == PomodoroService.TimerState.BREAK) {
                        updateTimerDisplay(remaining); if (cycleCountTextView != null) cycleCountTextView.setText(cycleInfo); if (phaseLabelTextView != null) phaseLabelTextView.setText(phaseLabel);
                    } else if (currentState == PomodoroService.TimerState.IDLE) {
                        if (countdownTextView != null) countdownTextView.setText(formatTime(0)); if (cycleCountTextView != null) cycleCountTextView.setText(""); if (phaseLabelTextView != null) phaseLabelTextView.setText("Ready");
                    }
                }
            }
        };
    }

    @Override
    public void onResume() {
        super.onResume();
        if (timerUpdateReceiver != null && getContext() != null) {
            IntentFilter filter = new IntentFilter(PomodoroService.ACTION_TIMER_UPDATE);
            LocalBroadcastManager.getInstance(requireContext()).registerReceiver(timerUpdateReceiver, filter); Log.d(TAG, "TimerUpdateReceiver registered"); }
        updateUiForState(PomodoroService.TimerState.IDLE);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (timerUpdateReceiver != null && getContext() != null) { try { LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(timerUpdateReceiver); Log.d(TAG, "TimerUpdateReceiver unregistered"); } catch (IllegalArgumentException e) { Log.w(TAG, "Receiver already unregistered."); } }
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
                            // *** CORRECTION FOR PROLIFIC CALENDARDAY ***
                            // Prolific's from() method *might* expect 1-based month despite Java Calendar being 0-based
                            // Let's try passing month + 1
                            CalendarDay day = CalendarDay.from(
                                    cal.get(Calendar.YEAR),
                                    cal.get(Calendar.MONTH) + 1, // Use 1-based month (1-12) for CalendarDay.from()
                                    cal.get(Calendar.DAY_OF_MONTH)
                            );
                            // *** END CORRECTION ***
                            Log.d(TAG, "Adding date to highlight set: " + day); // Log the day being added
                            datesWithTasks.add(day);
                        }
                    }
                    updateCalendarDecorators();
                    Log.d(TAG, "Dates with tasks after fetch: " + datesWithTasks.size());
                    if(currentlySelectedDate != null) displayTasksForDate(currentlySelectedDate);
                })
                .addOnFailureListener(e -> Log.e(TAG, "Error fetching tasks for calendar", e));
    }

    private void updateCalendarDecorators() {
        if (isAdded() && getContext() != null && calendarView != null) { calendarView.removeDecorators(); if (!datesWithTasks.isEmpty()) { int color = ContextCompat.getColor(requireContext(), R.color.purple_500); calendarView.addDecorator(new EventDecorator(color, datesWithTasks, requireContext())); } }
    }

    @Override
    public void onDateSelected(@NonNull MaterialCalendarView widget, @NonNull CalendarDay date, boolean selected) {
        if (selected) { currentlySelectedDate = date; widget.setCurrentDate(date, true); displayTasksForDate(date); } else { currentlySelectedDate = null; if (selectedDateTasksLayout != null) selectedDateTasksLayout.setVisibility(View.GONE); if (pomodoroTaskDivider != null) pomodoroTaskDivider.setVisibility(View.GONE); }
    }

    private void displayTasksForDate(CalendarDay selectedDay) {
        if (selectedDay == null || getContext() == null || calendarTaskAdapter == null || selectedDateTitleTextView == null ||
                selectedDateTasksRecyclerView == null || noTasksForDateTextView == null) {
            Log.e(TAG, "displayTasksForDate: Cannot proceed, view or context is null.");
            return;
        }

        List<Task> tasksForDate = new ArrayList<>();
        Calendar calTask = Calendar.getInstance();
        Calendar calSelected = Calendar.getInstance();

        // SET Calendar object using 0-based month from selectedDay (from Prolific library)
        calSelected.set(selectedDay.getYear(), selectedDay.getMonth(), selectedDay.getDay());

        // Format title (uses the correctly set calSelected)
        String dateTitleString; try { SimpleDateFormat titleFormat = new SimpleDateFormat("MMM d, yyyy",Locale.getDefault()); dateTitleString = titleFormat.format(calSelected.getTime()); } catch (Exception e) { Log.e(TAG, "Error fmt date", e); dateTitleString = String.format(Locale.getDefault(), "%d/%d/%d", selectedDay.getMonth() + 1, selectedDay.getDay(), selectedDay.getYear()); }
        selectedDateTitleTextView.setText("Tasks for " + dateTitleString);

        // Filter tasks (uses 0-based comparison)
        for (Task task : allUserTasks) { /* ... comparison logic using 0-based calSelected.get(Calendar.MONTH) ... */
            if (task.getTimestamp() != null) {
                calTask.setTime(task.getTimestamp());
                if (calTask.get(Calendar.YEAR) == calSelected.get(Calendar.YEAR) &&
                        calTask.get(Calendar.MONTH) == calSelected.get(Calendar.MONTH) && // 0-based compare
                        calTask.get(Calendar.DAY_OF_MONTH) == calSelected.get(Calendar.DAY_OF_MONTH)) {
                    tasksForDate.add(task);
                }
            }
        }

        // Update UI
        if (tasksForDate.isEmpty()) { /* ... */ selectedDateTasksRecyclerView.setVisibility(View.GONE); noTasksForDateTextView.setVisibility(View.VISIBLE); calendarTaskAdapter.submitList(new ArrayList<>()); } else { /* ... */ noTasksForDateTextView.setVisibility(View.GONE); selectedDateTasksRecyclerView.setVisibility(View.VISIBLE); calendarTaskAdapter.submitList(tasksForDate); }
        selectedDateTasksLayout.setVisibility(View.VISIBLE); if (pomodoroTaskDivider != null) pomodoroTaskDivider.setVisibility(View.VISIBLE);
    }

    // =========================================================================================
    // Pomodoro Interaction Methods & Helpers (Re-added)
    // =========================================================================================

    private void configureTimePickers() {
        configureNumberPicker(studyHoursPicker, 0, 23, 0);
        configureNumberPicker(studyMinutesPicker, 0, 59, 0);
        configureNumberPicker(studySecondsPicker, 0, 59, 5);
        configureNumberPicker(breakHoursPicker, 0, 23, 0);
        configureNumberPicker(breakMinutesPicker, 0, 59, 0);
        configureNumberPicker(breakSecondsPicker, 0, 59, 3);
    }

    private void configureNumberPicker(NumberPicker picker, int min, int max, int defaultValue) {
        if (picker == null) return;
        picker.setMinValue(min); picker.setMaxValue(max); picker.setValue(defaultValue); picker.setWrapSelectorWheel(false);
    }

    private void setNavigationListeners() {
        if (studyNextButton == null || breakBackButton == null || breakNextButton == null || cyclesBackButton == null || cyclesDoneStartButton == null || stopButton == null) {
            Log.e(TAG, "One or more Pomodoro buttons null."); return;
        }
        studyNextButton.setOnClickListener(v -> { currentConfigStep = ConfigStep.BREAK; updateUiForState(PomodoroService.TimerState.IDLE); });
        breakBackButton.setOnClickListener(v -> { currentConfigStep = ConfigStep.STUDY; updateUiForState(PomodoroService.TimerState.IDLE); });
        breakNextButton.setOnClickListener(v -> { currentConfigStep = ConfigStep.CYCLES; updateUiForState(PomodoroService.TimerState.IDLE); });
        cyclesBackButton.setOnClickListener(v -> { currentConfigStep = ConfigStep.BREAK; updateUiForState(PomodoroService.TimerState.IDLE); });
        cyclesDoneStartButton.setOnClickListener(v -> startPomodoroService());
        stopButton.setOnClickListener(v -> stopPomodoroService());
    }

    private void startPomodoroService() {
        if (studyHoursPicker == null || getContext() == null) { if (getContext() != null) Toast.makeText(getContext(), "Error: UI not ready.", Toast.LENGTH_SHORT).show(); return; }
        long studyH=studyHoursPicker.getValue(); long studyM=studyMinutesPicker.getValue(); long studyS=studySecondsPicker.getValue(); long studyDuration=TimeUnit.HOURS.toMillis(studyH)+TimeUnit.MINUTES.toMillis(studyM)+TimeUnit.SECONDS.toMillis(studyS);
        long breakH=breakHoursPicker.getValue(); long breakM=breakMinutesPicker.getValue(); long breakS=breakSecondsPicker.getValue(); long breakDuration=TimeUnit.HOURS.toMillis(breakH)+TimeUnit.MINUTES.toMillis(breakM)+TimeUnit.SECONDS.toMillis(breakS);
        int cycles=cyclesPicker.getValue(); String studyLbl=studyLabelEditText.getText().toString().trim(); if(studyLbl.isEmpty())studyLbl=getString(R.string.default_study_label); String breakLbl=breakLabelEditText.getText().toString().trim(); if(breakLbl.isEmpty())breakLbl=getString(R.string.default_break_label);
        if(studyDuration<=0){Toast.makeText(getContext(),"Study duration > 0.",Toast.LENGTH_SHORT).show();currentConfigStep=ConfigStep.STUDY;updateUiForState(PomodoroService.TimerState.IDLE);return;}
        if(breakDuration<=0){Toast.makeText(getContext(),"Break duration > 0.",Toast.LENGTH_SHORT).show();currentConfigStep=ConfigStep.BREAK;updateUiForState(PomodoroService.TimerState.IDLE);return;}
        if(cycles<=0){Toast.makeText(getContext(),"Cycles >= 1.",Toast.LENGTH_SHORT).show();currentConfigStep=ConfigStep.CYCLES;updateUiForState(PomodoroService.TimerState.IDLE);return;}
        Intent serviceIntent=new Intent(getContext(),PomodoroService.class); serviceIntent.setAction(PomodoroService.ACTION_START); serviceIntent.putExtra(PomodoroService.EXTRA_STUDY_DURATION,studyDuration); serviceIntent.putExtra(PomodoroService.EXTRA_BREAK_DURATION,breakDuration); serviceIntent.putExtra(PomodoroService.EXTRA_TOTAL_CYCLES,cycles); serviceIntent.putExtra(PomodoroService.EXTRA_STUDY_LABEL,studyLbl); serviceIntent.putExtra(PomodoroService.EXTRA_BREAK_LABEL,breakLbl);
        Log.d(TAG,"Starting Pomodoro Service..."); ContextCompat.startForegroundService(requireContext(),serviceIntent); currentConfigStep=ConfigStep.STUDY;
    }

    private void stopPomodoroService() {
        if(getContext()==null)return; Log.d(TAG,"Stopping Pomodoro Service..."); Intent serviceIntent=new Intent(getContext(),PomodoroService.class); serviceIntent.setAction(PomodoroService.ACTION_STOP); ContextCompat.startForegroundService(requireContext(),serviceIntent); currentConfigStep=ConfigStep.STUDY; updateUiForState(PomodoroService.TimerState.IDLE);
    }

    private void updateTimerDisplay(long millisUntilFinished) {
        if (countdownTextView != null) {
            countdownTextView.setText(formatTime(millisUntilFinished));
        } else { Log.w(TAG, "countdownTextView null in updateTimerDisplay"); }
    }

    private String formatTime(long millis) {
        long h=TimeUnit.MILLISECONDS.toHours(millis); long m=TimeUnit.MILLISECONDS.toMinutes(millis)-TimeUnit.HOURS.toMinutes(h); long s=TimeUnit.MILLISECONDS.toSeconds(millis)-TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(millis));
        if(h>0)return String.format(Locale.getDefault(),"%d:%02d:%02d",h,m,s); else return String.format(Locale.getDefault(),"%02d:%02d",m,s);
    }

    // =========================================================================================
    // UI State Update Logic
    // =========================================================================================

    private void updateUiForState(@Nullable PomodoroService.TimerState serviceState) {
        if (serviceState == null) serviceState = PomodoroService.TimerState.IDLE;
        if (getView() == null || pomodoroContentArea == null || selectedDateTasksLayout == null || pomodoroTaskDivider == null || configStepsContainer == null || timerDisplayLayout == null || studyConfigLayout == null || breakConfigLayout == null || cyclesConfigLayout == null) { Log.w(TAG, "updateUiForState views null."); return; }

        boolean showSelectedDateTasks = (currentlySelectedDate != null);
        selectedDateTasksLayout.setVisibility(showSelectedDateTasks ? View.VISIBLE : View.GONE);
        pomodoroTaskDivider.setVisibility(showSelectedDateTasks ? View.VISIBLE : View.GONE);

        boolean showConfig = (serviceState == PomodoroService.TimerState.IDLE || serviceState == PomodoroService.TimerState.PAUSED);
        boolean showTimerDisplay = !showConfig;
        configStepsContainer.setVisibility(showConfig ? View.VISIBLE : View.GONE);
        timerDisplayLayout.setVisibility(showTimerDisplay ? View.VISIBLE : View.GONE);

        if (showConfig) {
            studyConfigLayout.setVisibility(currentConfigStep == ConfigStep.STUDY ? View.VISIBLE : View.GONE);
            breakConfigLayout.setVisibility(currentConfigStep == ConfigStep.BREAK ? View.VISIBLE : View.GONE);
            cyclesConfigLayout.setVisibility(currentConfigStep == ConfigStep.CYCLES ? View.VISIBLE : View.GONE);
            if (countdownTextView != null) countdownTextView.setText(formatTime(0)); if (cycleCountTextView != null) cycleCountTextView.setText(""); if (phaseLabelTextView != null) phaseLabelTextView.setText("Ready");
        }
        // Timer display updates handled by broadcast receiver
    }

    // =========================================================================================
    // Fragment Lifecycle (Cleanup)
    // =========================================================================================
    @Override
    public void onDestroyView() {
        super.onDestroyView();
    }
}
