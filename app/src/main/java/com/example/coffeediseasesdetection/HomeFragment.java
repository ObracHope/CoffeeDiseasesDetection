package com.example.coffeediseasesdetection;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.coffeediseasesdetection.adapter.CoffeeTipsAdapter;
import com.example.coffeediseasesdetection.model.CoffeeTip;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class HomeFragment extends Fragment implements FarmerScanStatsHost.StatsCallback {

    private TextView tvTotalScans, tvStatus, tvRiskLevel;
    private RecyclerView rvRecentScans;
    private TextView tvNoRecentScans;
    private RecyclerView rvCoffeeTips;
    private LinearLayoutManager tipsLayoutManager;

    private final Handler marqueeHandler = new Handler(Looper.getMainLooper());
    private Runnable marqueeScrollRunnable;
    private boolean marqueePaused;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        tvTotalScans = view.findViewById(R.id.tvTotalScans);
        tvStatus = view.findViewById(R.id.tvStatus);
        tvRiskLevel = view.findViewById(R.id.tvRiskLevel);
        rvRecentScans = view.findViewById(R.id.rvRecentScans);
        tvNoRecentScans = view.findViewById(R.id.tvNoRecentScans);
        rvCoffeeTips = view.findViewById(R.id.rvCoffeeTips);

        if (rvRecentScans != null) {
            rvRecentScans.setLayoutManager(new LinearLayoutManager(requireContext(),
                    LinearLayoutManager.HORIZONTAL, false));
        }

        View welcome = view.findViewById(R.id.tvWelcomeName);
        if (welcome != null) {
            welcome.setVisibility(View.GONE);
        }

        View btnCapture = view.findViewById(R.id.btnHomeCapture);
        if (btnCapture != null) {
            btnCapture.setOnClickListener(v ->
                    startActivity(new Intent(requireContext(), CameraActivity.class)));
        }
        View btnUpload = view.findViewById(R.id.btnHomeUpload);
        if (btnUpload != null) {
            btnUpload.setOnClickListener(v ->
                    startActivity(new Intent(requireContext(), UploadImageActivity.class)));
        }
        View tvSeeAll = view.findViewById(R.id.tvSeeAll);
        if (tvSeeAll != null) {
            tvSeeAll.setOnClickListener(v ->
                    startActivity(new Intent(requireContext(), CoffeeCareGuideActivity.class)));
        }
        View tvViewAllScans = view.findViewById(R.id.tvViewAllScans);
        if (tvViewAllScans != null) {
            tvViewAllScans.setOnClickListener(v -> {
                if (getActivity() == null) return;
                BottomNavigationView nav = getActivity().findViewById(R.id.bottom_navigation);
                if (nav != null) {
                    nav.setSelectedItemId(R.id.nav_history);
                }
            });
        }

        if (rvCoffeeTips != null) {
            setupCoffeeTipsMarquee();
        }

        return view;
    }

    @Override
    public void onStatsUpdated(ScanHistoryLoader.DashboardStats stats) {
        if (isAdded()) {
            applyDashboardStats(stats);
        }
    }

    private void setupCoffeeTipsMarquee() {
        List<CoffeeTip> baseTips = buildCoffeeTips();
        List<CoffeeTip> loopTips = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            loopTips.addAll(baseTips);
        }

        tipsLayoutManager = new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false);
        rvCoffeeTips.setLayoutManager(tipsLayoutManager);
        rvCoffeeTips.setHasFixedSize(true);

        CoffeeTipsAdapter adapter = new CoffeeTipsAdapter(requireContext(), loopTips, this::openCoffeeTipDetail);
        rvCoffeeTips.setAdapter(adapter);

        rvCoffeeTips.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_MOVE:
                    pauseMarquee();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    resumeMarquee();
                    break;
                default:
                    break;
            }
            return false;
        });

        rvCoffeeTips.post(() -> {
            if (!isAdded()) return;
            ViewGroup.LayoutParams lp = rvCoffeeTips.getLayoutParams();
            lp.height = CoffeeTipsAdapter.calculateCardHeightPx(requireContext());
            rvCoffeeTips.setLayoutParams(lp);
            startMarquee(baseTips.size());
        });
    }

    private List<CoffeeTip> buildCoffeeTips() {
        return Arrays.asList(
                new CoffeeTip(
                        getString(R.string.tip_healthy_title),
                        getString(R.string.tip_healthy_desc),
                        getString(R.string.tip_healthy_symptoms),
                        getString(R.string.tip_healthy_treatment),
                        R.drawable.photo_healthy_coffee
                ),
                new CoffeeTip(
                        getString(R.string.tip_rust_title),
                        getString(R.string.tip_rust_desc),
                        getString(R.string.tip_rust_symptoms),
                        getString(R.string.tip_rust_treatment),
                        R.drawable.photo_rust_disease
                ),
                new CoffeeTip(
                        getString(R.string.tip_berry_title),
                        getString(R.string.tip_berry_desc),
                        getString(R.string.tip_berry_symptoms),
                        getString(R.string.tip_berry_treatment),
                        R.drawable.photo_berry_disease
                ),
                new CoffeeTip(
                        getString(R.string.tip_wilt_title),
                        getString(R.string.tip_wilt_desc),
                        getString(R.string.tip_wilt_symptoms),
                        getString(R.string.tip_wilt_treatment),
                        R.drawable.photo_wilt_disease
                ),
                new CoffeeTip(
                        getString(R.string.tip_leaf_miner_title),
                        getString(R.string.tip_leaf_miner_desc),
                        getString(R.string.tip_leaf_miner_symptoms),
                        getString(R.string.tip_leaf_miner_treatment),
                        R.drawable.photo_leaf_miner
                ),
                new CoffeeTip(
                        getString(R.string.tip_root_rot_title),
                        getString(R.string.tip_root_rot_desc),
                        getString(R.string.tip_root_rot_symptoms),
                        getString(R.string.tip_root_rot_treatment),
                        R.drawable.photo_root_rot
                )
        );
    }

    private void startMarquee(int originalCount) {
        stopMarquee();
        marqueeScrollRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isAdded() || rvCoffeeTips == null || tipsLayoutManager == null || marqueePaused) {
                    return;
                }

                rvCoffeeTips.scrollBy(1, 0);

                int firstVisible = tipsLayoutManager.findFirstVisibleItemPosition();
                if (firstVisible >= originalCount) {
                    View firstView = tipsLayoutManager.findViewByPosition(firstVisible);
                    int offset = firstView != null ? firstView.getLeft() : 0;
                    tipsLayoutManager.scrollToPositionWithOffset(firstVisible - originalCount, offset);
                }

                marqueeHandler.postDelayed(this, 28L);
            }
        };
        marqueeHandler.post(marqueeScrollRunnable);
    }

    private void pauseMarquee() {
        marqueePaused = true;
        if (marqueeScrollRunnable != null) {
            marqueeHandler.removeCallbacks(marqueeScrollRunnable);
        }
    }

    private void resumeMarquee() {
        if (!marqueePaused || marqueeScrollRunnable == null || !isAdded()) return;
        marqueePaused = false;
        marqueeHandler.post(marqueeScrollRunnable);
    }

    private void stopMarquee() {
        if (marqueeScrollRunnable != null) {
            marqueeHandler.removeCallbacks(marqueeScrollRunnable);
            marqueeScrollRunnable = null;
        }
    }

    private void applyDashboardStats(ScanHistoryLoader.DashboardStats stats) {
        if (tvTotalScans != null) tvTotalScans.setText(String.valueOf(stats.totalScans));

        if (tvStatus != null) {
            if (stats.totalScans == 0) {
                tvStatus.setText("—");
                tvStatus.setTextColor(requireContext().getColor(R.color.textLight));
            } else if ("IsNotCoffee".equals(stats.latestDiseaseKey)) {
                tvStatus.setText(getString(R.string.not_coffee_title));
                tvStatus.setTextColor(requireContext().getColor(R.color.status_warning));
            } else if ("Uncertain".equals(stats.latestDiseaseKey)) {
                tvStatus.setText(getString(R.string.uncertain_desc));
                tvStatus.setTextColor(requireContext().getColor(R.color.status_warning));
            } else if (stats.diseasesFound == 0 || "Healthy".equals(stats.latestDiseaseKey)) {
                tvStatus.setText(getString(R.string.status_healthy_label));
                tvStatus.setTextColor(requireContext().getColor(R.color.status_healthy));
            } else {
                tvStatus.setText(DiseaseTextProvider.displayName(requireContext(), stats.latestDiseaseKey));
                tvStatus.setTextColor(requireContext().getColor(R.color.status_warning));
            }
        }

        if (tvRiskLevel != null) {
            tvRiskLevel.clearAnimation();
            if (stats.totalScans == 0) {
                tvRiskLevel.setText("—");
                tvRiskLevel.setTextColor(requireContext().getColor(R.color.textLight));
            } else {
                String latestKey = stats.latestDiseaseKey != null ? stats.latestDiseaseKey : "Healthy";
                tvRiskLevel.setText(DiseaseRiskHelper.riskLabel(requireContext(), latestKey));
                tvRiskLevel.setTextColor(requireContext().getColor(
                        DiseaseRiskHelper.riskColorRes(latestKey)));
                if (DiseaseRiskHelper.shouldPulse(latestKey)) {
                    Animation pulse = AnimationUtils.loadAnimation(requireContext(), R.anim.anim_pulse_risk);
                    tvRiskLevel.startAnimation(pulse);
                }
            }
        }

        bindRecentScans(stats.recentScans);
    }

    private void bindRecentScans(List<Map<String, Object>> scans) {
        if (rvRecentScans == null) return;
        List<Map<String, Object>> items = scans != null ? scans : new ArrayList<>();
        if (items.isEmpty()) {
            rvRecentScans.setVisibility(View.GONE);
            if (tvNoRecentScans != null) tvNoRecentScans.setVisibility(View.VISIBLE);
            return;
        }
        rvRecentScans.setVisibility(View.VISIBLE);
        if (tvNoRecentScans != null) tvNoRecentScans.setVisibility(View.GONE);
        HistoryAdapter adapter = new HistoryAdapter(items, item -> {
            Object id = item.get("id");
            if (id != null) {
                Intent i = new Intent(requireContext(), ScanDetailActivity.class);
                i.putExtra("scanId", id.toString());
                startActivity(i);
            }
        }, true);
        rvRecentScans.setAdapter(adapter);
    }

    private void openCoffeeTipDetail(CoffeeTip tip) {
        CoffeeTipDetailBottomSheet sheet = CoffeeTipDetailBottomSheet.newInstance(tip);
        sheet.show(getParentFragmentManager(), "coffee_tip_detail");
    }

    @Override
    public void onPause() {
        super.onPause();
        pauseMarquee();
        if (getActivity() instanceof FarmerScanStatsHost) {
            ((FarmerScanStatsHost) getActivity()).setScanStatsCallback(null);
        }
    }

    @Override
    public void onDestroyView() {
        stopMarquee();
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopMarquee();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() instanceof FarmerScanStatsHost) {
            ((FarmerScanStatsHost) getActivity()).setScanStatsCallback(this);
        }
        if (marqueePaused) {
            resumeMarquee();
        }
    }
}
