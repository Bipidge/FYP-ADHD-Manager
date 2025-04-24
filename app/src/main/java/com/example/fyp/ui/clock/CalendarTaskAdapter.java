package com.example.fyp.ui.clock;

import android.content.Context;
import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.fyp.R;
import com.example.fyp.data.model.Task; // Assuming Task has isChecked()

import java.util.ArrayList;
import java.util.List;

public class CalendarTaskAdapter extends RecyclerView.Adapter<CalendarTaskAdapter.ViewHolder> {

    private List<Task> tasks = new ArrayList<>();
    private Context context; // Needed for drawables

    public CalendarTaskAdapter(Context context) {
        this.context = context;
    }

    public void submitList(List<Task> newTasks) {
        tasks.clear();
        if (newTasks != null) {
            tasks.addAll(newTasks);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.list_item_calendar_task, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(tasks.get(position), context);
    }

    @Override
    public int getItemCount() {
        return tasks.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView statusImageView;
        TextView titleTextView;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            statusImageView = itemView.findViewById(R.id.image_view_task_status);
            titleTextView = itemView.findViewById(R.id.text_view_calendar_task_title);
        }

        void bind(Task task, Context context) {
            titleTextView.setText(task.getTitle());
            if (task.isChecked()) {
                statusImageView.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_task_complete)); // Use your complete icon
                titleTextView.setPaintFlags(titleTextView.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                titleTextView.setAlpha(0.6f);
            } else {
                statusImageView.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_task_incomplete)); // Use your incomplete icon
                titleTextView.setPaintFlags(titleTextView.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
                titleTextView.setAlpha(1.0f);
            }
        }
    }

}
