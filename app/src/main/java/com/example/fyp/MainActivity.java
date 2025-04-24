package com.example.fyp;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import android.os.Bundle;
import android.view.MenuItem;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationBarView;

// packages
import com.example.fyp.ui.todos.TodosFragment;
import com.example.fyp.ui.clock.ClockFragment;
import com.example.fyp.ui.quests.QuestsFragment;
import com.example.fyp.ui.settings.SettingsFragment;

import com.example.fyp.utils.NotificationHelper;

public class MainActivity extends AppCompatActivity {

    BottomNavigationView bottomNavigationView;

    // Declare Fragments (optional, but can help manage instances if needed later)
    TodosFragment todosFragment = new TodosFragment();
    ClockFragment clockFragment = new ClockFragment();
    QuestsFragment questsFragment = new QuestsFragment();
    SettingsFragment settingsFragment = new SettingsFragment();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        NotificationHelper.createNotificationChannels(this);
        setContentView(R.layout.activity_main);

        bottomNavigationView = findViewById(R.id.bottom_navigation);

        // Load the default fragment initially
        loadFragment(todosFragment); // Start with the To-do's screen

        // Set the listener for item selection
        bottomNavigationView.setOnItemSelectedListener(new NavigationBarView.OnItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(MenuItem item) {
                int itemId = item.getItemId(); // Get ID of the selected item

                if (itemId == R.id.nav_todos) {
                    loadFragment(todosFragment);
                    return true; // Indicate event was handled
                } else if (itemId == R.id.nav_clock) {
                    loadFragment(clockFragment);
                    return true;
                } else if (itemId == R.id.nav_quests) {
                    loadFragment(questsFragment);
                    return true;
                } else if (itemId == R.id.nav_settings) {
                    loadFragment(settingsFragment);
                    return true;
                }

                return false; // Item selection not handled
            }
        });
    }

    // Helper method to replace the current fragment
    private void loadFragment(Fragment fragment) {
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.replace(R.id.fragment_container, fragment); // Replace content of FrameLayout
        fragmentTransaction.commit(); // Apply the changes
    }
}