package com.example.fyp.ui.ai;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.NestedScrollView;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.example.fyp.BuildConfig; // Import BuildConfig
import com.example.fyp.R;
import com.example.fyp.data.model.Subtask;
import com.example.fyp.data.model.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AiTaskActivity extends AppCompatActivity {

    private static final String TAG = "AiTaskActivity";
    private static final String GEMINI_API_URL_TEMPLATE = "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";
    private static final String GEMINI_MODEL = "gemini-1.5-flash-latest";

    // --- UI Elements (remain the same) ---
    private TextView chatHistoryTextView;
    private EditText userInputEditText;
    private Button sendButton;
    private NestedScrollView scrollView;
    private ProgressBar progressBar;

    // --- Other variables (remain the same) ---
    private RequestQueue requestQueue;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;
    private CollectionReference tasksCollection;
    private final StringBuilder chatHistory = new StringBuilder();


    // --- SYSTEM PROMPT (Keep similar JSON instructions) ---
    private static final String SYSTEM_PROMPT_TEMPLATE =
            "You are an ADHD-friendly task planning assistant. Your goal is to help users break down tasks, manage executive function challenges, and successfully complete their objectives in a supportive way. " +
                    "When the user describes what they want to do, break it down into manageable tasks, and potentially smaller subtasks for each task. " +
                    "IMPORTANT: Respond ONLY with a valid JSON object containing a single key 'tasks'. The value of 'tasks' should be an array of task objects. " +
                    "Each task object must have a 'title' (string) and 'subtasks' (array of objects). Each subtask object must have a 'text' (string). " +
                    "Optionally, a task object can include 'notes' (string). Do not include any explanatory text before or after the JSON object. Ensure the entire output is valid JSON. " +
                    "Example JSON output format: " +
                    "{\"tasks\": [{\"title\": \"Clean Kitchen\", \"notes\": \"Focus on counters first\", \"subtasks\": [{\"text\": \"Clear counters\"}, {\"text\": \"Wipe counters\"}]}, {\"title\": \"Read Chapter 5\", \"notes\": \"\", \"subtasks\": []}]}";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_task);

        // ... (findViews remains the same) ...
        chatHistoryTextView = findViewById(R.id.text_view_chat_history);
        userInputEditText = findViewById(R.id.edit_text_user_input);
        sendButton = findViewById(R.id.button_send_to_ai);
        scrollView = findViewById(R.id.scroll_view_chat);
        progressBar = findViewById(R.id.progress_bar_ai);


        requestQueue = Volley.newRequestQueue(this);
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();

        if (currentUser == null) {
            Log.e(TAG, "User not logged in!");
            Toast.makeText(this, "Error: User not logged in.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        tasksCollection = db.collection("users").document(currentUser.getUid()).collection("tasks");

        setupInitialChat();
        sendButton.setOnClickListener(v -> handleSendClick());
    }

    // ... (setupInitialChat, appendChatMessage, handleSendClick remain the same) ...
    private void setupInitialChat() {
        appendChatMessage("AI", "Hello! What tasks would you like to work on today?");
    }

    private void appendChatMessage(String sender, String message) {
        chatHistory.append("**").append(sender).append(":** ").append(message).append("\n\n");
        chatHistoryTextView.setText(chatHistory.toString());
        scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
    }

    private void handleSendClick() {
        String inputText = userInputEditText.getText().toString().trim();
        if (TextUtils.isEmpty(inputText)) {
            return;
        }
        appendChatMessage("User", inputText);
        userInputEditText.setText("");
        showProgressBar(true);
        sendToGemini(inputText); // Call the Gemini function now
    }


    // --- Updated function to call Gemini API ---
    private void sendToGemini(String userMessage) {
        String apiKey = BuildConfig.GOOGLE_AI_API_KEY; // Use the correct BuildConfig field
        if (TextUtils.isEmpty(apiKey) || apiKey.equals("null")) {
            Log.e(TAG, "Google AI API Key is missing!");
            showError("AI Service not configured.");
            showProgressBar(false);
            return;
        }

        // Construct the full API URL with model and key
        String apiUrl = String.format(GEMINI_API_URL_TEMPLATE, GEMINI_MODEL, apiKey);

        // --- Gemini uses a different payload structure ---
        JSONObject payload = new JSONObject();
        try {
            JSONArray contents = new JSONArray();
            JSONObject content = new JSONObject();
            JSONArray parts = new JSONArray();
            JSONObject part1 = new JSONObject();
            // Combine system prompt and user message into one text part for simple cases
            part1.put("text", SYSTEM_PROMPT_TEMPLATE + "\n\nUser request: " + userMessage);
            parts.put(part1);
            content.put("parts", parts);
            contents.put(content);
            payload.put("contents", contents);

            // --- Optional: Add generation config (safety, output format) ---
            JSONObject generationConfig = new JSONObject();
            generationConfig.put("response_mime_type", "application/json"); // Explicitly request JSON
            generationConfig.put("temperature", 0.7); // Adjust creativity
            // generationConfig.put("maxOutputTokens", 1000);
            payload.put("generationConfig", generationConfig);

            // --- Optional: Add safety settings (adjust as needed) ---
            JSONArray safetySettings = new JSONArray();
            String[] categories = {"HARM_CATEGORY_HARASSMENT", "HARM_CATEGORY_HATE_SPEECH", "HARM_CATEGORY_SEXUALLY_EXPLICIT", "HARM_CATEGORY_DANGEROUS_CONTENT"};
            for(String category : categories){
                JSONObject setting = new JSONObject();
                setting.put("category", category);
                setting.put("threshold", "BLOCK_MEDIUM_AND_ABOVE"); // Or BLOCK_LOW_AND_ABOVE, BLOCK_NONE
                safetySettings.put(setting);
            }
            payload.put("safetySettings", safetySettings);


            Log.d(TAG, "Gemini Payload: " + payload.toString());

        } catch (JSONException e) {
            Log.e(TAG, "Error creating Gemini JSON payload", e);
            showError("Error preparing AI request.");
            showProgressBar(false);
            return;
        }

        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(
                Request.Method.POST, apiUrl, payload,
                response -> {
                    Log.d(TAG, "Gemini Response: " + response.toString());
                    parseAndSaveTasksFromGemini(response); // Use Gemini-specific parser
                    showProgressBar(false);
                },
                error -> {
                    Log.e(TAG, "Volley Error (Gemini): ", error);
                    String errorMessage = "AI request failed.";
                    if (error.networkResponse != null) {
                        errorMessage += " Status: " + error.networkResponse.statusCode;
                        try {
                            String responseBody = new String(error.networkResponse.data, "utf-8");
                            Log.e(TAG, "Error Body: "+ responseBody);
                            JSONObject errorJson = new JSONObject(responseBody);
                            if(errorJson.has("error") && errorJson.getJSONObject("error").has("message")){
                                errorMessage += " " + errorJson.getJSONObject("error").getString("message");
                            }
                        } catch (Exception e) { /* Ignore */ }
                    } else {
                        errorMessage += " Network error.";
                    }
                    showError(errorMessage);
                    showProgressBar(false);
                }) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                // --- Gemini uses API key in URL or x-goog-api-key header ---
                // We put it in the URL, so no special Auth header needed here.
                // If NOT putting key in URL, use this:
                // Map<String, String> headers = new HashMap<>();
                // headers.put("x-goog-api-key", apiKey);
                // headers.put("Content-Type", "application/json");
                // return headers;
                Map<String, String> headers = new HashMap<>();
                headers.put("Content-Type", "application/json"); // Still need Content-Type
                return headers;
            }
        };

        requestQueue.add(jsonObjectRequest);
    }

    // --- Updated parser for Gemini response structure ---
    private void parseAndSaveTasksFromGemini(JSONObject response) {
        try {
            // Gemini structure: { "candidates": [ { "content": { "parts": [ { "text": "{\"tasks\": [...]}" } ], "role": "model" } } ] }
            if (!response.has("candidates")) {
                Log.e(TAG, "Gemini response missing 'candidates'. Response: " + response.toString());
                // Check for prompt feedback if blocked
                if (response.has("promptFeedback") && response.getJSONObject("promptFeedback").has("blockReason")){
                    showError("AI request blocked: " + response.getJSONObject("promptFeedback").getString("blockReason"));
                } else {
                    showError("AI response format unexpected (no candidates).");
                }
                return;
            }

            JSONArray candidates = response.getJSONArray("candidates");
            if (candidates.length() == 0) {
                Log.e(TAG, "Gemini response has empty 'candidates' array.");
                showError("AI returned no response candidate.");
                return;
            }

            JSONObject firstCandidate = candidates.getJSONObject(0);
            if (!firstCandidate.has("content") || !firstCandidate.getJSONObject("content").has("parts")) {
                Log.e(TAG, "Gemini candidate missing 'content' or 'parts'.");
                showError("AI response format unexpected (no content/parts).");
                return;
            }

            JSONArray parts = firstCandidate.getJSONObject("content").getJSONArray("parts");
            if (parts.length() == 0 || !parts.getJSONObject(0).has("text")) {
                Log.e(TAG, "Gemini parts array is empty or first part has no 'text'.");
                showError("AI response format unexpected (no text part).");
                return;
            }

            String jsonContent = parts.getJSONObject(0).getString("text");
            // Clean potential markdown ```json ... ``` wrappers (sometimes still happens)
            jsonContent = jsonContent.trim().replace("```json", "").replace("```", "").trim();

            Log.d(TAG, "Extracted JSON Content: " + jsonContent);

            // Now parse the extracted JSON string containing the tasks
            JSONObject tasksJson = new JSONObject(jsonContent);
            if (!tasksJson.has("tasks")) {
                Log.e(TAG, "Parsed JSON content does not contain 'tasks' key. Content was: " + jsonContent);
                appendChatMessage("AI", "I couldn't structure the tasks correctly. Could you describe them again?"); // Give feedback
                return;
            }

            JSONArray tasksArray = tasksJson.getJSONArray("tasks");
            List<Task> tasksToSave = new ArrayList<>();

            for (int i = 0; i < tasksArray.length(); i++) {
                JSONObject taskJson = tasksArray.getJSONObject(i);
                String title = taskJson.optString("title", "Untitled Task");
                String notes = taskJson.optString("notes", "");

                if (TextUtils.isEmpty(title)) continue;

                Task newTask = new Task(title, notes); // Generates ID

                List<Subtask> subtasks = new ArrayList<>();
                JSONArray subtasksArray = taskJson.optJSONArray("subtasks");
                if (subtasksArray != null) {
                    for (int j = 0; j < subtasksArray.length(); j++) {
                        JSONObject subtaskJson = subtasksArray.getJSONObject(j);
                        String subtaskText = subtaskJson.optString("text", "");
                        if (!TextUtils.isEmpty(subtaskText)) {
                            subtasks.add(new Subtask(subtaskText)); // Generates ID
                        }
                    }
                }
                newTask.setSubtasks(subtasks);
                tasksToSave.add(newTask);
            }

            if (!tasksToSave.isEmpty()) {
                saveTasksToFirestore(tasksToSave);
            } else {
                appendChatMessage("AI", "I couldn't identify specific tasks from your description. Could you please rephrase?");
                showProgressBar(false);
            }

        } catch (JSONException e) {
            Log.e(TAG, "Error parsing JSON response from Gemini", e);
            showError("Error processing AI response.");
            showProgressBar(false);
        }
    }


    // --- saveTasksToFirestore, showError, showProgressBar remain the same ---
    private void saveTasksToFirestore(List<Task> tasks) {
        if (tasksCollection == null) {
            Log.e(TAG, "Tasks collection is null. Cannot save tasks.");
            showError("Error: Could not access tasks database.");
            showProgressBar(false);
            return;
        }
        WriteBatch batch = db.batch();
        for (Task task : tasks) {
            batch.set(tasksCollection.document(task.getId()), task);
        }
        batch.commit()
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Batch task save successful.");
                    Toast.makeText(AiTaskActivity.this, "Tasks added!", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Error writing batch tasks", e);
                    showError("Failed to save tasks: " + e.getMessage());
                    showProgressBar(false);
                });
    }

    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        // appendChatMessage("System", "Error: " + message);
    }

    private void showProgressBar(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        sendButton.setEnabled(!show);
        userInputEditText.setEnabled(!show);
    }
}
