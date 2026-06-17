package com.example.coffeediseasesdetection;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;

public class ForgotPasswordActivity extends BaseActivity {

    private TextInputEditText etInput;
    private ProgressBar progressBar;
    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);

        auth = FirebaseAuth.getInstance();
        etInput = findViewById(R.id.etResetInput);
        progressBar = findViewById(R.id.progressBar);

        findViewById(R.id.btnSendReset).setOnClickListener(v -> handleResetRequest());
        findViewById(R.id.tvBackToLogin).setOnClickListener(v -> finish());
    }

    private void handleResetRequest() {
        String input = etInput.getText() != null ? etInput.getText().toString().trim() : "";
        if (TextUtils.isEmpty(input)) {
            etInput.setError(getString(R.string.required));
            return;
        }
        sendEmailReset(input);
    }

    private void sendEmailReset(String input) {
        progressBar.setVisibility(View.VISIBLE);
        AuthHelper.resolveEmailForLogin(input, new AuthHelper.EmailResolverCallback() {
            @Override
            public void onResolved(String email) {
                auth.sendPasswordResetEmail(email)
                        .addOnCompleteListener(task -> {
                            progressBar.setVisibility(View.GONE);
                            if (task.isSuccessful()) {
                                Toast.makeText(ForgotPasswordActivity.this,
                                        getString(R.string.reset_password_sent) + " " + email,
                                        Toast.LENGTH_LONG).show();
                                finish();
                            } else {
                                String msg = task.getException() != null
                                        ? task.getException().getMessage() : "Failed";
                                Toast.makeText(ForgotPasswordActivity.this,
                                        "Error: " + msg, Toast.LENGTH_LONG).show();
                            }
                        });
            }

            @Override
            public void onError(String message) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(ForgotPasswordActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }
}
