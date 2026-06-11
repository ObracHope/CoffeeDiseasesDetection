package com.example.coffeediseasesdetection;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.google.android.material.button.MaterialButton;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class CaptureLeafActivity extends BaseActivity {

    private static final String TAG = "CaptureLeafActivity";
    private ImageView ivImagePreview;
    private View btnCapture, btnRetake, btnDetect, btnGallery;
    private ProgressBar progressBar;
    private TextView tvInstruction;

    private Bitmap capturedBitmap;
    private String currentPhotoPath;
    private Uri currentPhotoUri;

    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    openCamera();
                } else {
                    Toast.makeText(this, R.string.camera_permission_denied, Toast.LENGTH_SHORT).show();
                }
            });

    private final ActivityResultLauncher<Intent> cameraLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    processCapturedPhoto();
                }
            });

    private final ActivityResultLauncher<Intent> galleryLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri imageUri = result.getData().getData();
                    processGalleryImage(imageUri);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_capture_leaf);

        // Preload TensorFlow Lite model for faster detection
        try {
            DiseaseDetector.loadModel(this);
            Log.d(TAG, "Model loaded successfully in onCreate");
        } catch (Exception e) {
            Log.e(TAG, "Failed to load model in onCreate", e);
        }
        
        // Initialize views
        ivImagePreview = findViewById(R.id.ivImagePreview);
        if (ivImagePreview == null) ivImagePreview = findViewById(R.id.imgPreview);
        
        btnCapture = findViewById(R.id.btnCapture);
        btnRetake = findViewById(R.id.btnRetake);
        btnDetect = findViewById(R.id.btnDetect);
        btnGallery = findViewById(R.id.btnGallery);
        progressBar = findViewById(R.id.progressBar);
        tvInstruction = findViewById(R.id.tvInstruction);

        View ivBack = findViewById(R.id.ivBackButton);
        if (ivBack == null) ivBack = findViewById(R.id.ivBack);
        if (ivBack != null) ivBack.setOnClickListener(v -> finish());

        if (btnCapture != null) btnCapture.setOnClickListener(v -> checkPermissionAndOpenCamera());
        if (btnRetake != null) btnRetake.setOnClickListener(v -> resetCapture());
        if (btnDetect != null) btnDetect.setOnClickListener(v -> detectDisease());
        if (btnGallery != null) btnGallery.setOnClickListener(v -> openGallery());
        
        // Initial state
        if (btnDetect != null) btnDetect.setVisibility(View.GONE);
        if (btnRetake != null) btnRetake.setVisibility(View.GONE);
    }

    private void checkPermissionAndOpenCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            openCamera();
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void openCamera() {
        try {
            File photoFile = createImageFile();
            currentPhotoUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", photoFile);
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, currentPhotoUri);
            cameraLauncher.launch(intent);
        } catch (IOException ex) {
            Toast.makeText(this, getString(R.string.file_error, ex.getMessage()), Toast.LENGTH_SHORT).show();
        }
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        galleryLauncher.launch(intent);
    }

    private void processGalleryImage(Uri imageUri) {
        try {
            if (imageUri != null) {
                currentPhotoUri = imageUri;
                currentPhotoPath = getRealPathFromURI(imageUri);
                
                // Load and optimize bitmap from gallery
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeStream(getContentResolver().openInputStream(imageUri), null, options);
                
                // Calculate sample size
                options.inSampleSize = calculateInSampleSize(options, 1024, 1024);
                options.inJustDecodeBounds = false;
                
                capturedBitmap = BitmapFactory.decodeStream(getContentResolver().openInputStream(imageUri), null, options);
                
                if (capturedBitmap != null) {
                    ivImagePreview.setImageBitmap(capturedBitmap);
                    if (btnCapture != null) btnCapture.setVisibility(View.GONE);
                    if (btnRetake != null) btnRetake.setVisibility(View.VISIBLE);
                    if (btnDetect != null) {
                        btnDetect.setVisibility(View.VISIBLE);
                        btnDetect.setEnabled(true);
                    }
                    if (tvInstruction != null) tvInstruction.setText(R.string.instruction_image_selected);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing gallery image", e);
            Toast.makeText(this, getString(R.string.image_load_error, e.getMessage()), Toast.LENGTH_LONG).show();
        }
    }

    private String getRealPathFromURI(Uri contentUri) {
        String[] proj = { MediaStore.Images.Media.DATA };
        android.database.Cursor cursor = getContentResolver().query(contentUri, proj, null, null, null, null);
        if (cursor != null) {
            int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            cursor.moveToFirst();
            String path = cursor.getString(column_index);
            cursor.close();
            return path;
        }
        return contentUri.getPath();
    }

    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile("JPEG_" + timeStamp + "_", ".jpg", storageDir);
        currentPhotoPath = image.getAbsolutePath();
        return image;
    }

    private void processCapturedPhoto() {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 2; // Resize to save memory
            capturedBitmap = BitmapFactory.decodeFile(currentPhotoPath, options);
            if (capturedBitmap != null && ivImagePreview != null) {
                ivImagePreview.setImageBitmap(capturedBitmap);
                if (btnCapture != null) btnCapture.setVisibility(View.GONE);
                if (btnRetake != null) btnRetake.setVisibility(View.VISIBLE);
                if (btnDetect != null) {
                    btnDetect.setVisibility(View.VISIBLE);
                    btnDetect.setEnabled(true);
                }
                if (tvInstruction != null) tvInstruction.setText(R.string.instruction_photo_captured);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing photo", e);
            Toast.makeText(this, R.string.image_load_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void resetCapture() {
        capturedBitmap = null;
        if (ivImagePreview != null) {
            ivImagePreview.setImageBitmap(null);
        }
        if (btnCapture != null) btnCapture.setVisibility(View.VISIBLE);
        if (btnRetake != null) btnRetake.setVisibility(View.GONE);
        if (btnDetect != null) btnDetect.setVisibility(View.GONE);
        if (tvInstruction != null) tvInstruction.setText(R.string.instruction_capture_leaf);
    }

    private void detectDisease() {
        if (capturedBitmap == null) return;
        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
        if (btnDetect != null) btnDetect.setEnabled(false);
        if (btnRetake != null) btnRetake.setEnabled(false);

        DiseaseDetector.detect(this, capturedBitmap, result -> runOnUiThread(() -> {
            if (isFinishing()) return;
            if (progressBar != null) progressBar.setVisibility(View.GONE);
            if (btnDetect != null) btnDetect.setEnabled(true);
            if (btnRetake != null) btnRetake.setEnabled(true);
            DetectionResultLauncher.open(this, result, currentPhotoPath);
        }));
    }
}
