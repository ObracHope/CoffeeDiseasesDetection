package com.example.coffeediseasesdetection;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.Timestamp;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ChallengesAdapter extends RecyclerView.Adapter<ChallengesAdapter.ViewHolder> {

    public interface OnReplyClickListener {
        void onReply(Map<String, Object> challenge);
    }

    private final List<Map<String, Object>> challenges;
    private final OnReplyClickListener replyListener;

    public ChallengesAdapter(List<Map<String, Object>> challenges) {
        this(challenges, null);
    }

    public ChallengesAdapter(List<Map<String, Object>> challenges, OnReplyClickListener replyListener) {
        this.challenges = challenges;
        this.replyListener = replyListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_challenge, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int pos) {
        Map<String, Object> c = challenges.get(pos);
        h.tvTitle.setText(c.get("title") != null ? String.valueOf(c.get("title")) : "");
        h.tvCategory.setText(c.get("category") != null ? String.valueOf(c.get("category")) : "");
        h.tvDescription.setText(c.get("description") != null ? String.valueOf(c.get("description")) : "");
        String status = c.get("status") != null ? String.valueOf(c.get("status")) : "pending";
        h.tvStatus.setText(h.itemView.getContext().getString(R.string.challenge_status, status));

        String reply = c.get("adminReply") != null ? String.valueOf(c.get("adminReply")) : "";
        if (!reply.trim().isEmpty()) {
            h.tvReply.setVisibility(View.VISIBLE);
            h.tvReply.setText(h.itemView.getContext().getString(R.string.admin_reply, reply));
        } else {
            h.tvReply.setVisibility(View.GONE);
        }

        Object ts = c.get("timestamp");
        if (ts instanceof Timestamp) {
            h.tvDate.setText(new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
                    .format(((Timestamp) ts).toDate()));
        } else if (ts instanceof Long) {
            h.tvDate.setText(new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
                    .format(new Date((Long) ts)));
        } else {
            h.tvDate.setText("");
        }

        h.itemView.setOnClickListener(v -> {
            if (replyListener != null) replyListener.onReply(c);
        });
    }

    @Override
    public int getItemCount() {
        return challenges.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvCategory, tvDescription, tvDate, tvReply, tvStatus;

        ViewHolder(View v) {
            super(v);
            tvTitle = v.findViewById(R.id.tvChallengeTitle);
            tvCategory = v.findViewById(R.id.tvChallengeCategory);
            tvDescription = v.findViewById(R.id.tvChallengeDescription);
            tvDate = v.findViewById(R.id.tvChallengeDate);
            tvReply = v.findViewById(R.id.tvChallengeReply);
            tvStatus = v.findViewById(R.id.tvChallengeStatus);
        }
    }
}
