package com.example.coffeediseasesdetection;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AdminManageFarmersActivity extends BaseActivity {

    private FirebaseFirestore firestore;
    private final List<Map<String, Object>> usersList = new ArrayList<>();
    private final List<Map<String, Object>> filteredList = new ArrayList<>();
    private ManageUsersAdapter adapter;
    private String searchQuery = "";
    private String actorRole = "admin";

    private final ActivityResultLauncher<Intent> registerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) loadUsers();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_users);
        setTitle(R.string.manage_farmers);

        firestore = FirebaseFirestore.getInstance();
        actorRole = AuthHelper.normalizeRole(
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(KEY_ROLE, "admin"));

        FloatingActionButton btnAddUser = findViewById(R.id.btnAddUser);
        RecyclerView recyclerUsers = findViewById(R.id.recyclerUsers);

        if (btnAddUser != null) {
            btnAddUser.setOnClickListener(v -> openRegisterUser(null));
        }

        recyclerUsers.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ManageUsersAdapter(filteredList, actorRole,
                this::confirmRemoveUser,
                this::openEditUser,
                this::showResetPasswordDialog);
        recyclerUsers.setAdapter(adapter);

        TextInputEditText etSearch = findViewById(R.id.etSearchFarmers);
        if (etSearch != null) {
            etSearch.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
                @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                    searchQuery = s != null ? s.toString().trim().toLowerCase(Locale.US) : "";
                    applyFilter();
                }
                @Override public void afterTextChanged(Editable s) {}
            });
        }

        loadUsers();
    }

    private void openRegisterUser(String userId) {
        Intent intent = new Intent(this, AdminRegisterUserActivity.class);
        if (!TextUtils.isEmpty(userId)) {
            intent.putExtra(AdminRegisterUserActivity.EXTRA_USER_ID, userId);
            intent.putExtra(AdminRegisterUserActivity.EXTRA_EDIT_MODE, true);
        }
        registerLauncher.launch(intent);
    }

    private void openEditUser(String userId, Map<String, Object> userData) {
        openRegisterUser(userId);
    }

    private void loadUsers() {
        usersList.clear();
        firestore.collection("users").get()
                .addOnSuccessListener(snapshots -> {
                    for (QueryDocumentSnapshot doc : snapshots) {
                        Map<String, Object> m = new HashMap<>(doc.getData());
                        m.put("_id", doc.getId());
                        if (m.get("uid") == null) m.put("uid", doc.getId());
                        usersList.add(m);
                    }
                    applyFilter();
                });
    }

    private void applyFilter() {
        filteredList.clear();
        for (Map<String, Object> u : usersList) {
            if (searchQuery.isEmpty() || matchesUser(u, searchQuery)) {
                filteredList.add(u);
            }
        }
        adapter.notifyDataSetChanged();
    }

    private static boolean matchesUser(Map<String, Object> u, String q) {
        return PhoneSearchHelper.mapMatchesSearch(u, q,
                "name", "firstName", "middleName", "lastName", "gender",
                "email", "phone", "phoneNumber", "username", "role",
                "region", "location", "district");
    }

    private void confirmRemoveUser(String userId, String docId) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.remove)
                .setMessage("Remove this user from the system? Their Firestore profile will be deleted.")
                .setPositiveButton(android.R.string.ok, (d, w) -> removeUser(userId))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void removeUser(String userId) {
        firestore.collection("users").document(userId).delete()
                .addOnSuccessListener(v -> {
                    AuthHelper.usersRtdb().child(userId).removeValue();
                    Toast.makeText(this, getString(R.string.user_removed), Toast.LENGTH_SHORT).show();
                    loadUsers();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to remove", Toast.LENGTH_SHORT).show());
    }

    private void showResetPasswordDialog(String userId, Map<String, Object> userData) {
        String targetRole = userData.get("role") != null ? userData.get("role").toString() : "farmer";
        if (!AdminPasswordResetHelper.canResetTarget(actorRole, targetRole)) {
            Toast.makeText(this, R.string.reset_password_not_allowed, Toast.LENGTH_LONG).show();
            return;
        }

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_admin_reset_password, null);
        TextView tvName = dialogView.findViewById(R.id.tvResetUserName);
        TextView tvEmail = dialogView.findViewById(R.id.tvResetUserEmail);
        TextInputEditText etOld = dialogView.findViewById(R.id.etOldPassword);
        TextInputEditText etNew = dialogView.findViewById(R.id.etNewPassword);
        TextInputEditText etConfirm = dialogView.findViewById(R.id.etConfirmPassword);
        TextView tvOldHint = dialogView.findViewById(R.id.tvOldPasswordHint);
        MaterialButton btnCancel = dialogView.findViewById(R.id.btnCancelReset);
        MaterialButton btnConfirm = dialogView.findViewById(R.id.btnConfirmReset);

        String name = userData.get("name") != null ? userData.get("name").toString() : "User";
        String email = userData.get("email") != null ? userData.get("email").toString() : "—";
        tvName.setText(getString(R.string.reset_user_label, name));
        tvEmail.setText(getString(R.string.reset_email_label, email));

        String storedOld = AdminPasswordResetHelper.getStoredOldPassword(userData);
        if (!TextUtils.isEmpty(storedOld)) {
            etOld.setText(storedOld);
        } else {
            etOld.setText("********");
        }
        if (tvOldHint != null) tvOldHint.setVisibility(View.GONE);

        String firstName = AdminPasswordResetHelper.extractFirstName(userData);
        String defaultPwd = AdminPasswordResetHelper.generateDefaultPassword(firstName);
        etNew.setText(defaultPwd);
        etConfirm.setText(defaultPwd);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnConfirm.setOnClickListener(v -> {
            String newPwd = etNew.getText() != null ? etNew.getText().toString() : "";
            String confirmPwd = etConfirm.getText() != null ? etConfirm.getText().toString() : "";
            if (TextUtils.isEmpty(newPwd) || newPwd.length() < 6) {
                Toast.makeText(this, R.string.password_min, Toast.LENGTH_SHORT).show();
                return;
            }
            if (!newPwd.equals(confirmPwd)) {
                Toast.makeText(this, R.string.passwords_do_not_match, Toast.LENGTH_SHORT).show();
                return;
            }
            btnConfirm.setEnabled(false);
            AdminPasswordResetHelper.resetPassword(this, userId, newPwd,
                    new AdminPasswordResetHelper.ResetCallback() {
                        @Override
                        public void onSuccess(String message) {
                            btnConfirm.setEnabled(true);
                            dialog.dismiss();
                            Toast.makeText(AdminManageFarmersActivity.this, message, Toast.LENGTH_LONG).show();
                            loadUsers();
                        }

                        @Override
                        public void onError(String message) {
                            btnConfirm.setEnabled(true);
                            Toast.makeText(AdminManageFarmersActivity.this, message, Toast.LENGTH_LONG).show();
                        }
                    });
        });

        dialog.show();
    }
}
