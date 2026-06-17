package com.example.coffeediseasesdetection;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import android.provider.MediaStore;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.File;
import java.util.Collections;

public class ProfileFragment extends Fragment {

    private static final String TAG = "ProfileFragment";
    private View ivProfilePhoto;
    private TextView tvName;
    private TextView tvRole;
    private FirebaseAuth auth;
    private DatabaseReference mDatabase;
    private FirebaseStorage storage;
    private Uri currentPhotoUri;

    private final ActivityResultLauncher<String> galleryLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(),
                    uri -> {
                        if (uri != null) {
                            uploadProfilePhoto(uri);
                        }
                    }
            );

    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    openCamera();
                } else {
                    Toast.makeText(requireContext(), R.string.camera_permission_denied, Toast.LENGTH_SHORT).show();
                }
            });

    private final ActivityResultLauncher<Intent> cameraLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == android.app.Activity.RESULT_OK && currentPhotoUri != null) {
                    uploadProfilePhoto(currentPhotoUri);
                }
            }
    );

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_profile, container, false);

        auth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance("https://coffee-diseases-detection-default-rtdb.firebaseio.com").getReference();
        storage = FirebaseStorage.getInstance();

        ivProfilePhoto = view.findViewById(R.id.ivProfilePhoto);
        tvName = view.findViewById(R.id.tvProfileName);
        tvRole = view.findViewById(R.id.tvProfileRole);
        TextView tvEmail = view.findViewById(R.id.tvProfileEmail);
        View btnChangePhoto = view.findViewById(R.id.btnChangePhoto);
        View btnLogout = view.findViewById(R.id.btnPopupSignOut);

        FirebaseUser user = auth.getCurrentUser();
        if (user != null) {
            tvEmail.setText(user.getEmail());
            loadProfileData(user.getUid());
        }

        btnChangePhoto.setOnClickListener(v -> showPhotoDialog());

        if (btnLogout != null && requireActivity() instanceof BaseActivity) {
            btnLogout.setOnClickListener(v -> ((BaseActivity) requireActivity()).performLogout());
        }

        return view;
    }

    private void showPhotoDialog() {
        String[] options = {
                getString(R.string.take_photo),
                getString(R.string.choose_from_gallery)
        };
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.update_profile_photo)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        openCamera();
                    } else {
                        galleryLauncher.launch("image/*");
                    }
                }).show();
    }

    private void openCamera() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.CAMERA)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA);
            return;
        }

        try {
            File photoFile = new File(requireContext().getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES), "profile_" + System.currentTimeMillis() + ".jpg");
            currentPhotoUri = androidx.core.content.FileProvider.getUriForFile(requireContext(),
                    "com.example.coffeediseasesdetection.fileprovider", photoFile);
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, currentPhotoUri);
            cameraLauncher.launch(intent);
        } catch (Exception e) {
            Toast.makeText(requireContext(), getString(R.string.error_with_message, e.getMessage()), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        FirebaseUser user = auth.getCurrentUser();
        if (user != null) {
            loadProfileData(user.getUid());
        }
    }

    private void loadProfileData(String uid) {
        if (!isAdded()) return;
        
        mDatabase.child("users").child(uid).get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.exists() && isAdded()) {
                        String name = snapshot.child("name").getValue(String.class);
                        String photoUrl = snapshot.child("photoUrl").getValue(String.class);
                        String firstName = snapshot.child("firstName").getValue(String.class);
                        String lastName = snapshot.child("lastName").getValue(String.class);
                        String role = snapshot.child("role").getValue(String.class);

                        String displayName = ProfileHelper.fullName(firstName, lastName, name);
                        if (tvName != null) {
                            tvName.setText(!displayName.isEmpty() ? displayName : getString(R.string.profile_name_placeholder));
                        }
                        if (tvRole != null) {
                            tvRole.setText(ProfileHelper.roleLabel(requireContext(), role));
                            tvRole.setVisibility(android.view.View.VISIBLE);
                        }

                        if (photoUrl != null && !photoUrl.isEmpty() && ivProfilePhoto != null) {
                            Glide.with(this)
                                    .load(photoUrl)
                                    .placeholder(R.drawable.ic_nav_profile)
                                    .circleCrop()
                                    .into((android.widget.ImageView) ivProfilePhoto);
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    if (isAdded()) Log.e(TAG, "Failed to load profile", e);
                });
    }

    private void uploadProfilePhoto(Uri uri) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        if (ivProfilePhoto != null && isAdded()) {
            Glide.with(this)
                    .load(uri)
                    .placeholder(R.drawable.ic_nav_profile)
                    .circleCrop()
                    .into((android.widget.ImageView) ivProfilePhoto);
        }

        Toast.makeText(requireContext(), R.string.updating_photo, Toast.LENGTH_SHORT).show();
        
        StorageReference ref = storage.getReference("profile_pics").child(user.getUid() + ".jpg");
        
        ref.putFile(uri)
                .addOnSuccessListener(taskSnapshot ->
                    ref.getDownloadUrl().addOnSuccessListener(downloadUri -> {
                        if (isAdded()) {
                            mDatabase.child("users").child(user.getUid()).child("photoUrl")
                                    .setValue(downloadUri.toString())
                                    .addOnSuccessListener(aVoid -> {
                                        if (isAdded()) {
                                            Toast.makeText(requireContext(), R.string.profile_photo_updated, Toast.LENGTH_SHORT).show();
                                            loadProfileData(user.getUid());
                                        }
                                    })
                                    .addOnFailureListener(e -> {
                                        if (isAdded()) Toast.makeText(requireContext(), R.string.failed_save_url, Toast.LENGTH_SHORT).show();
                                    });
                        }
                    })
                )
                .addOnFailureListener(e -> {
                    if (isAdded()) {
                        Log.e(TAG, "Upload failed", e);
                        Toast.makeText(requireContext(), getString(R.string.upload_failed, e.getMessage()), Toast.LENGTH_LONG).show();
                    }
                });
    }
}
