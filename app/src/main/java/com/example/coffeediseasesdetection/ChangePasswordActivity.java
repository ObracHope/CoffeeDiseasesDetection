package com.example.coffeediseasesdetection;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class ChangePasswordActivity extends BaseActivity {

    private EditText etCurrentPassword, etNewPassword, etConfirmNewPassword;
    private Button btnChangePassword;
    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_change_password);
        setTitle(R.string.settings_change_password);

        auth = FirebaseAuth.getInstance();

        etCurrentPassword = findViewById(R.id.etCurrentPassword);
        etNewPassword = findViewById(R.id.etNewPassword);
        etConfirmNewPassword = findViewById(R.id.etConfirmNewPassword);
        btnChangePassword = findViewById(R.id.btnChangePassword);

        btnChangePassword.setOnClickListener(v -> handleChangePassword());

        findViewById(R.id.tvBackToDashboard).setOnClickListener(v -> finish());
    }

    private void handleChangePassword() {
        String currentPassword = etCurrentPassword.getText().toString().trim();
        String newPassword = etNewPassword.getText().toString().trim();
        String confirmPassword = etConfirmNewPassword.getText().toString().trim();

        if (TextUtils.isEmpty(currentPassword) || TextUtils.isEmpty(newPassword) || TextUtils.isEmpty(confirmPassword)) {
            Toast.makeText(this, "All fields are required", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!newPassword.equals(confirmPassword)) {
            Toast.makeText(this, getString(R.string.passwords_do_not_match), Toast.LENGTH_SHORT).show();
            return;
        }

        if (newPassword.length() < 6) {
            Toast.makeText(this, getString(R.string.password_min), Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseUser user = auth.getCurrentUser();
        if (user != null && user.getEmail() != null) {
            btnChangePassword.setEnabled(false);
            btnChangePassword.setText(getString(R.string.processing));

            // Re-authenticate user
            AuthCredential credential = EmailAuthProvider.getCredential(user.getEmail(), currentPassword);
            user.reauthenticate(credential).addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    // Update password
                    user.updatePassword(newPassword).addOnCompleteListener(updateTask -> {
                        btnChangePassword.setEnabled(true);
                        btnChangePassword.setText(getString(R.string.btn_change_password));
                        if (updateTask.isSuccessful()) {
                            Toast.makeText(ChangePasswordActivity.this, getString(R.string.password_changed), Toast.LENGTH_LONG).show();
                            finish();
                        } else {
                            String error = updateTask.getException() != null ? updateTask.getException().getMessage() : "Update failed";
                            Toast.makeText(ChangePasswordActivity.this, "Failed: " + error, Toast.LENGTH_LONG).show();
                        }
                    });
                } else {
                    btnChangePassword.setEnabled(true);
                    btnChangePassword.setText(getString(R.string.btn_change_password));
                    Toast.makeText(ChangePasswordActivity.this, "Authentication failed. Check current password.", Toast.LENGTH_LONG).show();
                }
            });
        }
    }
}
