package com.example.coffeediseasesdetection;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class AdminRecommendationsActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_recommendations);
        setTitle(R.string.recommendations);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnOpenCareGuide).setOnClickListener(v ->
                startActivity(new Intent(this, CoffeeCareGuideActivity.class)));

        RecyclerView rv = findViewById(R.id.rvRecommendations);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(new RecommendationAdapter(DiseaseCatalog.ALL_CONDITIONS));
    }

    private class RecommendationAdapter extends RecyclerView.Adapter<RecommendationAdapter.Holder> {

        private final List<String> keys;

        RecommendationAdapter(List<String> keys) {
            this.keys = new ArrayList<>(keys);
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_admin_recommendation, parent, false);
            return new Holder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            String key = keys.get(position);
            AiRecommendationProvider.Recommendation rec =
                    AiRecommendationProvider.forDisease(h.itemView.getContext(), key);
            h.tvName.setText(DiseaseTextProvider.displayName(h.itemView.getContext(), key));
            h.tvMedicine.setText(rec.medicines);
            h.tvPrevention.setText(rec.prevention);
            h.tvTreatment.setText(rec.treatment);
            h.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(v.getContext(), TreatmentDetailActivity.class);
                intent.putExtra("diseaseKey", key);
                intent.putExtra("diseaseName", DiseaseTextProvider.displayName(v.getContext(), key));
                intent.putExtra("description", DiseaseTextProvider.description(v.getContext(), key));
                intent.putExtra("symptoms", DiseaseTextProvider.symptoms(v.getContext(), key));
                intent.putExtra("treatment", DiseaseTextProvider.treatment(v.getContext(), key));
                v.getContext().startActivity(intent);
            });
        }

        @Override
        public int getItemCount() {
            return keys.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            final TextView tvName, tvMedicine, tvPrevention, tvTreatment;

            Holder(@NonNull View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tvRecDiseaseName);
                tvMedicine = itemView.findViewById(R.id.tvRecMedicine);
                tvPrevention = itemView.findViewById(R.id.tvRecPrevention);
                tvTreatment = itemView.findViewById(R.id.tvRecTreatment);
            }
        }
    }
}
