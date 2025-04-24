package com.example.fyp.ui.todos;

import android.content.Context; // Import Context
import android.content.Intent;
import android.graphics.Canvas; // Import Canvas
import android.graphics.drawable.ColorDrawable; // Import ColorDrawable
import android.graphics.drawable.Drawable; // Import Drawable
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat; // Import ContextCompat
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper; // Import ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText; // Import EditText for dialog
import android.widget.LinearLayout; // Import LinearLayout for dialog
import android.widget.ProgressBar;
import android.widget.Toast;

import com.example.fyp.R;
import com.example.fyp.data.model.Subtask;
import com.example.fyp.data.model.Task;
import com.example.fyp.ui.ai.AiTaskActivity;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar; // Import Snackbar for undo
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.WriteBatch; // Keep WriteBatch

import java.util.ArrayList;
import java.util.HashMap; // Import HashMap for updates
import java.util.List;
import java.util.Map; // Import Map for updates

public class TodosFragment extends Fragment implements TaskAdapter.OnTaskInteractionListener {

    private static final String TAG = "TodosFragment";
    private RecyclerView tasksRecyclerView;
    private TaskAdapter taskAdapter;
    private FloatingActionButton fabAddTask;
    private ProgressBar progressBar;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;
    private CollectionReference tasksCollection;
    private ListenerRegistration tasksListenerRegistration;

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

        fabAddTask.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), AiTaskActivity.class);
            startActivity(intent);
        });

        // --- Attach ItemTouchHelper for Swipe-to-Delete ---
        attachSwipeToDelete();
        // --- Load Tasks ---
        loadTasks();
    }

    // --- Load Tasks (remains the same) ---
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

    // --- Listener Implementation ---

    @Override
    public void onTaskCheckedChange(int position, boolean isChecked) {
        Task task = taskAdapter.getTaskAt(position);
        if (task != null && tasksCollection != null) {
            tasksCollection.document(task.getId()).update("checked", isChecked)
                    .addOnSuccessListener(aVoid -> Log.d(TAG, "Task check state updated."))
                    .addOnFailureListener(e -> Log.w(TAG, "Error updating task check state", e));
        }
    }

    @Override
    public void onSubtaskCheckedChange(int taskPosition, int subtaskPosition, boolean isChecked) {
        Task task = taskAdapter.getTaskAt(taskPosition);
        if (task != null && task.getSubtasks() != null && subtaskPosition >= 0 && subtaskPosition < task.getSubtasks().size() && tasksCollection != null) {
            List<Subtask> currentSubtasks = new ArrayList<>(task.getSubtasks());
            if (subtaskPosition < currentSubtasks.size()) {
                currentSubtasks.get(subtaskPosition).setChecked(isChecked);
            } else { return; } // Safety check
            tasksCollection.document(task.getId()).update("subtasks", currentSubtasks)
                    .addOnSuccessListener(aVoid -> Log.d(TAG, "Subtask list updated."))
                    .addOnFailureListener(e -> Log.w(TAG, "Error updating subtask list", e));
        }
    }

    @Override
    public void onTaskExpansionChange(int position, boolean isExpanded) {
        // Local UI state, no Firestore update needed based on current design
        Log.d(TAG, "Task expansion toggled (local)");
    }

    @Override
    public void onTaskLongPressed(Task task) {
        Log.d(TAG, "Long pressed task: " + task.getTitle());
        showEditTaskDialog(task); // Show the edit dialog
    }

    // --- Edit Task Dialog ---

    private void showEditTaskDialog(final Task taskToEdit) {
        if (getContext() == null || getActivity() == null) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_edit_task, null);
        builder.setView(dialogView);

        final TextInputEditText titleEditText = dialogView.findViewById(R.id.edit_text_dialog_edit_task_title);
        final TextInputEditText notesEditText = dialogView.findViewById(R.id.edit_text_dialog_edit_task_notes);
        final LinearLayout dynamicSubtasksLayout = dialogView.findViewById(R.id.layout_dialog_edit_dynamic_subtasks);
        final Button addSubtaskButton = dialogView.findViewById(R.id.button_dialog_edit_add_subtask);
        final Button cancelButton = dialogView.findViewById(R.id.button_dialog_edit_cancel);
        final Button saveButton = dialogView.findViewById(R.id.button_dialog_edit_save);

        // Keep track of subtask views and their data within the dialog
        final List<View> subtaskViewList = new ArrayList<>();
        final List<EditText> subtaskEditTextList = new ArrayList<>(); // To easily get text later

        // Pre-fill existing data
        titleEditText.setText(taskToEdit.getTitle());
        notesEditText.setText(taskToEdit.getNotes());

        // Pre-fill existing subtasks
        if (taskToEdit.getSubtasks() != null) {
            for (Subtask sub : taskToEdit.getSubtasks()) {
                addSubtaskEditView(inflater, dynamicSubtasksLayout, subtaskViewList, subtaskEditTextList, sub.getText());
            }
        }

        final AlertDialog dialog = builder.create();

        // Add new subtask button within the dialog
        addSubtaskButton.setOnClickListener(v -> {
            addSubtaskEditView(inflater, dynamicSubtasksLayout, subtaskViewList, subtaskEditTextList, ""); // Add empty one
        });

        cancelButton.setOnClickListener(v -> dialog.dismiss());

        saveButton.setOnClickListener(v -> {
            String newTitle = titleEditText.getText().toString().trim();
            String newNotes = notesEditText.getText().toString().trim();

            if (TextUtils.isEmpty(newTitle)) {
                titleEditText.setError("Task title cannot be empty");
                return;
            }

            // Collect updated/new subtasks
            List<Subtask> updatedSubtasks = new ArrayList<>();
            for (EditText subtaskEditText : subtaskEditTextList) {
                String subtaskText = subtaskEditText.getText().toString().trim();
                if (!TextUtils.isEmpty(subtaskText)) {
                    // Create NEW Subtask objects. We don't try to reuse old IDs here
                    // because the check state is managed separately via the listener.
                    // When saving, we just save the current text list.
                    updatedSubtasks.add(new Subtask(subtaskText));
                    // Note: This approach resets the checked state of subtasks upon editing the list.
                    // A more complex approach would involve mapping old subtasks to new ones if IDs were stable.
                }
            }

            // Update task in Firestore
            updateTaskInFirestore(taskToEdit.getId(), newTitle, newNotes, updatedSubtasks);

            dialog.dismiss();
        });

        dialog.show();
    }

    // Helper to add a subtask row to the edit dialog
    private void addSubtaskEditView(LayoutInflater inflater, LinearLayout container, List<View> viewList, List<EditText> editTextList, String existingText) {
        View subtaskInputView = inflater.inflate(R.layout.dialog_add_subtask_item, container, false); // Reuse the layout
        EditText subtaskEditText = subtaskInputView.findViewById(R.id.edit_text_dialog_subtask_input);
        Button removeSubtaskButton = subtaskInputView.findViewById(R.id.button_dialog_remove_subtask);

        subtaskEditText.setText(existingText); // Pre-fill if editing

        // Store references
        viewList.add(subtaskInputView);
        editTextList.add(subtaskEditText);

        container.addView(subtaskInputView);

        removeSubtaskButton.setOnClickListener(removeBtn -> {
            container.removeView(subtaskInputView);
            viewList.remove(subtaskInputView);
            editTextList.remove(subtaskEditText);
        });
    }

    // --- Update Task in Firestore ---
    private void updateTaskInFirestore(String taskId, String newTitle, String newNotes, List<Subtask> newSubtasks) {
        if (tasksCollection == null || taskId == null) return;

        // Use a Map to update specific fields
        Map<String, Object> updates = new HashMap<>();
        updates.put("title", newTitle);
        updates.put("notes", newNotes);
        updates.put("subtasks", newSubtasks); // Replace the entire subtask list

        showProgressBar(true);
        tasksCollection.document(taskId).update(updates)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Task updated successfully: " + taskId);
                    Toast.makeText(getContext(), "Task updated", Toast.LENGTH_SHORT).show();
                    showProgressBar(false);
                    // UI updates via listener
                })
                .addOnFailureListener(e -> {
                    showProgressBar(false);
                    Log.w(TAG, "Error updating task", e);
                    Toast.makeText(getContext(), "Error updating task: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    // --- Swipe to Delete Logic ---
    private void attachSwipeToDelete() {
        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                0, // No drag & drop
                ItemTouchHelper.LEFT // Swipe left to delete
        ) {
            // Store drawables for performance
            Drawable deleteIcon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_delete_sweep);
            ColorDrawable background = new ColorDrawable(ContextCompat.getColor(requireContext(), R.color.swipe_delete_background)); // Use a color resource

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                return false; // Drag and drop not enabled
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();
                if (position == RecyclerView.NO_POSITION) return; // Check if position is valid

                Task taskToDelete = taskAdapter.getTaskAt(position);
                if (taskToDelete != null) {
                    deleteTaskFromFirestore(taskToDelete.getId());
                    // Show Snackbar with Undo (optional)
                    showUndoSnackbar(taskToDelete); // Pass the deleted task for potential undo
                }
            }

            // Draw background and icon behind the swiped item
            @Override
            public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
                View itemView = viewHolder.itemView;
                int backgroundCornerOffset = 20; // Adjust as needed

                if (dX < 0) { // Swiping left
                    // Draw the red background
                    background.setBounds(itemView.getRight() + (int) dX - backgroundCornerOffset,
                            itemView.getTop(), itemView.getRight(), itemView.getBottom());
                    background.draw(c);

                    // Calculate position of delete icon
                    int iconMargin = (itemView.getHeight() - deleteIcon.getIntrinsicHeight()) / 2;
                    int iconTop = itemView.getTop() + iconMargin;
                    int iconBottom = iconTop + deleteIcon.getIntrinsicHeight();
                    int iconLeft = itemView.getRight() - iconMargin - deleteIcon.getIntrinsicWidth();
                    int iconRight = itemView.getRight() - iconMargin;

                    // Draw the delete icon
                    deleteIcon.setBounds(iconLeft, iconTop, iconRight, iconBottom);
                    deleteIcon.draw(c);
                }
            }

            // Control swipe threshold if needed (optional)
            @Override
            public float getSwipeThreshold(@NonNull RecyclerView.ViewHolder viewHolder) {
                return 0.7f; // User needs to swipe 70% of the width
            }

        }).attachToRecyclerView(tasksRecyclerView);
    }

    // --- Delete Task from Firestore ---
    private void deleteTaskFromFirestore(String taskId) {
        if (tasksCollection == null || taskId == null) return;
        Log.d(TAG, "Deleting task: " + taskId);
        tasksCollection.document(taskId).delete()
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Task deleted successfully from Firestore.");
                    // No Toast here, Snackbar handles feedback
                    // UI updates via snapshot listener
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Error deleting task from Firestore", e);
                    Toast.makeText(getContext(), "Error deleting task: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    // If delete failed, the snapshot listener won't remove it, so UI remains consistent.
                });
    }

    // --- Snackbar for Undo Delete (Optional) ---
    private void showUndoSnackbar(final Task deletedTask) {
        if (getView() == null) return; // Need a view for Snackbar

        Snackbar snackbar = Snackbar.make(getView(), "Task deleted", Snackbar.LENGTH_LONG);
        snackbar.setAction("UNDO", view -> {
            // Undo action: Re-add the task to Firestore
            // Note: This uses 'set' which will overwrite if somehow it wasn't deleted,
            // or create it if it was successfully deleted.
            if (tasksCollection != null && deletedTask != null) {
                tasksCollection.document(deletedTask.getId()).set(deletedTask)
                        .addOnSuccessListener(aVoid -> {
                            Log.d(TAG, "Task undo successful: " + deletedTask.getId());
                            Toast.makeText(getContext(), "Task restored", Toast.LENGTH_SHORT).show();
                            // UI update via listener
                        })
                        .addOnFailureListener(e -> {
                            Log.w(TAG, "Error undoing task delete", e);
                            Toast.makeText(getContext(), "Error restoring task", Toast.LENGTH_SHORT).show();
                        });
            }
        });
        snackbar.show();
    }


    // --- ProgressBar and Listener Cleanup (remain the same) ---
    private void showProgressBar(boolean show) {
        if (progressBar != null) progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
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
