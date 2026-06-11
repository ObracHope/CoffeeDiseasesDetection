package com.example.coffeediseasesdetection;

import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.firebase.Timestamp;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {

    public interface OnItemClickListener {
        void onClick(Map<String, Object> item);
    }

    private final List<Map<String, Object>> historyList;
    private final OnItemClickListener listener;

    public HistoryAdapter(List<Map<String, Object>> historyList, OnItemClickListener listener) {
        this.historyList = historyList;
        this.listener = listener;
    }

    /** Load scan thumbnail from Firebase URL or local path. */
    public static void loadThumb(@Nullable ImageView imageView, @Nullable Map<String, Object> data) {
        if (imageView == null) return;
        if (data == null) {
            imageView.setImageResource(R.drawable.ic_history_custom);
            return;
        }
        String imageUrl = data.get("imageUrl") != null ? data.get("imageUrl").toString() : null;
        String imagePath = data.get("imagePath") != null ? data.get("imagePath").toString() : null;

        if (imageUrl != null && !imageUrl.isEmpty()) {
            Glide.with(imageView.getContext()).load(imageUrl)
                    .centerCrop()
                    .placeholder(R.drawable.ic_history_custom)
                    .error(R.drawable.ic_history_custom)
                    .into(imageView);
        } else if (imagePath != null && !imagePath.isEmpty() && new File(imagePath).exists()) {
            Glide.with(imageView.getContext()).load(Uri.fromFile(new File(imagePath)))
                    .centerCrop()
                    .placeholder(R.drawable.ic_history_custom)
                    .into(imageView);
        } else {
            String disease = data.get("disease") != null ? data.get("disease").toString() : null;
            if (disease == null && data.get("diseaseName") != null) {
                disease = DiseaseLabels.normalizeKey(data.get("diseaseName").toString());
            }
            String key = DiseaseLabels.normalizeKey(disease != null ? disease : "Unknown");
            imageView.setImageResource(DiseaseDetector.getDrawableForDisease(key));
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_history, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Map<String, Object> data = historyList.get(position);
        String diseaseName = (String) data.get("diseaseName");
        if (diseaseName == null) {
            String raw = (String) data.get("disease");
            diseaseName = DiseaseTextProvider.displayName(holder.itemView.getContext(),
                    DiseaseLabels.normalizeKey(raw));
        }
        Object timestampObj = data.get("timestamp");
        Object confidenceObj = data.get("confidence");

        holder.tvDiseaseName.setText(diseaseName != null ? diseaseName : "Unknown");

        SimpleDateFormat sdfFull = new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault());
        if (timestampObj instanceof Timestamp) {
            holder.tvDate.setText(holder.itemView.getContext().getString(R.string.detected_on)
                    + sdfFull.format(((Timestamp) timestampObj).toDate()));
        } else if (timestampObj instanceof Long) {
            holder.tvDate.setText(holder.itemView.getContext().getString(R.string.detected_on)
                    + sdfFull.format(new Date((Long) timestampObj)));
        } else {
            holder.tvDate.setText(holder.itemView.getContext().getString(R.string.date_unknown));
        }

        holder.tvAccuracy.setText(formatConfidence(confidenceObj));
        loadThumb(holder.imgThumb, data);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onClick(data);
        });
    }

    @Override
    public int getItemCount() {
        return historyList.size();
    }

    private String formatConfidence(Object confidenceObj) {
        if (confidenceObj == null) return "--";
        if (confidenceObj instanceof String) {
            String value = (String) confidenceObj;
            return value.contains("%") ? value : value + "%";
        }
        if (confidenceObj instanceof Number) {
            return String.format(Locale.getDefault(), "%.1f%%", ((Number) confidenceObj).floatValue());
        }
        return "--";
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvDiseaseName, tvDate, tvAccuracy;
        ImageView imgThumb;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDiseaseName = itemView.findViewById(R.id.tvHistoryDiseaseName);
            tvDate = itemView.findViewById(R.id.tvHistoryDate);
            tvAccuracy = itemView.findViewById(R.id.tvHistoryAccuracy);
            imgThumb = itemView.findViewById(R.id.imgHistoryThumb);
        }
    }
}
