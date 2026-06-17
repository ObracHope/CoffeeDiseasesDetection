package com.example.coffeediseasesdetection;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialCancellationException;
import androidx.credentials.exceptions.GetCredentialException;

import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;

import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends BaseActivity {

    private static final String TAG = "MainActivity";
    private EditText etEmail, etPassword;
    private Button btnLogin;
    private View btnGoogleLogin;
    private ProgressBar progressBar;
    private FirebaseAuth auth;
    private CredentialManager credentialManager;
    private final AtomicBoolean loginInProgress = new AtomicBoolean(false);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        auth = FirebaseAuth.getInstance();
        if (AuthHelper.tryFastSessionRestore(this)) {
            return;
        }

        setContentView(R.layout.activity_main);
        credentialManager = CredentialManager.create(this);

        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        progressBar = findViewById(R.id.progressBar);
        btnGoogleLogin = findViewById(R.id.btnGoogleLogin);

        if (btnGoogleLogin != null) {
            btnGoogleLogin.setOnClickListener(v -> startGoogleLogin());
        }
        View tvRegister = findViewById(R.id.tvRegister);
        if (tvRegister != null) {
            tvRegister.setOnClickListener(v ->
                    startActivity(new Intent(this, RegisterActivity.class)));
        }
        View tvForgotPassword = findViewById(R.id.tvForgotPassword);
        if (tvForgotPassword != null) {
            tvForgotPassword.setOnClickListener(v ->
                    startActivity(new Intent(this, ForgotPasswordActivity.class)));
        }

        if (btnLogin != null) {
            btnLogin.setOnClickListener(v -> handleLogin());
        }
    }

    private void handleLogin() {
        if (etEmail == null || etPassword == null) return;

        String identifier = InputValidator.sanitize(etEmail.getText().toString());
        String password = etPassword.getText().toString();

        String idError = InputValidator.validateLoginIdentifier(identifier);
        if (idError != null) {
            etEmail.setError(idError);
            return;
        }
        if (TextUtils.isEmpty(password)) {
            etPassword.setError(getString(R.string.login_password_required));
            return;
        }
        if (loginInProgress.getAndSet(true)) return;

        setLoading(true);

        AuthHelper.signInWithEmailPassword(this, identifier, password, new AuthHelper.LoginCallback() {
            @Override
            public void onSuccess() {
                FirebaseUser user = auth.getCurrentUser();
                if (user != null) {
                    AuthHelper.completeEmailLoginAndRedirect(MainActivity.this, user);
                } else {
                    finishLoginError(getString(R.string.login_failed_generic));
                }
            }

            @Override
            public void onError(String message) {
                finishLoginError(message);
            }
        });
    }

    private void startGoogleLogin() {
        if (loginInProgress.getAndSet(true)) return;
        if (isFinishing()) {
            loginInProgress.set(false);
            return;
        }
        setLoading(true);

        try {
            String webClientId = getString(R.string.default_web_client_id);
            if (TextUtils.isEmpty(webClientId) || webClientId.startsWith("YOUR_")) {
                finishLoginError(getString(R.string.google_sign_in_failed)
                        + " — default_web_client_id haijasanidi.");
                return;
            }
            if (credentialManager == null) {
                credentialManager = CredentialManager.create(this);
            }
            GetGoogleIdOption googleIdOption = new GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(webClientId)
                    .setAutoSelectEnabled(false)
                    .build();

            GetCredentialRequest request = new GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build();

            Executor executor = ContextCompat.getMainExecutor(this);
            credentialManager.getCredentialAsync(this, request, null, executor,
                    new androidx.credentials.CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                        @Override
                        public void onResult(GetCredentialResponse result) {
                            if (isFinishing()) {
                                resetLoading();
                                return;
                            }
                            handleGoogleSignIn(result.getCredential());
                        }

                        @Override
                        public void onError(GetCredentialException e) {
                            if (isFinishing()) {
                                resetLoading();
                                return;
                            }
                            if (e instanceof GetCredentialCancellationException) {
                                resetLoading();
                                return;
                            }
                            Log.e(TAG, "Google Login Error: " + e.getMessage(), e);
                            String detail = e.getMessage() != null ? e.getMessage() : "";
                            if (detail.toLowerCase(Locale.US).contains("developer") || detail.contains("10:")) {
                                finishLoginError(getString(R.string.google_sign_in_failed)
                                        + " — ongeza SHA-1 ya app kwenye Firebase Console → Project Settings.");
                            } else {
                                finishLoginError(getString(R.string.google_sign_in_failed) + " " + detail);
                            }
                        }
                    });
        } catch (Exception e) {
            Log.e(TAG, "Google config error", e);
            finishLoginError(getString(R.string.google_sign_in_failed));
        }
    }

    private void handleGoogleSignIn(Credential credential) {
        if (!(credential instanceof CustomCredential)
                || !GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(credential.getType())) {
            finishLoginError(getString(R.string.google_sign_in_failed));
            return;
        }

        try {
            GoogleIdTokenCredential googleCred = GoogleIdTokenCredential.createFrom(credential.getData());
            AuthCredential authCredential = GoogleAuthProvider.getCredential(googleCred.getIdToken(), null);

            auth.signInWithCredential(authCredential).addOnCompleteListener(this, task -> {
                if (isFinishing()) {
                    resetLoading();
                    return;
                }
                if (task.isSuccessful() && auth.getCurrentUser() != null) {
                    FirebaseUser user = auth.getCurrentUser();
                    AuthHelper.completeGoogleLoginAndRedirect(MainActivity.this, user,
                            () -> finishLoginError(getString(R.string.admin_google_login_blocked)));
                } else if (AuthHelper.isGoogleAccountCollision(task.getException())) {
                    String googleEmail = AuthHelper.emailFromGoogleAuthError(task.getException(), googleCred);
                    if (TextUtils.isEmpty(googleEmail)) {
                        finishLoginError(getString(R.string.google_sign_in_failed));
                        return;
                    }
                    promptLinkGoogleAccount(googleEmail, authCredential);
                } else {
                    finishLoginError(AuthHelper.friendlyGoogleAuthError(task.getException()));
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Google token error", e);
            finishLoginError(getString(R.string.google_sign_in_failed));
        }
    }

    private void promptLinkGoogleAccount(String email, AuthCredential googleCredential) {
        resetLoading();

        final EditText passwordInput = new EditText(this);
        passwordInput.setHint(getString(R.string.hint_password));
        passwordInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);

        new AlertDialog.Builder(this)
                .setTitle(R.string.google_link_title)
                .setMessage(getString(R.string.google_link_message, email))
                .setView(passwordInput)
                .setPositiveButton(R.string.google_link_confirm, (dialog, which) -> {
                    String password = passwordInput.getText().toString().trim();
                    if (TextUtils.isEmpty(password)) {
                        Toast.makeText(this, R.string.login_password_required, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (loginInProgress.getAndSet(true)) return;
                    setLoading(true);
                    AuthHelper.linkGoogleWithPassword(this, email, password, googleCredential,
                            googleLoginCallback());
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private AuthHelper.GoogleLoginCallback googleLoginCallback() {
        return new AuthHelper.GoogleLoginCallback() {
            @Override
            public void onFarmerReady() {
                resetLoading();
            }

            @Override
            public void onAdminBlocked() {
                finishLoginError(getString(R.string.admin_google_login_blocked));
            }

            @Override
            public void onError(String message) {
                finishLoginError(message);
            }
        };
    }

    private void setLoading(boolean loading) {
        if (progressBar != null) {
            progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
        if (btnLogin != null) btnLogin.setEnabled(!loading);
        if (btnGoogleLogin != null) btnGoogleLogin.setEnabled(!loading);
    }

    private void finishLoginError(String message) {
        resetLoading();
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void resetLoading() {
        loginInProgress.set(false);
        setLoading(false);
    }
}
