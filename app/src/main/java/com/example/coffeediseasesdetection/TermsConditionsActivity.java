package com.example.coffeediseasesdetection;

import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.button.MaterialButton;

public class TermsConditionsActivity extends BaseActivity {

    private static final String PREFS = "app_prefs";
    private static final String KEY_TERMS = "terms_accepted";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_terms_conditions);

        findViewById(R.id.ivBackButton).setOnClickListener(v -> finish());

        MaterialButton btnAccept = findViewById(R.id.btnAccept);
        MaterialButton btnDecline = findViewById(R.id.btnDecline);

        if (btnAccept != null) {
            btnAccept.setOnClickListener(v -> {
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_TERMS, true).apply();
                Toast.makeText(this, R.string.terms_accepted_toast, Toast.LENGTH_SHORT).show();
                finish();
            });
        }

        if (btnDecline != null) {
            btnDecline.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setTitle(R.string.terms_decline_title)
                    .setMessage(R.string.terms_decline_message)
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> finishAffinity())
                    .setNegativeButton(android.R.string.cancel, null)
                    .show());
        }
    }

    public static boolean isAccepted(android.content.Context context) {
        return context.getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_TERMS, false);
    }
}
