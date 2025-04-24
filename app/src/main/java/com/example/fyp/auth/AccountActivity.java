package com.example.fyp.auth;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;

import android.content.DialogInterface;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem; // Import MenuItem
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.example.fyp.R;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.appbar.MaterialToolbar; // Import if using MaterialToolbar
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserInfo;
import com.google.firebase.auth.UserProfileChangeRequest;

public class AccountActivity extends AppCompatActivity {

    private static final String TAG = "AccountActivity";

    private TextView displayNameTextView;
    private TextView uidTextView;
    private TextView providerTextView;
    private ImageButton editDisplayNameButton;
    private MaterialToolbar toolbar; // Add Toolbar reference

    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account);

        toolbar = findViewById(R.id.account_toolbar); // Find the toolbar
        setSupportActionBar(toolbar); // Set it as the action bar

        // Enable the Up button (back arrow)
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }

        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();

        displayNameTextView = findViewById(R.id.text_view_display_name);
        uidTextView = findViewById(R.id.text_view_uid);
        providerTextView = findViewById(R.id.text_view_provider);
        editDisplayNameButton = findViewById(R.id.button_edit_display_name);

        if (currentUser == null) {
            Log.e(TAG, "Current user is null. Cannot display account info.");
            Toast.makeText(this, "Error: Not logged in.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        loadUserInfo();
        editDisplayNameButton.setOnClickListener(v -> showEditDisplayNameDialog());
    }

    // --- Handle Toolbar Item Clicks ---
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        // Handle arrow click here
        if (item.getItemId() == android.R.id.home) {
            // Navigate back to the previous activity (usually finishes this one)
            // onBackPressed(); // Deprecated approach
            getOnBackPressedDispatcher().onBackPressed(); // Recommended approach
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    // --- End Toolbar Handling ---


    private void loadUserInfo() {
        // ... (loadUserInfo remains the same) ...
        if (currentUser == null) return;
        String displayName = currentUser.getDisplayName();
        if (displayName == null || displayName.trim().isEmpty()) { displayNameTextView.setText("Not Set"); } else { displayNameTextView.setText(displayName); }
        uidTextView.setText("UID: " + currentUser.getUid());
        StringBuilder providerInfo = new StringBuilder("Account provider: "); boolean firstProvider = true;
        for (UserInfo profile : currentUser.getProviderData()) { if (!firstProvider) { providerInfo.append(", "); } String providerId = profile.getProviderId(); if (providerId.equals("password")) { providerInfo.append("Email/Password"); } else if (providerId.equals("google.com")) { providerInfo.append("Google"); } else { providerInfo.append(providerId); } firstProvider = false; }
        providerTextView.setText(providerInfo.toString());
    }

    private void showEditDisplayNameDialog() {
        // ... (showEditDisplayNameDialog remains the same) ...
        AlertDialog.Builder builder = new AlertDialog.Builder(this); LayoutInflater inflater = this.getLayoutInflater(); View dialogView = inflater.inflate(R.layout.dialog_edit_display_name, null); final TextInputEditText nameInput = dialogView.findViewById(R.id.edit_text_edit_name_dialog);
        nameInput.setText(currentUser.getDisplayName() != null ? currentUser.getDisplayName() : "");
        builder.setView(dialogView).setTitle("Edit Display Name").setPositiveButton("Save", (dialog, id) -> { String newName = nameInput.getText().toString().trim(); if (validateDisplayName(newName, nameInput)) { updateDisplayName(newName); } }).setNegativeButton("Cancel", (dialog, id) -> dialog.cancel()); builder.create().show();
    }

    private boolean validateDisplayName(String name, EditText inputField) {
        // ... (validateDisplayName remains the same) ...
        if (TextUtils.isEmpty(name)) { inputField.setError("Display name cannot be empty."); return false; } inputField.setError(null); return true;
    }

    private void updateDisplayName(String newName) {
        // ... (updateDisplayName remains the same) ...
        if (currentUser == null) return; UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder().setDisplayName(newName).build();
        currentUser.updateProfile(profileUpdates).addOnCompleteListener(task -> { if (task.isSuccessful()) { Log.d(TAG, "User profile updated."); Toast.makeText(AccountActivity.this, "Display name updated.", Toast.LENGTH_SHORT).show(); loadUserInfo(); } else { Log.w(TAG, "Error updating profile.", task.getException()); Toast.makeText(AccountActivity.this, "Failed to update display name.", Toast.LENGTH_SHORT).show(); } });
    }
}
