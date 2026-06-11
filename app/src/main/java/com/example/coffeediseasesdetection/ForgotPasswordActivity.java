package com.example.coffeediseasesdetection;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.firebase.FirebaseException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;

import java.util.concurrent.TimeUnit;

public class ForgotPasswordActivity extends BaseActivity {

    private EditText etInput, etOtp;
    private RadioGroup rgResetMethod;
    private ProgressBar progressBar;
    private FirebaseAuth auth;
    private String mVerificationId;
    private PhoneAuthProvider.ForceResendingToken mResendToken;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);

        auth = FirebaseAuth.getInstance();
        etInput = findViewById(R.id.etResetInput);
        etOtp = findViewById(R.id.etOtp);
        rgResetMethod = findViewById(R.id.rgResetMethod);
        progressBar = findViewById(R.id.progressBar);

        rgResetMethod.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbEmail) {
                etInput.setHint("Enter Email");
                etInput.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
                etOtp.setVisibility(View.GONE);
            } else {
                etInput.setHint("Enter Phone Number (+255...)");
                etInput.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
            }
        });

        findViewById(R.id.btnSendReset).setOnClickListener(v -> handleResetRequest());
        findViewById(R.id.btnVerifyOtp).setOnClickListener(v -> verifyOtp());
        findViewById(R.id.tvBackToLogin).setOnClickListener(v -> finish());
    }

    private void handleResetRequest() {
        String input = etInput.getText().toString().trim();
        if (TextUtils.isEmpty(input)) {
            etInput.setError("Required");
            return;
        }

        if (rgResetMethod.getCheckedRadioButtonId() == R.id.rbEmail) {
            sendEmailReset(input);
        } else {
            startPhoneVerification(input);
        }
    }

    private void sendEmailReset(String email) {
        progressBar.setVisibility(View.VISIBLE);
        auth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    progressBar.setVisibility(View.GONE);
                    if (task.isSuccessful()) {
                        Toast.makeText(this, "Reset link sent to " + email, Toast.LENGTH_LONG).show();
                        finish();
                    } else {
                        Toast.makeText(this, "Error: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void startPhoneVerification(String phone) {
        progressBar.setVisibility(View.VISIBLE);
        PhoneAuthOptions options = PhoneAuthOptions.newBuilder(auth)
                .setPhoneNumber(phone)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(this)
                .setCallbacks(new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                    @Override
                    public void onVerificationCompleted(@NonNull PhoneAuthCredential credential) {
                        progressBar.setVisibility(View.GONE);
                        // Auto-verification handled by Firebase
                    }

                    @Override
                    public void onVerificationFailed(@NonNull FirebaseException e) {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(ForgotPasswordActivity.this, "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }

                    @Override
                    public void onCodeSent(@NonNull String verificationId, @NonNull PhoneAuthProvider.ForceResendingToken token) {
                        progressBar.setVisibility(View.GONE);
                        mVerificationId = verificationId;
                        mResendToken = token;
                        etOtp.setVisibility(View.VISIBLE);
                        findViewById(R.id.btnVerifyOtp).setVisibility(View.VISIBLE);
                        Toast.makeText(ForgotPasswordActivity.this, "OTP Sent", Toast.LENGTH_SHORT).show();
                    }
                })
                .build();
        PhoneAuthProvider.verifyPhoneNumber(options);
    }

    private void verifyOtp() {
        String code = etOtp.getText().toString().trim();
        if (TextUtils.isEmpty(code)) return;
        if (mVerificationId == null) {
            Toast.makeText(this, "Tafadhali tuma OTP kwanza.", Toast.LENGTH_SHORT).show();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        PhoneAuthCredential credential = PhoneAuthProvider.getCredential(mVerificationId, code);
        // After verifying, user can be signed in or redirected to a Change Password screen
        // For simplicity in this flow, we'll notify success.
        auth.signInWithCredential(credential).addOnCompleteListener(task -> {
            progressBar.setVisibility(View.GONE);
            if (task.isSuccessful()) {
                Toast.makeText(this, "Phone Verified! Please use 'Change Password' in settings after login.", Toast.LENGTH_LONG).show();
                finish();
            } else {
                Toast.makeText(this, "Verification Failed", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
