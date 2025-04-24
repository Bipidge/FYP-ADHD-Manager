package com.example.fyp.ui.clock;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import androidx.core.content.ContextCompat;

import com.example.fyp.R; // Use your app's R
import com.prolificinteractive.materialcalendarview.CalendarDay;
import com.prolificinteractive.materialcalendarview.DayViewDecorator;
import com.prolificinteractive.materialcalendarview.DayViewFacade;
import com.prolificinteractive.materialcalendarview.spans.DotSpan; // Example span

import java.util.Collection;
import java.util.HashSet;

/**
 * Decorate days containing events.
 */

public class EventDecorator implements DayViewDecorator {

    private final int color;
    private final HashSet<CalendarDay> dates;
    // private final Drawable highlightDrawable; // Alternative: Use a background drawable

    public EventDecorator(int color, Collection<CalendarDay> dates, Context context) {
        this.color = color;
        this.dates = new HashSet<>(dates);
        // this.highlightDrawable = ContextCompat.getDrawable(context, R.drawable.date_highlight_background); // Example
    }

    @Override
    public boolean shouldDecorate(CalendarDay day) {
        return dates.contains(day); // Highlight if the day is in our set
    }

    @Override
    public void decorate(DayViewFacade view) {
        // Apply a dot span below the date number
        view.addSpan(new DotSpan(8, color)); // Adjust size (5f) and color
        // Or apply a background drawable:
        // view.setBackgroundDrawable(highlightDrawable);
        // Or make text bold, change color, etc.
    }
}
