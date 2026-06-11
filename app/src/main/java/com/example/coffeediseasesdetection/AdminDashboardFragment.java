package com.example.coffeediseasesdetection;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.coffeediseasesdetection.admin.AdminOverview;
import com.example.coffeediseasesdetection.admin.AdminRepository;

import java.util.List;
import java.util.Map;

public class AdminDashboardFragment extends Fragment {

    private final AdminRepository repository = new AdminRepository();
    private ProgressBar progressBar;
    private TextView tvTopAreas;
    private TextView tvNoRecent;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                           @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_admin_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        progressBar = view.findViewById(R.id.adminHomeProgress);
        tvTopAreas = view.findViewById(R.id.tvTopDiseasedAreas);
        tvNoRecent = view.findViewById(R.id.tvNoRecentScans);

        loadData(view);
    }

    private void loadData(View view) {
        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);

        repository.loadOverview(new AdminRepository.OverviewCallback() {
            @Override
            public void onSuccess(AdminOverview o) {
                if (!isAdded()) return;
                if (progressBar != null) progressBar.setVisibility(View.GONE);

                bindStatCard(view.findViewById(R.id.cardOnlineUsers),
                        R.string.admin_online_users, String.valueOf(o.onlineUsers));
                bindStatCard(view.findViewById(R.id.cardTodayScans),
                        R.string.admin_today_scans, String.valueOf(o.todayScans));
                bindStatCard(view.findViewById(R.id.cardActiveFarmers),
                        R.string.admin_active_farmers, String.valueOf(o.activeFarmers));
                bindStatCard(view.findViewById(R.id.cardSystemHealth),
                        R.string.admin_system_health, o.systemHealth);

                bindMiniStat(view.findViewById(R.id.cardTotalFarmers),
                        R.string.total_farmers, String.valueOf(o.totalFarmers));
                bindMiniStat(view.findViewById(R.id.cardTotalScans),
                        R.string.admin_total_scans, String.valueOf(o.totalScans));
                bindMiniStat(view.findViewById(R.id.cardImagesUploaded),
                        R.string.images_uploaded, String.valueOf(o.imagesUploaded));
                bindMiniStat(view.findViewById(R.id.cardDiseasesDetected),
                        R.string.diseases_detected, String.valueOf(o.diseasesDetected));
                bindMiniStat(view.findViewById(R.id.cardReportsCount),
                        R.string.reports_count, String.valueOf(o.reportsCount));

                if (tvTopAreas != null) {
                    if (o.topDiseasedAreas.isEmpty()) {
                        tvTopAreas.setText(R.string.no_data_yet);
                    } else {
                        StringBuilder sb = new StringBuilder();
                        for (String line : o.topDiseasedAreas) {
                            sb.append("• ").append(line).append("\n");
                        }
                        tvTopAreas.setText(sb.toString().trim());
                    }
                }

                RecyclerView rv = view.findViewById(R.id.rvRecentScans);
                if (rv != null) {
                    rv.setLayoutManager(new LinearLayoutManager(requireContext()));
                    HistoryAdapter adapter = new HistoryAdapter(o.recentScans, item -> {
                        Object id = item.get("id");
                        if (id != null) {
                            Intent i = new Intent(requireContext(), ScanDetailActivity.class);
                            i.putExtra("scanId", id.toString());
                            startActivity(i);
                        }
                    });
                    rv.setAdapter(adapter);
                    if (tvNoRecent != null) {
                        tvNoRecent.setVisibility(o.recentScans.isEmpty() ? View.VISIBLE : View.GONE);
                    }
                }
            }

            @Override
            public void onError(Exception e) {
                if (!isAdded()) return;
                if (progressBar != null) progressBar.setVisibility(View.GONE);
                Toast.makeText(requireContext(), R.string.error_loading_data, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void bindStatCard(View card, int labelRes, String value) {
        if (card == null) return;
        TextView label = card.findViewById(R.id.tvStatLabel);
        TextView val = card.findViewById(R.id.tvStatValue);
        if (label != null) label.setText(labelRes);
        if (val != null) val.setText(value);
    }

    private void bindMiniStat(View card, int labelRes, String value) {
        bindStatCard(card, labelRes, value);
    }
}
