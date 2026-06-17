package com.example.coffeediseasesdetection;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.example.coffeediseasesdetection.admin.AdminOverview;
import com.example.coffeediseasesdetection.admin.AdminRepository;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AdminDashboardFragment extends Fragment {

    private final AdminRepository repository = new AdminRepository();
    private ProgressBar progressBar;
    private LineChart chartUploadTrend;
    private PieChart chartDiseasePie;
    private final List<String> pieDiseaseKeys = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_admin_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        progressBar = view.findViewById(R.id.adminHomeProgress);
        chartUploadTrend = view.findViewById(R.id.chartUploadTrend);
        chartDiseasePie = view.findViewById(R.id.chartDiseasePie);

        setupCharts();
        setupRecentActivityLinks(view);

        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
        repository.startOverview(requireContext(), new AdminRepository.OverviewCallback() {
            @Override
            public void onSuccess(AdminOverview o) {
                if (!isAdded()) return;
                bindOverview(view, o);
            }

            @Override
            public void onError(Exception e) {
                if (!isAdded()) return;
                if (progressBar != null) progressBar.setVisibility(View.GONE);
                Toast.makeText(requireContext(), R.string.error_loading_data, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onDestroyView() {
        repository.stopOverview();
        super.onDestroyView();
    }

    private void setupCharts() {
        if (chartUploadTrend != null) {
            chartUploadTrend.getDescription().setEnabled(false);
            chartUploadTrend.setDrawGridBackground(false);
            chartUploadTrend.getLegend().setEnabled(false);
            chartUploadTrend.getAxisRight().setEnabled(false);
            chartUploadTrend.getAxisLeft().setGranularity(1f);
            chartUploadTrend.getAxisLeft().setAxisMinimum(0f);
            XAxis xAxis = chartUploadTrend.getXAxis();
            xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
            xAxis.setDrawGridLines(false);
            xAxis.setGranularity(1f);
        }
        if (chartDiseasePie != null) {
            chartDiseasePie.getDescription().setEnabled(false);
            chartDiseasePie.setDrawEntryLabels(false);
            chartDiseasePie.setUsePercentValues(true);
            chartDiseasePie.setEntryLabelTextSize(10f);
            chartDiseasePie.setCenterTextSize(13f);
            chartDiseasePie.setHoleRadius(45f);
            chartDiseasePie.setTransparentCircleRadius(50f);
            chartDiseasePie.setHighlightPerTapEnabled(true);
            chartDiseasePie.setOnChartValueSelectedListener(new OnChartValueSelectedListener() {
                @Override
                public void onValueSelected(Entry e, Highlight h) {
                    if (!(e instanceof PieEntry) || !isAdded()) return;
                    PieEntry entry = (PieEntry) e;
                    int index = h != null ? (int) h.getX() : -1;
                    String diseaseName = entry.getLabel() != null ? entry.getLabel() : "";
                    if (index >= 0 && index < pieDiseaseKeys.size()) {
                        diseaseName = pieDiseaseKeys.get(index);
                    }
                    int cases = Math.round(entry.getValue());
                    new AlertDialog.Builder(requireContext())
                            .setTitle(R.string.admin_disease_slice_title)
                            .setMessage(getString(R.string.admin_disease_slice_info, diseaseName, cases))
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                }

                @Override
                public void onNothingSelected() {
                }
            });
        }
    }

    private void bindOverview(View view, AdminOverview o) {
        if (progressBar != null) progressBar.setVisibility(View.GONE);

        TextView tvLiveSync = requireActivity().findViewById(R.id.tvLiveSync);
        if (tvLiveSync != null) {
            tvLiveSync.setText(o.liveSync
                    ? getString(R.string.admin_systems_active)
                    : getString(R.string.admin_syncing));
        }

        bindGridStat(view.findViewById(R.id.cardTotalUsers),
                R.drawable.ic_person_custom, Color.parseColor("#1B5E20"),
                Color.parseColor("#1B5E20"), R.string.total_users, String.valueOf(o.totalUsers));
        bindGridStat(view.findViewById(R.id.cardTotalFarmers),
                R.drawable.ic_farmers_group, Color.parseColor("#388E3C"),
                Color.parseColor("#388E3C"), R.string.total_farmers, String.valueOf(o.totalFarmers));
        bindGridStat(view.findViewById(R.id.cardTotalScanned),
                R.drawable.ic_camera_custom, Color.parseColor("#2196F3"),
                Color.parseColor("#2196F3"), R.string.admin_total_scans, String.valueOf(o.totalScans));
        bindGridStat(view.findViewById(R.id.cardDiseasesDetected),
                R.drawable.ic_disease_search, Color.parseColor("#FF9800"),
                Color.parseColor("#FF9800"), R.string.diseases_detected, String.valueOf(o.diseasesDetected));
        bindGridStat(view.findViewById(R.id.cardActivityLogs),
                R.drawable.ic_history_custom, Color.parseColor("#9C27B0"),
                Color.parseColor("#9C27B0"), R.string.admin_activity_log, String.valueOf(o.activityLogsCount));
        bindGridStat(view.findViewById(R.id.cardHealthyCoffeeOverview),
                R.drawable.img_healthy_leaf, Color.parseColor("#4CAF50"),
                Color.parseColor("#4CAF50"), R.string.admin_health_coffee, String.valueOf(o.healthCoffeeCount));

        setupOverviewCardLinks(view, o);
        populateLineChart(o);
        populatePieChart(o);
    }

    private void setupOverviewCardLinks(View view, AdminOverview o) {
        bindStatLink(view.findViewById(R.id.cardTotalUsers), R.string.total_users,
                getString(R.string.admin_stat_users_info, o.totalUsers),
                new Intent(requireContext(), AdminManageFarmersActivity.class));
        bindStatLink(view.findViewById(R.id.cardTotalFarmers), R.string.total_farmers,
                getString(R.string.admin_stat_farmers_info, o.totalFarmers),
                new Intent(requireContext(), AdminManageFarmersActivity.class));
        bindStatLink(view.findViewById(R.id.cardTotalScanned), R.string.admin_total_scans,
                getString(R.string.admin_stat_scans_info, o.totalScans),
                scanListIntent(AdminScansListActivity.FILTER_ALL, null));
        bindStatLink(view.findViewById(R.id.cardDiseasesDetected), R.string.diseases_detected,
                getString(R.string.admin_stat_diseases_info, o.diseasesDetected),
                scanListIntent(AdminScansListActivity.FILTER_DISEASES, null));
        bindStatLink(view.findViewById(R.id.cardActivityLogs), R.string.admin_activity_log,
                getString(R.string.admin_stat_logs_info, o.activityLogsCount),
                new Intent(requireContext(), AdminActivityLogActivity.class));
        bindStatLink(view.findViewById(R.id.cardHealthyCoffeeOverview), R.string.admin_health_coffee,
                getString(R.string.admin_stat_healthy_info, o.healthCoffeeCount),
                scanListIntent(AdminScansListActivity.FILTER_HEALTHY, null));
    }

    private Intent scanListIntent(String filter, String diseaseKey) {
        Intent intent = new Intent(requireContext(), AdminScansListActivity.class);
        intent.putExtra(AdminScansListActivity.EXTRA_FILTER, filter);
        if (diseaseKey != null) {
            intent.putExtra(AdminScansListActivity.EXTRA_DISEASE_KEY, diseaseKey);
        }
        return intent;
    }

    private void bindStatLink(@Nullable View card, int titleRes, String message,
                              Intent destination) {
        if (card == null) return;
        card.setOnClickListener(v -> new AlertDialog.Builder(requireContext())
                .setTitle(titleRes)
                .setMessage(message)
                .setPositiveButton(R.string.view_details, (d, w) -> startActivity(destination))
                .setNegativeButton(R.string.no, null)
                .show());
    }

    private void bindGridStat(View card, int iconRes, int iconBgColor, int valueColor,
                              int labelRes, String value) {
        if (card == null) return;
        FrameLayout iconBg = card.findViewById(R.id.flStatIconBg);
        ImageView icon = card.findViewById(R.id.ivStatIcon);
        TextView label = card.findViewById(R.id.tvOverviewLabel);
        TextView val = card.findViewById(R.id.tvOverviewValue);
        if (iconBg != null) iconBg.getBackground().setTint(iconBgColor);
        if (icon != null) {
            icon.setImageResource(iconRes);
            if (iconRes == R.drawable.img_healthy_leaf) {
                icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
            } else {
                icon.setColorFilter(Color.WHITE);
                icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            }
        }
        if (label != null) label.setText(labelRes);
        if (val != null) {
            val.setText(value);
            val.setTextColor(valueColor);
        }
    }

    private void setupRecentActivityLinks(View view) {
        View rowFarmer = view.findViewById(R.id.rowActivityNewFarmer);
        if (rowFarmer != null) {
            rowFarmer.setOnClickListener(v ->
                    startActivity(new Intent(requireContext(), AdminManageFarmersActivity.class)));
        }
        View rowScan = view.findViewById(R.id.rowActivityScanUploaded);
        if (rowScan != null) {
            rowScan.setOnClickListener(v ->
                    startActivity(new Intent(requireContext(), AdminScansListActivity.class)));
        }
        View rowAlert = view.findViewById(R.id.rowActivityDiseaseAlert);
        if (rowAlert != null) {
            rowAlert.setOnClickListener(v ->
                    startActivity(new Intent(requireContext(), AdminBroadcastNotificationsActivity.class)));
        }
    }

    private void populateLineChart(AdminOverview o) {
        if (chartUploadTrend == null) return;
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < o.monthCounts.size(); i++) {
            entries.add(new Entry(i, o.monthCounts.get(i)));
        }
        if (entries.isEmpty()) {
            chartUploadTrend.clear();
            chartUploadTrend.setNoDataText(getString(R.string.no_data_yet));
            chartUploadTrend.invalidate();
            return;
        }
        LineDataSet dataSet = new LineDataSet(entries, "Scans");
        dataSet.setColor(Color.parseColor("#33691E"));
        dataSet.setCircleColor(Color.parseColor("#33691E"));
        dataSet.setLineWidth(2.5f);
        dataSet.setCircleRadius(4f);
        dataSet.setDrawFilled(true);
        dataSet.setFillColor(Color.parseColor("#558B2F"));
        dataSet.setFillAlpha(60);
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);

        chartUploadTrend.getXAxis().setValueFormatter(new IndexAxisValueFormatter(o.monthLabels));
        chartUploadTrend.getXAxis().setLabelCount(o.monthLabels.size());
        chartUploadTrend.setData(new LineData(dataSet));
        chartUploadTrend.invalidate();
    }

    private void populatePieChart(AdminOverview o) {
        if (chartDiseasePie == null) return;
        pieDiseaseKeys.clear();
        List<PieEntry> entries = new ArrayList<>();
        int total = 0;
        int limit = Math.min(6, o.topDiseases.size());
        for (int i = 0; i < limit; i++) {
            Map.Entry<String, Integer> e = o.topDiseases.get(i);
            pieDiseaseKeys.add(e.getKey());
            entries.add(new PieEntry(e.getValue(), truncate(e.getKey(), 18)));
            total += e.getValue();
        }
        if (entries.isEmpty()) {
            chartDiseasePie.clear();
            chartDiseasePie.setNoDataText(getString(R.string.no_data_yet));
            chartDiseasePie.invalidate();
            return;
        }
        chartDiseasePie.setCenterText(total + "\n" + getString(R.string.admin_total_label));

        PieDataSet dataSet = new PieDataSet(entries, "");
        dataSet.setColors(new int[]{
                Color.parseColor("#E65100"), Color.parseColor("#2E7D32"),
                Color.parseColor("#1565C0"), Color.parseColor("#6A1B9A"),
                Color.parseColor("#BF360C"), Color.parseColor("#558B2F")
        });
        dataSet.setSliceSpace(2f);
        dataSet.setValueTextSize(11f);

        PieData pieData = new PieData(dataSet);
        chartDiseasePie.setData(pieData);
        chartDiseasePie.invalidate();
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
