package com.example.coffeediseasesdetection;

import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

public class TreatmentDetailActivity extends BaseActivity {

    private String scanId;
    private String diseaseKey;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_treatment_detail);

        scanId = getIntent().getStringExtra("scanId");
        diseaseKey = getIntent().getStringExtra("diseaseKey");
        String diseaseName = getIntent().getStringExtra("diseaseName");
        String description = getIntent().getStringExtra("description");
        String symptoms = getIntent().getStringExtra("symptoms");
        String treatment = getIntent().getStringExtra("treatment");
        int imageRes = getIntent().getIntExtra("imageRes", 0);

        if (diseaseName != null) {
            ((TextView) findViewById(R.id.tvTreatmentTitle)).setText(diseaseName);
        }

        TextView tvDesc = findViewById(R.id.tvTreatmentDescription);
        if (tvDesc != null && description != null) tvDesc.setText(description);

        TextView tvSymp = findViewById(R.id.tvTreatmentSymptoms);
        if (tvSymp != null && symptoms != null) tvSymp.setText(symptoms);

        TextView tvTreat = findViewById(R.id.tvTreatmentSteps);
        if (tvTreat != null && treatment != null) tvTreat.setText(treatment);

        if (diseaseKey != null) {
            AiRecommendationProvider.Recommendation rec =
                    AiRecommendationProvider.forDisease(this, diseaseKey);
            TextView tvAi = findViewById(R.id.tvAiRecommendations);
            if (tvAi != null) tvAi.setText(AiRecommendationProvider.formatSummary(this, rec));
        }

        ImageView ivTreatmentImage = findViewById(R.id.ivTreatmentImage);
        if (ivTreatmentImage != null) {
            if (imageRes != 0) {
                ivTreatmentImage.setImageResource(imageRes);
            } else if (diseaseKey != null) {
                ivTreatmentImage.setImageResource(DiseaseDetector.getDrawableForDisease(diseaseKey));
            } else {
                ivTreatmentImage.setImageResource(R.drawable.coffee_leaf_sample);
            }
            ivTreatmentImage.setVisibility(android.view.View.VISIBLE);
        }

        TextInputEditText etMedicine = findViewById(R.id.etMedicineUsed);
        MaterialButton btnSave = findViewById(R.id.btnSaveTreatment);
        if (btnSave != null) {
            btnSave.setOnClickListener(v -> {
                String medicine = etMedicine != null && etMedicine.getText() != null
                        ? etMedicine.getText().toString().trim() : "";
                if (scanId != null && !scanId.isEmpty()) {
                    ScanRepository.updateTreatment(this, scanId, medicine, "in_treatment");
                    Toast.makeText(this, R.string.treatment_saved_body, Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, R.string.treatment_saved_body, Toast.LENGTH_SHORT).show();
                }
            });
        }

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
    }
}
