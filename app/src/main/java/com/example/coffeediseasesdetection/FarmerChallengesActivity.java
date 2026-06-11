package com.example.coffeediseasesdetection;

import android.os.Bundle;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class FarmerChallengesActivity extends BaseActivity {

    private final List<Map<String, Object>> challengesList = new ArrayList<>();
    private ChallengesAdapter adapter;
    private DatabaseReference databaseReference;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_farmer_challenges);
        setTitle("My Challenges");

        RecyclerView recyclerView = findViewById(R.id.recyclerFarmerChallenges);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ChallengesAdapter(challengesList);
        recyclerView.setAdapter(adapter);

        ImageView back = findViewById(R.id.ivBackButton);
        if (back != null) back.setOnClickListener(v -> finish());

        databaseReference = FirebaseDatabase.getInstance("https://coffee-diseases-detection-default-rtdb.firebaseio.com").getReference();
        loadMyChallenges();
    }

    private void loadMyChallenges() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        Query query = databaseReference.child("farmer_challenges")
                .orderByChild("userId")
                .equalTo(user.getUid());

        query.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                challengesList.clear();
                for (DataSnapshot child : snapshot.getChildren()) {
                    Map<String, Object> row = (Map<String, Object>) child.getValue();
                    if (row != null) {
                        row.put("_id", child.getKey());
                        challengesList.add(row);
                    }
                }
                Collections.sort(challengesList, (a, b) -> {
                    Long t1 = parseLong(a.get("timestamp"));
                    Long t2 = parseLong(b.get("timestamp"));
                    return Long.compare(t2, t1);
                });
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // no-op
            }
        });
    }

    private long parseLong(Object value) {
        if (value instanceof Long) return (Long) value;
        if (value instanceof Integer) return ((Integer) value).longValue();
        if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (Exception ignored) {
            }
        }
        return 0L;
    }
}
