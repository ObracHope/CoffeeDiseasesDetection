package com.example.coffeediseasesdetection;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class TreatmentGuideActivity extends BaseActivity {

    private static final List<String> DISEASE_KEYS = DiseaseCatalog.ALL_CONDITIONS;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_treatment_guide);

        findViewById(R.id.ivBackButton).setOnClickListener(v -> finish());

        RecyclerView rv = findViewById(R.id.rvTreatmentGuide);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(new GuideAdapter(DISEASE_KEYS, key -> {
            Intent intent = new Intent(this, TreatmentDetailActivity.class);
            intent.putExtra("diseaseKey", key);
            intent.putExtra("diseaseName", DiseaseTextProvider.displayName(this, key));
            intent.putExtra("description", DiseaseTextProvider.description(this, key));
            intent.putExtra("symptoms", DiseaseTextProvider.symptoms(this, key));
            intent.putExtra("treatment", DiseaseTextProvider.treatment(this, key));
            startActivity(intent);
        }));
    }

    private static class GuideAdapter extends RecyclerView.Adapter<GuideAdapter.Holder> {

        interface OnGuideClick { void onClick(String diseaseKey); }

        private final List<String> keys;
        private final OnGuideClick listener;

        GuideAdapter(List<String> keys, OnGuideClick listener) {
            this.keys = keys;
            this.listener = listener;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_treatment_guide, parent, false);
            return new Holder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            String key = keys.get(position);
            holder.tvName.setText(DiseaseTextProvider.displayName(holder.itemView.getContext(), key));
            holder.tvSymptoms.setText(holder.itemView.getContext().getString(R.string.symptoms_label)
                    + " " + DiseaseTextProvider.symptoms(holder.itemView.getContext(), key));
            holder.tvTreatment.setText(holder.itemView.getContext().getString(R.string.treatment_label)
                    + " " + DiseaseTextProvider.treatment(holder.itemView.getContext(), key));
            holder.itemView.setOnClickListener(v -> listener.onClick(key));
        }

        @Override
        public int getItemCount() {
            return keys.size();
        }

        static class Holder extends RecyclerView.ViewHolder {
            final TextView tvName, tvSymptoms, tvTreatment;

            Holder(@NonNull View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tvGuideDiseaseName);
                tvSymptoms = itemView.findViewById(R.id.tvGuideSymptoms);
                tvTreatment = itemView.findViewById(R.id.tvGuideTreatment);
            }
        }
    }
}
