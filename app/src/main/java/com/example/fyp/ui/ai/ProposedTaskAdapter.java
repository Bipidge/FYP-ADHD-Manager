package com.example.fyp.ui.ai;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.R; // Import AppCompat R explicitly if needed
import androidx.recyclerview.widget.RecyclerView;

// Remove the import for your app's R if it causes ambiguity,
// or ensure you qualify the style reference fully as shown below.
// import com.example.fyp.R;
import com.example.fyp.data.model.Subtask;
import com.example.fyp.data.model.Task;

import java.util.ArrayList;
import java.util.List;

public class ProposedTaskAdapter extends RecyclerView.Adapter<ProposedTaskAdapter.ProposedTaskViewHolder> {

    private List<Task> proposedTasks = new ArrayList<>();

    public void submitList(List<Task> tasks) {
        proposedTasks.clear();
        if (tasks != null) {
            proposedTasks.addAll(tasks);
        }
        notifyDataSetChanged(); // Use DiffUtil for larger lists
    }

    @NonNull
    @Override
    public ProposedTaskViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(com.example.fyp.R.layout.list_item_proposed_task, parent, false); // Use your app's R for layout
        return new ProposedTaskViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ProposedTaskViewHolder holder, int position) {
        holder.bind(proposedTasks.get(position));
    }

    @Override
    public int getItemCount() {
        return proposedTasks.size();
    }

    static class ProposedTaskViewHolder extends RecyclerView.ViewHolder {
        TextView titleTextView;
        TextView notesTextView;
        LinearLayout subtasksLayout; // Layout to show subtask text

        public ProposedTaskViewHolder(@NonNull View itemView) {
            super(itemView);
            titleTextView = itemView.findViewById(com.example.fyp.R.id.text_view_proposed_title); // Use your app's R for IDs
            notesTextView = itemView.findViewById(com.example.fyp.R.id.text_view_proposed_notes);
            subtasksLayout = itemView.findViewById(com.example.fyp.R.id.layout_proposed_subtasks);
        }

        void bind(Task task) {
            titleTextView.setText(task.getTitle());

            if (!TextUtils.isEmpty(task.getNotes())) {
                notesTextView.setText(task.getNotes());
                notesTextView.setVisibility(View.VISIBLE);
            } else {
                notesTextView.setVisibility(View.GONE);
            }

            subtasksLayout.removeAllViews(); // Clear previous subtasks
            if (task.getSubtasks() != null && !task.getSubtasks().isEmpty()) {
                for (Subtask subtask : task.getSubtasks()) {
                    TextView subtaskTextView = new TextView(itemView.getContext());
                    subtaskTextView.setText("- " + subtask.getText());

                    // *** CORRECTED LINE ***
                    subtaskTextView.setTextAppearance(itemView.getContext(), androidx.appcompat.R.style.TextAppearance_AppCompat_Body1);

                    subtaskTextView.setPadding(0, 2, 0, 2);
                    subtasksLayout.addView(subtaskTextView);
                }
                subtasksLayout.setVisibility(View.VISIBLE);
            } else {
                subtasksLayout.setVisibility(View.GONE);
            }
        }
    }
}
