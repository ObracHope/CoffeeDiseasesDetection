package com.example.coffeediseasesdetection;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.ListenerRegistration;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class NotificationsActivity extends BaseActivity {

    private RecyclerView rvNotifications;
    private NotificationDbHelper dbHelper;
    private TextView tvNoNotifications;
    private final List<NotificationModel> displayList = new ArrayList<>();
    private NotificationsAdapter adapter;
    private ListenerRegistration scanListener;
    private static final String PREFS_REPLY_SYNC = "reply_sync_prefs";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notifications);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnMarkAllRead).setOnClickListener(v -> {
            dbHelper.markAllRead();
            refreshUi();
            android.widget.Toast.makeText(this, R.string.all_notifications_read, Toast.LENGTH_SHORT).show();
        });

        rvNotifications = findViewById(R.id.rvNotifications);
        tvNoNotifications = findViewById(R.id.tvNoNotifications);
        dbHelper = new NotificationDbHelper(this);

        adapter = new NotificationsAdapter(displayList, item -> {
            if (item.getScanId() != null && !item.getScanId().isEmpty()) {
                Intent i = new Intent(this, ScanDetailActivity.class);
                i.putExtra("scanId", item.getScanId());
                startActivity(i);
            }
        });
        rvNotifications.setLayoutManager(new LinearLayoutManager(this));
        rvNotifications.setAdapter(adapter);

        syncAdminReplies();
        loadScanNotifications();
        mergeLocalNotifications();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (scanListener != null) {
            scanListener.remove();
            scanListener = null;
        }
    }

    private void syncAdminReplies() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        DatabaseReference db = FirebaseDatabase
                .getInstance("https://coffee-diseases-detection-default-rtdb.firebaseio.com")
                .getReference();
        Query query = db.child("farmer_challenges").orderByChild("userId").equalTo(user.getUid());
        SharedPreferences prefs = getSharedPreferences(PREFS_REPLY_SYNC, MODE_PRIVATE);

        query.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                boolean hasNewItems = false;
                for (DataSnapshot child : snapshot.getChildren()) {
                    String challengeId = child.getKey();
                    if (challengeId == null) continue;
                    String adminReply = child.child("adminReply").getValue(String.class);
                    if (adminReply == null || adminReply.trim().isEmpty()) continue;
                    long replyAt = 0L;
                    Long replyTs = child.child("adminReplyAt").getValue(Long.class);
                    if (replyTs != null) replyAt = replyTs;
                    if (replyAt == 0L) {
                        Long ts = child.child("timestamp").getValue(Long.class);
                        if (ts != null) replyAt = ts;
                    }
                    long seen = prefs.getLong(challengeId, 0L);
                    if (replyAt > seen) {
                        String title = child.child("title").getValue(String.class);
                        if (title == null || title.trim().isEmpty()) title = "Farmer Challenge";
                        dbHelper.insertNotification("Admin Reply: " + title, adminReply);
                        prefs.edit().putLong(challengeId, replyAt).apply();
                        hasNewItems = true;
                    }
                }
                if (hasNewItems) mergeLocalNotifications();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // no-op
            }
        });
    }

    private void loadScanNotifications() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            refreshUi();
            return;
        }

        scanListener = ScanHistoryLoader.listen(user, new ScanHistoryLoader.Callback() {
            @Override
            public void onLoaded(List<Map<String, Object>> scans) {
                displayList.clear();
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

                for (Map<String, Object> row : scans) {
                    String raw = (String) row.get("disease");
                    if (raw == null) raw = (String) row.get("diseaseName");
                    String key = DiseaseLabels.normalizeKey(raw);
                    if (!DiseaseLabels.isDiseaseFound(key)) continue;

                    String diseaseName = (String) row.get("diseaseName");
                    if (diseaseName == null) {
                        diseaseName = DiseaseTextProvider.displayName(NotificationsActivity.this, key);
                    }
                    Object conf = row.get("confidence");
                    String confStr = conf instanceof Number
                            ? String.format(Locale.getDefault(), "%.1f%%", ((Number) conf).floatValue())
                            : (conf != null ? conf.toString() : "--");

                    String tsText = formatTimestamp(row.get("timestamp"), sdf);
                    String scanId = row.get("id") != null ? row.get("id").toString() : "";
                    String imageUrl = (String) row.get("imageUrl");
                    String imagePath = (String) row.get("imagePath");

                    String message = getString(R.string.notif_scan_message, diseaseName, confStr);
                    displayList.add(new NotificationModel(
                            getString(R.string.disease_detected_title),
                            message,
                            tsText,
                            scanId,
                            imageUrl,
                            imagePath,
                            diseaseName,
                            confStr,
                            true
                    ));
                }

                mergeLocalNotifications();
            }

            @Override
            public void onError(Exception e) {
                mergeLocalNotifications();
            }
        });
    }

    private void mergeLocalNotifications() {
        List<NotificationModel> local = dbHelper.getAllNotifications();
        for (NotificationModel n : local) {
            boolean duplicate = false;
            if (n.getScanId() != null) {
                for (NotificationModel existing : displayList) {
                    if (n.getScanId().equals(existing.getScanId())) {
                        duplicate = true;
                        break;
                    }
                }
            }
            if (!duplicate) displayList.add(n);
        }

        Collections.sort(displayList, (a, b) -> b.getTimestamp().compareTo(a.getTimestamp()));
        refreshUi();
    }

    private String formatTimestamp(Object ts, SimpleDateFormat sdf) {
        if (ts instanceof Timestamp) return sdf.format(((Timestamp) ts).toDate());
        if (ts instanceof Long) return sdf.format(new Date((Long) ts));
        return sdf.format(new Date());
    }

    private void refreshUi() {
        View emptyLayout = findViewById(R.id.layoutNoNotifications);
        TextView tvDate = findViewById(R.id.tvNotifPanelDate);
        TextView tvGreeting = findViewById(R.id.tvNotifGreeting);
        if (tvDate != null) {
            tvDate.setText(new SimpleDateFormat("EEEE, MMMM d yyyy", Locale.US)
                    .format(new Date()).toUpperCase(Locale.US));
        }
        if (tvGreeting != null) {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            String name = prefs.getString(KEY_NAME, "System");
            if (name == null || name.trim().isEmpty()) name = "System";
            tvGreeting.setText("Hello, " + name.split(" ")[0]);
        }

        if (displayList.isEmpty()) {
            if (emptyLayout != null) emptyLayout.setVisibility(View.VISIBLE);
            if (tvNoNotifications != null) tvNoNotifications.setVisibility(View.VISIBLE);
            rvNotifications.setVisibility(View.GONE);
        } else {
            if (emptyLayout != null) emptyLayout.setVisibility(View.GONE);
            rvNotifications.setVisibility(View.VISIBLE);
            adapter.notifyDataSetChanged();
        }
    }

    private static class NotificationsAdapter extends RecyclerView.Adapter<NotificationsAdapter.ViewHolder> {
        private final List<NotificationModel> notifications;
        private final OnNotifClick listener;

        interface OnNotifClick { void onClick(NotificationModel item); }

        NotificationsAdapter(List<NotificationModel> notifications, OnNotifClick listener) {
            this.notifications = notifications;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_notification, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            NotificationModel item = notifications.get(position);
            holder.tvTitle.setText(item.getTitle());
            holder.tvMessage.setText(item.getMessage());
            holder.tvDate.setText(item.getTimestamp());

            if (item.getDiseaseName() != null && item.getConfidence() != null) {
                holder.tvResult.setVisibility(View.VISIBLE);
                holder.tvResult.setText(holder.itemView.getContext().getString(
                        R.string.scan_result_line, item.getDiseaseName(), item.getConfidence()));
            } else {
                holder.tvResult.setVisibility(View.GONE);
            }

            HistoryAdapter.loadThumb(holder.ivImage, toMap(item));
            holder.itemView.setOnClickListener(v -> {
                if (listener != null) listener.onClick(item);
            });
        }

        private static Map<String, Object> toMap(NotificationModel item) {
            java.util.HashMap<String, Object> m = new java.util.HashMap<>();
            m.put("imageUrl", item.getImageUrl());
            m.put("imagePath", item.getImagePath());
            return m;
        }

        @Override
        public int getItemCount() {
            return notifications.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvTitle, tvMessage, tvDate, tvResult;
            ImageView ivImage;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvTitle = itemView.findViewById(R.id.tvNotifTitle);
                tvMessage = itemView.findViewById(R.id.tvNotifMessage);
                tvDate = itemView.findViewById(R.id.tvNotifDate);
                tvResult = itemView.findViewById(R.id.tvNotifResult);
                ivImage = itemView.findViewById(R.id.ivNotifImage);
            }
        }
    }
}
