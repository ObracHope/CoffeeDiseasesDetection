package com.example.coffeediseasesdetection;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.coffeediseasesdetection.adapter.CoffeeTipsAdapter;
import com.example.coffeediseasesdetection.model.CoffeeTip;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class HomeFragment extends Fragment {

    private TextView tvWelcomeName;
    private TextView tvTotalScans, tvStatus, tvRiskLevel;
    private RecyclerView rvCoffeeTips;
    private LinearLayoutManager tipsLayoutManager;

    private final Handler greetingHandler = new Handler(Looper.getMainLooper());
    private final Handler marqueeHandler = new Handler(Looper.getMainLooper());
    private Runnable greetingDismissRunnable;
    private Runnable marqueeScrollRunnable;
    private boolean marqueePaused;
    private ListenerRegistration statsListener;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        tvWelcomeName = view.findViewById(R.id.tvWelcomeName);
        tvTotalScans = view.findViewById(R.id.tvTotalScans);
        tvStatus = view.findViewById(R.id.tvStatus);
        tvRiskLevel = view.findViewById(R.id.tvRiskLevel);
        rvCoffeeTips = view.findViewById(R.id.rvCoffeeTips);

        SharedPreferences prefs = requireContext().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        String firstName = prefs.getString("user_first_name", "");
        String lastName = prefs.getString("user_last_name", "");
        if (!firstName.isEmpty() || !lastName.isEmpty()) {
            setWelcomeText(firstName, lastName);
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            FirebaseFirestore.getInstance().collection("users")
                    .document(user.getUid())
                    .get()
                    .addOnSuccessListener(doc -> {
                        if (doc != null && doc.exists() && isAdded()) {
                            setWelcomeText(
                                    doc.getString("firstName") != null ? doc.getString("firstName") : "",
                                    doc.getString("lastName") != null ? doc.getString("lastName") : ""
                            );
                        }
                    });
            attachDashboardStatsListener(user);
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

        if (rvCoffeeTips != null) {
            setupCoffeeTipsMarquee();
        }

        greetingDismissRunnable = () -> {
            if (tvWelcomeName != null && isAdded()) {
                tvWelcomeName.animate()
                        .alpha(0f)
                        .setDuration(500)
                        .withEndAction(() -> {
                            if (tvWelcomeName != null) {
                                tvWelcomeName.setVisibility(View.GONE);
                            }
                        })
                        .start();
            }
        };
        greetingHandler.postDelayed(greetingDismissRunnable, 30000);

        return view;
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

    private void attachDashboardStatsListener(FirebaseUser user) {
        if (statsListener != null) {
            statsListener.remove();
            statsListener = null;
        }
        statsListener = ScanHistoryLoader.listen(requireContext(), user, new ScanHistoryLoader.Callback() {
            @Override
            public void onLoaded(List<Map<String, Object>> scans) {
                if (!isAdded()) return;
                applyDashboardStats(ScanHistoryLoader.computeStats(scans));
            }

            @Override
            public void onError(Exception e) {
                if (!isAdded()) return;
                ScanHistoryLoader.loadOnce(requireContext(), user, new ScanHistoryLoader.Callback() {
                    @Override
                    public void onLoaded(List<Map<String, Object>> scans) {
                        if (isAdded()) applyDashboardStats(ScanHistoryLoader.computeStats(scans));
                    }

                    @Override
                    public void onError(Exception ex) {
                        if (isAdded()) applyDashboardStats(new ScanHistoryLoader.DashboardStats(0, 0, "Healthy"));
                    }
                });
            }
        });
    }

    private void applyDashboardStats(ScanHistoryLoader.DashboardStats stats) {
        if (tvTotalScans != null) tvTotalScans.setText(String.valueOf(stats.totalScans));

        if (tvStatus != null) {
            if (stats.totalScans == 0) {
                tvStatus.setText("—");
            } else if (stats.diseasesFound == 0) {
                tvStatus.setText(getString(R.string.status_healthy_label));
                tvStatus.setTextColor(requireContext().getColor(R.color.status_healthy));
            } else {
                tvStatus.setText(DiseaseTextProvider.displayName(requireContext(), stats.latestDiseaseKey));
                tvStatus.setTextColor(requireContext().getColor(R.color.status_warning));
            }
        }

        if (tvRiskLevel != null) {
            if (stats.diseasesFound == 0) {
                tvRiskLevel.setText(getString(R.string.risk_low));
                tvRiskLevel.setTextColor(requireContext().getColor(R.color.status_info));
            } else if (stats.diseasesFound <= 2) {
                tvRiskLevel.setText(getString(R.string.risk_medium));
                tvRiskLevel.setTextColor(requireContext().getColor(R.color.status_warning));
            } else {
                tvRiskLevel.setText(getString(R.string.risk_high));
                tvRiskLevel.setTextColor(requireContext().getColor(R.color.status_error));
            }
        }
    }

    private void setWelcomeText(String firstName, String lastName) {
        String fullName = (firstName + " " + lastName).trim();
        if (fullName.isEmpty()) return;
        tvWelcomeName.setText(getString(R.string.habari_greeting) + fullName + getString(R.string.greeting_exclamation));
    }

    private void openCoffeeTipDetail(CoffeeTip tip) {
        CoffeeTipDetailBottomSheet sheet = CoffeeTipDetailBottomSheet.newInstance(tip);
        sheet.show(getParentFragmentManager(), "coffee_tip_detail");
    }

    @Override
    public void onPause() {
        super.onPause();
        pauseMarquee();
        if (greetingHandler != null && greetingDismissRunnable != null) {
            greetingHandler.removeCallbacks(greetingDismissRunnable);
        }
    }

    @Override
    public void onDestroyView() {
        stopMarquee();
        if (statsListener != null) {
            statsListener.remove();
            statsListener = null;
        }
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (greetingHandler != null && greetingDismissRunnable != null) {
            greetingHandler.removeCallbacks(greetingDismissRunnable);
        }
        stopMarquee();
    }

    @Override
    public void onResume() {
        super.onResume();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) attachDashboardStatsListener(user);
        if (marqueePaused) {
            resumeMarquee();
        }
    }
}
