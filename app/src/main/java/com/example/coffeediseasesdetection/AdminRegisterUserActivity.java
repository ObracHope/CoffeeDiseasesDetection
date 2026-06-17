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
import android.widget.LinearLayout;
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
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AdminRegisterUserActivity extends BaseActivity {

    public static final String EXTRA_USER_ID = "user_id";
    public static final String EXTRA_EDIT_MODE = "edit_mode";

    private FirebaseFirestore firestore;
    private FirebaseStorage storage;

    private EditText etFirstName, etLastName, etEmail, etPhone, etPassword, etRepeatPassword;
    private EditText etRegion, etDistrict, etWard;
    private CheckBox cbAgree;
    private RadioGroup rgGender;
    private Spinner spinnerUserRole, spinnerCountryCode;
    private ImageView ivProfile;
    private ProgressBar progressBar;
    private LinearLayout layoutPasswordSection;
    private FloatingActionButton btnPickPhoto, btnRemovePhoto;
    private TextView tvTitle;
    private Button btnCreateAccount;

    private Uri selectedImageUri;
    private String existingPhotoUrl;
    private final List<String> registerRoleValues = new ArrayList<>();

    private boolean editMode;
    private String editUserId;

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

        firestore = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();

        editUserId = getIntent().getStringExtra(EXTRA_USER_ID);
        editMode = getIntent().getBooleanExtra(EXTRA_EDIT_MODE, false)
                || !TextUtils.isEmpty(editUserId);

        etFirstName = findViewById(R.id.etFirstName);
        etLastName = findViewById(R.id.etLastName);
        etEmail = findViewById(R.id.etEmailRegister);
        etPhone = findViewById(R.id.etPhone);
        etPassword = findViewById(R.id.etPasswordRegister);
        etRepeatPassword = findViewById(R.id.etRepeatPassword);
        etRegion = findViewById(R.id.etRegion);
        etDistrict = findViewById(R.id.etDistrict);
        etWard = findViewById(R.id.etWard);
        cbAgree = findViewById(R.id.cbAgree);
        rgGender = findViewById(R.id.rgGender);
        spinnerUserRole = findViewById(R.id.spinnerUserRole);
        spinnerCountryCode = findViewById(R.id.spinnerCountryCode);
        ivProfile = findViewById(R.id.ivRegisterProfile);
        progressBar = findViewById(R.id.progressBar);
        btnPickPhoto = findViewById(R.id.btnPickPhoto);
        btnRemovePhoto = findViewById(R.id.btnRemovePhoto);
        layoutPasswordSection = findViewById(R.id.layoutPasswordSection);
        tvTitle = findViewById(R.id.tvRegisterTitle);
        btnCreateAccount = findViewById(R.id.btnCreateAccount);
        TextView tvBack = findViewById(R.id.tvBack);

        if (tvTitle == null) {
            // Title TextView may not have id in older layout — find first bold TextView at top
            tvTitle = null;
        }

        btnPickPhoto.setOnClickListener(v -> galleryLauncher.launch("image/*"));
        btnRemovePhoto.setOnClickListener(v -> removeProfilePhoto());

        ArrayAdapter<CharSequence> codeAdapter = ArrayAdapter.createFromResource(this,
                R.array.phone_country_codes, android.R.layout.simple_spinner_item);
        codeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCountryCode.setAdapter(codeAdapter);

        setupRoleSpinner();

        if (editMode) {
            if (layoutPasswordSection != null) layoutPasswordSection.setVisibility(View.GONE);
            if (cbAgree != null) cbAgree.setVisibility(View.GONE);
            btnCreateAccount.setText("Save Changes");
            if (tvTitle != null) tvTitle.setText(getString(R.string.admin_edit_user));
            setTitle(R.string.admin_edit_user);
            if (editUserId != null) loadUserForEdit(editUserId);
        } else {
            setTitle(R.string.add_user);
        }

        btnCreateAccount.setOnClickListener(v -> {
            if (editMode) updateUser();
            else registerUser();
        });
        tvBack.setOnClickListener(v -> finish());
    }

    private void setupRoleSpinner() {
        registerRoleValues.clear();
        List<String> labels = new ArrayList<>();

        registerRoleValues.add("farmer");
        labels.add("Farmer (Mkulima)");
        registerRoleValues.add("admin");
        labels.add("Admin");

        String actorRole = AuthHelper.normalizeRole(
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(KEY_ROLE, "admin"));
        if ("system_admin".equals(actorRole) || "main".equals(actorRole)
                || "superadmin".equals(actorRole) || "it".equals(actorRole)) {
            registerRoleValues.add("system_admin");
            labels.add("System Admin");
            registerRoleValues.add("it");
            labels.add("IT");
            registerRoleValues.add("technician");
            labels.add("Technician");
            registerRoleValues.add("bwana_kilimo");
            labels.add("Bwana Kilimo");
            registerRoleValues.add("waziri_wa_kilimo");
            labels.add("Waziri wa Kilimo");
        }

        ArrayAdapter<String> roleAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, labels);
        roleAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerUserRole.setAdapter(roleAdapter);
    }

    private void selectRole(String role) {
        if (role == null) return;
        String normalized = AuthHelper.normalizeRole(role);
        for (int i = 0; i < registerRoleValues.size(); i++) {
            if (registerRoleValues.get(i).equals(normalized)) {
                spinnerUserRole.setSelection(i);
                return;
            }
        }
    }

    private void loadUserForEdit(String userId) {
        progressBar.setVisibility(View.VISIBLE);
        firestore.collection("users").document(userId).get()
                .addOnSuccessListener(doc -> {
                    progressBar.setVisibility(View.GONE);
                    if (!doc.exists()) {
                        Toast.makeText(this, "User not found", Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }
                    String firstName = doc.getString("firstName");
                    String lastName = doc.getString("lastName");
                    if (TextUtils.isEmpty(firstName) && doc.getString("name") != null) {
                        String[] parts = doc.getString("name").split(" ", 2);
                        firstName = parts[0];
                        lastName = parts.length > 1 ? parts[1] : "";
                    }
                    if (firstName != null) etFirstName.setText(firstName);
                    if (lastName != null) etLastName.setText(lastName);
                    if (doc.getString("email") != null) etEmail.setText(doc.getString("email"));

                    String phone = doc.getString("phone");
                    if (phone != null) {
                        String digits = phone.replaceAll("\\D", "");
                        String[] codes = getResources().getStringArray(R.array.phone_country_codes);
                        for (int i = 0; i < codes.length; i++) {
                            String prefix = codes[i].split(" ")[0].replace("+", "");
                            if (digits.startsWith(prefix)) {
                                spinnerCountryCode.setSelection(i);
                                etPhone.setText(digits.substring(prefix.length()));
                                break;
                            }
                        }
                        if (etPhone.getText().toString().isEmpty()) {
                            etPhone.setText(phone.replaceAll("[^0-9]", ""));
                        }
                    }

                    if (doc.getString("region") != null) etRegion.setText(doc.getString("region"));
                    if (doc.getString("district") != null) etDistrict.setText(doc.getString("district"));
                    if (doc.getString("ward") != null) etWard.setText(doc.getString("ward"));

                    String gender = doc.getString("gender");
                    if (gender != null) {
                        if (gender.toLowerCase(Locale.US).contains("male") && !gender.toLowerCase(Locale.US).contains("fe")) {
                            rgGender.check(R.id.rbMale);
                        } else if (gender.toLowerCase(Locale.US).contains("female") || gender.toLowerCase(Locale.US).contains("fe")) {
                            rgGender.check(R.id.rbFemale);
                        }
                    }

                    selectRole(doc.getString("role"));

                    existingPhotoUrl = doc.getString("photoUrl");
                    if (!TextUtils.isEmpty(existingPhotoUrl)) {
                        Glide.with(this).load(existingPhotoUrl).circleCrop()
                                .placeholder(R.drawable.placeholder_user).into(ivProfile);
                        btnRemovePhoto.setVisibility(View.VISIBLE);
                    }
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Failed to load user", Toast.LENGTH_SHORT).show();
                    finish();
                });
    }

    private void removeProfilePhoto() {
        selectedImageUri = null;
        existingPhotoUrl = null;
        ivProfile.setImageResource(R.drawable.placeholder_user);
        btnRemovePhoto.setVisibility(View.GONE);
    }

    private boolean validatePhone(String digitsOnly) {
        int pos = spinnerCountryCode.getSelectedItemPosition();
        if (pos < 0 || pos >= PHONE_LENGTHS.length) return digitsOnly.length() >= 9;
        return digitsOnly.length() == PHONE_LENGTHS[pos];
    }

    private String buildPhone() {
        String phoneRaw = etPhone.getText().toString().trim();
        String digitsOnly = phoneRaw.replaceAll("\\D", "");
        String countryCode = getResources().getStringArray(R.array.phone_country_codes)[spinnerCountryCode.getSelectedItemPosition()];
        return countryCode.split(" ")[0].trim() + " " + digitsOnly;
    }

    private String selectedRole() {
        int roleIndex = spinnerUserRole != null ? spinnerUserRole.getSelectedItemPosition() : 0;
        return roleIndex >= 0 && roleIndex < registerRoleValues.size()
                ? registerRoleValues.get(roleIndex) : "farmer";
    }

    private String selectedGender() {
        int selectedGenderId = rgGender.getCheckedRadioButtonId();
        return (selectedGenderId != -1)
                ? ((RadioButton) findViewById(selectedGenderId)).getText().toString()
                : "Not Specified";
    }

    private void registerUser() {
        String firstName = etFirstName.getText().toString().trim();
        String lastName = etLastName.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String phoneRaw = etPhone.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String repeatPassword = etRepeatPassword.getText().toString().trim();
        String region = etRegion.getText().toString().trim();
        String district = etDistrict.getText().toString().trim();
        String ward = etWard.getText().toString().trim();

        if (TextUtils.isEmpty(firstName)) { etFirstName.setError("First name required"); return; }
        if (TextUtils.isEmpty(lastName)) { etLastName.setError("Last name required"); return; }
        if (TextUtils.isEmpty(email)) { etEmail.setError("Email required"); return; }

        String digitsOnly = phoneRaw.replaceAll("\\D", "");
        if (TextUtils.isEmpty(phoneRaw) || digitsOnly.isEmpty()) { etPhone.setError("Phone required"); return; }
        if (!validatePhone(digitsOnly)) {
            etPhone.setError("Invalid phone number");
            return;
        }

        if (TextUtils.isEmpty(password) || password.length() < 6) { etPassword.setError("Password min 6 chars"); return; }
        if (!password.equals(repeatPassword)) {
            etRepeatPassword.setError("Passwords do not match");
            return;
        }
        if (cbAgree != null && !cbAgree.isChecked()) {
            Toast.makeText(this, "You must agree to terms", Toast.LENGTH_SHORT).show();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        final String finalPhone = buildPhone();
        final String finalEmail = email;
        final String finalFirstName = firstName;
        final String finalLastName = lastName;

        AdminAuthHelper.secondaryAuth().createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult().getUser() != null) {
                        FirebaseUser user = task.getResult().getUser();
                        user.sendEmailVerification();
                        if (selectedImageUri != null) {
                            uploadProfilePhoto(user, finalFirstName, finalLastName, finalPhone, finalEmail, region, district, ward);
                        } else {
                            saveUserData(user, finalFirstName, finalLastName, finalPhone, finalEmail, region, district, ward, null);
                        }
                    } else {
                        progressBar.setVisibility(View.GONE);
                        String error = (task.getException() != null) ? task.getException().getMessage() : "Registration failed";
                        if (error != null) {
                            if (error.contains("email address is already")) {
                                error = "This email is already registered.";
                            } else if (error.contains("weak password")) {
                                error = "Password is too weak.";
                            }
                        }
                        Toast.makeText(this, "Registration Failed: " + error, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void updateUser() {
        if (TextUtils.isEmpty(editUserId)) return;

        String firstName = etFirstName.getText().toString().trim();
        String lastName = etLastName.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String region = etRegion.getText().toString().trim();
        String district = etDistrict.getText().toString().trim();
        String ward = etWard.getText().toString().trim();

        if (TextUtils.isEmpty(firstName)) { etFirstName.setError("First name required"); return; }
        if (TextUtils.isEmpty(lastName)) { etLastName.setError("Last name required"); return; }
        if (TextUtils.isEmpty(email)) { etEmail.setError("Email required"); return; }

        String phoneRaw = etPhone.getText().toString().trim();
        String digitsOnly = phoneRaw.replaceAll("\\D", "");
        if (TextUtils.isEmpty(phoneRaw) || digitsOnly.isEmpty()) { etPhone.setError("Phone required"); return; }
        if (!validatePhone(digitsOnly)) {
            etPhone.setError("Invalid phone number");
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        final String finalPhone = buildPhone();
        final String fullName = firstName + " " + lastName;
        final String role = selectedRole();
        final String gender = selectedGender();

        Runnable persist = () -> {
            Map<String, Object> data = new HashMap<>();
            data.put("uid", editUserId);
            data.put("name", fullName);
            data.put("firstName", firstName);
            data.put("lastName", lastName);
            data.put("email", email);
            data.put("phone", finalPhone);
            data.put("gender", gender);
            data.put("role", role);
            data.put("region", region);
            data.put("district", district);
            data.put("ward", ward);
            data.put("username", AuthHelper.buildUsername(firstName, lastName, email));
            if (existingPhotoUrl != null) data.put("photoUrl", existingPhotoUrl);

            firestore.collection("users").document(editUserId).set(data)
                    .addOnSuccessListener(unused -> {
                        AuthHelper.usersRtdb().child(editUserId).setValue(data);
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(this, R.string.user_updated_success, Toast.LENGTH_SHORT).show();
                        setResult(RESULT_OK);
                        finish();
                    })
                    .addOnFailureListener(e -> {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(this, "Update failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
        };

        if (selectedImageUri != null) {
            StorageReference ref = storage.getReference("profile_pics").child(editUserId + ".jpg");
            ref.putFile(selectedImageUri)
                    .addOnSuccessListener(taskSnapshot -> ref.getDownloadUrl()
                            .addOnSuccessListener(uri -> {
                                existingPhotoUrl = uri.toString();
                                persist.run();
                            })
                            .addOnFailureListener(e -> persist.run()))
                    .addOnFailureListener(e -> persist.run());
        } else {
            persist.run();
        }
    }

    private void uploadProfilePhoto(FirebaseUser user, String fName, String lName, String phone, String email,
                                    String region, String district, String ward) {
        StorageReference ref = storage.getReference("profile_pics").child(user.getUid() + ".jpg");
        ref.putFile(selectedImageUri)
                .addOnSuccessListener(taskSnapshot -> ref.getDownloadUrl()
                        .addOnSuccessListener(uri -> saveUserData(user, fName, lName, phone, email, region, district, ward, uri.toString())))
                .addOnFailureListener(e -> saveUserData(user, fName, lName, phone, email, region, district, ward, null));
    }

    private void saveUserData(FirebaseUser user, String fName, String lName, String phone, String email,
                              String region, String district, String ward, String photoUrl) {
        String fullName = fName + " " + lName;
        String role = selectedRole();
        String gender = selectedGender();

        Map<String, Object> data = new HashMap<>();
        data.put("uid", user.getUid());
        data.put("name", fullName);
        data.put("firstName", fName);
        data.put("lastName", lName);
        data.put("email", email);
        data.put("phone", phone);
        data.put("gender", gender);
        data.put("role", role);
        data.put("region", region);
        data.put("district", district);
        data.put("ward", ward);
        data.put("username", AuthHelper.buildUsername(fName, lName, email));
        if (photoUrl != null) data.put("photoUrl", photoUrl);
        data.put("lastSetPassword", etPassword.getText().toString().trim());

        firestore.collection("users").document(user.getUid())
                .set(data)
                .addOnSuccessListener(unused -> {
                    AuthHelper.usersRtdb().child(user.getUid()).setValue(data);
                    UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                            .setDisplayName(fullName)
                            .setPhotoUri(photoUrl != null ? Uri.parse(photoUrl) : null)
                            .build();

                    user.updateProfile(profileUpdates).addOnCompleteListener(t -> {
                        AdminAuthHelper.secondaryAuth().signOut();
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(this, R.string.user_added_success, Toast.LENGTH_SHORT).show();
                        NotificationHelper.showNewUserNotification(this, fullName, role);
                        setResult(RESULT_OK);
                        finish();
                    });
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Failed to save profile: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
}
