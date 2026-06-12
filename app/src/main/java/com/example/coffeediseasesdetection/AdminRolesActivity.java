package com.example.coffeediseasesdetection;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Read-only list of admin accounts (mirrors web Admin Roles section). */
public class AdminRolesActivity extends BaseActivity {

    private final List<Map<String, Object>> admins = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_roles);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        TextView tvEmpty = findViewById(R.id.tvNoAdmins);
        RecyclerView rv = findViewById(R.id.rvAdmins);
        rv.setLayoutManager(new LinearLayoutManager(this));
        AdminAdapter adapter = new AdminAdapter();
        rv.setAdapter(adapter);

        FirebaseFirestore.getInstance().collection("users").get()
                .addOnSuccessListener(snap -> {
                    if (isFinishing()) return;
                    admins.clear();
                    for (QueryDocumentSnapshot doc : snap) {
                        String role = doc.getString("role");
                        if (role == null) continue;
                        String r = role.toLowerCase(Locale.US);
                        if (r.equals("admin") || r.equals("main")
                                || r.equals("superadmin") || r.equals("super_admin")) {
                            Map<String, Object> row = doc.getData();
                            row.put("id", doc.getId());
                            admins.add(row);
                        }
                    }
                    adapter.notifyDataSetChanged();
                    if (tvEmpty != null) {
                        tvEmpty.setVisibility(admins.isEmpty() ? View.VISIBLE : View.GONE);
                    }
                });
    }

    private class AdminAdapter extends RecyclerView.Adapter<AdminAdapter.H> {
        @NonNull
        @Override
        public H onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(android.R.layout.simple_list_item_2, parent, false);
            return new H(v);
        }

        @Override
        public void onBindViewHolder(@NonNull H h, int pos) {
            Map<String, Object> a = admins.get(pos);
            String name = a.get("name") != null ? String.valueOf(a.get("name")) : "Admin";
            String first = a.get("firstName") != null ? String.valueOf(a.get("firstName")) : "";
            String last = a.get("lastName") != null ? String.valueOf(a.get("lastName")) : "";
            if ("Admin".equals(name) && (!first.isEmpty() || !last.isEmpty())) {
                name = (first + " " + last).trim();
            }
            String email = a.get("email") != null ? String.valueOf(a.get("email")) : "";
            String role = a.get("role") != null ? String.valueOf(a.get("role")) : "admin";
            String uid = a.get("id") != null ? String.valueOf(a.get("id")) : "";
            h.t1.setText(name);
            h.t2.setText(email + "\nRole: " + role + " · UID: " + uid.substring(0, Math.min(8, uid.length())) + "…");
        }

        @Override
        public int getItemCount() {
            return admins.size();
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
