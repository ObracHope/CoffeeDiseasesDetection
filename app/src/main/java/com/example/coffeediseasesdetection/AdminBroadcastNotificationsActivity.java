package com.example.coffeediseasesdetection;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.coffeediseasesdetection.admin.AdminRepository;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Admin notifications — list sent broadcasts + send new (Firestore notifications). */
public class AdminBroadcastNotificationsActivity extends BaseActivity {

    private final List<Map<String, Object>> notifications = new ArrayList<>();
    private final AdminRepository repository = new AdminRepository();
    private ListenerRegistration listener;
    private TextView tvEmpty;
    private NotifAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_broadcast_notifications);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        tvEmpty = findViewById(R.id.tvNoNotifications);
        RecyclerView rv = findViewById(R.id.rvNotifications);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new NotifAdapter();
        rv.setAdapter(adapter);

        MaterialButton btnNew = findViewById(R.id.btnNewBroadcast);
        if (btnNew != null) btnNew.setOnClickListener(v -> showSendDialog());

        listener = FirebaseFirestore.getInstance().collection("notifications")
                .addSnapshotListener((snap, error) -> {
                    if (isFinishing()) return;
                    notifications.clear();
                    if (snap != null) {
                        List<QueryDocumentSnapshot> docs = new ArrayList<>();
                        for (QueryDocumentSnapshot doc : snap) docs.add(doc);
                        docs.sort((a, b) -> Long.compare(parseMs(b), parseMs(a)));
                        for (QueryDocumentSnapshot doc : docs) {
                            Map<String, Object> row = doc.getData();
                            row.put("id", doc.getId());
                            notifications.add(row);
                        }
                    }
                    adapter.notifyDataSetChanged();
                    if (tvEmpty != null) {
                        tvEmpty.setVisibility(notifications.isEmpty() ? View.VISIBLE : View.GONE);
                    }
                });
    }

    private static long parseMs(QueryDocumentSnapshot doc) {
        Long ms = doc.getLong("createdAtMs");
        if (ms != null) return ms;
        if (doc.getTimestamp("createdAt") != null) {
            return doc.getTimestamp("createdAt").toDate().getTime();
        }
        return 0;
    }

    private void showSendDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_send_notification, null);
        TextInputEditText etTitle = dialogView.findViewById(R.id.etNotifyTitle);
        TextInputEditText etBody = dialogView.findViewById(R.id.etNotifyBody);

        new AlertDialog.Builder(this)
                .setTitle(R.string.admin_send_notification)
                .setView(dialogView)
                .setPositiveButton(R.string.send, (d, w) -> {
                    String title = etTitle != null && etTitle.getText() != null
                            ? etTitle.getText().toString().trim() : "";
                    String body = etBody != null && etBody.getText() != null
                            ? etBody.getText().toString().trim() : "";
                    if (title.isEmpty() || body.isEmpty()) {
                        Toast.makeText(this, R.string.fill_all_fields, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    repository.sendBroadcastNotification(title, body, () -> {
                        repository.logActivity(this, "notification_send", title);
                        runOnUiThread(() ->
                                Toast.makeText(this, R.string.admin_notification_sent,
                                        Toast.LENGTH_SHORT).show());
                    });
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    protected void onDestroy() {
        if (listener != null) {
            listener.remove();
            listener = null;
        }
        super.onDestroy();
    }

    private class NotifAdapter extends RecyclerView.Adapter<NotifAdapter.H> {
        private final SimpleDateFormat sdf =
                new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault());

        @NonNull
        @Override
        public H onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(android.R.layout.simple_list_item_2, parent, false);
            return new H(v);
        }

        @Override
        public void onBindViewHolder(@NonNull H h, int pos) {
            Map<String, Object> n = notifications.get(pos);
            String title = n.get("title") != null ? String.valueOf(n.get("title")) : "Notification";
            String body = n.get("body") != null ? String.valueOf(n.get("body")) : "";
            String audience = n.get("audience") != null ? String.valueOf(n.get("audience")) : "all";
            long ms = 0;
            Object created = n.get("createdAtMs");
            if (created instanceof Number) ms = ((Number) created).longValue();
            String date = ms > 0 ? sdf.format(new Date(ms)) : "";

            h.t1.setText(title);
            h.t2.setText(body + "\n" + audience + (date.isEmpty() ? "" : " · " + date));
        }

        @Override
        public int getItemCount() {
            return notifications.size();
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
