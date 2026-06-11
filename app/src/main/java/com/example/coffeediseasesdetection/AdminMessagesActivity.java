package com.example.coffeediseasesdetection;

import android.content.Intent;
import android.os.Bundle;

import com.google.android.material.button.MaterialButton;

/** Hub for admin messages: notifications and farmer challenges. */
public class AdminMessagesActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_messages);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        MaterialButton btnNotifications = findViewById(R.id.btnOpenNotifications);
        MaterialButton btnChallenges = findViewById(R.id.btnOpenChallenges);

        btnNotifications.setOnClickListener(v ->
                startActivity(new Intent(this, NotificationsActivity.class)));
        btnChallenges.setOnClickListener(v ->
                startActivity(new Intent(this, AdminChallengesActivity.class)));
    }
}
