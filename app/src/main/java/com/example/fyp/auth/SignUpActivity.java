package com.example.fyp.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.fyp.MainActivity;
import com.example.fyp.R;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;

public class SignUpActivity extends AppCompatActivity{

    private TextInputEditText emailEditText, passwordEditText;
    private Button signUpButton;
    private TextView goToLoginTextView;
    private ProgressBar progressBar;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        mAuth = FirebaseAuth.getInstance();

        emailEditText = findViewById(R.id.edit_text_email_signup);
        passwordEditText = findViewById(R.id.edit_text_password_signup);
        signUpButton = findViewById(R.id.button_signup);
        goToLoginTextView = findViewById(R.id.text_view_go_to_login);
        progressBar = findViewById(R.id.progress_bar_signup);

        signUpButton.setOnClickListener(v -> registerUser());

        goToLoginTextView.setOnClickListener(v -> {
            // Go back to Login Activity
            // Depending on how you started SignUp, finish() might be enough
            finish();
            // Or start explicitly if needed:
            // startActivity(new Intent(SignUpActivity.this, LoginActivity.class));
        });
    }

    private void registerUser() {
        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();

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

        if (password.length() < 6) {
            passwordEditText.setError("Password should be at least 6 characters.");
            passwordEditText.requestFocus();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        signUpButton.setEnabled(false);
        goToLoginTextView.setEnabled(false);


        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    progressBar.setVisibility(View.GONE);
                    signUpButton.setEnabled(true);
                    goToLoginTextView.setEnabled(true);

                    if (task.isSuccessful()) {
                        // Sign in success, update UI with the signed-in user's information
                        Toast.makeText(SignUpActivity.this, "Registration Successful.", Toast.LENGTH_SHORT).show();

                        // Navigate to MainActivity - Firebase automatically logs user in
                        Intent intent = new Intent(SignUpActivity.this, MainActivity.class);
                        // Clear back stack
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish(); // Close SignUpActivity
                    } else {
                        // If sign up fails, display a message to the user.
                        Toast.makeText(SignUpActivity.this, "Registration Failed: " + task.getException().getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

}
