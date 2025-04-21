package com.example.fyp.ui.todos;

import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
//import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.text.TextUtils;
import android.util.Log; // Import Log
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
//import android.widget.EditText;
// import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.example.fyp.R;
import com.example.fyp.data.model.Subtask;
import com.example.fyp.data.model.Task;
import com.example.fyp.ui.ai.AiTaskActivity;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth; // Import FirebaseAuth
import com.google.firebase.auth.FirebaseUser; // Import FirebaseUser
import com.google.firebase.firestore.CollectionReference; // Import CollectionReference
import com.google.firebase.firestore.DocumentChange; // Import DocumentChange
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore; // Import FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException; // Import Exception
import com.google.firebase.firestore.ListenerRegistration; // Import ListenerRegistration
import com.google.firebase.firestore.Query; // Import Query
import com.google.firebase.firestore.WriteBatch; // Import WriteBatch

import java.util.ArrayList;
import java.util.Date; // Import Date
import java.util.List;

public class TodosFragment extends Fragment implements TaskAdapter.OnTaskInteractionListener {

    // ... (TAG, UI elements like RecyclerView, FAB, ProgressBar) ...
    private static final String TAG = "TodosFragment";
    private RecyclerView tasksRecyclerView;
    private TaskAdapter taskAdapter;
    private FloatingActionButton fabAddTask;
    private ProgressBar progressBar;

    // ... (Firestore variables) ...
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;
    private CollectionReference tasksCollection;
    private ListenerRegistration tasksListenerRegistration;

    // ... (constructor, onCreate, onCreateView remain similar) ...
    public TodosFragment() {}
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Log.e(TAG, "User not logged in!");
            return;
        }
        tasksCollection = db.collection("users").document(currentUser.getUid()).collection("tasks");
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_todos, container, false);
        progressBar = view.findViewById(R.id.progress_bar_todos);
        return view;
    }


    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (currentUser == null) return;

        tasksRecyclerView = view.findViewById(R.id.recycler_view_tasks);
        fabAddTask = view.findViewById(R.id.fab_add_task);
        progressBar = view.findViewById(R.id.progress_bar_todos);

        taskAdapter = new TaskAdapter(requireContext(), this);
        tasksRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        tasksRecyclerView.setAdapter(taskAdapter);

        // --- Updated FAB click listener ---
        fabAddTask.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), AiTaskActivity.class);
            startActivity(intent);
        });

        loadTasks();
    }

    // --- REMOVE showAddTaskDialog() method ---
    // private void showAddTaskDialog() { ... }

    // --- REMOVE addTaskToFirestore() method (will be done in AiTaskActivity) ---
    // private void addTaskToFirestore(Task task) { ... }

    // ... (loadTasks, Listener implementations, showProgressBar, onDestroyView remain the same) ...
    private void loadTasks() {
        if (tasksCollection == null) return;
        showProgressBar(true);
        tasksListenerRegistration = tasksCollection.orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener((snapshots, e) -> {
                    showProgressBar(false);
                    if (e != null) {
                        Log.w(TAG, "Listen failed.", e);
                        Toast.makeText(getContext(), "Failed to load tasks: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (snapshots != null) {
                        List<Task> updatedTaskList = snapshots.toObjects(Task.class);
                        taskAdapter.setTasks(updatedTaskList);
                        Log.d(TAG, "Tasks loaded/updated: " + updatedTaskList.size());
                    } else {
                        Log.d(TAG, "Current data: null");
                        taskAdapter.setTasks(new ArrayList<>());
                    }
                });
    }

    @Override
    public void onTaskCheckedChange(int position, boolean isChecked) {
        Task task = taskAdapter.getTaskAt(position);
        if (task != null && tasksCollection != null) {
            Log.d(TAG, "Updating task check state: " + task.getId() + " to " + isChecked);
            tasksCollection.document(task.getId()).update("checked", isChecked)
                    .addOnSuccessListener(aVoid -> Log.d(TAG, "Task check state updated successfully."))
                    .addOnFailureListener(e -> Log.w(TAG, "Error updating task check state", e));
        } else {
            Log.e(TAG, "Cannot update task check state - Task or Collection is null");
        }
    }

    @Override
    public void onSubtaskCheckedChange(int taskPosition, int subtaskPosition, boolean isChecked) {
        Task task = taskAdapter.getTaskAt(taskPosition);
        if (task != null && task.getSubtasks() != null && subtaskPosition >= 0 && subtaskPosition < task.getSubtasks().size() && tasksCollection != null) {
            List<Subtask> currentSubtasks = new ArrayList<>(task.getSubtasks());
            if (subtaskPosition < currentSubtasks.size()) {
                currentSubtasks.get(subtaskPosition).setChecked(isChecked);
                Log.d(TAG, "Updating subtask check state: Task " + task.getId() + ", Subtask Index " + subtaskPosition + " to " + isChecked);
            } else {
                Log.e(TAG, "Subtask position out of bounds!");
                return;
            }
            tasksCollection.document(task.getId()).update("subtasks", currentSubtasks)
                    .addOnSuccessListener(aVoid -> Log.d(TAG, "Subtask list updated successfully."))
                    .addOnFailureListener(e -> Log.w(TAG, "Error updating subtask list", e));
        } else {
            Log.e(TAG, "Cannot update subtask check state - Task, Subtasks, or Collection is null or index invalid");
        }
    }

    @Override
    public void onTaskExpansionChange(int position, boolean isExpanded) {
        Log.d(TAG, "Task at position " + position + " expansion toggled (local state)");
    }

    private void showProgressBar(boolean show) {
        if (progressBar != null) {
            progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        if (tasksRecyclerView != null) tasksRecyclerView.setEnabled(!show);
        if (fabAddTask != null) fabAddTask.setEnabled(!show);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (tasksListenerRegistration != null) {
            tasksListenerRegistration.remove();
            tasksListenerRegistration = null;
            Log.d(TAG,"Firestore listener removed.");
        }
    }

}
