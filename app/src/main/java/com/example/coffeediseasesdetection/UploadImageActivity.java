package com.example.coffeediseasesdetection;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.google.android.material.button.MaterialButton;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

public class UploadImageActivity extends BaseActivity {

    private static final String TAG = "UploadImageActivity";
    private ImageView ivImagePreview;
    private MaterialButton btnSelectImage;
    private MaterialButton btnDetect;
    private ProgressBar progressBar;
    private TextView tvInstruction;
    private Bitmap selectedBitmap;
    private String savedImagePath;

    private final ActivityResultLauncher<Intent> galleryLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri selectedImage = result.getData().getData();
                    loadImageFromUri(selectedImage);
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_upload_image);

        // Preload TensorFlow Lite model for faster detection
        try {
            DiseaseDetector.loadModel(this);
            Log.d(TAG, "Model loaded successfully in onCreate");
        } catch (Exception e) {
            Log.e(TAG, "Failed to load model in onCreate", e);
        }

        ivImagePreview = findViewById(R.id.ivImagePreview);
        btnSelectImage = findViewById(R.id.btnSelectImage);
        btnDetect = findViewById(R.id.btnDetect);
        progressBar = findViewById(R.id.progressBar);
        tvInstruction = findViewById(R.id.tvInstruction);

        if (findViewById(R.id.ivBackButton) != null) {
            findViewById(R.id.ivBackButton).setOnClickListener(v -> finish());
        }

        btnSelectImage.setOnClickListener(v -> openGallery());
        btnDetect.setOnClickListener(v -> detectDisease());
        
        // Initial state
        if (btnDetect != null) btnDetect.setEnabled(false);
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        galleryLauncher.launch(intent);
    }

    private void loadImageFromUri(Uri uri) {
        try {
            InputStream stream = getContentResolver().openInputStream(uri);
            
            // Downsample image to save memory
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 2; 
            
            selectedBitmap = BitmapFactory.decodeStream(stream, null, options);
            if (stream != null) stream.close();

            if (selectedBitmap != null) {
                ivImagePreview.setImageBitmap(selectedBitmap);
                btnDetect.setEnabled(true);
                if (tvInstruction != null) {
                    tvInstruction.setText(R.string.instruction_image_selected);
                }
                // Save image to temp file for the result activity
                savedImagePath = saveBitmapToTempFile(selectedBitmap);
            } else {
                Toast.makeText(this, R.string.image_load_failed, Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading image", e);
            Toast.makeText(this, getString(R.string.error_with_message, e.getMessage()), Toast.LENGTH_SHORT).show();
        }
    }

    private String saveBitmapToTempFile(Bitmap bitmap) {
        try {
            File tempFile = new File(getExternalFilesDir(null), "upload_temp_" + System.currentTimeMillis() + ".jpg");
            FileOutputStream out = new FileOutputStream(tempFile);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
            out.flush();
            out.close();
            return tempFile.getAbsolutePath();
        } catch (IOException e) {
            Log.e(TAG, "Failed to save temp image", e);
            return null;
        }
    }

    private void detectDisease() {
        if (selectedBitmap == null) return;

        progressBar.setVisibility(View.VISIBLE);
        btnDetect.setEnabled(false);

        DiseaseDetector.detect(this, selectedBitmap, result -> runOnUiThread(() -> {
            progressBar.setVisibility(View.GONE);
            btnDetect.setEnabled(true);
            DetectionResultLauncher.open(this, result, savedImagePath, ScanRepository.SOURCE_UPLOAD);
        }));
    }
}
