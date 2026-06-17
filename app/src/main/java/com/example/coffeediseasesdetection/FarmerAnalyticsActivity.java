package com.example.coffeediseasesdetection;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public class FarmerAnalyticsActivity extends BaseActivity {

    private static final int[] DISEASE_COLORS = {
            Color.parseColor("#E65100"),
            Color.parseColor("#2E7D32"),
            Color.parseColor("#6A1B9A"),
            Color.parseColor("#1565C0"),
            Color.parseColor("#BF360C"),
            Color.parseColor("#558B2F")
    };

    private TextView tvTotalScans, tvDiseasesFound, tvTopDisease, tvNoDiseaseData;
    private RecyclerView rvDiseaseBreakdown;
    private LineChart lineChartTrend;
    private ListenerRegistration registration;
    private String topDiseaseKey = null;
    private DiseaseBreakdownAdapter diseaseAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_farmer_analytics);

        tvTotalScans = findViewById(R.id.tvTotalScans);
        tvDiseasesFound = findViewById(R.id.tvDiseasesFound);
        tvTopDisease = findViewById(R.id.tvTopDisease);
        tvNoDiseaseData = findViewById(R.id.tvNoDiseaseData);
        rvDiseaseBreakdown = findViewById(R.id.rvDiseaseBreakdown);
        lineChartTrend = findViewById(R.id.lineChartTrend);

        findViewById(R.id.ivBackButton).setOnClickListener(v -> finish());

        CardView cardTotal = findViewById(R.id.cardTotalScans);
        CardView cardDiseases = findViewById(R.id.cardDiseasesFound);
        CardView cardTop = findViewById(R.id.cardTopDisease);

        cardTotal.setOnClickListener(v -> openList(AnalyticsScansListActivity.FILTER_ALL, null));
        cardDiseases.setOnClickListener(v -> openList(AnalyticsScansListActivity.FILTER_DISEASES, null));
        cardTop.setOnClickListener(v -> {
            if (topDiseaseKey != null) {
                openList(AnalyticsScansListActivity.FILTER_TOP_DISEASE, topDiseaseKey);
            } else {
                Toast.makeText(this, R.string.no_data_yet, Toast.LENGTH_SHORT).show();
            }
        });

        diseaseAdapter = new DiseaseBreakdownAdapter();
        rvDiseaseBreakdown.setLayoutManager(new LinearLayoutManager(this));
        rvDiseaseBreakdown.setAdapter(diseaseAdapter);

        setupLineChart();
        loadAnalytics();
    }

    private void openList(String filter, String diseaseKey) {
        Intent i = new Intent(this, AnalyticsScansListActivity.class);
        i.putExtra(AnalyticsScansListActivity.EXTRA_FILTER, filter);
        if (diseaseKey != null) {
            i.putExtra(AnalyticsScansListActivity.EXTRA_DISEASE_KEY, diseaseKey);
        }
        startActivity(i);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (registration != null) {
            registration.remove();
            registration = null;
        }
    }

    private void setupLineChart() {
        lineChartTrend.getDescription().setEnabled(false);
        lineChartTrend.setDrawGridBackground(false);
        lineChartTrend.animateX(800);
        lineChartTrend.getLegend().setEnabled(false);
        lineChartTrend.getAxisRight().setEnabled(false);
        lineChartTrend.getAxisLeft().setGranularity(1f);
        lineChartTrend.getAxisLeft().setAxisMinimum(0f);
        XAxis xAxis = lineChartTrend.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setDrawGridLines(false);
        xAxis.setTextSize(11f);
    }

    private void loadAnalytics() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, R.string.login_failed_generic, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        tvTotalScans.setText("…");
        tvDiseasesFound.setText("…");
        tvTopDisease.setText("…");

        registration = ScanHistoryLoader.listen(this, user, new ScanHistoryLoader.Callback() {
            @Override
            public void onLoaded(List<Map<String, Object>> scans) {
                if (isFinishing() || isDestroyed()) return;
                renderAnalytics(scans);
            }

            @Override
            public void onError(Exception e) {
                if (isFinishing() || isDestroyed()) return;
                ScanHistoryLoader.loadOnce(FarmerAnalyticsActivity.this, user, new ScanHistoryLoader.Callback() {
                    @Override
                    public void onLoaded(List<Map<String, Object>> scans) {
                        if (!isFinishing() && !isDestroyed()) renderAnalytics(scans);
                    }

                    @Override
                    public void onError(Exception ex) {
                        if (isFinishing() || isDestroyed()) return;
                        renderAnalytics(new ArrayList<>());
                    }
                });
            }
        });
    }

    private void renderAnalytics(List<Map<String, Object>> scans) {
        int totalScans = 0;
        int diseasesFound = 0;
        Map<String, Integer> diseaseCounts = new HashMap<>();
        Map<String, Integer> monthlyCounts = new TreeMap<>();

        SimpleDateFormat sdf = new SimpleDateFormat("MMM", Locale.getDefault());
        List<String> monthLabels = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            Calendar c = Calendar.getInstance();
            c.add(Calendar.MONTH, -i);
            String key = sdf.format(c.getTime());
            monthLabels.add(key);
            monthlyCounts.put(key, 0);
        }

        for (Map<String, Object> doc : scans) {
            String raw = doc.get("disease") != null ? doc.get("disease").toString() : null;
            if (raw == null && doc.get("diseaseName") != null) {
                raw = doc.get("diseaseName").toString();
            }
            String key = DiseaseLabels.normalizeKey(raw);

            if (!DiseaseLabels.isValidScan(key)) continue;

            totalScans++;

            if (DiseaseLabels.isDiseaseFound(key)) {
                diseasesFound++;
                diseaseCounts.put(key, diseaseCounts.getOrDefault(key, 0) + 1);
            }

            Object tsObj = doc.get("timestamp");
            if (tsObj instanceof Timestamp) {
                String monthKey = sdf.format(((Timestamp) tsObj).toDate());
                if (monthlyCounts.containsKey(monthKey)) {
                    monthlyCounts.put(monthKey, monthlyCounts.get(monthKey) + 1);
                }
            } else if (tsObj instanceof Long) {
                String monthKey = sdf.format(new java.util.Date((Long) tsObj));
                if (monthlyCounts.containsKey(monthKey)) {
                    monthlyCounts.put(monthKey, monthlyCounts.get(monthKey) + 1);
                }
            }
        }

        tvTotalScans.setText(String.valueOf(totalScans));
        tvDiseasesFound.setText(String.valueOf(diseasesFound));

        topDiseaseKey = null;
        int maxCount = 0;
        for (Map.Entry<String, Integer> e : diseaseCounts.entrySet()) {
            if (e.getValue() > maxCount) {
                maxCount = e.getValue();
                topDiseaseKey = e.getKey();
            }
        }
        tvTopDisease.setText(topDiseaseKey == null ? "-"
                : DiseaseTextProvider.displayName(this, topDiseaseKey));

        populateDiseaseList(diseaseCounts);
        populateLineChart(monthLabels, monthlyCounts);
    }

    private void populateDiseaseList(Map<String, Integer> diseaseCounts) {
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(diseaseCounts.entrySet());
        sorted.sort((a, b) -> b.getValue().compareTo(a.getValue()));

        List<DiseaseRow> rows = new ArrayList<>();
        for (int i = 0; i < Math.min(6, sorted.size()); i++) {
            Map.Entry<String, Integer> e = sorted.get(i);
            rows.add(new DiseaseRow(
                    DiseaseTextProvider.displayName(this, e.getKey()),
                    e.getValue(),
                    DISEASE_COLORS[i % DISEASE_COLORS.length]
            ));
        }

        if (rows.isEmpty()) {
            rvDiseaseBreakdown.setVisibility(View.GONE);
            if (tvNoDiseaseData != null) tvNoDiseaseData.setVisibility(View.VISIBLE);
        } else {
            rvDiseaseBreakdown.setVisibility(View.VISIBLE);
            if (tvNoDiseaseData != null) tvNoDiseaseData.setVisibility(View.GONE);
            diseaseAdapter.setRows(rows);
        }
    }

    private void populateLineChart(List<String> monthLabels, Map<String, Integer> monthlyCounts) {
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < monthLabels.size(); i++) {
            entries.add(new Entry(i, monthlyCounts.getOrDefault(monthLabels.get(i), 0)));
        }

        if (entries.isEmpty()) {
            lineChartTrend.clear();
            lineChartTrend.setNoDataText(getString(R.string.no_data_yet));
            lineChartTrend.invalidate();
            return;
        }

        LineDataSet dataSet = new LineDataSet(entries, "Scans");
        dataSet.setColor(Color.parseColor("#2E7D32"));
        dataSet.setCircleColor(Color.parseColor("#2E7D32"));
        dataSet.setLineWidth(2.5f);
        dataSet.setCircleRadius(4f);
        dataSet.setDrawFilled(true);
        dataSet.setFillColor(Color.parseColor("#81C784"));
        dataSet.setFillAlpha(80);
        dataSet.setValueTextSize(10f);
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);

        lineChartTrend.getXAxis().setValueFormatter(new IndexAxisValueFormatter(monthLabels));
        lineChartTrend.getXAxis().setLabelCount(monthLabels.size());
        lineChartTrend.setData(new LineData(dataSet));
        lineChartTrend.invalidate();
    }

    private static final class DiseaseRow {
        final String name;
        final int count;
        final int color;

        DiseaseRow(String name, int count, int color) {
            this.name = name;
            this.count = count;
            this.color = color;
        }
    }

    private class DiseaseBreakdownAdapter extends RecyclerView.Adapter<DiseaseBreakdownAdapter.Holder> {
        private final List<DiseaseRow> rows = new ArrayList<>();

        void setRows(List<DiseaseRow> items) {
            rows.clear();
            rows.addAll(items);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_analytics_disease_row, parent, false);
            return new Holder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            DiseaseRow row = rows.get(position);
            holder.tvName.setText(row.name);
            holder.tvCount.setText("(" + row.count + ")");
            holder.colorDot.getBackground().setTint(row.color);
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            final View colorDot;
            final TextView tvName;
            final TextView tvCount;

            Holder(@NonNull View itemView) {
                super(itemView);
                colorDot = itemView.findViewById(R.id.viewDiseaseColor);
                tvName = itemView.findViewById(R.id.tvDiseaseName);
                tvCount = itemView.findViewById(R.id.tvDiseaseCount);
            }
        }
    }
}
