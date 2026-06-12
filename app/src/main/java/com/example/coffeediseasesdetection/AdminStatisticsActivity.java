package com.example.coffeediseasesdetection;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

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

import com.google.android.material.tabs.TabLayout;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public class AdminStatisticsActivity extends BaseActivity {

    private FirebaseFirestore firestore;
    private TextView tvFarmersAccessed, tvImagesCount, tvDiseasesFound, tvTopDiseases;
    private BarChart barChartDiseases;
    private LineChart lineChartTrend;
    private int period = 0; // 0=daily, 1=weekly, 2=monthly

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_statistics);
        setTitle(R.string.statistics);

        firestore = FirebaseFirestore.getInstance();
        tvFarmersAccessed = findViewById(R.id.tvFarmersAccessed);
        tvImagesCount = findViewById(R.id.tvImagesCount);
        tvDiseasesFound = findViewById(R.id.tvDiseasesFound);
        tvTopDiseases = findViewById(R.id.tvTopDiseases);
        barChartDiseases = findViewById(R.id.barChartDiseases);
        lineChartTrend = findViewById(R.id.lineChartTrend);

        findViewById(R.id.ivBackButton).setOnClickListener(v -> finish());

        setupBarChart();
        setupLineChart();

        TabLayout tabPeriod = findViewById(R.id.tabPeriod);
        tabPeriod.addTab(tabPeriod.newTab().setText(getString(R.string.daily)));
        tabPeriod.addTab(tabPeriod.newTab().setText(getString(R.string.weekly)));
        tabPeriod.addTab(tabPeriod.newTab().setText(getString(R.string.monthly)));
        tabPeriod.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                period = tab.getPosition();
                loadStats();
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });
        tabPeriod.selectTab(tabPeriod.getTabAt(0));
        loadStats();
        setupClickableCards();
    }

    private void setupClickableCards() {
        View cardFarmers = findViewById(R.id.cardFarmersAccessed);
        if (cardFarmers != null) {
            cardFarmers.setOnClickListener(v ->
                    startActivity(new android.content.Intent(this, AdminManageFarmersActivity.class)));
        }
        View cardImages = findViewById(R.id.cardImagesCount);
        if (cardImages != null) {
            cardImages.setOnClickListener(v ->
                    startActivity(new android.content.Intent(this, AdminScansListActivity.class)));
        }
        View cardDiseases = findViewById(R.id.cardDiseasesFound);
        if (cardDiseases != null) {
            cardDiseases.setOnClickListener(v -> {
                android.content.Intent i = new android.content.Intent(this, AdminScansListActivity.class);
                startActivity(i);
            });
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

    private void loadStats() {
        Calendar cal = Calendar.getInstance();
        if (period == 0) {
            cal.add(Calendar.DAY_OF_YEAR, -1);
        } else if (period == 1) {
            cal.add(Calendar.WEEK_OF_YEAR, -1);
        } else {
            cal.add(Calendar.MONTH, -1);
        }
        Timestamp start = new Timestamp(cal.getTime());

        firestore.collection("scan_history")
                .whereGreaterThanOrEqualTo("timestamp", start)
                .get()
                .addOnSuccessListener(snapshots -> {
                    if (isFinishing() || isDestroyed()) return;

                    java.util.Set<String> farmerIds = new java.util.HashSet<>();
                    int images = 0;
                    int diseases = 0;
                    Map<String, Integer> diseaseCounts = new HashMap<>();

                    // Monthly trend: build last 6 month buckets
                    SimpleDateFormat sdf = new SimpleDateFormat("MMM yy", Locale.getDefault());
                    List<String> monthLabels = new ArrayList<>();
                    Map<String, Integer> monthlyCounts = new TreeMap<>();
                    for (int i = 5; i >= 0; i--) {
                        Calendar c = Calendar.getInstance();
                        c.add(Calendar.MONTH, -i);
                        String key = sdf.format(c.getTime());
                        monthLabels.add(key);
                        monthlyCounts.put(key, 0);
                    }

                    for (QueryDocumentSnapshot doc : snapshots) {
                        images++;
                        String uid = doc.getString("userId");
                        if (uid != null) farmerIds.add(uid);

                        String d = doc.getString("diseaseName");
                        if (d == null) d = doc.getString("disease");
                        if (d != null && !d.startsWith("SORRY") && !d.equals("Healthy")
                                && !d.equals("IsNotCoffee") && !d.equals("Uncertain")
                                && !d.equals("Error")) {
                            diseases++;
                            diseaseCounts.put(d, diseaseCounts.getOrDefault(d, 0) + 1);
                        }

                        // Monthly aggregation
                        Timestamp ts = doc.getTimestamp("timestamp");
                        if (ts != null) {
                            String monthKey = sdf.format(ts.toDate());
                            if (monthlyCounts.containsKey(monthKey)) {
                                monthlyCounts.put(monthKey, monthlyCounts.get(monthKey) + 1);
                            }
                        }
                    }

                    // Update summary cards
                    tvFarmersAccessed.setText(String.valueOf(farmerIds.size()));
                    tvImagesCount.setText(String.valueOf(images));
                    tvDiseasesFound.setText(String.valueOf(diseases));

                    // Top diseases text
                    List<Map.Entry<String, Integer>> sorted = new ArrayList<>(diseaseCounts.entrySet());
                    sorted.sort((a, b) -> b.getValue().compareTo(a.getValue()));
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < Math.min(5, sorted.size()); i++) {
                        Map.Entry<String, Integer> e = sorted.get(i);
                        sb.append("• ").append(e.getKey()).append(": ").append(e.getValue()).append("\n");
                    }
                    tvTopDiseases.setText(sb.length() > 0 ? sb.toString().trim() : "-");

                    // Populate charts
                    populateBarChart(diseaseCounts);
                    populateLineChart(monthLabels, monthlyCounts);
                });
    }

    private void populateBarChart(Map<String, Integer> diseaseCounts) {
        List<BarEntry> entries = new ArrayList<>();
        List<String> labels = new ArrayList<>();

        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(diseaseCounts.entrySet());
        sorted.sort((a, b) -> b.getValue().compareTo(a.getValue()));

        int index = 0;
        for (Map.Entry<String, Integer> e : sorted) {
            entries.add(new BarEntry(index, e.getValue()));
            labels.add(e.getKey());
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
        dataSet.setValueTextSize(12f);
        dataSet.setValueTextColor(Color.BLACK);

        BarData barData = new BarData(dataSet);
        barData.setBarWidth(0.6f);

        barChartDiseases.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));
        barChartDiseases.getXAxis().setLabelCount(labels.size());
        barChartDiseases.getXAxis().setLabelRotationAngle(-30f);
        barChartDiseases.setData(barData);
        barChartDiseases.animateY(800);
        barChartDiseases.invalidate();
    }

    private void populateLineChart(List<String> monthLabels, Map<String, Integer> monthlyCounts) {
        List<Entry> entries = new ArrayList<>();

        for (int i = 0; i < monthLabels.size(); i++) {
            String key = monthLabels.get(i);
            int count = monthlyCounts.getOrDefault(key, 0);
            entries.add(new Entry(i, count));
        }

        LineDataSet dataSet = new LineDataSet(entries, "Scans");
        dataSet.setColor(Color.parseColor("#33691E"));
        dataSet.setCircleColor(Color.parseColor("#33691E"));
        dataSet.setLineWidth(2.5f);
        dataSet.setCircleRadius(4f);
        dataSet.setDrawFilled(true);
        dataSet.setFillColor(Color.parseColor("#558B2F"));
        dataSet.setFillAlpha(60);
        dataSet.setValueTextSize(11f);
        dataSet.setValueTextColor(Color.BLACK);
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);

        LineData lineData = new LineData(dataSet);

        lineChartTrend.getXAxis().setValueFormatter(new IndexAxisValueFormatter(monthLabels));
        lineChartTrend.getXAxis().setLabelCount(monthLabels.size());
        lineChartTrend.setData(lineData);
        lineChartTrend.animateX(800);
        lineChartTrend.invalidate();
    }
}
