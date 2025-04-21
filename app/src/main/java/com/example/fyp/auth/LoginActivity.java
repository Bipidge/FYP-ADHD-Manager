package com.example.fyp.auth;

import android.app.Activity; // Import Activity
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log; // Import Log
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResult; // Import ActivityResult
import androidx.activity.result.ActivityResultCallback; // Import ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher; // Import ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts; // Import ActivityResultContracts
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;


import com.example.fyp.MainActivity;
import com.example.fyp.R;
import com.google.android.gms.auth.api.signin.GoogleSignIn; // Import GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount; // Import GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient; // Import GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions; // Import GoogleSignInOptions
import com.google.android.gms.common.SignInButton; // Import SignInButton
import com.google.android.gms.common.api.ApiException; // Import ApiException
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.AuthCredential; // Import AuthCredential
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.GoogleAuthProvider; // Import GoogleAuthProvider

public class LoginActivity extends AppCompatActivity {

    private TextInputEditText emailEditText, passwordEditText;
    private Button loginButton;
    private SignInButton googleSignInButton; // Google Sign In Button
    private TextView goToSignUpTextView;
    private ProgressBar progressBar;
    private FirebaseAuth mAuth;

    // --- Google Sign In ---
    private GoogleSignInClient mGoogleSignInClient;
    private ActivityResultLauncher<Intent> googleSignInLauncher;
    private static final String TAG = "LoginActivityGoogle"; // For logging


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        mAuth = FirebaseAuth.getInstance();

        emailEditText = findViewById(R.id.edit_text_email);
        passwordEditText = findViewById(R.id.edit_text_password);
        loginButton = findViewById(R.id.button_login);
        googleSignInButton = findViewById(R.id.button_google_sign_in); // Find Google button
        goToSignUpTextView = findViewById(R.id.text_view_go_to_signup);
        progressBar = findViewById(R.id.progress_bar_login);

        configureGoogleSignIn();
        setupGoogleSignInLauncher();

        loginButton.setOnClickListener(v -> loginUser());
        googleSignInButton.setOnClickListener(v -> signInWithGoogle());
        goToSignUpTextView.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, SignUpActivity.class));
        });
    }

    // --- Configure Google Sign In ---
    private void configureGoogleSignIn() {
        // Configure Google Sign In
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                // R.string.default_web_client_id is generated from google-services.json
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();

        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);
    }

    // --- Setup Activity Result Launcher for Google Sign In ---
    private void setupGoogleSignInLauncher() {
        googleSignInLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        Intent data = result.getData();
                        // Result returned from launching the Intent from GoogleSignInApi.getSignInIntent(...);
                        Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
                        try {
                            // Google Sign In was successful, authenticate with Firebase
                            GoogleSignInAccount account = task.getResult(ApiException.class);
                            Log.d(TAG, "firebaseAuthWithGoogle:" + account.getId());
                            if (account != null) {
                                firebaseAuthWithGoogle(account.getIdToken());
                            } else {
                                showLoginError("Google sign in failed: Account is null");
                            }
                        } catch (ApiException e) {
                            // Google Sign In failed, update UI appropriately
                            Log.w(TAG, "Google sign in failed", e);
                            showLoginError("Google sign in failed: " + e.getStatusCode());
                        }
                    } else {
                        // Sign-in flow cancelled or failed
                        showLoginError("Google sign in cancelled or failed.");
                    }
                });
    }


    // --- Start Google Sign In Flow ---
    private void signInWithGoogle() {
        progressBar.setVisibility(View.VISIBLE);
        setLoginControlsEnabled(false); // Disable buttons during sign in
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        googleSignInLauncher.launch(signInIntent);
    }

    // --- Authenticate with Firebase using Google Token ---
    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        // Sign in success
                        Toast.makeText(LoginActivity.this, "Google Sign In Successful.", Toast.LENGTH_SHORT).show();
                        navigateToMainActivity();
                    } else {
                        // If sign in fails, display a message to the user.
                        showLoginError("Firebase Authentication Failed: " + task.getException().getMessage());
                    }
                    progressBar.setVisibility(View.GONE);
                    setLoginControlsEnabled(true); // Re-enable buttons
                });
    }


    // --- Existing Email/Password Login ---
    private void loginUser() {
        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();

        // Validation remains the same...
        if (TextUtils.isEmpty(email)) {
            emailEditText.setError("Email is required.");
            emailEditText.requestFocus();
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailEditText.setError("Please enter a valid email.");
            emailEditText.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(password)) {
            passwordEditText.setError("Password is required.");
            passwordEditText.requestFocus();
            return;
        }


        progressBar.setVisibility(View.VISIBLE);
        setLoginControlsEnabled(false); // Disable buttons

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(LoginActivity.this, "Login Successful.", Toast.LENGTH_SHORT).show();
                        navigateToMainActivity();
                    } else {
                        showLoginError("Authentication Failed: " + task.getException().getMessage());
                    }
                    progressBar.setVisibility(View.GONE);
                    setLoginControlsEnabled(true); // Re-enable buttons
                });
    }

    // --- Helper to navigate to Main Activity ---
    private void navigateToMainActivity() {
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish(); // Close LoginActivity
    }

    // --- Helper to show errors and hide progress ---
    private void showLoginError(String message) {
        Toast.makeText(LoginActivity.this, message, Toast.LENGTH_LONG).show();
        progressBar.setVisibility(View.GONE);
        setLoginControlsEnabled(true); // Ensure controls are re-enabled on error
    }

    // --- Helper to enable/disable login controls ---
    private void setLoginControlsEnabled(boolean enabled) {
        loginButton.setEnabled(enabled);
        googleSignInButton.setEnabled(enabled);
        goToSignUpTextView.setEnabled(enabled);
        emailEditText.setEnabled(enabled);
        passwordEditText.setEnabled(enabled);
    }
}
