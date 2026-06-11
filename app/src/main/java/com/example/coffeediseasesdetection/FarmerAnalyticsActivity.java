package com.example.coffeediseasesdetection;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.cardview.widget.CardView;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
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

    private TextView tvTotalScans, tvDiseasesFound, tvTopDisease;
    private BarChart barChartDiseases;
    private LineChart lineChartTrend;
    private ListenerRegistration registration;
    private String topDiseaseKey = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_farmer_analytics);

        tvTotalScans = findViewById(R.id.tvTotalScans);
        tvDiseasesFound = findViewById(R.id.tvDiseasesFound);
        tvTopDisease = findViewById(R.id.tvTopDisease);
        barChartDiseases = findViewById(R.id.barChartDiseases);
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

        setupBarChart();
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

    private void setupBarChart() {
        barChartDiseases.getDescription().setEnabled(false);
        barChartDiseases.setDrawGridBackground(false);
        barChartDiseases.setFitBars(true);
        barChartDiseases.animateY(800);
        barChartDiseases.getLegend().setEnabled(false);
        barChartDiseases.getAxisRight().setEnabled(false);
        barChartDiseases.getAxisLeft().setGranularity(1f);
        barChartDiseases.getAxisLeft().setAxisMinimum(0f);
        XAxis xAxis = barChartDiseases.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setDrawGridLines(false);
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
                        renderAnalytics(new java.util.ArrayList<>());
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

        SimpleDateFormat sdf = new SimpleDateFormat("MMM yy", Locale.getDefault());
        List<String> monthLabels = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            Calendar c = Calendar.getInstance();
            c.add(Calendar.MONTH, -i);
            String key = sdf.format(c.getTime());
            monthLabels.add(key);
            monthlyCounts.put(key, 0);
        }

        for (Map<String, Object> doc : scans) {
            String raw = (String) doc.get("disease");
            if (raw == null) raw = (String) doc.get("diseaseName");
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

        populateBarChart(diseaseCounts);
        populateLineChart(monthLabels, monthlyCounts);
    }

    private void populateBarChart(Map<String, Integer> diseaseCounts) {
        List<BarEntry> entries = new ArrayList<>();
        List<String> labels = new ArrayList<>();

        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(diseaseCounts.entrySet());
        sorted.sort((a, b) -> b.getValue().compareTo(a.getValue()));

        int index = 0;
        for (Map.Entry<String, Integer> e : sorted) {
            entries.add(new BarEntry(index, e.getValue()));
            labels.add(DiseaseTextProvider.displayName(this, e.getKey()));
            index++;
        }

        if (entries.isEmpty()) {
            barChartDiseases.clear();
            barChartDiseases.setNoDataText(getString(R.string.no_data_yet));
            barChartDiseases.invalidate();
            return;
        }

        BarDataSet dataSet = new BarDataSet(entries, "Diseases");
        dataSet.setColors(
                Color.parseColor("#E65100"),
                Color.parseColor("#1B5E20"),
                Color.parseColor("#BF360C"),
                Color.parseColor("#4E342E"),
                Color.parseColor("#33691E"),
                Color.parseColor("#558B2F")
        );
        dataSet.setValueTextSize(11f);
        dataSet.setValueTextColor(Color.parseColor("#212121"));

        BarData barData = new BarData(dataSet);
        barData.setBarWidth(0.55f);
        barChartDiseases.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));
        barChartDiseases.getXAxis().setLabelCount(Math.min(labels.size(), 6));
        barChartDiseases.getXAxis().setLabelRotationAngle(-25f);
        barChartDiseases.setData(barData);
        barChartDiseases.invalidate();
    }

    private void populateLineChart(List<String> monthLabels, Map<String, Integer> monthlyCounts) {
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < monthLabels.size(); i++) {
            entries.add(new Entry(i, monthlyCounts.getOrDefault(monthLabels.get(i), 0)));
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
        lineChartTrend.setData(new LineData(dataSet));
        lineChartTrend.invalidate();
    }
}
