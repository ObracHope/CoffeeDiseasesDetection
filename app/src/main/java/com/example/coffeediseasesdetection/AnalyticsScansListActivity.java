package com.example.coffeediseasesdetection;

import android.os.Bundle;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AnalyticsScansListActivity extends BaseActivity {

    public static final String EXTRA_FILTER = "filter";
    public static final String EXTRA_DISEASE_KEY = "disease_key";
    public static final String FILTER_ALL = "all";
    public static final String FILTER_DISEASES = "diseases";
    public static final String FILTER_TOP_DISEASE = "top_disease";

    private final List<Map<String, Object>> displayList = new ArrayList<>();
    private HistoryAdapter adapter;
    private ListenerRegistration registration;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_analytics_scans_list);

        final String filter;
        String f = getIntent().getStringExtra(EXTRA_FILTER);
        filter = f != null ? f : FILTER_ALL;
        final String diseaseKey = getIntent().getStringExtra(EXTRA_DISEASE_KEY);

        TextView tvTitle = findViewById(R.id.tvListTitle);
        if (FILTER_DISEASES.equals(filter)) {
            tvTitle.setText(R.string.diseases_found);
        } else if (FILTER_TOP_DISEASE.equals(filter) && diseaseKey != null) {
            tvTitle.setText(DiseaseTextProvider.displayName(this, diseaseKey));
        } else {
            tvTitle.setText(R.string.total_scans);
        }

        findViewById(R.id.ivBackButton).setOnClickListener(v -> finish());

        RecyclerView rv = findViewById(R.id.rvAnalyticsScans);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new HistoryAdapter(displayList, item -> {
            Object id = item.get("id");
            if (id != null) {
                android.content.Intent i = new android.content.Intent(this, ScanDetailActivity.class);
                i.putExtra("scanId", id.toString());
                startActivity(i);
            }
        });
        rv.setAdapter(adapter);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            finish();
            return;
        }

        registration = ScanHistoryLoader.listen(this, user, new ScanHistoryLoader.Callback() {
            @Override
            public void onLoaded(List<Map<String, Object>> scans) {
                applyFilter(scans, filter, diseaseKey);
            }

            @Override
            public void onError(Exception e) {
                displayList.clear();
                adapter.notifyDataSetChanged();
            }
        });
    }

    private void applyFilter(List<Map<String, Object>> scans, String filter, String diseaseKey) {
        displayList.clear();
        for (Map<String, Object> row : scans) {
            String raw = (String) row.get("disease");
            if (raw == null) raw = (String) row.get("diseaseName");
            String key = DiseaseLabels.normalizeKey(raw);
            if (!DiseaseLabels.isValidScan(key)) continue;

            if (FILTER_DISEASES.equals(filter)) {
                if (!DiseaseLabels.isDiseaseFound(key)) continue;
            } else if (FILTER_TOP_DISEASE.equals(filter)) {
                if (diseaseKey == null || !diseaseKey.equals(key)) continue;
            }
            displayList.add(row);
        }
        adapter.notifyDataSetChanged();
        TextView tvEmpty = findViewById(R.id.tvEmptyList);
        tvEmpty.setVisibility(displayList.isEmpty() ? android.view.View.VISIBLE : android.view.View.GONE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (registration != null) {
            registration.remove();
            registration = null;
        }
    }
}
