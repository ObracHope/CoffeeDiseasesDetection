package com.example.coffeediseasesdetection;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Map;

public class ManageUsersAdapter extends RecyclerView.Adapter<ManageUsersAdapter.ViewHolder> {

    private final List<Map<String, Object>> users;
    private final OnRemoveListener onRemoveListener;
    private final OnEditListener onEditListener;

    public interface OnRemoveListener {
        void onRemove(String userId, String docId);
    }

    public interface OnEditListener {
        void onEdit(String userId, Map<String, Object> userData);
    }

    public ManageUsersAdapter(List<Map<String, Object>> users, OnRemoveListener listener, OnEditListener editListener) {
        this.users = users;
        this.onRemoveListener = listener;
        this.onEditListener = editListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_manage_user, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Map<String, Object> user = users.get(position);
        String name = (String) user.get("name");
        String email = (String) user.get("email");
        String role = (String) user.get("role");
        String uid = (String) user.get("uid");

        holder.tvName.setText(name != null ? name : "—");
        holder.tvEmail.setText(email != null ? email + (role != null ? " (" + role + ")" : "") : "—");

        holder.btnEdit.setOnClickListener(v -> {
            if (uid != null && onEditListener != null) {
                onEditListener.onEdit(uid, user);
            }
        });
        holder.btnRemove.setOnClickListener(v -> {
            if (uid != null && onRemoveListener != null) {
                onRemoveListener.onRemove(uid, uid);
            }
        });
    }

    @Override
    public int getItemCount() {
        return users.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName;
        TextView tvEmail;
        Button btnEdit;
        Button btnRemove;

        ViewHolder(View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvUserName);
            tvEmail = itemView.findViewById(R.id.tvUserEmail);
            btnEdit = itemView.findViewById(R.id.btnEdit);
            btnRemove = itemView.findViewById(R.id.btnRemove);
        }
    }
}
