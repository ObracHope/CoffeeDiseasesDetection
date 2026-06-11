package com.example.coffeediseasesdetection;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.coffeediseasesdetection.admin.AdminRepository;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class AdminChallengesActivity extends BaseActivity {

    private DatabaseReference mDatabase;
    private RecyclerView recyclerView;
    private ChallengesAdapter adapter;
    private final List<Map<String, Object>> challengesList = new ArrayList<>();
    private String currentCategory = "All";
    private final AdminRepository repository = new AdminRepository();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_challenges);
        setTitle(R.string.view_challenges);

        mDatabase = FirebaseDatabase.getInstance(AuthHelper.RTDB_URL).getReference();

        findViewById(R.id.ivBack).setOnClickListener(v -> finish());
        View fab = findViewById(R.id.fabRefresh);
        if (fab != null) fab.setOnClickListener(v -> loadChallenges());

        recyclerView = findViewById(R.id.recyclerChallenges);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ChallengesAdapter(challengesList, this::showReplyDialog);
        recyclerView.setAdapter(adapter);

        Spinner spinnerSort = findViewById(R.id.spinnerSortCategory);
        String[] categories = {"All", "Technical", "App Issue", "Detection Problem", "Other"};
        ArrayAdapter<String> spinAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, categories);
        spinAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerSort.setAdapter(spinAdapter);
        spinnerSort.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                currentCategory = categories[position];
                loadChallenges();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                loadChallenges();
            }
        });

        loadChallenges();
    }

    private void showReplyDialog(Map<String, Object> challenge) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_admin_reply, null);
        TextInputEditText etReply = dialogView.findViewById(R.id.etAdminReply);
        String existing = challenge.get("adminReply") != null
                ? String.valueOf(challenge.get("adminReply")) : "";
        if (!existing.isEmpty()) etReply.setText(existing);

        new AlertDialog.Builder(this)
                .setTitle(R.string.admin_reply_challenge)
                .setView(dialogView)
                .setPositiveButton(R.string.send, (d, w) -> {
                    String reply = etReply.getText() != null
                            ? etReply.getText().toString().trim() : "";
                    if (TextUtils.isEmpty(reply)) {
                        Toast.makeText(this, R.string.admin_reply_required, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String id = challenge.get("_id") != null ? String.valueOf(challenge.get("_id")) : null;
                    if (id == null) return;
                    repository.replyToChallenge(id, reply, () -> {
                        repository.logActivity(AdminChallengesActivity.this,
                                "challenge_reply", "Replied to challenge " + id);
                        Toast.makeText(AdminChallengesActivity.this,
                                R.string.admin_reply_sent, Toast.LENGTH_SHORT).show();
                        loadChallenges();
                    });
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void loadChallenges() {
        mDatabase.child("farmer_challenges").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                challengesList.clear();
                for (DataSnapshot doc : snapshot.getChildren()) {
                    Map<String, Object> m = (Map<String, Object>) doc.getValue();
                    if (m != null) {
                        m.put("_id", doc.getKey());
                        String category = (String) m.get("category");
                        if ("All".equals(currentCategory) || currentCategory.equals(category)) {
                            challengesList.add(m);
                        }
                    }
                }
                Collections.sort(challengesList, (o1, o2) -> {
                    Long t1 = o1.get("timestamp") instanceof Long ? (Long) o1.get("timestamp") : 0L;
                    Long t2 = o2.get("timestamp") instanceof Long ? (Long) o2.get("timestamp") : 0L;
                    return t2.compareTo(t1);
                });
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        });
    }
}
