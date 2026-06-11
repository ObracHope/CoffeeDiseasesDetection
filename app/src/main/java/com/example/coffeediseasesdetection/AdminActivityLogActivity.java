package com.example.coffeediseasesdetection;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.coffeediseasesdetection.admin.AdminRepository;
import com.google.firebase.Timestamp;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AdminActivityLogActivity extends BaseActivity {

    private final List<Map<String, Object>> logs = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_activity_log);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        RecyclerView rv = findViewById(R.id.rvActivityLog);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(new LogAdapter());

        new AdminRepository().loadActivityLogs(new AdminRepository.ScansCallback() {
            @Override
            public void onSuccess(List<Map<String, Object>> list) {
                if (isFinishing()) return;
                logs.clear();
                logs.addAll(list);
                rv.getAdapter().notifyDataSetChanged();
            }

            @Override
            public void onError(Exception e) {
                new AdminRepository().logActivity(AdminActivityLogActivity.this,
                        "view_logs", "Opened activity log");
            }
        });
    }

    private class LogAdapter extends RecyclerView.Adapter<LogAdapter.H> {
        @NonNull
        @Override
        public H onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(android.R.layout.simple_list_item_2, parent, false);
            return new H(v);
        }

        @Override
        public void onBindViewHolder(@NonNull H h, int pos) {
            Map<String, Object> log = logs.get(pos);
            String action = log.get("action") != null ? String.valueOf(log.get("action")) : "";
            String detail = log.get("detail") != null ? String.valueOf(log.get("detail")) : "";
            h.t1.setText(action);
            h.t2.setText(detail);
            Object ts = log.get("timestamp");
            if (ts instanceof Timestamp) {
                h.t2.append("\n" + new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
                        .format(((Timestamp) ts).toDate()));
            }
        }

        @Override
        public int getItemCount() {
            return logs.size();
        }

        class H extends RecyclerView.ViewHolder {
            TextView t1, t2;

            H(View v) {
                super(v);
                t1 = v.findViewById(android.R.id.text1);
                t2 = v.findViewById(android.R.id.text2);
            }
        }
    }
}
