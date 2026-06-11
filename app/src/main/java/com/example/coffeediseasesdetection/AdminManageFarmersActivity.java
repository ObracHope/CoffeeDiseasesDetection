package com.example.coffeediseasesdetection;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AdminManageFarmersActivity extends BaseActivity {

    private FirebaseAuth auth;
    private FirebaseFirestore firestore;
    private List<Map<String, Object>> usersList = new ArrayList<>();
    private ManageUsersAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_users);
        setTitle(R.string.manage_farmers);

        auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();

        CardView cardAddUser = findViewById(R.id.cardAddUser);
        FloatingActionButton btnAddUser = findViewById(R.id.btnAddUser);
        EditText etName = findViewById(R.id.etNameAdmin);
        EditText etEmail = findViewById(R.id.etEmailAdmin);
        EditText etPassword = findViewById(R.id.etPasswordAdmin);
        Spinner spinnerRole = findViewById(R.id.spinnerRole);
        MaterialButton btnSave = findViewById(R.id.btnSaveUser);
        RecyclerView recyclerUsers = findViewById(R.id.recyclerUsers);

        if (btnAddUser != null && cardAddUser != null) {
            btnAddUser.setOnClickListener(v -> {
                boolean show = cardAddUser.getVisibility() != View.VISIBLE;
                cardAddUser.setVisibility(show ? View.VISIBLE : View.GONE);
                if (show && etName != null) {
                    etName.requestFocus();
                }
            });
        }

        ArrayAdapter<CharSequence> roleAdapter = ArrayAdapter.createFromResource(
                this, R.array.user_roles, android.R.layout.simple_spinner_item);
        roleAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerRole.setAdapter(roleAdapter);

        recyclerUsers.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ManageUsersAdapter(usersList, this::removeUser, this::showEditUserDialog);
        recyclerUsers.setAdapter(adapter);

        loadUsers();

        if (btnSave == null) return;

        btnSave.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            String email = etEmail.getText().toString().trim();
            String password = etPassword.getText().toString().trim();
            String roleStr = spinnerRole.getSelectedItem().toString().toLowerCase();
            String role = roleStr.contains("mkulima") || roleStr.contains("farmer") ? "farmer" : "admin";

            if (TextUtils.isEmpty(name) || TextUtils.isEmpty(email)) {
                Toast.makeText(this, getString(R.string.required), Toast.LENGTH_SHORT).show();
                return;
            }
            if (TextUtils.isEmpty(password) || password.length() < 6) {
                Toast.makeText(this, getString(R.string.password_min), Toast.LENGTH_SHORT).show();
                return;
            }
            addUser(name, email, password, role, etName, etEmail, etPassword, cardAddUser);
        });
    }

    private void addUser(String name, String email, String password, String role,
                        EditText etName, EditText etEmail, EditText etPassword, View cardAddUser) {
        AdminAuthHelper.secondaryAuth().createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    String uid = authResult.getUser().getUid();
                    Map<String, Object> data = new HashMap<>();
                    data.put("uid", uid);
                    data.put("name", name);
                    data.put("email", email);
                    data.put("role", role);
                    AuthHelper.usersRtdb().child(uid).setValue(data);
                    firestore.collection("users").document(uid).set(data)
                            .addOnSuccessListener(v -> {
                                AdminAuthHelper.secondaryAuth().signOut();
                                Toast.makeText(this, R.string.user_added_success, Toast.LENGTH_SHORT).show();
                                etName.setText("");
                                etEmail.setText("");
                                etPassword.setText("");
                                if (cardAddUser != null) {
                                    cardAddUser.setVisibility(View.GONE);
                                }
                                loadUsers();
                            });
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed: " + (e.getMessage() != null ? e.getMessage() : ""), Toast.LENGTH_SHORT).show());
    }

    private void loadUsers() {
        usersList.clear();
        firestore.collection("users").get()
                .addOnSuccessListener(snapshots -> {
                    for (QueryDocumentSnapshot doc : snapshots) {
                        Map<String, Object> m = new HashMap<>(doc.getData());
                        m.put("_id", doc.getId());
                        usersList.add(m);
                    }
                    adapter.notifyDataSetChanged();
                });
    }

    private void removeUser(String userId, String docId) {
        firestore.collection("users").document(userId).delete()
                .addOnSuccessListener(v -> {
                    Toast.makeText(this, getString(R.string.user_removed), Toast.LENGTH_SHORT).show();
                    loadUsers();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to remove", Toast.LENGTH_SHORT).show());
    }

    private void showEditUserDialog(String userId, Map<String, Object> userData) {
        android.view.View dialogView = LayoutInflater.from(this).inflate(android.R.layout.simple_list_item_2, null);
        final EditText etName = new EditText(this);
        etName.setHint(getString(R.string.register_firstname) + " / Name");
        etName.setText(userData.get("name") != null ? userData.get("name").toString() : "");
        etName.setPadding(80, 40, 80, 20);
        final EditText etPhone = new EditText(this);
        etPhone.setHint(getString(R.string.register_phone));
        etPhone.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        etPhone.setText(userData.get("phone") != null ? userData.get("phone").toString() : "");
        etPhone.setPadding(80, 20, 80, 20);
        final EditText etEmail = new EditText(this);
        etEmail.setHint(getString(R.string.hint_email));
        etEmail.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        etEmail.setText(userData.get("email") != null ? userData.get("email").toString() : "");
        etEmail.setPadding(80, 20, 80, 40);
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.addView(etName);
        layout.addView(etPhone);
        layout.addView(etEmail);
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.edit))
                .setView(layout)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    String name = etName.getText().toString().trim();
                    String phone = etPhone.getText().toString().trim();
                    String email = etEmail.getText().toString().trim();
                    if (TextUtils.isEmpty(name)) {
                        Toast.makeText(this, getString(R.string.required), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("name", name);
                    updates.put("phone", phone);
                    updates.put("email", email);
                    firestore.collection("users").document(userId).update(updates)
                            .addOnSuccessListener(v -> {
                                Toast.makeText(this, getString(R.string.updated), Toast.LENGTH_SHORT).show();
                                loadUsers();
                            })
                            .addOnFailureListener(e -> Toast.makeText(this, "Update failed", Toast.LENGTH_SHORT).show());
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
