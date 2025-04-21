package com.example.fyp;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

import com.example.fyp.auth.LoginActivity; // Import LoginActivity
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

@SuppressLint("CustomSplashScreen") // Suppress warning

public class LauncherActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Optional: Set a simple splash screen layout if desired
        // setContentView(R.layout.activity_launcher);

        // Use a Handler to delay slightly, allowing Firebase to initialize
        // and improving perceived startup time. Adjust delay as needed.
        new Handler(Looper.getMainLooper()).postDelayed(this::checkUserStatus, 1000); // 1 second delay
    }

    private void checkUserStatus() {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = auth.getCurrentUser();

        Intent intent;
        if (currentUser != null) {
            // User is signed in, go to MainActivity
            intent = new Intent(LauncherActivity.this, MainActivity.class);
        } else {
            // No user is signed in, go to LoginActivity
            intent = new Intent(LauncherActivity.this, LoginActivity.class);
        }

        startActivity(intent);
        finish(); // Close the launcher activity so it's not in the back stack
    }

}
