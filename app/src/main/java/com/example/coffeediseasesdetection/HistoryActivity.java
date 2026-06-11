package com.example.coffeediseasesdetection;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class HistoryActivity extends BaseActivity {

    private RecyclerView rvHistory;
    private HistoryAdapter adapter;
    private TextView tvEmpty;
    private final List<Map<String, Object>> historyList = new ArrayList<>();
    private final List<Map<String, Object>> fullList = new ArrayList<>();
    private ListenerRegistration historyListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);
        setTitle(getString(R.string.scan_history_title));

        rvHistory = findViewById(R.id.rvHistory);
        tvEmpty = findViewById(R.id.tvEmptyList);
        rvHistory.setLayoutManager(new LinearLayoutManager(this));
        adapter = new HistoryAdapter(historyList, item -> openScanDetail(item));
        rvHistory.setAdapter(adapter);

        ImageView ivBack = findViewById(R.id.ivBackButton);
        ivBack.setOnClickListener(v -> finish());

        TextInputEditText etSearch = findViewById(R.id.etSearch);
        if (etSearch != null) {
            etSearch.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    applyFilter(s != null ? s.toString() : "");
                }

                @Override
                public void afterTextChanged(Editable s) {
                }
            });
        }

        loadHistory();
    }

    private void openScanDetail(Map<String, Object> item) {
        Object id = item.get("id");
        if (id == null) return;
        Intent i = new Intent(this, ScanDetailActivity.class);
        i.putExtra("scanId", id.toString());
        startActivity(i);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (historyListener != null) {
            historyListener.remove();
            historyListener = null;
        }
    }

    private void loadHistory() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            fullList.clear();
            applyFilter("");
            return;
        }

        if (historyListener != null) {
            historyListener.remove();
        }

        historyListener = ScanHistoryLoader.listen(this, user, new ScanHistoryLoader.Callback() {
            @Override
            public void onLoaded(List<Map<String, Object>> scans) {
                if (isFinishing()) return;
                fullList.clear();
                fullList.addAll(scans);
                TextInputEditText etSearch = findViewById(R.id.etSearch);
                String q = etSearch != null && etSearch.getText() != null
                        ? etSearch.getText().toString() : "";
                applyFilter(q);
            }

            @Override
            public void onError(Exception e) {
                if (isFinishing()) return;
                Toast.makeText(HistoryActivity.this,
                        getString(R.string.history_load_failed), Toast.LENGTH_SHORT).show();
                fullList.clear();
                applyFilter("");
            }
        });
    }

    private void applyFilter(String query) {
        List<Map<String, Object>> filtered = HistoryFilterUtil.filter(fullList, query);
        historyList.clear();
        historyList.addAll(filtered);
        adapter.notifyDataSetChanged();
        updateEmptyState(query);
    }

    private void updateEmptyState(String query) {
        if (tvEmpty == null) return;
        if (historyList.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            boolean hasData = !fullList.isEmpty();
            boolean searching = query != null && !query.trim().isEmpty();
            tvEmpty.setText(hasData && searching
                    ? R.string.history_no_search_match
                    : R.string.no_scan_history_yet);
        } else {
            tvEmpty.setVisibility(View.GONE);
        }
    }
}
