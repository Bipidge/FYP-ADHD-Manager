package com.example.fyp.data.model;

import com.google.firebase.firestore.Exclude;
import com.google.firebase.firestore.ServerTimestamp;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public class Task {
    private String id;
    private String title;
    private String notes;
    private List<Subtask> subtasks;
    private boolean isChecked;
    private @ServerTimestamp Date timestamp; // Added for ordering

    // --- Exclude UI state from Firestore ---
    @Exclude
    private boolean isExpanded;

    // --- Firestore requires a no-argument constructor ---
    public Task() {
        // Needed for Firestore deserialization
        this.subtasks = new ArrayList<>(); // Initialize list
        this.isExpanded = false; // Default UI state
    }

    // Constructor for manual creation
    public Task(String title, String notes) {
        this.id = UUID.randomUUID().toString(); // Generate a unique ID
        this.title = title;
        this.notes = notes;
        this.subtasks = new ArrayList<>();
        this.isChecked = false;
        this.isExpanded = false; // Start collapsed
        // Timestamp will be set by Firestore on the server or just before saving
    }

    // --- Getters (Needed by Firestore) ---
    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getNotes() { return notes; }
    public List<Subtask> getSubtasks() { return subtasks; } // Firestore needs this
    public boolean isChecked() { return isChecked; }
    public Date getTimestamp() { return timestamp; } // Timestamp getter

    // --- Exclude getter for UI state ---
    @Exclude
    public boolean isExpanded() { return isExpanded; }
    @Exclude
    public boolean areAllSubtasksChecked() { // Helper method, should be excluded
        if (subtasks == null || subtasks.isEmpty()) { // Add null check
            return true;
        }
        for (Subtask sub : subtasks) {
            if (!sub.isChecked()) {
                return false;
            }
        }
        return true;
    }


    // --- Setters (Needed by Firestore & for updates) ---
    public void setId(String id) { this.id = id; }
    public void setTitle(String title) { this.title = title; }
    public void setNotes(String notes) { this.notes = notes; }
    public void setSubtasks(List<Subtask> subtasks) { this.subtasks = subtasks; } // Firestore needs this
    public void setChecked(boolean checked) { isChecked = checked; }
    public void setTimestamp(Date timestamp) { this.timestamp = timestamp; } // Timestamp setter

    // --- Setter for UI state (only used locally) ---
    @Exclude
    public void setExpanded(boolean expanded) { isExpanded = expanded; }

    // Method to add subtask remains useful for building the object before saving
    public void addSubtask(Subtask subtask) {
        if (this.subtasks == null) {
            this.subtasks = new ArrayList<>();
        }
        this.subtasks.add(subtask);
    }
}
