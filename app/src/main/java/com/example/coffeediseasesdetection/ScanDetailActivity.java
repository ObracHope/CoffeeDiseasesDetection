package com.example.coffeediseasesdetection;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.example.coffeediseasesdetection.data.LocalScanStore;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.Map;

public class ScanDetailActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan_detail);

        String scanId = getIntent().getStringExtra("scanId");
        if (scanId == null) {
            finish();
            return;
        }

        findViewById(R.id.ivBack).setOnClickListener(v -> finish());
        loadScan(scanId);
    }

    private void loadScan(String scanId) {
        FirebaseFirestore.getInstance().collection("scan_history").document(scanId).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists() && doc.getData() != null) {
                        bind(doc.getId(), doc.getData());
                        return;
                    }
                    tryLocal(scanId);
                })
                .addOnFailureListener(e -> tryLocal(scanId));
    }

    private void tryLocal(String scanId) {
        String uid = FirebaseAuth.getInstance().getUid();
        Map<String, Object> local = uid != null
                ? LocalScanStore.getById(this, uid, scanId) : null;
        if (local != null) {
            bind(scanId, local);
        } else {
            Toast.makeText(this, R.string.history_scan_not_found, Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void bind(String scanId, Map<String, Object> data) {
        String diseaseKey = data.get("disease") != null
                ? DiseaseLabels.normalizeKey(data.get("disease").toString()) : "Unknown";
        String displayName = data.get("diseaseName") != null
                ? data.get("diseaseName").toString()
                : DiseaseTextProvider.displayName(this, diseaseKey);

        ((TextView) findViewById(R.id.tvDiseaseName)).setText(displayName);

        Object conf = data.get("confidence");
        TextView tvConf = findViewById(R.id.tvConfidence);
        if ("IsNotCoffee".equals(diseaseKey)) {
            tvConf.setText(getString(R.string.not_applicable));
        } else {
            tvConf.setText(getString(R.string.confidence_label) + ": " + formatConf(conf));
        }

        Object ts = data.get("timestamp");
        TextView tvDate = findViewById(R.id.tvDate);
        if (ts instanceof Timestamp) {
            tvDate.setText(new SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault())
                    .format(((Timestamp) ts).toDate()));
        } else if (ts instanceof Long) {
            tvDate.setText(new SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault())
                    .format(new java.util.Date((Long) ts)));
        } else {
            tvDate.setText(getString(R.string.date_unknown));
        }

        Object lat = data.get("latitude");
        Object lng = data.get("longitude");
        TextView tvLoc = findViewById(R.id.tvLocation);
        if (lat instanceof Number && lng instanceof Number
                && (((Number) lat).doubleValue() != 0 || ((Number) lng).doubleValue() != 0)) {
            tvLoc.setText(String.format(Locale.getDefault(), "GPS: %.5f, %.5f",
                    ((Number) lat).doubleValue(), ((Number) lng).doubleValue()));
        } else {
            String region = data.get("region") != null ? data.get("region").toString() : "";
            tvLoc.setText(region.isEmpty() ? getString(R.string.unknown) : region);
        }

        String med = data.get("medicineUsed") != null ? data.get("medicineUsed").toString() : "";
        ((TextView) findViewById(R.id.tvMedicine)).setText(
                getString(R.string.label_medicine_used) + ": " + (med.isEmpty() ? "—" : med));

        String desc = data.get("description") != null ? data.get("description").toString() : "";
        TextView tvRec = findViewById(R.id.tvRecommendations);
        if ("IsNotCoffee".equals(diseaseKey) || "Uncertain".equals(diseaseKey)) {
            tvRec.setText(desc.isEmpty()
                    ? DiseaseTextProvider.description(this, diseaseKey) : desc);
        } else {
            AiRecommendationProvider.Recommendation rec =
                    AiRecommendationProvider.forDisease(this, diseaseKey);
            tvRec.setText(AiRecommendationProvider.formatSummary(this, rec));
        }

        ImageView iv = findViewById(R.id.ivScanImage);
        String url = data.get("imageUrl") != null ? data.get("imageUrl").toString() : "";
        String path = data.get("imagePath") != null ? data.get("imagePath").toString() : "";
        if (!url.isEmpty()) {
            Glide.with(this).load(url).placeholder(R.drawable.bg_image_placeholder).into(iv);
        } else if (!path.isEmpty() && new File(path).exists()) {
            Glide.with(this).load(new File(path)).placeholder(R.drawable.bg_image_placeholder).into(iv);
        } else {
            iv.setImageResource(DiseaseDetector.getDrawableForDisease(diseaseKey));
        }

        String prevId = data.get("previousScanId") != null ? data.get("previousScanId").toString() : "";
        MaterialCardView card = findViewById(R.id.cardProgress);
        if (!prevId.isEmpty()) {
            FirebaseFirestore.getInstance().collection("scan_history").document(prevId).get()
                    .addOnSuccessListener(prev -> {
                        if (prev.exists()) {
                            ScanComparisonUtil.ComparisonResult cmp =
                                    ScanComparisonUtil.compare(prev.getData(), data);
                            ((TextView) findViewById(R.id.tvProgressStatus)).setText(label(cmp.status));
                            ((TextView) findViewById(R.id.tvProgressSummary)).setText(cmp.summary);
                            card.setVisibility(View.VISIBLE);
                        }
                    });
        } else {
            card.setVisibility(View.GONE);
        }

        MaterialButton btn = findViewById(R.id.btnTreatment);
        if ("IsNotCoffee".equals(diseaseKey) || "Uncertain".equals(diseaseKey)) {
            btn.setText(R.string.btn_try_again);
            btn.setOnClickListener(v -> finish());
        } else {
            btn.setOnClickListener(v -> {
                Intent i = new Intent(this, TreatmentDetailActivity.class);
                i.putExtra("scanId", scanId);
                i.putExtra("diseaseKey", diseaseKey);
                i.putExtra("diseaseName", displayName);
                i.putExtra("description", desc);
                i.putExtra("symptoms", DiseaseTextProvider.symptoms(this, diseaseKey));
                i.putExtra("treatment", DiseaseTextProvider.treatment(this, diseaseKey));
                startActivity(i);
            });
        }
    }

    private String label(ScanComparisonUtil.ProgressStatus s) {
        switch (s) {
            case IMPROVING:
                return getString(R.string.progress_improving);
            case WORSENING:
                return getString(R.string.progress_worsening);
            case RECOVERED:
                return getString(R.string.progress_recovered);
            case STABLE:
                return getString(R.string.progress_stable);
            default:
                return getString(R.string.progress_first);
        }
    }

    private String formatConf(Object conf) {
        if (conf instanceof Number) {
            return String.format(Locale.getDefault(), "%.1f%%", ((Number) conf).floatValue());
        }
        return conf != null ? conf.toString() : "—";
    }
}
