package com.example.coffeediseasesdetection;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;

import java.util.HashMap;
import java.util.Map;

public class ReportChallengeActivity extends BaseActivity {

    private FirebaseAuth auth;
    private DatabaseReference mDatabase;
    private String cachedUserName = "Farmer";
    private boolean isSubmitting = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report_challenge);
        setTitle(R.string.report_challenge);

        auth = FirebaseAuth.getInstance();
        // Initialize Realtime Database
        mDatabase = FirebaseDatabase.getInstance("https://coffee-diseases-detection-default-rtdb.firebaseio.com").getReference();

        EditText etTitle = findViewById(R.id.etChallengeTitle);
        EditText etDescription = findViewById(R.id.etChallengeDescription);
        Spinner spinnerCategory = findViewById(R.id.spinnerCategory);
        Button btnSubmit = findViewById(R.id.btnSubmitChallenge);
        Button btnViewMyChallenges = findViewById(R.id.btnViewMyChallenges);

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this, R.array.challenge_categories, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCategory.setAdapter(adapter);

        // Pre-fetch user name from Realtime Database
        FirebaseUser user = auth.getCurrentUser();
        if (user != null) {
            if (user.getDisplayName() != null && !user.getDisplayName().isEmpty()) {
                cachedUserName = user.getDisplayName();
            }
            mDatabase.child("users").child(user.getUid()).child("name").get()
                    .addOnSuccessListener(snapshot -> {
                        if (snapshot.exists() && snapshot.getValue() != null) {
                            cachedUserName = snapshot.getValue(String.class);
                        }
                    });
        }

        btnSubmit.setOnClickListener(v -> {
            if (isSubmitting) return;

            String title = etTitle.getText().toString().trim();
            String description = etDescription.getText().toString().trim();
            Object selected = spinnerCategory.getSelectedItem();
            String category = selected != null ? selected.toString() : "Other";

            if (TextUtils.isEmpty(title) || TextUtils.isEmpty(description)) {
                if (TextUtils.isEmpty(title)) etTitle.setError(getString(R.string.required));
                if (TextUtils.isEmpty(description)) etDescription.setError(getString(R.string.required));
                Toast.makeText(this, R.string.fill_all_fields, Toast.LENGTH_SHORT).show();
                return;
            }

            if (user == null) {
                Toast.makeText(this, R.string.please_login_first, Toast.LENGTH_SHORT).show();
                return;
            }

            isSubmitting = true;
            btnSubmit.setEnabled(false);
            Toast.makeText(this, R.string.submitting_report, Toast.LENGTH_SHORT).show();

            Map<String, Object> challenge = new HashMap<>();
            challenge.put("userId", user.getUid());
            challenge.put("userEmail", user.getEmail());
            challenge.put("userName", cachedUserName);
            challenge.put("title", title);
            challenge.put("description", description);
            challenge.put("category", category);
            challenge.put("status", "pending");
            challenge.put("timestamp", ServerValue.TIMESTAMP);

            mDatabase.child("farmer_challenges").push().setValue(challenge)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            ActivityLogHelper.log(ReportChallengeActivity.this, "farmer_message",
                                    Map.of("title", title, "category", category, "status", "pending"));
                            Toast.makeText(this, getString(R.string.challenge_submitted), Toast.LENGTH_SHORT).show();
                            finish();
                        } else {
                            isSubmitting = false;
                            btnSubmit.setEnabled(true);
                            String error = task.getException() != null ? task.getException().getMessage() : "Unknown error";
                            Toast.makeText(this, getString(R.string.submission_failed, error), Toast.LENGTH_LONG).show();
                        }
                    });
        });

        if (btnViewMyChallenges != null) {
            btnViewMyChallenges.setOnClickListener(v ->
                    startActivity(new android.content.Intent(this, FarmerChallengesActivity.class)));
        }
    }
}
