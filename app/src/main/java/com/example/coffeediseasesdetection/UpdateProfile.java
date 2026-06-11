package com.example.coffeediseasesdetection;

import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.HashMap;
import java.util.Map;

public class UpdateProfile extends BaseActivity {

    private static final String TAG = "UpdateProfile";
    private EditText etFirstName, etLastName, etRegion, etDistrict, etWard;
    private ImageView ivProfilePhoto;
    private MaterialButton btnSave;
    private ProgressBar progressBar;
    private FirebaseAuth auth;
    private FirebaseFirestore firestore;
    private Uri selectedImageUri;
    private String lockedFirstName = "";
    private String lockedLastName = "";

    private final ActivityResultLauncher<String> galleryLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    selectedImageUri = uri;
                    Glide.with(this).load(uri).circleCrop().into(ivProfilePhoto);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_update_profile);

        auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();

        etFirstName = findViewById(R.id.etFirstName);
        etLastName = findViewById(R.id.etLastName);
        etRegion = findViewById(R.id.etRegion);
        etDistrict = findViewById(R.id.etDistrict);
        etWard = findViewById(R.id.etWard);
        EditText etEmail = findViewById(R.id.etEmail);
        ivProfilePhoto = findViewById(R.id.ivProfilePhoto);
        btnSave = findViewById(R.id.btnSaveProfile);
        MaterialButton btnChangePhoto = findViewById(R.id.btnChangePhoto);
        progressBar = findViewById(R.id.progressBar);
        TextView tvProfileHint = findViewById(R.id.tvProfileHint);

        if (tvProfileHint != null) {
            tvProfileHint.setText(R.string.profile_photo_only);
        }

        lockNameFields(etFirstName, etLastName);
        if (etEmail != null) {
            etEmail.setEnabled(false);
            etEmail.setFocusable(false);
        }

        FirebaseUser user = auth.getCurrentUser();
        if (user != null) {
            if (etEmail != null) etEmail.setText(user.getEmail());
            if (user.getPhotoUrl() != null) {
                Glide.with(this).load(user.getPhotoUrl()).circleCrop()
                        .placeholder(R.drawable.ic_person_custom).into(ivProfilePhoto);
            }
            loadUserData(user.getUid());
        }

        View.OnClickListener openGallery = v -> galleryLauncher.launch("image/*");
        if (ivProfilePhoto != null) ivProfilePhoto.setOnClickListener(openGallery);
        if (btnChangePhoto != null) btnChangePhoto.setOnClickListener(openGallery);
        if (btnSave != null) btnSave.setOnClickListener(v -> updateProfile());
    }

    private void lockNameFields(EditText first, EditText last) {
        if (first != null) {
            first.setEnabled(false);
            first.setFocusable(false);
            first.setClickable(false);
        }
        if (last != null) {
            last.setEnabled(false);
            last.setFocusable(false);
            last.setClickable(false);
        }
    }

    private void loadUserData(String uid) {
        android.content.SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        lockedFirstName = prefs.getString(KEY_FIRST_NAME, "");
        lockedLastName = prefs.getString(KEY_LAST_NAME, "");
        if (!lockedFirstName.isEmpty()) etFirstName.setText(lockedFirstName);
        if (!lockedLastName.isEmpty()) etLastName.setText(lockedLastName);

        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);

        firestore.collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (progressBar != null) progressBar.setVisibility(View.GONE);
                    if (doc.exists()) {
                        if (doc.getString("firstName") != null) {
                            lockedFirstName = doc.getString("firstName");
                            etFirstName.setText(lockedFirstName);
                        }
                        if (doc.getString("lastName") != null) {
                            lockedLastName = doc.getString("lastName");
                            etLastName.setText(lockedLastName);
                        }
                        if (etRegion != null && doc.getString("region") != null) {
                            etRegion.setText(doc.getString("region"));
                        }
                        if (etDistrict != null && doc.getString("district") != null) {
                            etDistrict.setText(doc.getString("district"));
                        }
                        if (etWard != null && doc.getString("ward") != null) {
                            etWard.setText(doc.getString("ward"));
                        }
                        String photo = doc.getString("photoUrl");
                        if (photo != null && !photo.isEmpty()) {
                            Glide.with(this).load(photo).circleCrop().into(ivProfilePhoto);
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    if (progressBar != null) progressBar.setVisibility(View.GONE);
                    Log.e(TAG, "Load failed", e);
                });
    }

    private void updateProfile() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        if (selectedImageUri == null) {
            String region = etRegion != null ? etRegion.getText().toString().trim() : "";
            String district = etDistrict != null ? etDistrict.getText().toString().trim() : "";
            String ward = etWard != null ? etWard.getText().toString().trim() : "";
            saveProfile(user, null, region, district, ward);
            return;
        }

        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
        if (btnSave != null) btnSave.setEnabled(false);

        StorageReference storageRef = FirebaseStorage.getInstance()
                .getReference("profile_pics/" + user.getUid() + ".jpg");
        storageRef.putFile(selectedImageUri)
                .addOnSuccessListener(t -> storageRef.getDownloadUrl()
                        .addOnSuccessListener(uri -> {
                            String region = etRegion != null ? etRegion.getText().toString().trim() : "";
                            String district = etDistrict != null ? etDistrict.getText().toString().trim() : "";
                            String ward = etWard != null ? etWard.getText().toString().trim() : "";
                            saveProfile(user, uri, region, district, ward);
                        }))
                .addOnFailureListener(e -> {
                    if (progressBar != null) progressBar.setVisibility(View.GONE);
                    if (btnSave != null) btnSave.setEnabled(true);
                    Toast.makeText(this, R.string.photo_upload_failed, Toast.LENGTH_SHORT).show();
                });
    }

    private void saveProfile(FirebaseUser user, Uri photoUri, String region, String district, String ward) {
        String uid = user.getUid();
        String fullName = (lockedFirstName + " " + lockedLastName).trim();

        Map<String, Object> updates = new HashMap<>();
        updates.put("firstName", lockedFirstName);
        updates.put("lastName", lockedLastName);
        updates.put("name", fullName);
        updates.put("region", region);
        updates.put("district", district);
        updates.put("ward", ward);
        if (photoUri != null) {
            updates.put("photoUrl", photoUri.toString());
        }

        firestore.collection("users").document(uid).set(updates, SetOptions.merge())
                .addOnSuccessListener(v -> AuthHelper.usersRtdb().child(uid).updateChildren(updates)
                        .addOnCompleteListener(t -> applyAuthAndFinish(user, fullName, photoUri)))
                .addOnFailureListener(e -> {
                    if (progressBar != null) progressBar.setVisibility(View.GONE);
                    if (btnSave != null) btnSave.setEnabled(true);
                    Toast.makeText(this, R.string.update_failed, Toast.LENGTH_SHORT).show();
                });
    }

    private void applyAuthAndFinish(FirebaseUser user, String fullName, Uri photoUri) {
        saveUserCache(null, fullName, photoUri != null ? photoUri.toString() : null, lockedFirstName, lockedLastName);

        UserProfileChangeRequest.Builder builder = new UserProfileChangeRequest.Builder()
                .setDisplayName(fullName);
        if (photoUri != null) builder.setPhotoUri(photoUri);

        user.updateProfile(builder.build()).addOnCompleteListener(task -> {
            if (progressBar != null) progressBar.setVisibility(View.GONE);
            if (btnSave != null) btnSave.setEnabled(true);
            if (task.isSuccessful()) {
                Toast.makeText(this, R.string.profile_updated, Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }
}
