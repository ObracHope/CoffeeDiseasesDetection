package com.example.coffeediseasesdetection;

import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import java.util.Locale;

public class DiseaseResultActivity extends BaseActivity {

    private String lastSavedScanId = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_disease_result);

        ImageView ivResultImage = findViewById(R.id.ivResultImage);
        TextView tvStep1 = findViewById(R.id.tvStep1);
        TextView tvStep2 = findViewById(R.id.tvStep2);
        TextView tvStep3 = findViewById(R.id.tvStep3);
        TextView tvDiseaseName = findViewById(R.id.tvDiseaseName);
        TextView tvConfidence = findViewById(R.id.tvConfidence);
        TextView tvDiseaseDescription = findViewById(R.id.tvDiseaseDescription);
        CircularProgressIndicator progressConfidence = findViewById(R.id.progressConfidence);
        MaterialButton btnViewTreatment = findViewById(R.id.btnViewTreatment);
        ImageView ivBack = findViewById(R.id.ivBack);

        boolean isCoffee = getIntent().getBooleanExtra("isCoffee", false);
        boolean isHealthy = getIntent().getBooleanExtra("isHealthy", false);
        String disease = getIntent().getStringExtra("disease");
        String confidence = getIntent().getStringExtra("confidence");
        String description = getIntent().getStringExtra("description");
        String symptoms = getIntent().getStringExtra("symptoms");
        String treatment = getIntent().getStringExtra("treatment");
        String imagePath = getIntent().getStringExtra("imagePath");
        String step1 = getIntent().getStringExtra("step1");
        String step2 = getIntent().getStringExtra("step2");
        String step3 = getIntent().getStringExtra("step3");

        String diseaseKey = disease != null ? DiseaseLabels.normalizeKey(disease) : "Unknown";
        boolean isNotCoffee = DiseaseDetector.NOT_COFFEE_LABEL.equals(diseaseKey);
        boolean isUncertain = "Uncertain".equals(diseaseKey);

        if (tvStep1 != null) {
            tvStep1.setText(step1 != null ? step1 : getString(isCoffee ? R.string.step1_coffee_yes : R.string.step1_coffee_no));
        }
        if (isCoffee) {
            if (tvStep2 != null) {
                tvStep2.setVisibility(View.VISIBLE);
                tvStep2.setText(step2 != null ? step2 : getString(isHealthy ? R.string.step2_healthy : R.string.step2_diseased));
            }
            if (tvStep3 != null) {
                tvStep3.setVisibility(View.VISIBLE);
                tvStep3.setText(step3 != null ? step3 : "");
            }
        } else {
            if (tvStep2 != null) tvStep2.setVisibility(View.GONE);
            if (tvStep3 != null) tvStep3.setVisibility(View.GONE);
        }

        if (isNotCoffee) {
            if (tvDiseaseName != null) tvDiseaseName.setText(getString(R.string.not_coffee_title));
            if (tvDiseaseDescription != null) tvDiseaseDescription.setText(getString(R.string.not_coffee_desc));
        } else {
            if (tvDiseaseName != null) tvDiseaseName.setText(DiseaseTextProvider.displayName(this, diseaseKey));
            if (tvDiseaseDescription != null) tvDiseaseDescription.setText(description);
        }

        String safeConfidenceText = normalizeConfidence(confidence);
        if (tvConfidence != null) {
            tvConfidence.setText(isNotCoffee ? "—" : safeConfidenceText);
        }

        if (progressConfidence != null && safeConfidenceText.contains("%")) {
            try {
                int confVal = (int) Float.parseFloat(safeConfidenceText.replace("%", ""));
                progressConfidence.setProgress(Math.min(100, confVal));
            } catch (Exception e) {
                progressConfidence.setProgress(0);
            }
        }

        if (ivResultImage != null) {
            if (imagePath != null) {
                ivResultImage.setImageBitmap(BitmapFactory.decodeFile(imagePath));
            } else if (disease != null) {
                ivResultImage.setImageResource(DiseaseDetector.getDrawableForDisease(diseaseKey));
            }
        }

        float confVal = 0f;
        try {
            confVal = Float.parseFloat(safeConfidenceText.replace("%", ""));
        } catch (Exception ignored) {
        }
        final float confidenceFinal = confVal;
        final String diseaseKeyFinal = diseaseKey;
        final String descFinal = description != null ? description : "";
        final String pathFinal = imagePath;

        // Save every scan to history — coffee, non-coffee, uncertain, healthy, diseased.
        ScanRepository.saveScan(this, diseaseKeyFinal, confidenceFinal, descFinal, pathFinal,
                isCoffee, isHealthy, new ScanRepository.SaveCallback() {
                    @Override
                    public void onSuccess(String scanId) {
                        lastSavedScanId = scanId;
                    }

                    @Override
                    public void onError(Exception e) {
                        Toast.makeText(DiseaseResultActivity.this,
                                getString(R.string.error_with_message,
                                        e.getMessage() != null ? e.getMessage() : ""),
                                Toast.LENGTH_SHORT).show();
                    }
                });

        if (btnViewTreatment != null) {
            if (isNotCoffee || isUncertain) {
                btnViewTreatment.setText(isNotCoffee ? getString(R.string.btn_try_again) : getString(R.string.btn_retake_photo));
                btnViewTreatment.setOnClickListener(v -> finish());
                if (isNotCoffee) {
                    Toast.makeText(this, getString(R.string.not_coffee_title), Toast.LENGTH_LONG).show();
                }
            } else {
                btnViewTreatment.setText(isHealthy ? getString(R.string.btn_view_care) : getString(R.string.btn_view_treatment));
                btnViewTreatment.setOnClickListener(v -> {
                    Intent intent = new Intent(DiseaseResultActivity.this, TreatmentDetailActivity.class);
                    intent.putExtra("scanId", lastSavedScanId);
                    intent.putExtra("diseaseName", DiseaseTextProvider.displayName(this, diseaseKeyFinal));
                    intent.putExtra("description", descFinal);
                    intent.putExtra("symptoms", symptoms);
                    intent.putExtra("treatment", treatment);
                    intent.putExtra("diseaseKey", diseaseKeyFinal);
                    startActivity(intent);
                });
            }
        }

        if (ivBack != null) {
            ivBack.setOnClickListener(v -> finish());
        }
    }

    private String normalizeConfidence(String confidence) {
        if (confidence == null || confidence.trim().isEmpty()) return "0%";
        if (confidence.contains("%")) return confidence;
        try {
            float value = Float.parseFloat(confidence);
            return String.format(Locale.getDefault(), "%.1f%%", value);
        } catch (Exception ignored) {
            return "0%";
        }
    }

}
