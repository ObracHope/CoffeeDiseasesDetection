package com.example.coffeediseasesdetection;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.coffeediseasesdetection.admin.AdminRepository;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.Timestamp;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AdminActivityLogActivity extends BaseActivity {

    private final List<Map<String, Object>> allLogs = new ArrayList<>();
    private final List<Map<String, Object>> trailLogs = new ArrayList<>();
    private final List<Map<String, Object>> authLogs = new ArrayList<>();
    private TrailAdapter trailAdapter;
    private AuthAdapter authAdapter;
    private String currentFilter = ActivityLogFilterHelper.FILTER_DAY;
    private String currentDateYmd = "";
    private String timeFromHm = "";
    private String timeToHm = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_activity_log);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        TextView tvDate = findViewById(R.id.tvLogPanelDate);
        TextView tvGreeting = findViewById(R.id.tvLogGreeting);
        if (tvDate != null) {
            tvDate.setText(new SimpleDateFormat("EEEE, MMMM d yyyy", Locale.US)
                    .format(new Date()).toUpperCase(Locale.US));
        }
        if (tvGreeting != null) {
            String name = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(KEY_NAME, "System");
            if (name == null || name.trim().isEmpty()) name = "System";
            tvGreeting.setText("Hello, " + name.split(" ")[0]);
        }

        RecyclerView rvTrail = findViewById(R.id.rvActivityTrail);
        RecyclerView rvAuth = findViewById(R.id.rvAuthStream);
        rvTrail.setLayoutManager(new LinearLayoutManager(this));
        rvAuth.setLayoutManager(new LinearLayoutManager(this));
        trailAdapter = new TrailAdapter();
        authAdapter = new AuthAdapter();
        rvTrail.setAdapter(trailAdapter);
        rvAuth.setAdapter(authAdapter);

        setupLogFilters();

        new AdminRepository().loadActivityLogs(new AdminRepository.ScansCallback() {
            @Override
            public void onSuccess(List<Map<String, Object>> list) {
                if (isFinishing()) return;
                allLogs.clear();
                if (list != null) allLogs.addAll(list);
                applyLogFilter();
            }

            @Override
            public void onError(Exception e) {
                if (!isFinishing()) {
                    android.widget.Toast.makeText(AdminActivityLogActivity.this,
                            R.string.error_loading_data, android.widget.Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void setupLogFilters() {
        ChipGroup chipGroup = findViewById(R.id.chipGroupLogFilter);
        Chip chipDay = findViewById(R.id.chipFilterDay);
        Chip chipWeek = findViewById(R.id.chipFilterWeek);
        Chip chipMonth = findViewById(R.id.chipFilterMonth);
        TextInputEditText etDate = findViewById(R.id.etLogFilterDate);
        TextInputEditText etTimeFrom = findViewById(R.id.etLogFilterTimeFrom);
        TextInputEditText etTimeTo = findViewById(R.id.etLogFilterTimeTo);

        if (chipGroup != null) {
            chipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
                if (checkedIds.isEmpty()) return;
                int id = checkedIds.get(0);
                if (id == R.id.chipFilterDay) currentFilter = ActivityLogFilterHelper.FILTER_DAY;
                else if (id == R.id.chipFilterWeek) currentFilter = ActivityLogFilterHelper.FILTER_WEEK;
                else if (id == R.id.chipFilterMonth) currentFilter = ActivityLogFilterHelper.FILTER_MONTH;
                currentDateYmd = "";
                if (etDate != null) etDate.setText("");
                applyLogFilter();
            });
        }

        if (etDate != null) {
            etDate.setOnClickListener(v -> {
                Calendar cal = Calendar.getInstance();
                new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
                    currentDateYmd = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayOfMonth);
                    currentFilter = ActivityLogFilterHelper.FILTER_DATE;
                    etDate.setText(currentDateYmd);
                    if (chipGroup != null) chipGroup.clearCheck();
                    applyLogFilter();
                }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
            });
        }

        if (etTimeFrom != null) {
            etTimeFrom.setOnClickListener(v -> showTimePicker(etTimeFrom, true));
        }
        if (etTimeTo != null) {
            etTimeTo.setOnClickListener(v -> showTimePicker(etTimeTo, false));
        }

        if (chipDay != null) chipDay.setChecked(true);
    }

    private void showTimePicker(TextInputEditText target, boolean isFrom) {
        Calendar cal = Calendar.getInstance();
        new android.app.TimePickerDialog(this, (view, hourOfDay, minute) -> {
            String value = String.format(Locale.US, "%02d:%02d", hourOfDay, minute);
            target.setText(value);
            if (isFrom) timeFromHm = value;
            else timeToHm = value;
            applyLogFilter();
        }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show();
    }

    private void applyLogFilter() {
        List<Map<String, Object>> filtered =
                ActivityLogFilterHelper.filter(allLogs, currentFilter, currentDateYmd, timeFromHm, timeToHm);
        trailLogs.clear();
        authLogs.clear();
        for (Map<String, Object> log : filtered) {
            if (isAuthLog(log)) authLogs.add(log);
            else trailLogs.add(log);
        }
        trailAdapter.notifyDataSetChanged();
        authAdapter.notifyDataSetChanged();
    }

    private static boolean isAuthLog(Map<String, Object> log) {
        String action = log.get("action") != null ? String.valueOf(log.get("action")).toLowerCase(Locale.US) : "";
        return action.contains("password") || action.contains("login") || action.contains("logout") || action.contains("auth");
    }

    private static String formatDetails(Map<String, Object> log) {
        Object detail = log.get("detail");
        if (detail != null && !String.valueOf(detail).trim().isEmpty()) {
            return String.valueOf(detail);
        }
        Object detailsObj = log.get("details");
        if (detailsObj instanceof Map) {
            Map<?, ?> d = (Map<?, ?>) detailsObj;
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<?, ?> e : d.entrySet()) {
                if (e.getValue() == null) continue;
                if (sb.length() > 0) sb.append(" · ");
                sb.append(e.getKey()).append(": ").append(e.getValue());
            }
            if (sb.length() > 0) return sb.toString();
        }
        if (log.get("diseaseName") != null) {
            return "disease: " + log.get("diseaseName");
        }
        return "—";
    }

    private static String platformLabel(Map<String, Object> log) {
        Object platform = log.get("platform");
        if (platform != null) return String.valueOf(platform);
        Object source = log.get("_source");
        if ("scan_activity_logs".equals(source)) return "mobile";
        return "web";
    }

    private static String timeAgo(Map<String, Object> log) {
        long ms = ActivityLogFilterHelper.logTimestampMs(log);
        if (ms <= 0) return "—";
        long diff = System.currentTimeMillis() - ms;
        long min = diff / 60000;
        if (min < 1) return "just now";
        if (min < 60) return min + " minutes ago";
        long hr = min / 60;
        if (hr < 24) return hr + " hours ago";
        return new SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()).format(new Date(ms));
    }

    private class TrailAdapter extends RecyclerView.Adapter<TrailAdapter.H> {
        @NonNull @Override
        public H onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new H(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_activity_log_row, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull H h, int pos) {
            Map<String, Object> log = trailLogs.get(pos);
            String action = log.get("action") != null ? String.valueOf(log.get("action")) : "activity";
            h.tvAction.setText(action.replace(' ', '_').toLowerCase(Locale.US) + "  " + timeAgo(log));
            h.tvDetail.setText(formatDetails(log));
            h.tvTime.setText("User: " + (log.get("adminName") != null ? log.get("adminName") : "System")
                    + " · " + platformLabel(log));
        }

        @Override
        public int getItemCount() { return trailLogs.size(); }

        class H extends RecyclerView.ViewHolder {
            final TextView tvAction, tvDetail, tvTime;
            H(View v) {
                super(v);
                tvAction = v.findViewById(R.id.tvLogAction);
                tvDetail = v.findViewById(R.id.tvLogDetail);
                tvTime = v.findViewById(R.id.tvLogTime);
            }
        }
    }

    private class AuthAdapter extends RecyclerView.Adapter<AuthAdapter.H> {
        @NonNull @Override
        public H onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new H(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_auth_log_row, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull H h, int pos) {
            Map<String, Object> log = authLogs.get(pos);
            String action = log.get("action") != null ? String.valueOf(log.get("action")).toLowerCase(Locale.US) : "";
            if (action.contains("denied") || action.contains("fail")) {
                h.tvTitle.setText("Access denied");
                h.tvTitle.setTextColor(0xFFDC2626);
            } else if (action.contains("password")) {
                h.tvTitle.setText("Password updated");
                h.tvTitle.setTextColor(0xFF16A34A);
            } else {
                h.tvTitle.setText("Successful sign-in");
                h.tvTitle.setTextColor(0xFF16A34A);
            }
            h.tvTime.setText(timeAgo(log));
            String who = log.get("adminName") != null ? String.valueOf(log.get("adminName"))
                    : (log.get("adminEmail") != null ? String.valueOf(log.get("adminEmail")) : "Unknown");
            h.tvDetail.setText("User: " + who + " · " + platformLabel(log));
        }

        @Override
        public int getItemCount() { return authLogs.size(); }

        class H extends RecyclerView.ViewHolder {
            final TextView tvTitle, tvDetail, tvTime;
            H(View v) {
                super(v);
                tvTitle = v.findViewById(R.id.tvAuthTitle);
                tvDetail = v.findViewById(R.id.tvAuthDetail);
                tvTime = v.findViewById(R.id.tvAuthTime);
            }
        }
    }
}
