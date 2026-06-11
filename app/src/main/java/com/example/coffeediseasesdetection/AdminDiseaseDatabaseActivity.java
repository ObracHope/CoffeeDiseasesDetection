package com.example.coffeediseasesdetection;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.coffeediseasesdetection.admin.AdminRepository;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;

public class AdminDiseaseDatabaseActivity extends BaseActivity {

    private final List<String> allKeys = new ArrayList<>(DiseaseCatalog.ALL_CONDITIONS);
    private final List<String> filteredKeys = new ArrayList<>(DiseaseCatalog.ALL_CONDITIONS);
    private DiseaseDbAdapter adapter;
    private TextView tvNoResults;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_disease_database);
        setTitle(R.string.disease_database);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnAdd).setOnClickListener(v ->
                startActivity(new Intent(this, TreatmentGuideActivity.class)));
        findViewById(R.id.btnExportDisease).setOnClickListener(v -> showExportFormatDialog());

        tvNoResults = findViewById(R.id.tvNoDiseaseResults);
        RecyclerView rv = findViewById(R.id.rvDiseaseDatabase);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new DiseaseDbAdapter();
        rv.setAdapter(adapter);

        TextInputEditText etSearch = findViewById(R.id.etSearchDisease);
        if (etSearch != null) {
            etSearch.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    filter(s != null ? s.toString() : "");
                }

                @Override
                public void afterTextChanged(Editable s) { }
            });
        }
        updateEmptyState();
    }

    private void filter(String query) {
        filteredKeys.clear();
        for (String key : allKeys) {
            if (DiseaseCatalog.matchesQuery(this, key, query)) {
                filteredKeys.add(key);
            }
        }
        adapter.notifyDataSetChanged();
        updateEmptyState();
    }

    private void updateEmptyState() {
        if (tvNoResults != null) {
            tvNoResults.setVisibility(filteredKeys.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    private void showExportFormatDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.export_choose_format)
                .setItems(new CharSequence[]{
                        getString(R.string.export_as_pdf),
                        getString(R.string.export_as_excel)
                }, (d, which) -> {
                    String format = which == 0
                            ? ReportExportHelper.FORMAT_PDF
                            : ReportExportHelper.FORMAT_EXCEL;
                    Toast.makeText(this, R.string.export_in_progress, Toast.LENGTH_SHORT).show();
                    ReportExportHelper.exportDiseaseCatalog(this, format,
                            new ReportExportHelper.ExportCallback() {
                                @Override
                                public void onSuccess(java.io.File file, android.net.Uri uri, String mimeType) {
                                    if (isFinishing()) return;
                                    Toast.makeText(AdminDiseaseDatabaseActivity.this,
                                            R.string.export_success, Toast.LENGTH_LONG).show();
                                    ReportExportHelper.openShareSheet(AdminDiseaseDatabaseActivity.this,
                                            uri, mimeType, getString(R.string.disease_database));
                                    new AdminRepository().logActivity(AdminDiseaseDatabaseActivity.this,
                                            "export_disease_db", "Exported disease database as " + format);
                                }

                                @Override
                                public void onError(Exception e) {
                                    if (!isFinishing()) {
                                        String msg = e.getMessage() != null ? e.getMessage() : "Error";
                                        Toast.makeText(AdminDiseaseDatabaseActivity.this,
                                                getString(R.string.export_failed, msg),
                                                Toast.LENGTH_LONG).show();
                                    }
                                }
                            });
                })
                .show();
    }

    private void openDetail(String key) {
        Intent intent = new Intent(this, TreatmentDetailActivity.class);
        intent.putExtra("diseaseKey", key);
        intent.putExtra("diseaseName", DiseaseTextProvider.displayName(this, key));
        intent.putExtra("description", DiseaseTextProvider.description(this, key));
        intent.putExtra("symptoms", DiseaseTextProvider.symptoms(this, key));
        intent.putExtra("treatment", DiseaseTextProvider.treatment(this, key));
        startActivity(intent);
    }

    private class DiseaseDbAdapter extends RecyclerView.Adapter<DiseaseDbAdapter.Holder> {

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_admin_disease_db, parent, false);
            return new Holder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            String key = filteredKeys.get(position);
            h.tvName.setText(DiseaseTextProvider.displayName(h.itemView.getContext(), key));
            h.tvScientific.setText(DiseaseCatalog.scientificName(h.itemView.getContext(), key));
            h.tvSummary.setText(DiseaseTextProvider.description(h.itemView.getContext(), key));
            h.ivThumb.setImageResource(DiseaseDetector.getDrawableForDisease(key));
            h.itemView.setOnClickListener(v -> openDetail(key));
        }

        @Override
        public int getItemCount() {
            return filteredKeys.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            final TextView tvName, tvScientific, tvSummary;
            final ImageView ivThumb;

            Holder(@NonNull View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tvDiseaseName);
                tvScientific = itemView.findViewById(R.id.tvDiseaseScientific);
                tvSummary = itemView.findViewById(R.id.tvDiseaseSummary);
                ivThumb = itemView.findViewById(R.id.ivDiseaseThumb);
            }
        }
    }
}
