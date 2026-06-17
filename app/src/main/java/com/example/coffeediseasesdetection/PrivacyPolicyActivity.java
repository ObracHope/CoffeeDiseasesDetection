package com.example.coffeediseasesdetection;

import android.content.Intent;
import android.os.Bundle;

import com.google.android.material.button.MaterialButton;

public class PrivacyPolicyActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_privacy_policy);

        findViewById(R.id.ivBackButton).setOnClickListener(v -> finish());

        MaterialButton btnContact = findViewById(R.id.btnContactSupport);
        if (btnContact != null) {
            btnContact.setOnClickListener(v ->
                    startActivity(new Intent(this, HelpSupportActivity.class)));
        }
    }
}
