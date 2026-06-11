package com.example.coffeediseasesdetection;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.example.coffeediseasesdetection.admin.AdminRepository;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AdminReviewImagesActivity extends BaseActivity {

    private final List<Map<String, Object>> images = new ArrayList<>();
    private ImageGridAdapter gridAdapter;
    private TextView tvEmpty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_review_images);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        tvEmpty = findViewById(R.id.tvEmptyImages);
        GridView grid = findViewById(R.id.gridReviewImages);
        gridAdapter = new ImageGridAdapter();
        grid.setAdapter(gridAdapter);
        grid.setOnItemClickListener((parent, view, position, id) -> {
            Object scanId = images.get(position).get("id");
            if (scanId != null) {
                Intent i = new Intent(this, ScanDetailActivity.class);
                i.putExtra("scanId", scanId.toString());
                startActivity(i);
            }
        });

        new AdminRepository().loadScansWithImages(200, new AdminRepository.ScansCallback() {
            @Override
            public void onSuccess(List<Map<String, Object>> list) {
                if (isFinishing()) return;
                images.clear();
                images.addAll(list);
                gridAdapter.notifyDataSetChanged();
                updateEmptyState();
            }

            @Override
            public void onError(Exception e) {
                android.widget.Toast.makeText(AdminReviewImagesActivity.this,
                        R.string.error_loading_data, android.widget.Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateEmptyState() {
        if (tvEmpty != null) {
            tvEmpty.setVisibility(images.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    private class ImageGridAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return images.size();
        }

        @Override
        public Object getItem(int position) {
            return images.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ImageView iv;
            if (convertView instanceof ImageView) {
                iv = (ImageView) convertView;
            } else {
                iv = new ImageView(parent.getContext());
                iv.setLayoutParams(new GridView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 280));
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            }

            Map<String, Object> row = images.get(position);
            String url = row.get("imageUrl") != null ? String.valueOf(row.get("imageUrl")) : "";
            String path = row.get("imagePath") != null ? String.valueOf(row.get("imagePath")) : "";

            if (!url.isEmpty()) {
                Glide.with(AdminReviewImagesActivity.this).load(url)
                        .placeholder(R.drawable.bg_image_placeholder)
                        .error(R.drawable.bg_image_placeholder)
                        .centerCrop()
                        .into(iv);
            } else if (!path.isEmpty() && new File(path).exists()) {
                Glide.with(AdminReviewImagesActivity.this).load(Uri.fromFile(new File(path)))
                        .placeholder(R.drawable.bg_image_placeholder)
                        .centerCrop()
                        .into(iv);
            } else {
                String disease = row.get("disease") != null ? row.get("disease").toString() : "Unknown";
                iv.setImageResource(DiseaseDetector.getDrawableForDisease(
                        DiseaseLabels.normalizeKey(disease)));
            }
            return iv;
        }
    }
}
