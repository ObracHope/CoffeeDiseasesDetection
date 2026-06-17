package com.example.coffeediseasesdetection;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

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
            Toast.makeText(this, R.string.password_all_fields_required, Toast.LENGTH_SHORT).show();
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
        if (user == null || user.getEmail() == null) {
            Toast.makeText(this, R.string.login_failed_generic, Toast.LENGTH_SHORT).show();
            return;
        }

        btnChangePassword.setEnabled(false);
        btnChangePassword.setText(getString(R.string.processing));

        AuthHelper.changeAdminPassword(this, currentPassword, newPassword, new AuthHelper.PasswordChangeCallback() {
            @Override
            public void onSuccess() {
                btnChangePassword.setEnabled(true);
                btnChangePassword.setText(getString(R.string.btn_change_password));
                Toast.makeText(ChangePasswordActivity.this,
                        R.string.password_changed_synced, Toast.LENGTH_LONG).show();
                finish();
            }

            @Override
            public void onError(String message) {
                btnChangePassword.setEnabled(true);
                btnChangePassword.setText(getString(R.string.btn_change_password));
                Toast.makeText(ChangePasswordActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }
}
