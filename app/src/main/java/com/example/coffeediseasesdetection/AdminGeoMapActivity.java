package com.example.coffeediseasesdetection;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.coffeediseasesdetection.admin.AdminRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Lists farmers with GPS or region data; tap to open in maps app. */
public class AdminGeoMapActivity extends BaseActivity {

    private final List<Map<String, Object>> farmers = new ArrayList<>();
    private TextView tvEmpty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_geo_map);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        tvEmpty = findViewById(R.id.tvEmptyFarmers);

        RecyclerView rv = findViewById(R.id.rvFarmersMap);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(new FarmerLocationAdapter());

        new AdminRepository().loadFarmersWithLocation(new AdminRepository.ScansCallback() {
            @Override
            public void onSuccess(List<Map<String, Object>> list) {
                if (isFinishing()) return;
                farmers.clear();
                farmers.addAll(list);
                rv.getAdapter().notifyDataSetChanged();
                updateEmptyState();
            }

            @Override
            public void onError(Exception e) {
                if (!isFinishing()) {
                    android.widget.Toast.makeText(AdminGeoMapActivity.this,
                            R.string.error_loading_data, android.widget.Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void updateEmptyState() {
        if (tvEmpty != null) {
            tvEmpty.setVisibility(farmers.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    private static String buildRegionLine(Map<String, Object> f) {
        String region = f.get("region") != null ? String.valueOf(f.get("region")).trim() : "";
        String district = f.get("district") != null ? String.valueOf(f.get("district")).trim() : "";
        String ward = f.get("ward") != null ? String.valueOf(f.get("ward")).trim() : "";
        StringBuilder sb = new StringBuilder();
        if (!region.isEmpty()) sb.append(region);
        if (!district.isEmpty()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(district);
        }
        if (!ward.isEmpty()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(ward);
        }
        return sb.toString();
    }

    private class FarmerLocationAdapter extends RecyclerView.Adapter<FarmerLocationAdapter.H> {
        @NonNull
        @Override
        public H onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(android.R.layout.simple_list_item_2, parent, false);
            return new H(v);
        }

        @Override
        public void onBindViewHolder(@NonNull H h, int pos) {
            Map<String, Object> f = farmers.get(pos);
            String name = f.get("name") != null ? String.valueOf(f.get("name")) : "Farmer";
            Double lat = AdminRepository.parseDouble(f.get("lastLatitude"));
            Double lng = AdminRepository.parseDouble(f.get("lastLongitude"));
            boolean hasGps = Boolean.TRUE.equals(f.get("hasGps"))
                    || (lat != null && lng != null && !(lat == 0.0 && lng == 0.0));
            String regionLine = buildRegionLine(f);

            h.t1.setText(name);
            if (hasGps && lat != null && lng != null) {
                h.t2.setText(getString(R.string.admin_farmer_gps_region_line, lat, lng, regionLine));
            } else {
                h.t2.setText(getString(R.string.admin_farmer_region_line, regionLine));
            }

            h.itemView.setOnClickListener(v -> {
                if (hasGps && lat != null && lng != null) {
                    Uri uri = Uri.parse("geo:" + lat + "," + lng + "?q=" + lat + "," + lng);
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                } else if (!regionLine.isEmpty()) {
                    Uri uri = Uri.parse("geo:0,0?q=" + Uri.encode(regionLine + ", Tanzania"));
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                }
            });
        }

        @Override
        public int getItemCount() {
            return farmers.size();
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
