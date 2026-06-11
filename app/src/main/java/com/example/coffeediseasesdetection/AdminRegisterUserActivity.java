package com.example.coffeediseasesdetection;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.bumptech.glide.Glide;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.HashMap;
import java.util.Map;

public class AdminRegisterUserActivity extends BaseActivity {

    private FirebaseAuth auth;
    private FirebaseFirestore firestore;
    private FirebaseStorage storage;

    private EditText etFirstName, etLastName, etEmail, etPhone, etPassword, etRepeatPassword;
    private CheckBox cbAgree;
    private RadioGroup rgGender, rgRole;
    private Spinner spinnerCountryCode;
    private ImageView ivProfile;
    private ProgressBar progressBar;
    private FloatingActionButton btnPickPhoto, btnRemovePhoto;
    private Uri selectedImageUri;

    private static final int[] PHONE_LENGTHS = {9, 9, 9, 9, 9, 10, 10, 9};

    private final ActivityResultLauncher<String> galleryLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    selectedImageUri = uri;
                    Glide.with(this).load(uri).circleCrop().into(ivProfile);
                    btnRemovePhoto.setVisibility(View.VISIBLE);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_register_user);

        auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();

        // Initialize views
        etFirstName = findViewById(R.id.etFirstName);
        etLastName = findViewById(R.id.etLastName);
        etEmail = findViewById(R.id.etEmailRegister);
        etPhone = findViewById(R.id.etPhone);
        etPassword = findViewById(R.id.etPasswordRegister);
        etRepeatPassword = findViewById(R.id.etRepeatPassword);
        cbAgree = findViewById(R.id.cbAgree);
        rgGender = findViewById(R.id.rgGender);
        rgRole = findViewById(R.id.rgRole);
        spinnerCountryCode = findViewById(R.id.spinnerCountryCode);
        ivProfile = findViewById(R.id.ivRegisterProfile);
        progressBar = findViewById(R.id.progressBar);
        btnPickPhoto = findViewById(R.id.btnPickPhoto);
        btnRemovePhoto = findViewById(R.id.btnRemovePhoto);

        btnPickPhoto.setOnClickListener(v -> galleryLauncher.launch("image/*"));
        btnRemovePhoto.setOnClickListener(v -> removeProfilePhoto());

        Button btnCreateAccount = findViewById(R.id.btnCreateAccount);
        TextView tvBack = findViewById(R.id.tvBack);

        ArrayAdapter<CharSequence> codeAdapter = ArrayAdapter.createFromResource(this,
                R.array.phone_country_codes, android.R.layout.simple_spinner_item);
        codeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCountryCode.setAdapter(codeAdapter);

        btnCreateAccount.setOnClickListener(v -> registerUser());
        tvBack.setOnClickListener(v -> finish());
    }

    private void removeProfilePhoto() {
        selectedImageUri = null;
        ivProfile.setImageResource(R.drawable.placeholder_user);
        btnRemovePhoto.setVisibility(View.GONE);
        Toast.makeText(this, "Profile photo removed", Toast.LENGTH_SHORT).show();
    }

    private boolean validatePhone(String digitsOnly) {
        int pos = spinnerCountryCode.getSelectedItemPosition();
        if (pos < 0 || pos >= PHONE_LENGTHS.length) return digitsOnly.length() >= 9;
        return digitsOnly.length() == PHONE_LENGTHS[pos];
    }

    private void registerUser() {
        String firstName = etFirstName.getText().toString().trim();
        String lastName = etLastName.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String phoneRaw = etPhone.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String repeatPassword = etRepeatPassword.getText().toString().trim();

        if (TextUtils.isEmpty(firstName)) { etFirstName.setError("First name required"); return; }
        if (TextUtils.isEmpty(lastName)) { etLastName.setError("Last name required"); return; }
        if (TextUtils.isEmpty(email)) { etEmail.setError("Email required"); return; }
        
        String digitsOnly = phoneRaw.replaceAll("\\D", "");
        if (TextUtils.isEmpty(phoneRaw) || digitsOnly.isEmpty()) { etPhone.setError("Phone required"); return; }
        if (!validatePhone(digitsOnly)) {
            etPhone.setError("Invalid phone number");
            return;
        }
        
        String countryCode = getResources().getStringArray(R.array.phone_country_codes)[spinnerCountryCode.getSelectedItemPosition()];
        final String finalPhone = countryCode.split(" ")[0].trim() + " " + digitsOnly;

        if (TextUtils.isEmpty(password) || password.length() < 6) { etPassword.setError("Password min 6 chars"); return; }
        if (!password.equals(repeatPassword)) {
            etRepeatPassword.setError("Passwords do not match");
            return;
        }
        if (!cbAgree.isChecked()) {
            Toast.makeText(this, "You must agree to terms", Toast.LENGTH_SHORT).show();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);

        // Make variables effectively final for lambda expressions
        final String finalEmail = email;
        final String finalFirstName = firstName;
        final String finalLastName = lastName;

        auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && auth.getCurrentUser() != null) {
                        FirebaseUser user = auth.getCurrentUser();
                        
                        // Send email verification
                        user.sendEmailVerification()
                                .addOnCompleteListener(verificationTask -> {
                                    if (verificationTask.isSuccessful()) {
                                        Toast.makeText(this, "Verification email sent to " + finalEmail, Toast.LENGTH_LONG).show();
                                    }
                                });
                        
                        if (selectedImageUri != null) {
                            uploadProfilePhoto(user, finalFirstName, finalLastName, finalPhone, finalEmail);
                        } else {
                            saveUserData(user, finalFirstName, finalLastName, finalPhone, finalEmail, null);
                        }
                    } else {
                        progressBar.setVisibility(View.GONE);
                        String error = (task.getException() != null) ? task.getException().getMessage() : "Registration failed";
                        
                        // Provide better error messages
                        if (error.contains("email address is already")) {
                            error = "This email is already registered. Please use a different email.";
                        } else if (error.contains("weak password")) {
                            error = "Password is too weak. Please use a stronger password.";
                        } else if (error.contains("email address is badly")) {
                            error = "Invalid email format. Please check your email.";
                        }
                        
                        Toast.makeText(this, "Registration Failed: " + error, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void uploadProfilePhoto(FirebaseUser user, String fName, String lName, String phone, String email) {
        StorageReference ref = storage.getReference("profile_pics").child(user.getUid() + ".jpg");
        ref.putFile(selectedImageUri)
                .addOnSuccessListener(taskSnapshot -> ref.getDownloadUrl().addOnSuccessListener(uri -> saveUserData(user, fName, lName, phone, email, uri.toString())))
                .addOnFailureListener(e -> saveUserData(user, fName, lName, phone, email, null));
    }

    private void saveUserData(FirebaseUser user, String fName, String lName, String phone, String email, String photoUrl) {
        String fullName = fName + " " + lName;
        
        // Get selected role from admin
        int selectedRoleId = rgRole.getCheckedRadioButtonId();
        String role = (selectedRoleId == R.id.rbAdmin) ? "admin" : "farmer";
        
        int selectedGenderId = rgGender.getCheckedRadioButtonId();
        String gender = (selectedGenderId != -1) ? ((RadioButton)findViewById(selectedGenderId)).getText().toString() : "Not Specified";

        Map<String, Object> data = new HashMap<>();
        data.put("uid", user.getUid());
        data.put("name", fullName);
        data.put("firstName", fName);
        data.put("lastName", lName);
        data.put("email", email);
        data.put("phone", phone);
        data.put("gender", gender);
        data.put("role", role);
        if (photoUrl != null) data.put("photoUrl", photoUrl);

        firestore.collection("users").document(user.getUid())
                .set(data)
                .addOnSuccessListener(unused -> {
                    UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                            .setDisplayName(fullName)
                            .setPhotoUri(photoUrl != null ? Uri.parse(photoUrl) : null)
                            .build();
                    
                    user.updateProfile(profileUpdates).addOnCompleteListener(t -> {
                        progressBar.setVisibility(View.GONE);
                        if (t.isSuccessful()) {
                            Toast.makeText(this, "User Registration Successful", Toast.LENGTH_SHORT).show();
                            
                            // Send notification about new user registration
                            NotificationHelper.showNewUserNotification(this, fullName, role);
                            
                            // Sign out the newly created user and continue as admin
                            auth.signOut();
                            
                            // Re-login as admin (you might want to store admin credentials)
                            Intent intent = new Intent(this, AdminDashboardActivity.class);
                            startActivity(intent);
                            finish();
                        } else {
                            Toast.makeText(this, "Partial Registration: Auth Update Failed", Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Failed to save profile: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
}
