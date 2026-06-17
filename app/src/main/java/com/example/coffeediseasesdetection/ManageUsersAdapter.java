package com.example.coffeediseasesdetection;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ManageUsersAdapter extends RecyclerView.Adapter<ManageUsersAdapter.ViewHolder> {

    private final List<Map<String, Object>> users;
    private final String actorRole;
    private final OnRemoveListener onRemoveListener;
    private final OnEditListener onEditListener;
    private final OnResetListener onResetListener;

    public interface OnRemoveListener {
        void onRemove(String userId, String docId);
    }

    public interface OnEditListener {
        void onEdit(String userId, Map<String, Object> userData);
    }

    public interface OnResetListener {
        void onReset(String userId, Map<String, Object> userData);
    }

    public ManageUsersAdapter(List<Map<String, Object>> users,
                              String actorRole,
                              OnRemoveListener removeListener,
                              OnEditListener editListener,
                              OnResetListener resetListener) {
        this.users = users;
        this.actorRole = actorRole;
        this.onRemoveListener = removeListener;
        this.onEditListener = editListener;
        this.onResetListener = resetListener;
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
        String name = user.get("name") != null ? user.get("name").toString() : null;
        String email = user.get("email") != null ? user.get("email").toString() : null;
        String role = user.get("role") != null ? user.get("role").toString() : null;
        String uid = user.get("uid") != null ? user.get("uid").toString() : null;

        holder.tvName.setText(name != null ? name : "—");
        holder.tvEmail.setText(email != null ? email : "—");
        if (holder.tvUserRole != null) {
            holder.tvUserRole.setText(role != null
                    ? AuthHelper.displayRoleLabel(holder.itemView.getContext(), role) : "");
        }
        if (holder.tvUserInitial != null) {
            String initial = "U";
            if (name != null && !name.isEmpty()) {
                initial = name.substring(0, 1).toUpperCase(Locale.US);
            } else if (email != null && !email.isEmpty()) {
                initial = email.substring(0, 1).toUpperCase(Locale.US);
            }
            holder.tvUserInitial.setText(initial);
        }

        boolean canReset = AdminPasswordResetHelper.canResetTarget(actorRole, role);
        if (holder.btnResetPassword != null) {
            holder.btnResetPassword.setVisibility(canReset ? View.VISIBLE : View.GONE);
        }

        holder.btnEdit.setOnClickListener(v -> {
            if (uid != null && onEditListener != null) onEditListener.onEdit(uid, user);
        });
        holder.btnRemove.setOnClickListener(v -> {
            if (uid != null && onRemoveListener != null) onRemoveListener.onRemove(uid, uid);
        });
        if (holder.btnResetPassword != null) {
            holder.btnResetPassword.setOnClickListener(v -> {
                if (uid != null && onResetListener != null) onResetListener.onReset(uid, user);
            });
        }
    }

    @Override
    public int getItemCount() {
        return users.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName;
        TextView tvEmail;
        TextView tvUserInitial;
        TextView tvUserRole;
        Button btnEdit;
        Button btnRemove;
        Button btnResetPassword;

        ViewHolder(View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvUserName);
            tvEmail = itemView.findViewById(R.id.tvUserEmail);
            tvUserInitial = itemView.findViewById(R.id.tvUserInitial);
            tvUserRole = itemView.findViewById(R.id.tvUserRole);
            btnEdit = itemView.findViewById(R.id.btnEdit);
            btnRemove = itemView.findViewById(R.id.btnRemove);
            btnResetPassword = itemView.findViewById(R.id.btnResetPassword);
        }
    }
}
