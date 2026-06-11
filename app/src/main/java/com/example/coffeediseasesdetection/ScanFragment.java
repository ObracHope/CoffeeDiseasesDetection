package com.example.coffeediseasesdetection;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
public class ScanFragment extends Fragment {

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_scan, container, false);

        CardView cardCamera = view.findViewById(R.id.cardCamera);
        CardView cardGallery = view.findViewById(R.id.cardGallery);

        cardCamera.setOnClickListener(v -> openCamera());
        cardGallery.setOnClickListener(v -> openGallery());
        requestNotificationPermission();
        requestLocationPermission();

        return view;
    }

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (!isGranted && getContext() != null) {
                    Toast.makeText(getContext(), R.string.notification_permission_denied, Toast.LENGTH_SHORT).show();
                }
            });

    private final ActivityResultLauncher<String[]> requestLocationLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> { });

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && getContext() != null) {
            if (ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS);
            }
        }
    }

    private void requestLocationPermission() {
        if (getContext() == null) return;
        if (!LocationHelper.hasPermission(requireContext())) {
            requestLocationLauncher.launch(new String[]{
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
            });
        }
    }

    private void openCamera() {
        startActivity(new Intent(requireContext(), CaptureLeafActivity.class));
    }

    private void openGallery() {
        startActivity(new Intent(requireContext(), UploadImageActivity.class));
    }

}
