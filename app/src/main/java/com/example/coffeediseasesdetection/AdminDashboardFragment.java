package com.example.coffeediseasesdetection;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AdminDashboardFragment extends Fragment {

    private final AdminRepository repository = new AdminRepository();
    private ProgressBar progressBar;
    private LineChart chartUploadTrend;
    private PieChart chartDiseasePie;
    private MapView mapView;
    private LinearLayout layoutTopRegions;
    private TextView tvNoTopRegions;
    private TextView tvNoRecent;
    private TextView tvNoFarmers;
    private TextView tvFarmersOnMap;
    private TextView tvLiveSync;
    private boolean mapInitialized;

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
        mapView = view.findViewById(R.id.mapFarmerLocations);
        layoutTopRegions = view.findViewById(R.id.layoutTopRegions);
        tvNoTopRegions = view.findViewById(R.id.tvNoTopRegions);
        tvNoRecent = view.findViewById(R.id.tvNoRecentScans);
        tvNoFarmers = view.findViewById(R.id.tvNoFarmers);
        tvFarmersOnMap = view.findViewById(R.id.tvFarmersOnMap);
        tvLiveSync = view.findViewById(R.id.tvLiveSync);

        setupCharts();
        setupMap();
        setupNavigation(view);

        RecyclerView rvScans = view.findViewById(R.id.rvRecentScans);
        if (rvScans != null) {
            rvScans.setLayoutManager(new LinearLayoutManager(requireContext()));
        }
        RecyclerView rvFarmers = view.findViewById(R.id.rvFarmersMini);
        if (rvFarmers != null) {
            rvFarmers.setLayoutManager(new LinearLayoutManager(requireContext()));
        }

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
    public void onResume() {
        super.onResume();
        if (mapView != null) mapView.onResume();
    }

    @Override
    public void onPause() {
        if (mapView != null) mapView.onPause();
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        repository.stopOverview();
        if (mapView != null) {
            mapView.onDetach();
            mapView = null;
        }
        super.onDestroyView();
    }

    private void setupNavigation(View view) {
        View btnScans = view.findViewById(R.id.btnViewAllScans);
        if (btnScans != null) {
            btnScans.setOnClickListener(v ->
                    startActivity(new Intent(requireContext(), AdminScansListActivity.class)));
        }
        View btnFarmers = view.findViewById(R.id.btnViewAllFarmers);
        if (btnFarmers != null) {
            btnFarmers.setOnClickListener(v ->
                    startActivity(new Intent(requireContext(), AdminManageFarmersActivity.class)));
        }
        MaterialButton btnNotify = view.findViewById(R.id.btnSendNotification);
        if (btnNotify != null) {
            btnNotify.setOnClickListener(v -> showSendNotificationDialog());
        }
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
        }
    }

    private void setupMap() {
        if (mapView == null) return;
        try {
            mapView.setMultiTouchControls(true);
            mapView.getController().setZoom(6.5);
            mapView.getController().setCenter(new GeoPoint(-6.369, 34.888));
            mapInitialized = true;
        } catch (Exception ignored) {
            mapInitialized = false;
        }
    }

    private void bindOverview(View view, AdminOverview o) {
        if (progressBar != null) progressBar.setVisibility(View.GONE);
        if (tvLiveSync != null) {
            tvLiveSync.setText(o.liveSync
                    ? getString(R.string.admin_live_sync)
                    : getString(R.string.admin_syncing));
        }

        bindOverviewCard(view.findViewById(R.id.cardTotalFarmers),
                R.drawable.ic_history_custom, Color.parseColor("#2E7D32"),
                R.string.total_farmers, String.valueOf(o.totalFarmers),
                R.string.admin_registered_accounts);
        bindOverviewCard(view.findViewById(R.id.cardTotalScanned),
                R.drawable.ic_history_custom, Color.parseColor("#1565C0"),
                R.string.admin_total_scans, String.valueOf(o.totalScans),
                R.string.admin_leaf_analyses);
        bindOverviewCard(view.findViewById(R.id.cardDiseasesDetected),
                R.drawable.ic_history_custom, Color.parseColor("#E65100"),
                R.string.diseases_detected, String.valueOf(o.diseasesDetected),
                R.string.admin_positive_detections);
        bindOverviewCard(view.findViewById(R.id.cardActivityLogs),
                R.drawable.ic_notification_custom, Color.parseColor("#6A1B9A"),
                R.string.admin_activity_log, String.valueOf(o.activityLogsCount),
                R.string.admin_system_events);

        bindMiniStat(view.findViewById(R.id.cardOnlineUsers),
                R.string.admin_online_users, String.valueOf(o.onlineUsers));
        bindMiniStat(view.findViewById(R.id.cardTodayScans),
                R.string.admin_today_scans, String.valueOf(o.todayScans));
        bindMiniStat(view.findViewById(R.id.cardActiveFarmers),
                R.string.admin_active_farmers, String.valueOf(o.activeFarmers));
        bindMiniStat(view.findViewById(R.id.cardSystemHealth),
                R.string.admin_system_health, o.systemHealth);

        populateLineChart(o);
        populatePieChart(o);
        populateMap(o);
        populateTopRegions(o);
        bindRecentScans(view, o.recentScans);
        bindFarmersMini(view, o.recentFarmers);

        if (tvFarmersOnMap != null) {
            tvFarmersOnMap.setText(getString(R.string.admin_farmers_on_map, o.farmersOnMap));
        }
    }

    private void bindOverviewCard(View card, int iconRes, int iconBgColor,
                                  int labelRes, String value, int subtitleRes) {
        if (card == null) return;
        FrameLayout flIcon = card.findViewById(R.id.flIconBg);
        ImageView ivIcon = card.findViewById(R.id.ivOverviewIcon);
        TextView label = card.findViewById(R.id.tvOverviewLabel);
        TextView val = card.findViewById(R.id.tvOverviewValue);
        TextView subtitle = card.findViewById(R.id.tvOverviewSubtitle);
        if (flIcon != null) flIcon.getBackground().setTint(iconBgColor);
        if (ivIcon != null) ivIcon.setImageResource(iconRes);
        if (label != null) label.setText(labelRes);
        if (val != null) val.setText(value);
        if (subtitle != null) subtitle.setText(subtitleRes);
    }

    private void bindMiniStat(View card, int labelRes, String value) {
        if (card == null) return;
        TextView label = card.findViewById(R.id.tvStatLabel);
        TextView val = card.findViewById(R.id.tvStatValue);
        if (label != null) label.setText(labelRes);
        if (val != null) val.setText(value);
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
        List<PieEntry> entries = new ArrayList<>();
        int total = 0;
        int limit = Math.min(6, o.topDiseases.size());
        for (int i = 0; i < limit; i++) {
            Map.Entry<String, Integer> e = o.topDiseases.get(i);
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

    private void populateMap(AdminOverview o) {
        if (mapView == null || !mapInitialized) return;
        try {
            mapView.getOverlays().clear();
            if (o.mapMarkers.isEmpty()) return;

            double sumLat = 0, sumLng = 0;
            for (double[] pt : o.mapMarkers) {
                Marker marker = new Marker(mapView);
                marker.setPosition(new GeoPoint(pt[0], pt[1]));
                marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                mapView.getOverlays().add(marker);
                sumLat += pt[0];
                sumLng += pt[1];
            }
            GeoPoint center = new GeoPoint(sumLat / o.mapMarkers.size(), sumLng / o.mapMarkers.size());
            mapView.getController().setCenter(center);
            mapView.getController().setZoom(o.mapMarkers.size() == 1 ? 10.0 : 7.0);
            mapView.invalidate();
        } catch (Exception ignored) {
        }
    }

    private void populateTopRegions(AdminOverview o) {
        if (layoutTopRegions == null) return;
        layoutTopRegions.removeAllViews();
        if (o.topRegions.isEmpty()) {
            if (tvNoTopRegions != null) tvNoTopRegions.setVisibility(View.VISIBLE);
            return;
        }
        if (tvNoTopRegions != null) tvNoTopRegions.setVisibility(View.GONE);

        int max = o.topRegions.get(0).getValue();
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        int limit = Math.min(5, o.topRegions.size());
        for (int i = 0; i < limit; i++) {
            Map.Entry<String, Integer> e = o.topRegions.get(i);
            View row = inflater.inflate(R.layout.item_admin_region_progress, layoutTopRegions, false);
            TextView tvName = row.findViewById(R.id.tvRegionName);
            TextView tvPct = row.findViewById(R.id.tvRegionPercent);
            LinearProgressIndicator progress = row.findViewById(R.id.progressRegion);
            int pct = max > 0 ? (e.getValue() * 100 / max) : 0;
            if (tvName != null) tvName.setText(e.getKey());
            if (tvPct != null) tvPct.setText(pct + "%");
            if (progress != null) {
                progress.setProgress(pct);
                progress.setIndicatorColor(getResources().getColor(R.color.primaryGreen, null));
            }
            layoutTopRegions.addView(row);
        }
    }

    private void bindRecentScans(View view, List<Map<String, Object>> scans) {
        RecyclerView rv = view.findViewById(R.id.rvRecentScans);
        if (rv == null) return;
        HistoryAdapter adapter = new HistoryAdapter(scans, item -> {
            Object id = item.get("id");
            if (id != null) {
                Intent i = new Intent(requireContext(), ScanDetailActivity.class);
                i.putExtra("scanId", id.toString());
                startActivity(i);
            }
        });
        rv.setAdapter(adapter);
        if (tvNoRecent != null) {
            tvNoRecent.setVisibility(scans.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    private void bindFarmersMini(View view, List<Map<String, Object>> farmers) {
        RecyclerView rv = view.findViewById(R.id.rvFarmersMini);
        if (rv == null) return;
        rv.setAdapter(new FarmerMiniAdapter(farmers));
        if (tvNoFarmers != null) {
            tvNoFarmers.setVisibility(farmers.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    private void showSendNotificationDialog() {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_send_notification, null);
        TextInputEditText etTitle = dialogView.findViewById(R.id.etNotifyTitle);
        TextInputEditText etBody = dialogView.findViewById(R.id.etNotifyBody);

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.admin_send_notification)
                .setView(dialogView)
                .setPositiveButton(R.string.send, (d, w) -> {
                    String title = etTitle != null && etTitle.getText() != null
                            ? etTitle.getText().toString().trim() : "";
                    String body = etBody != null && etBody.getText() != null
                            ? etBody.getText().toString().trim() : "";
                    if (title.isEmpty() || body.isEmpty()) {
                        Toast.makeText(requireContext(), R.string.fill_all_fields, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    repository.sendBroadcastNotification(title, body, () -> {
                        if (!isAdded()) return;
                        repository.logActivity(requireContext(), "notification_send", title);
                        requireActivity().runOnUiThread(() ->
                                Toast.makeText(requireContext(), R.string.admin_notification_sent,
                                        Toast.LENGTH_SHORT).show());
                    });
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private static class FarmerMiniAdapter extends RecyclerView.Adapter<FarmerMiniAdapter.H> {
        private final List<Map<String, Object>> farmers;

        FarmerMiniAdapter(List<Map<String, Object>> farmers) {
            this.farmers = farmers != null ? farmers : new ArrayList<>();
        }

        @NonNull
        @Override
        public H onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_admin_farmer_mini, parent, false);
            return new H(v);
        }

        @Override
        public void onBindViewHolder(@NonNull H h, int pos) {
            Map<String, Object> f = farmers.get(pos);
            String first = f.get("firstName") != null ? String.valueOf(f.get("firstName")).trim() : "";
            String last = f.get("lastName") != null ? String.valueOf(f.get("lastName")).trim() : "";
            String name = f.get("name") != null ? String.valueOf(f.get("name")).trim() : "";
            if (name.isEmpty()) name = (first + " " + last).trim();
            if (name.isEmpty()) name = "Farmer";

            String email = f.get("email") != null ? String.valueOf(f.get("email")) : "";
            String status = f.get("status") != null ? String.valueOf(f.get("status")) : "active";

            h.tvName.setText(name);
            h.tvEmail.setText(email);
            h.tvStatus.setText(status);
            boolean inactive = "inactive".equalsIgnoreCase(status);
            h.tvStatus.setTextColor(h.itemView.getContext().getColor(
                    inactive ? R.color.status_warning : R.color.primaryGreen));
        }

        @Override
        public int getItemCount() {
            return farmers.size();
        }

        static class H extends RecyclerView.ViewHolder {
            final TextView tvName, tvEmail, tvStatus;

            H(View v) {
                super(v);
                tvName = v.findViewById(R.id.tvFarmerName);
                tvEmail = v.findViewById(R.id.tvFarmerEmail);
                tvStatus = v.findViewById(R.id.tvFarmerStatus);
            }
        }
    }
}
