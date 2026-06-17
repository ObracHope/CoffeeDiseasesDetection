package com.example.coffeediseasesdetection;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.coffeediseasesdetection.admin.AdminRepository;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AdminScansListActivity extends BaseActivity {

    public static final String EXTRA_FILTER = "filter";
    public static final String EXTRA_DISEASE_KEY = "disease_key";
    public static final String FILTER_ALL = "all";
    public static final String FILTER_HEALTHY = "healthy";
    public static final String FILTER_DISEASES = "diseases";

    private final List<Map<String, Object>> allScans = new ArrayList<>();
    private final List<Map<String, Object>> filteredScans = new ArrayList<>();
    private final AdminRepository repository = new AdminRepository();
    private HistoryAdapter adapter;
    private TextView tvEmpty;
    private TextView tvCount;
    private ListenerRegistration scanListener;
    private int periodFilter = 0;
    private String searchQuery = "";
    private String listFilter = FILTER_ALL;
    private String diseaseSortKey = "all";
    private final List<String> diseaseSortKeys = new ArrayList<>();
    private final List<String> diseaseSortLabels = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_scans_list);

        findViewById(R.id.ivBackButton).setOnClickListener(v -> finish());
        findViewById(R.id.btnExportCsv).setOnClickListener(v -> exportCsv());

        tvEmpty = findViewById(R.id.tvEmptyList);
        tvCount = findViewById(R.id.tvScanCount);

        RecyclerView rv = findViewById(R.id.rvScans);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new HistoryAdapter(filteredScans, this::onScanClick);
        rv.setAdapter(adapter);

        readIntentFilter();
        setupDiseaseSortSpinner();
        setupSearch();
        setupPeriodChips();
        applyIntentFilter();
        updateTitle();

        scanListener = repository.listenAllScans(500, new AdminRepository.ScansCallback() {
            @Override
            public void onSuccess(List<Map<String, Object>> list) {
                if (isFinishing()) return;
                allScans.clear();
                allScans.addAll(list);
                applyFilters();
            }

            @Override
            public void onError(Exception e) {
                if (!isFinishing()) {
                    Toast.makeText(AdminScansListActivity.this,
                            R.string.error_loading_data, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void readIntentFilter() {
        String f = getIntent().getStringExtra(EXTRA_FILTER);
        if (f != null) listFilter = f;
        String key = getIntent().getStringExtra(EXTRA_DISEASE_KEY);
        if (key != null && !key.isEmpty()) diseaseSortKey = DiseaseLabels.normalizeKey(key);
    }

    private void updateTitle() {
        TextView tvTitle = findViewById(R.id.tvScanListTitle);
        if (tvTitle == null) return;
        if (FILTER_HEALTHY.equals(listFilter)) {
            tvTitle.setText(R.string.admin_health_coffee);
        } else if (FILTER_DISEASES.equals(listFilter)) {
            tvTitle.setText(R.string.diseases_detected);
        } else {
            tvTitle.setText(R.string.admin_scan_records);
        }
    }

    private void setupDiseaseSortSpinner() {
        Spinner spinner = findViewById(R.id.spinnerDiseaseSort);
        if (spinner == null) return;

        diseaseSortKeys.clear();
        diseaseSortLabels.clear();
        diseaseSortKeys.add("all");
        diseaseSortLabels.add(getString(R.string.all));

        for (String key : DiseaseCatalog.ALL_CONDITIONS) {
            if ("Healthy".equals(key)) continue;
            diseaseSortKeys.add(key);
            diseaseSortLabels.add(DiseaseTextProvider.displayName(this, key));
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, diseaseSortLabels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        int selectIndex = diseaseSortKeys.indexOf(diseaseSortKey);
        if (selectIndex >= 0) spinner.setSelection(selectIndex);

        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                diseaseSortKey = diseaseSortKeys.get(position);
                applyFilters();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        View sortLabel = findViewById(R.id.tvDiseaseSortLabel);
        boolean showSort = FILTER_DISEASES.equals(listFilter) || FILTER_ALL.equals(listFilter);
        spinner.setVisibility(showSort ? View.VISIBLE : View.GONE);
        if (sortLabel != null) sortLabel.setVisibility(showSort ? View.VISIBLE : View.GONE);
    }

    private void applyIntentFilter() {
        String disease = getIntent().getStringExtra("filterDisease");
        if (disease != null && !disease.isEmpty()) {
            TextInputEditText et = findViewById(R.id.etSearchScans);
            if (et != null) et.setText(disease);
            searchQuery = disease.toLowerCase(Locale.US);
        }
    }

    private void setupSearch() {
        TextInputEditText et = findViewById(R.id.etSearchScans);
        if (et == null) return;
        et.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchQuery = s != null ? s.toString().trim().toLowerCase(Locale.US) : "";
                applyFilters();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void setupPeriodChips() {
        ChipGroup group = findViewById(R.id.chipGroupPeriod);
        if (group == null) return;
        group.setOnCheckedStateChangeListener((g, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            if (id == R.id.chipToday) periodFilter = 1;
            else if (id == R.id.chipWeek) periodFilter = 2;
            else if (id == R.id.chipMonth) periodFilter = 3;
            else periodFilter = 0;
            applyFilters();
        });
    }

    private void applyFilters() {
        filteredScans.clear();
        long cutoff = periodCutoffMs();

        for (Map<String, Object> scan : allScans) {
            String key = scanDiseaseKey(scan);
            if (!DiseaseLabels.isValidScan(key)) continue;

            if (FILTER_HEALTHY.equals(listFilter)) {
                if (!"Healthy".equals(key)) continue;
            } else if (FILTER_DISEASES.equals(listFilter)) {
                if (!DiseaseLabels.isDiseaseFound(key)) continue;
            }

            if (!"all".equals(diseaseSortKey) && !diseaseSortKey.equals(key)) continue;

            if (cutoff > 0) {
                long ts = parseTs(scan.get("timestamp"));
                if (ts < cutoff) continue;
            }
            if (!searchQuery.isEmpty() && !matchesSearch(scan, searchQuery)) continue;
            filteredScans.add(scan);
        }

        adapter.notifyDataSetChanged();
        if (tvCount != null) {
            tvCount.setText(getString(R.string.admin_scan_count, filteredScans.size()));
        }
        if (tvEmpty != null) {
            tvEmpty.setVisibility(filteredScans.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    private static String scanDiseaseKey(Map<String, Object> scan) {
        String raw = scan.get("disease") != null ? scan.get("disease").toString() : null;
        if (raw == null && scan.get("diseaseName") != null) {
            raw = scan.get("diseaseName").toString();
        }
        return DiseaseLabels.normalizeKey(raw);
    }

    private boolean matchesSearch(Map<String, Object> scan, String q) {
        String[] fields = {"diseaseName", "disease", "userName", "userEmail", "region", "district"};
        for (String f : fields) {
            Object v = scan.get(f);
            if (v != null && String.valueOf(v).toLowerCase(Locale.US).contains(q)) return true;
        }
        return false;
    }

    private long periodCutoffMs() {
        Calendar cal = Calendar.getInstance();
        if (periodFilter == 1) {
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
        } else if (periodFilter == 2) {
            cal.add(Calendar.DAY_OF_YEAR, -7);
        } else if (periodFilter == 3) {
            cal.add(Calendar.MONTH, -1);
        } else {
            return 0;
        }
        return cal.getTimeInMillis();
    }

    private static long parseTs(Object ts) {
        if (ts instanceof Timestamp) return ((Timestamp) ts).toDate().getTime();
        if (ts instanceof Number) return ((Number) ts).longValue();
        return 0;
    }

    private void onScanClick(Map<String, Object> item) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.admin_scan_actions)
                .setItems(new CharSequence[]{
                        getString(R.string.view_details),
                        getString(R.string.delete)
                }, (d, which) -> {
                    Object id = item.get("id");
                    if (id == null) return;
                    String scanId = id.toString();
                    if (which == 0) {
                        Intent i = new Intent(this, ScanDetailActivity.class);
                        i.putExtra("scanId", scanId);
                        startActivity(i);
                    } else {
                        confirmDelete(scanId);
                    }
                })
                .show();
    }

    private void confirmDelete(String scanId) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_scan_confirm)
                .setMessage(R.string.delete_scan_message)
                .setPositiveButton(R.string.yes, (d, w) ->
                        repository.deleteScan(scanId, new AdminRepository.SimpleCallback() {
                            @Override
                            public void onSuccess() {
                                repository.logActivity(AdminScansListActivity.this, "scan_delete", scanId);
                                Toast.makeText(AdminScansListActivity.this,
                                        R.string.scan_deleted, Toast.LENGTH_SHORT).show();
                            }

                            @Override
                            public void onError(Exception e) {
                                Toast.makeText(AdminScansListActivity.this,
                                        R.string.error_loading_data, Toast.LENGTH_SHORT).show();
                            }
                        }))
                .setNegativeButton(R.string.no, null)
                .show();
    }

    private void exportCsv() {
        if (filteredScans.isEmpty()) {
            Toast.makeText(this, R.string.no_data_yet, Toast.LENGTH_SHORT).show();
            return;
        }
        ScanExportHelper.exportScansCsv(this, filteredScans, new ScanExportHelper.Callback() {
            @Override
            public void onSuccess(java.io.File file, android.net.Uri uri) {
                if (!isFinishing()) {
                    repository.logActivity(AdminScansListActivity.this, "export_csv", file.getName());
                    ScanExportHelper.shareCsv(AdminScansListActivity.this, uri);
                }
            }

            @Override
            public void onError(Exception e) {
                if (!isFinishing()) {
                    Toast.makeText(AdminScansListActivity.this,
                            R.string.error_loading_data, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        if (scanListener != null) {
            scanListener.remove();
            scanListener = null;
        }
        super.onDestroy();
    }
}
