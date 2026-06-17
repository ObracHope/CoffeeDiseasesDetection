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
    private final boolean compactHorizontal;

    public HistoryAdapter(List<Map<String, Object>> historyList, OnItemClickListener listener) {
        this(historyList, listener, false);
    }

    public HistoryAdapter(List<Map<String, Object>> historyList, OnItemClickListener listener,
                          boolean compactHorizontal) {
        this.historyList = historyList;
        this.listener = listener;
        this.compactHorizontal = compactHorizontal;
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
        int layout = compactHorizontal ? R.layout.item_recent_scan_horizontal : R.layout.item_history;
        View view = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
        if (compactHorizontal) {
            float density = parent.getContext().getResources().getDisplayMetrics().density;
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(
                    (int) (140 * density), ViewGroup.LayoutParams.WRAP_CONTENT);
            view.setLayoutParams(lp);
        }
        return new ViewHolder(view, compactHorizontal);
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

        if (holder.compactHorizontal) {
            if (holder.tvScanDisease != null) {
                holder.tvScanDisease.setText(diseaseName != null ? diseaseName : "Unknown");
            }
            if (holder.tvScanConfidence != null) {
                holder.tvScanConfidence.setText(formatConfidence(confidenceObj));
                String key = DiseaseLabels.normalizeKey(diseaseName != null ? diseaseName : "Unknown");
                int color = holder.itemView.getContext().getColor(
                        "Healthy".equalsIgnoreCase(key) ? R.color.status_healthy : R.color.accentOrange);
                holder.tvScanConfidence.setTextColor(color);
            }
            if (holder.tvScanWhen != null) {
                holder.tvScanWhen.setText(formatWhenShort(timestampObj));
            }
            if (holder.ivScanThumb != null) {
                loadThumb(holder.ivScanThumb, data);
            }
        } else {
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
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onClick(data);
        });
    }

    private static String formatWhenShort(Object timestampObj) {
        Date date = null;
        if (timestampObj instanceof Timestamp) {
            date = ((Timestamp) timestampObj).toDate();
        } else if (timestampObj instanceof Long) {
            date = new Date((Long) timestampObj);
        }
        if (date == null) return "—";
        long diff = System.currentTimeMillis() - date.getTime();
        if (diff < 86_400_000L) return "Today";
        if (diff < 172_800_000L) return "Yesterday";
        return new SimpleDateFormat("dd MMM", Locale.getDefault()).format(date);
    }

    private static String emojiForDisease(String key) {
        if ("Healthy".equalsIgnoreCase(key)) return "🌿";
        if (key != null && key.toLowerCase(Locale.US).contains("rust")) return "🍂";
        if (key != null && key.toLowerCase(Locale.US).contains("berry")) return "🍒";
        return "🌱";
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
        final boolean compactHorizontal;
        TextView tvDiseaseName, tvDate, tvAccuracy;
        ImageView imgThumb;
        TextView tvScanEmoji, tvScanDisease, tvScanConfidence, tvScanWhen;
        ImageView ivScanThumb;

        ViewHolder(@NonNull View itemView, boolean compactHorizontal) {
            super(itemView);
            this.compactHorizontal = compactHorizontal;
            if (compactHorizontal) {
                tvScanEmoji = itemView.findViewById(R.id.tvScanEmoji);
                tvScanDisease = itemView.findViewById(R.id.tvScanDisease);
                tvScanConfidence = itemView.findViewById(R.id.tvScanConfidence);
                tvScanWhen = itemView.findViewById(R.id.tvScanWhen);
                ivScanThumb = itemView.findViewById(R.id.ivScanThumb);
            } else {
                tvDiseaseName = itemView.findViewById(R.id.tvHistoryDiseaseName);
                tvDate = itemView.findViewById(R.id.tvHistoryDate);
                tvAccuracy = itemView.findViewById(R.id.tvHistoryAccuracy);
                imgThumb = itemView.findViewById(R.id.imgHistoryThumb);
            }
        }
    }
}
