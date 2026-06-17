package com.example.coffeediseasesdetection;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.coffeediseasesdetection.admin.AdminRepository;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Global search: farmers, scans, diseases — mirrors web Ctrl+K search. */
public class AdminGlobalSearchActivity extends BaseActivity {

    private final List<SearchResult> results = new ArrayList<>();
    private final List<Map<String, Object>> farmers = new ArrayList<>();
    private final List<Map<String, Object>> scans = new ArrayList<>();
    private SearchAdapter adapter;
    private TextView tvNoResults;
    private boolean dataReady;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_global_search);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        tvNoResults = findViewById(R.id.tvNoResults);
        RecyclerView rv = findViewById(R.id.rvSearchResults);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SearchAdapter();
        rv.setAdapter(adapter);

        AdminRepository repo = new AdminRepository();
        final int[] pending = {2};
        repo.loadAllScans(300, new AdminRepository.ScansCallback() {
            @Override
            public void onSuccess(List<Map<String, Object>> list) {
                scans.clear();
                scans.addAll(list);
                if (--pending[0] <= 0) dataReady = true;
            }

            @Override
            public void onError(Exception e) {
                if (--pending[0] <= 0) dataReady = true;
            }
        });

        com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("users").get()
                .addOnSuccessListener(snap -> {
                    for (com.google.firebase.firestore.QueryDocumentSnapshot doc : snap) {
                        Map<String, Object> row = doc.getData();
                        row.put("id", doc.getId());
                        farmers.add(row);
                    }
                    if (--pending[0] <= 0) dataReady = true;
                });

        TextInputEditText et = findViewById(R.id.etGlobalSearch);
        if (et != null) {
            et.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
                @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                    search(s != null ? s.toString().trim() : "");
                }
                @Override public void afterTextChanged(Editable s) {}
            });
            et.requestFocus();
        }
    }

    private void search(String q) {
        results.clear();
        if (q.length() < 2) {
            adapter.notifyDataSetChanged();
            updateEmpty();
            return;
        }
        String lower = q.toLowerCase(Locale.US);

        for (Map<String, Object> f : farmers) {
            if (PhoneSearchHelper.mapMatchesSearch(f, q,
                    "name", "firstName", "lastName", "email", "phone", "phoneNumber", "username")) {
                String name = formatName(f);
                results.add(new SearchResult("farmer", name,
                        String.valueOf(f.get("email")), f));
            }
        }
        for (Map<String, Object> s : scans) {
            if (matches(s, lower, "diseaseName", "disease", "userName", "userEmail", "region")) {
                String disease = s.get("diseaseName") != null
                        ? String.valueOf(s.get("diseaseName"))
                        : String.valueOf(s.get("disease"));
                results.add(new SearchResult("scan", disease,
                        String.valueOf(s.get("userName")), s));
            }
        }
        for (String key : DiseaseCatalog.ALL_CONDITIONS) {
            String display = DiseaseTextProvider.displayName(this, key);
            if (display.toLowerCase(Locale.US).contains(lower)
                    || key.toLowerCase(Locale.US).contains(lower)) {
                results.add(new SearchResult("disease", display, key, null));
            }
        }

        adapter.notifyDataSetChanged();
        updateEmpty();
    }

    private void updateEmpty() {
        if (tvNoResults != null) {
            tvNoResults.setVisibility(results.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    private static boolean matches(Map<String, Object> map, String q, String... keys) {
        for (String k : keys) {
            Object v = map.get(k);
            if (v != null && String.valueOf(v).toLowerCase(Locale.US).contains(q)) return true;
        }
        return false;
    }

    private static String formatName(Map<String, Object> f) {
        String name = f.get("name") != null ? String.valueOf(f.get("name")).trim() : "";
        if (!name.isEmpty()) return name;
        String first = f.get("firstName") != null ? String.valueOf(f.get("firstName")).trim() : "";
        String last = f.get("lastName") != null ? String.valueOf(f.get("lastName")).trim() : "";
        return (first + " " + last).trim();
    }

    private void openResult(SearchResult r) {
        if ("farmer".equals(r.type)) {
            startActivity(new Intent(this, AdminManageFarmersActivity.class));
        } else if ("scan".equals(r.type) && r.data != null) {
            Object id = r.data.get("id");
            if (id != null) {
                Intent i = new Intent(this, ScanDetailActivity.class);
                i.putExtra("scanId", id.toString());
                startActivity(i);
            }
        } else if ("disease".equals(r.type)) {
            Intent i = new Intent(this, AdminDiseaseDatabaseActivity.class);
            startActivity(i);
        }
    }

    private static class SearchResult {
        final String type, title, subtitle;
        final Map<String, Object> data;

        SearchResult(String type, String title, String subtitle, Map<String, Object> data) {
            this.type = type;
            this.title = title;
            this.subtitle = subtitle;
            this.data = data;
        }
    }

    private class SearchAdapter extends RecyclerView.Adapter<SearchAdapter.H> {
        @NonNull
        @Override
        public H onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(android.R.layout.simple_list_item_2, parent, false);
            return new H(v);
        }

        @Override
        public void onBindViewHolder(@NonNull H h, int pos) {
            SearchResult r = results.get(pos);
            String badge = r.type.substring(0, 1).toUpperCase(Locale.US) + r.type.substring(1);
            h.t1.setText(badge + ": " + r.title);
            h.t2.setText(r.subtitle);
            h.itemView.setOnClickListener(v -> openResult(r));
        }

        @Override
        public int getItemCount() {
            return results.size();
        }

        class H extends RecyclerView.ViewHolder {
            TextView t1, t2;

            H(View v) {
                super(v);
                t1 = v.findViewById(android.R.id.text1);
                t2 = v.findViewById(android.R.id.text2);
            }
        }
    }
}
