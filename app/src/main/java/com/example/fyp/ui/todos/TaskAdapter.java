package com.example.fyp.ui.todos;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
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
        void onTaskLongPressed(Task task);
    }
    // --- End Interface ---

    private List<Task> taskList = new ArrayList<>();
    private LayoutInflater inflater;
    private Context context;
    private OnTaskInteractionListener listener;

    public TaskAdapter(Context context, OnTaskInteractionListener listener) {
        this.context = context;
        this.inflater = LayoutInflater.from(context);
        this.listener = listener;
    }

    public void setTasks(List<Task> newTaskList) {
        this.taskList.clear();
        if (newTaskList != null) { this.taskList.addAll(newTaskList); }
        notifyDataSetChanged();
    }

    public Task getTaskAt(int position) {
        if (position >= 0 && position < taskList.size()) { return taskList.get(position); }
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
    public int getItemCount() { return taskList.size(); }


    // -------- ViewHolder Class --------
    class TaskViewHolder extends RecyclerView.ViewHolder {
        CheckBox taskCheckBox;
        TextView taskTitleTextView;
        TextView taskNotesPreviewTextView; // *** ADDED NOTES VIEW REFERENCE ***
        ImageView expandCollapseImageView;
        LinearLayout subtasksContainer;
        Drawable arrowDown;
        Drawable arrowUp;

        public TaskViewHolder(@NonNull View itemView) {
            super(itemView);
            taskCheckBox = itemView.findViewById(R.id.checkbox_task);
            taskTitleTextView = itemView.findViewById(R.id.text_view_task_title);
            // *** Find the new TextView by ID ***
            taskNotesPreviewTextView = itemView.findViewById(R.id.text_view_task_notes_preview);
            // *** --- ***
            expandCollapseImageView = itemView.findViewById(R.id.image_view_expand_collapse);
            subtasksContainer = itemView.findViewById(R.id.layout_subtasks_container);

            arrowDown = ContextCompat.getDrawable(context, R.drawable.ic_arrow_down);
            arrowUp = ContextCompat.getDrawable(context, R.drawable.ic_arrow_up);
        }

        void bind(final Task task, final int position) {
            taskTitleTextView.setText(task.getTitle());

            // --- Checkbox Handling (remains the same) ---
            taskCheckBox.setOnCheckedChangeListener(null);
            taskCheckBox.setChecked(task.isChecked());
            updateTextStyle(taskTitleTextView, task.isChecked());
            taskCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (listener != null) {
                    task.setChecked(isChecked);
                    updateTextStyle(taskTitleTextView, isChecked);
                    updateTextStyle(taskNotesPreviewTextView, isChecked); // Also dim notes if checked
                    listener.onTaskCheckedChange(position, isChecked);
                }
            });

            // --- Notes Preview Handling ---
            String notes = task.getNotes();
            if (!TextUtils.isEmpty(notes)) {
                taskNotesPreviewTextView.setText(notes);
                taskNotesPreviewTextView.setVisibility(View.VISIBLE);
                // Dim notes text if the main task is checked
                updateTextStyle(taskNotesPreviewTextView, task.isChecked());
            } else {
                taskNotesPreviewTextView.setVisibility(View.GONE);
            }
            // --- ---

            // --- Expand/Collapse Handling (remains the same) ---
            boolean hasSubtasks = task.getSubtasks() != null && !task.getSubtasks().isEmpty();
            expandCollapseImageView.setImageDrawable( task.isExpanded() ? arrowUp : arrowDown );
            subtasksContainer.setVisibility(task.isExpanded() ? View.VISIBLE : View.GONE);
            if (hasSubtasks) {
                expandCollapseImageView.setVisibility(View.VISIBLE);
                itemView.setOnClickListener(v -> {
                    if (listener != null) {
                        boolean newState = !task.isExpanded();
                        task.setExpanded(newState); // Update local model
                        notifyItemChanged(getAdapterPosition(), null); // Use payload null for default anim
                        // listener.onTaskExpansionChange(position, newState); // Still local only
                    }
                });
            } else {
                expandCollapseImageView.setVisibility(View.INVISIBLE);
                itemView.setOnClickListener(null);
            }


            // --- Long Press Listener (remains the same) ---
            itemView.setOnLongClickListener(v -> {
                if (listener != null) { listener.onTaskLongPressed(task); return true; } return false;
            });


            // --- Subtask Handling (remains the same) ---
            subtasksContainer.removeAllViews();
            if (task.isExpanded() && hasSubtasks) {
                LayoutInflater subtaskInflater = LayoutInflater.from(context);
                List<Subtask> subtaskList = task.getSubtasks();
                for (int i = 0; i < subtaskList.size(); i++) {
                    final Subtask subtask = subtaskList.get(i); final int subtaskPosition = i;
                    View subtaskView = subtaskInflater.inflate(R.layout.list_item_subtask, subtasksContainer, false);
                    CheckBox subtaskCheckBox = subtaskView.findViewById(R.id.checkbox_subtask); TextView subtaskTextView = subtaskView.findViewById(R.id.text_view_subtask_text);
                    subtaskTextView.setText(subtask.getText());
                    subtaskCheckBox.setOnCheckedChangeListener(null); subtaskCheckBox.setChecked(subtask.isChecked()); updateTextStyle(subtaskTextView, subtask.isChecked());
                    subtaskCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                        if (listener != null) { subtask.setChecked(isChecked); updateTextStyle(subtaskTextView, isChecked); listener.onSubtaskCheckedChange(position, subtaskPosition, isChecked); }
                    });
                    subtasksContainer.addView(subtaskView);
                }
            }
        }

        private void updateTextStyle(TextView textView, boolean isChecked) {
            if (textView == null) return; // Safety check
            if (isChecked) {
                textView.setPaintFlags(textView.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                textView.setAlpha(0.5f);
            } else {
                textView.setPaintFlags(textView.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
                textView.setAlpha(1.0f);
            }
        }
    }
}
