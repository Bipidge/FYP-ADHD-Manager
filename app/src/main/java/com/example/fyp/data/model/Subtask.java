package com.example.fyp.data.model;

import com.google.firebase.firestore.Exclude;

import java.util.UUID;

public class Subtask {
    private String id;
    private String text;
    private boolean isChecked;

    // --- Firestore requires a no-argument constructor ---
    public Subtask() {
        // Needed for Firestore deserialization
        // Generate ID here ONLY if not set by Firestore (e.g., during manual creation)
        // if (this.id == null) {
        //     this.id = UUID.randomUUID().toString();
        // }
    }

    public Subtask(String text) {
        this.id = UUID.randomUUID().toString(); // Keep generating ID on manual creation
        this.text = text;
        this.isChecked = false;
    }

    // --- Getters (Needed by Firestore) ---
    public String getId() { return id; }
    public String getText() { return text; }
    public boolean isChecked() { return isChecked; }

    // --- Setters (Needed by Firestore for deserialization, and for updates) ---
    public void setId(String id) { this.id = id; } // Might be needed if ID comes from Firestore
    public void setText(String text) { this.text = text; }
    public void setChecked(boolean checked) { isChecked = checked; }
}
