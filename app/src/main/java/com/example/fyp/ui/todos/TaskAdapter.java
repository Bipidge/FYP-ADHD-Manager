package com.example.fyp.ui.todos;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.fyp.R;
import com.example.fyp.data.model.Subtask;
import com.example.fyp.data.model.Task;

import java.util.ArrayList;
import java.util.List;

public class TaskAdapter extends RecyclerView.Adapter<TaskAdapter.TaskViewHolder> {

    // --- Interface for callbacks to the Fragment/ViewModel ---
    public interface OnTaskInteractionListener {
        void onTaskCheckedChange(int position, boolean isChecked);
        void onSubtaskCheckedChange(int taskPosition, int subtaskPosition, boolean isChecked);
        void onTaskExpansionChange(int position, boolean isExpanded);
        // Add methods for delete, edit if needed later
    }
    // --- End Interface ---

    private List<Task> taskList = new ArrayList<>(); // Initialize to avoid nulls
    private LayoutInflater inflater;
    private Context context;
    private OnTaskInteractionListener listener; // Listener instance

    // Constructor updated to accept listener
    public TaskAdapter(Context context, OnTaskInteractionListener listener) {
        this.context = context;
        this.inflater = LayoutInflater.from(context);
        this.listener = listener;
    }

    // Method to update the entire list (called by Fragment when Firestore data changes)
    public void setTasks(List<Task> newTaskList) {
        this.taskList.clear();
        if (newTaskList != null) {
            this.taskList.addAll(newTaskList);
        }
        notifyDataSetChanged(); // Simple refresh, consider DiffUtil for performance later
    }

    // --- Getters for specific items (might be useful) ---
    public Task getTaskAt(int position) {
        if (position >= 0 && position < taskList.size()) {
            return taskList.get(position);
        }
        return null;
    }


    @NonNull
    @Override
    public TaskViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = inflater.inflate(R.layout.list_item_task, parent, false);
        return new TaskViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TaskViewHolder holder, int position) {
        Task currentTask = taskList.get(position);
        holder.bind(currentTask, position);
    }

    @Override
    public int getItemCount() {
        return taskList.size();
    }

    // No longer add/remove directly here, Fragment handles data source via Firestore
    // public void addTask(Task task) { ... }
    // public void removeTask(int position) { ... }


    // -------- ViewHolder Class --------
    class TaskViewHolder extends RecyclerView.ViewHolder {
        CheckBox taskCheckBox;
        TextView taskTitleTextView;
        ImageView expandCollapseImageView;
        LinearLayout subtasksContainer;
        Drawable arrowDown; // Cache drawables
        Drawable arrowUp;

        public TaskViewHolder(@NonNull View itemView) {
            super(itemView);
            taskCheckBox = itemView.findViewById(R.id.checkbox_task);
            taskTitleTextView = itemView.findViewById(R.id.text_view_task_title);
            expandCollapseImageView = itemView.findViewById(R.id.image_view_expand_collapse);
            subtasksContainer = itemView.findViewById(R.id.layout_subtasks_container);

            // Cache drawables for performance
            arrowDown = ContextCompat.getDrawable(context, R.drawable.ic_arrow_down);
            arrowUp = ContextCompat.getDrawable(context, R.drawable.ic_arrow_up);
        }

        // Bind data to the views
        void bind(final Task task, final int position) {
            taskTitleTextView.setText(task.getTitle());

            // --- Checkbox Handling ---
            taskCheckBox.setOnCheckedChangeListener(null); // Remove listener before setting state
            taskCheckBox.setChecked(task.isChecked());
            updateTextStyle(taskTitleTextView, task.isChecked());

            taskCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (listener != null) {
                    // Update local state immediately for responsiveness
                    task.setChecked(isChecked);
                    updateTextStyle(taskTitleTextView, isChecked);
                    // Notify Fragment to update Firestore
                    listener.onTaskCheckedChange(position, isChecked);
                    // Optional: Auto-collapse handled locally if needed, based on UI state only
                    // checkAndCollapseIfComplete(task, position);
                }
            });

            // --- Expand/Collapse Handling ---
            expandCollapseImageView.setImageDrawable(
                    task.isExpanded() ? arrowUp : arrowDown
            );
            subtasksContainer.setVisibility(task.isExpanded() ? View.VISIBLE : View.GONE);

            // Use itemView click for expand/collapse IF there are subtasks
            if (task.getSubtasks() != null && !task.getSubtasks().isEmpty()) {
                expandCollapseImageView.setVisibility(View.VISIBLE);
                itemView.setOnClickListener(v -> {
                    if (listener != null) {
                        // Toggle local expansion state immediately
                        boolean newState = !task.isExpanded();
                        task.setExpanded(newState); // Update local model
                        // Notify adapter to redraw this item
                        notifyItemChanged(getAdapterPosition());
                        // Optionally notify listener if expansion needs to be saved,
                        // but we decided against saving it for now.
                        // listener.onTaskExpansionChange(position, newState);
                    }
                });
            } else {
                // No subtasks, hide arrow and disable expand click
                expandCollapseImageView.setVisibility(View.INVISIBLE);
                itemView.setOnClickListener(null); // Remove click listener
            }


            // --- Subtask Handling ---
            subtasksContainer.removeAllViews(); // Clear previous subtasks
            if (task.isExpanded() && task.getSubtasks() != null && !task.getSubtasks().isEmpty()) {
                LayoutInflater subtaskInflater = LayoutInflater.from(context);
                List<Subtask> subtaskList = task.getSubtasks(); // Get the list reference

                for (int i = 0; i < subtaskList.size(); i++) { // Iterate with index
                    final Subtask subtask = subtaskList.get(i);
                    final int subtaskPosition = i; // Capture index for listener

                    View subtaskView = subtaskInflater.inflate(R.layout.list_item_subtask, subtasksContainer, false);
                    CheckBox subtaskCheckBox = subtaskView.findViewById(R.id.checkbox_subtask);
                    TextView subtaskTextView = subtaskView.findViewById(R.id.text_view_subtask_text);

                    subtaskTextView.setText(subtask.getText());

                    subtaskCheckBox.setOnCheckedChangeListener(null); // Remove listener
                    subtaskCheckBox.setChecked(subtask.isChecked());
                    updateTextStyle(subtaskTextView, subtask.isChecked());

                    subtaskCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                        if (listener != null) {
                            // Update local state immediately
                            subtask.setChecked(isChecked);
                            updateTextStyle(subtaskTextView, isChecked);
                            // Notify Fragment to update Firestore
                            listener.onSubtaskCheckedChange(position, subtaskPosition, isChecked);
                            // Optional: check if main task should auto-check (handle in Fragment)
                        }
                    });

                    subtasksContainer.addView(subtaskView);
                }
            }
        }

        // Helper to apply or remove strike-through text style
        private void updateTextStyle(TextView textView, boolean isChecked) {
            if (isChecked) {
                textView.setPaintFlags(textView.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                textView.setAlpha(0.5f);
            } else {
                textView.setPaintFlags(textView.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
                textView.setAlpha(1.0f);
            }
        }

        // Removed checkAndCollapseIfComplete - can be re-implemented based on UI state if desired
    }
}
