package com.example.coffeediseasesdetection;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

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
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

public class RegisterActivity extends BaseActivity {

    private static final String TAG = "RegisterActivity";
    private FirebaseAuth auth;
    private DatabaseReference mDatabase;
    private CredentialManager credentialManager;

    private EditText etFirstName, etLastName, etEmail, etPhone, etPassword, etRepeatPassword;
    private EditText etRegion, etDistrict, etWard;
    private RadioGroup rgGender;
    private CheckBox cbAgree;
    private Spinner spinnerCountryCode;
    private ProgressBar progressBar;

    private static final int[] PHONE_LENGTHS = {9, 9, 9, 9, 9, 10, 10, 9};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        auth = FirebaseAuth.getInstance();
        // Fixed DB URL for stability
        mDatabase = AuthHelper.usersRtdb();
        credentialManager = CredentialManager.create(this);

        etFirstName = findViewById(R.id.etFirstName);
        etLastName = findViewById(R.id.etLastName);
        etEmail = findViewById(R.id.etEmailRegister);
        etPhone = findViewById(R.id.etPhone);
        etPassword = findViewById(R.id.etPasswordRegister);
        etRepeatPassword = findViewById(R.id.etRepeatPassword);
        etRegion = findViewById(R.id.etRegion);
        etDistrict = findViewById(R.id.etDistrict);
        etWard = findViewById(R.id.etWard);
        rgGender = findViewById(R.id.rgGender);
        cbAgree = findViewById(R.id.cbAgree);
        spinnerCountryCode = findViewById(R.id.spinnerCountryCode);
        progressBar = findViewById(R.id.progressBar);

        Button btnCreateAccount = findViewById(R.id.btnCreateAccount);
        Button btnGoogleRegister = findViewById(R.id.btnGoogleRegister);
        TextView tvLoginLink = findViewById(R.id.tvLoginLink);

        ArrayAdapter<CharSequence> codeAdapter = ArrayAdapter.createFromResource(this,
                R.array.phone_country_codes, android.R.layout.simple_spinner_item);
        codeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCountryCode.setAdapter(codeAdapter);

        btnCreateAccount.setOnClickListener(v -> registerUser());
        btnGoogleRegister.setOnClickListener(v -> startGoogleRegister());

        tvLoginLink.setOnClickListener(v -> {
            startActivity(new Intent(RegisterActivity.this, MainActivity.class));
            finish();
        });
    }

    private void registerUser() {
        String firstName = etFirstName.getText().toString().trim();
        String lastName = etLastName.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String phoneRaw = etPhone.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String repeatPassword = etRepeatPassword.getText().toString().trim();

        if (TextUtils.isEmpty(firstName)) { etFirstName.setError("Required"); return; }
        if (TextUtils.isEmpty(email)) { etEmail.setError("Required"); return; }
        if (TextUtils.isEmpty(password) || password.length() < 6) { etPassword.setError("Min 6 chars"); return; }
        if (!password.equals(repeatPassword)) { etRepeatPassword.setError("No match"); return; }
        if (!cbAgree.isChecked()) {
            Toast.makeText(this, "Please agree to terms", Toast.LENGTH_SHORT).show();
            return;
        }
        
        progressBar.setVisibility(View.VISIBLE);
        auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && auth.getCurrentUser() != null) {
                        saveUserData(auth.getCurrentUser(), firstName, lastName, phoneRaw, email, false);
                    } else {
                        progressBar.setVisibility(View.GONE);
                        String error = task.getException() != null ? task.getException().getMessage() : "Failed";
                        Toast.makeText(this, "Error: " + error, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void startGoogleRegister() {
        try {
            progressBar.setVisibility(View.VISIBLE);
            GetGoogleIdOption googleIdOption = new GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(getString(R.string.default_web_client_id))
                    .setAutoSelectEnabled(true)
                    .build();

            GetCredentialRequest request = new GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build();

            Executor executor = ContextCompat.getMainExecutor(this);
            credentialManager.getCredentialAsync(this, request, null, executor, new androidx.credentials.CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                @Override
                public void onResult(GetCredentialResponse result) {
                    handleGoogleSignIn(result.getCredential());
                }

                @Override
                public void onError(GetCredentialException e) {
                    progressBar.setVisibility(View.GONE);
                    if (!(e instanceof GetCredentialCancellationException)) {
                        Toast.makeText(RegisterActivity.this, "Google Registration Failed", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        } catch (Exception e) {
            progressBar.setVisibility(View.GONE);
            Toast.makeText(this, "Google Setup Error", Toast.LENGTH_SHORT).show();
        }
    }

    private void handleGoogleSignIn(Credential credential) {
        if (credential instanceof CustomCredential && credential.getType().equals(GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)) {
            try {
                GoogleIdTokenCredential googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.getData());
                AuthCredential authCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.getIdToken(), null);
                auth.signInWithCredential(authCredential).addOnCompleteListener(this, task -> {
                    if (task.isSuccessful() && auth.getCurrentUser() != null) {
                        FirebaseUser user = auth.getCurrentUser();
                        mDatabase.child(user.getUid()).get()
                                .addOnSuccessListener(snapshot -> {
                                    if (snapshot.exists()) {
                                        progressBar.setVisibility(View.GONE);
                                        AuthHelper.completeGoogleLoginAndRedirect(RegisterActivity.this, user,
                                                () -> Toast.makeText(RegisterActivity.this,
                                                        R.string.admin_google_login_blocked, Toast.LENGTH_LONG).show());
                                    } else {
                                        String fName = user.getDisplayName() != null ? user.getDisplayName() : "User";
                                        saveUserData(user, fName, "", "", user.getEmail() != null ? user.getEmail() : "", true);
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    String fName = user.getDisplayName() != null ? user.getDisplayName() : "User";
                                    saveUserData(user, fName, "", "", user.getEmail() != null ? user.getEmail() : "", true);
                                });
                    } else {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(this, "Authentication Failed", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(this, "Sign-in error", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void saveUserData(FirebaseUser user, String fName, String lName, String phone, String email, boolean isGoogle) {
        String region = etRegion != null ? etRegion.getText().toString().trim() : "";
        String district = etDistrict != null ? etDistrict.getText().toString().trim() : "";
        String ward = etWard != null ? etWard.getText().toString().trim() : "";
        String gender = "";
        if (rgGender != null) {
            int checkedId = rgGender.getCheckedRadioButtonId();
            if (checkedId == R.id.rbMale) gender = "Male";
            else if (checkedId == R.id.rbFemale) gender = "Female";
        }

        String fullName = fName + " " + lName;
        String username = AuthHelper.buildUsername(fName, lName, email);
        Map<String, Object> data = new HashMap<>();
        data.put("uid", user.getUid());
        data.put("name", fullName.trim());
        data.put("email", email);
        data.put("username", username);
        data.put("phone", phone);
        data.put("role", "farmer");
        data.put("region", region);
        data.put("district", district);
        data.put("ward", ward);
        if (!gender.isEmpty()) data.put("gender", gender);

        Runnable persist = () -> {
            mDatabase.child(user.getUid()).setValue(data)
                    .addOnSuccessListener(unused -> {
                        Map<String, Object> fsUser = new HashMap<>(data);
                        fsUser.put("firstName", fName);
                        fsUser.put("lastName", lName);
                        fsUser.put("created_at", FieldValue.serverTimestamp());
                        FirebaseFirestore.getInstance().collection("users")
                                .document(user.getUid())
                                .set(fsUser, SetOptions.merge());

                        UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                                .setDisplayName(fullName.trim())
                                .build();
                        user.updateProfile(profileUpdates).addOnCompleteListener(t -> {
                            progressBar.setVisibility(View.GONE);
                            if (isGoogle) {
                                fetchUserRoleAndRedirect(user.getUid());
                            } else {
                                auth.signOut();
                                Toast.makeText(this, "Registration Successful. Please Login.", Toast.LENGTH_LONG).show();
                                startActivity(new Intent(this, MainActivity.class));
                                finish();
                            }
                        });
                    })
                    .addOnFailureListener(e -> {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(this, "Database Error", Toast.LENGTH_SHORT).show();
                    });
        };

        if (LocationHelper.hasPermission(this)) {
            LocationHelper.fetchLocation(this, loc -> {
                if (loc != null) {
                    data.put("lastLatitude", loc.latitude);
                    data.put("lastLongitude", loc.longitude);
                }
                persist.run();
            });
        } else {
            persist.run();
        }
    }

    private void fetchUserRoleAndRedirect(String uid) {
        FirebaseUser user = auth.getCurrentUser();
        progressBar.setVisibility(View.GONE);
        if (user != null) {
            AuthHelper.completeGoogleLoginAndRedirect(this, user,
                    () -> Toast.makeText(this, R.string.admin_google_login_blocked, Toast.LENGTH_LONG).show());
        } else {
            finish();
        }
    }
}
