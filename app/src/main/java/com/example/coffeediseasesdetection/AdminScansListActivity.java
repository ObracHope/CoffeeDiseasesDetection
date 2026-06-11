package com.example.coffeediseasesdetection;

import android.os.Bundle;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.coffeediseasesdetection.admin.AdminRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AdminScansListActivity extends BaseActivity {

    private final List<Map<String, Object>> scans = new ArrayList<>();
    private HistoryAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_analytics_scans_list);

        TextView tvTitle = findViewById(R.id.tvListTitle);
        tvTitle.setText(R.string.admin_scan_records);
        findViewById(R.id.ivBackButton).setOnClickListener(v -> finish());

        RecyclerView rv = findViewById(R.id.rvAnalyticsScans);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new HistoryAdapter(scans, item -> {
            Object id = item.get("id");
            if (id != null) {
                android.content.Intent i = new android.content.Intent(this, ScanDetailActivity.class);
                i.putExtra("scanId", id.toString());
                startActivity(i);
            }
        });
        rv.setAdapter(adapter);

        new AdminRepository().loadAllScans(500, new AdminRepository.ScansCallback() {
            @Override
            public void onSuccess(List<Map<String, Object>> list) {
                if (isFinishing()) return;
                scans.clear();
                scans.addAll(list);
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onError(Exception e) {
                if (!isFinishing()) {
                    android.widget.Toast.makeText(AdminScansListActivity.this,
                            R.string.error_loading_data, android.widget.Toast.LENGTH_SHORT).show();
                }
            }
        });
    }
}
