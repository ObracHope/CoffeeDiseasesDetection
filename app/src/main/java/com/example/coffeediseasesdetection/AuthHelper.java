package com.example.coffeediseasesdetection;

import android.app.Activity;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.Source;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Unified login: email/username + password (admin or farmer by role),
 * Google sign-in (farmers only).
 */
public final class AuthHelper {

    private static final String TAG = "AuthHelper";
    public static final String RTDB_URL = "https://coffee-diseases-detection-default-rtdb.firebaseio.com";
    public static final String DEMO_ADMIN_EMAIL = "admin@coffeediseases.com";

    public interface LoginCallback {
        void onSuccess();
        void onError(String message);
    }

    public interface EmailResolverCallback {
        void onResolved(String email);
        void onError(String message);
    }

    public interface RoleCallback {
        void onRole(String role);
        void onError(Exception e);
    }

    private AuthHelper() {}

    public static DatabaseReference usersRtdb() {
        return FirebaseDatabase.getInstance(RTDB_URL).getReference("users");
    }

    public static boolean isAdminRole(String role) {
        if (role == null || role.trim().isEmpty()) return false;
        String r = role.trim().toLowerCase(Locale.US);
        return r.equals("admin") || r.equals("main") || r.equals("superadmin")
                || r.equals("super_admin");
    }

    public static String normalizeRole(String role) {
        return isAdminRole(role) ? "admin" : "farmer";
    }

    public static String normalizeUsername(String raw) {
        if (raw == null) return "";
        return raw.trim().toLowerCase(Locale.US).replaceAll("\\s+", "");
    }

    public static String buildUsername(String firstName, String lastName, String email) {
        String base = (firstName != null ? firstName : "") + (lastName != null ? lastName : "");
        base = normalizeUsername(base.replaceAll("[^a-zA-Z0-9]", ""));
        if (base.length() < 3 && email != null && email.contains("@")) {
            base = normalizeUsername(email.substring(0, email.indexOf('@')).replaceAll("[^a-zA-Z0-9]", ""));
        }
        if (base.length() < 3) {
            base = "user" + System.currentTimeMillis() % 100000;
        }
        return base;
    }

    public static void resolveEmailForLogin(@NonNull String input, @NonNull EmailResolverCallback callback) {
        String trimmed = input.trim();
        if (trimmed.contains("@")) {
            callback.onResolved(trimmed.toLowerCase(Locale.US));
            return;
        }

        String username = normalizeUsername(trimmed);
        if (username.isEmpty()) {
            callback.onError("Ingiza barua pepe au jina la mtumiaji");
            return;
        }

        if ("admin".equals(username)) {
            callback.onResolved(DEMO_ADMIN_EMAIL);
            return;
        }

        android.content.SharedPreferences prefs = FirebaseAuth.getInstance().getApp().getApplicationContext()
                .getSharedPreferences(BaseActivity.PREFS_NAME, android.content.Context.MODE_PRIVATE);
        String cachedEmail = prefs.getString("login_email_" + username, null);
        if (cachedEmail != null && !cachedEmail.isEmpty()) {
            callback.onResolved(cachedEmail);
            return;
        }

        FirebaseFirestore.getInstance().collection("users")
                .whereEqualTo("username", username)
                .limit(1)
                .get()
                .addOnSuccessListener(snap -> {
                    if (!snap.isEmpty()) {
                        String email = snap.getDocuments().get(0).getString("email");
                        if (email != null && !email.isEmpty()) {
                            cacheUsernameEmail(username, email.trim().toLowerCase(Locale.US));
                            callback.onResolved(email.trim().toLowerCase(Locale.US));
                            return;
                        }
                    }
                    resolveEmailFromRtdb(username, trimmed, callback);
                })
                .addOnFailureListener(e -> resolveEmailFromRtdb(username, trimmed, callback));
    }

    private static void resolveEmailFromRtdb(String username, String trimmed,
                                             @NonNull EmailResolverCallback callback) {
        usersRtdb().get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.exists()) {
                        for (DataSnapshot child : snapshot.getChildren()) {
                            String stored = child.child("username").getValue(String.class);
                            if (stored != null && normalizeUsername(stored).equals(username)) {
                                String email = child.child("email").getValue(String.class);
                                if (email != null && !email.isEmpty()) {
                                    cacheUsernameEmail(username, email.trim().toLowerCase(Locale.US));
                                    callback.onResolved(email.trim().toLowerCase(Locale.US));
                                    return;
                                }
                            }
                        }
                    }
                    FirebaseFirestore.getInstance().collection("users")
                            .whereEqualTo("email", trimmed)
                            .limit(1)
                            .get()
                            .addOnSuccessListener(snap2 -> {
                                if (!snap2.isEmpty()) {
                                    String email = snap2.getDocuments().get(0).getString("email");
                                    if (email != null) {
                                        cacheUsernameEmail(username, email.trim().toLowerCase(Locale.US));
                                        callback.onResolved(email.trim().toLowerCase(Locale.US));
                                        return;
                                    }
                                }
                                callback.onError("Jina la mtumiaji halijapatikana. Tumia barua pepe uliyosajili.");
                            })
                            .addOnFailureListener(e2 -> callback.onError(friendlyFirestoreError(e2)));
                })
                .addOnFailureListener(e -> callback.onError(friendlyFirestoreError(e)));
    }

    public static void signInWithEmailPassword(@NonNull Activity activity,
                                               @NonNull String emailOrUsername,
                                               @NonNull String password,
                                               @NonNull LoginCallback callback) {
        resolveEmailForLogin(emailOrUsername, new EmailResolverCallback() {
            @Override
            public void onResolved(String email) {
                FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password)
                        .addOnCompleteListener(activity, task -> {
                            if (task.isSuccessful() && FirebaseAuth.getInstance().getCurrentUser() != null) {
                                ensureDemoAdminProfile(FirebaseAuth.getInstance().getCurrentUser(), null);
                                callback.onSuccess();
                            } else if (isDemoAdminEmail(email)) {
                                bootstrapDemoAdminAccount(activity, email, password, callback);
                            } else {
                                callback.onError(friendlyAuthError(task.getException()));
                            }
                        });
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    private static boolean isDemoAdminEmail(String email) {
        return email != null && DEMO_ADMIN_EMAIL.equalsIgnoreCase(email.trim());
    }

    /** Create demo admin in Firebase Auth on first login if missing, then write admin profile. */
    private static void bootstrapDemoAdminAccount(@NonNull Activity activity,
                                                  @NonNull String email,
                                                  @NonNull String password,
                                                  @NonNull LoginCallback callback) {
        FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(activity, createTask -> {
                    if (createTask.isSuccessful() && FirebaseAuth.getInstance().getCurrentUser() != null) {
                        ensureDemoAdminProfile(FirebaseAuth.getInstance().getCurrentUser(),
                                () -> callback.onSuccess());
                    } else {
                        Exception ex = createTask.getException();
                        if (ex != null && ex.getMessage() != null
                                && ex.getMessage().toLowerCase(Locale.US).contains("already")) {
                            callback.onError("Barua pepe/jina la mtumiaji au nenosiri si sahihi.");
                        } else {
                            callback.onError(friendlyAuthError(ex));
                        }
                    }
                });
    }

    /** Ensure Firestore + RTDB profile exists with role admin for the demo admin account. */
    private static void ensureDemoAdminProfile(@NonNull FirebaseUser user, @Nullable Runnable onDone) {
        if (!isDemoAdminEmail(user.getEmail())) {
            if (onDone != null) onDone.run();
            return;
        }

        String uid = user.getUid();
        java.util.HashMap<String, Object> data = new java.util.HashMap<>();
        data.put("uid", uid);
        data.put("email", DEMO_ADMIN_EMAIL);
        data.put("name", "Admin");
        data.put("username", "admin");
        data.put("role", "admin");

        usersRtdb().child(uid).setValue(data);
        FirebaseFirestore.getInstance().collection("users").document(uid)
                .set(data, com.google.firebase.firestore.SetOptions.merge())
                .addOnCompleteListener(t -> {
                    if (onDone != null) onDone.run();
                });
    }

    /** Email/password login — resolve role from profile, then redirect. */
    public static void completeEmailLoginAndRedirect(@NonNull Activity activity, @NonNull FirebaseUser user) {
        FcmTokenHelper.refreshAndSave();
        String uid = user.getUid();

        if (isDemoAdminEmail(user.getEmail())) {
            cacheRole(activity, uid, "admin", "Admin", null, null);
            if (activity instanceof BaseActivity) {
                ((BaseActivity) activity).saveUserCache("admin", "Admin", null, null, null);
            }
            ensureDemoAdminProfile(user, () -> redirectForRole(activity, "admin"));
            return;
        }

        SharedSessionCache cache = readCache(activity, uid);

        if (cache.hasRole) {
            redirectForRole(activity, cache.role);
            refreshProfileInBackground(activity, uid);
            return;
        }

        resolveRoleAndRedirect(activity, uid, false);
    }

    /**
     * Google login — farmers only. Admins are signed out with an error message.
     */
    public static void completeGoogleLoginAndRedirect(@NonNull Activity activity,
                                                      @NonNull FirebaseUser user,
                                                      @NonNull Runnable onAdminBlocked) {
        FcmTokenHelper.refreshAndSave();
        String uid = user.getUid();

        fetchUserRole(uid, new RoleCallback() {
            @Override
            public void onRole(String role) {
                if (isAdminRole(role)) {
                    FirebaseAuth.getInstance().signOut();
                    activity.getSharedPreferences(BaseActivity.PREFS_NAME, Activity.MODE_PRIVATE).edit().clear().apply();
                    onAdminBlocked.run();
                    return;
                }
                ensureGoogleUserProfile(user, () -> {
                    cacheRole(activity, uid, "farmer", user.getDisplayName(), null, null);
                    if (activity instanceof BaseActivity) {
                        ((BaseActivity) activity).saveUserCache("farmer",
                                user.getDisplayName(), user.getPhotoUrl() != null ? user.getPhotoUrl().toString() : null,
                                null, null);
                    }
                    redirectForRole(activity, "farmer");
                });
            }

            @Override
            public void onError(Exception e) {
                ensureGoogleUserProfile(user, () -> {
                    cacheRole(activity, uid, "farmer", user.getDisplayName(), null, null);
                    redirectForRole(activity, "farmer");
                });
            }
        });
    }

    /** @deprecated Use {@link #completeEmailLoginAndRedirect} or {@link #completeGoogleLoginAndRedirect}. */
    public static void completeLoginAndRedirect(@NonNull Activity activity, @NonNull FirebaseUser user) {
        completeEmailLoginAndRedirect(activity, user);
    }

    private static void resolveRoleAndRedirect(@NonNull Activity activity, String uid, boolean unused) {
        AtomicBoolean redirected = new AtomicBoolean(false);

        java.util.function.Consumer<DocumentSnapshot> tryRedirect = doc -> {
            if (doc != null && doc.exists() && redirected.compareAndSet(false, true)) {
                applyProfileAndRedirect(activity, uid, doc);
            }
        };

        Runnable tryRtdb = () -> {
            if (!redirected.get()) {
                fetchRoleFromRtdbAndRedirect(activity, uid, redirected);
            }
        };

        FirebaseFirestore.getInstance().collection("users").document(uid)
                .get(Source.CACHE)
                .addOnSuccessListener(tryRedirect::accept)
                .addOnFailureListener(e -> Log.w(TAG, "Cache profile miss", e));

        FirebaseFirestore.getInstance().collection("users").document(uid)
                .get(Source.SERVER)
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        tryRedirect.accept(doc);
                    } else {
                        tryRtdb.run();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Server profile failed, trying RTDB", e);
                    tryRtdb.run();
                });
    }

    public static void fetchUserRole(String uid, @NonNull RoleCallback callback) {
        FirebaseFirestore.getInstance().collection("users").document(uid)
                .get(Source.SERVER)
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        callback.onRole(normalizeRole(doc.getString("role")));
                        return;
                    }
                    usersRtdb().child(uid).get()
                            .addOnSuccessListener(snap -> {
                                String role = snap.exists() && snap.child("role").getValue() != null
                                        ? String.valueOf(snap.child("role").getValue()) : "farmer";
                                callback.onRole(normalizeRole(role));
                            })
                            .addOnFailureListener(callback::onError);
                })
                .addOnFailureListener(e ->
                        usersRtdb().child(uid).get()
                                .addOnSuccessListener(snap -> {
                                    String role = snap.exists() && snap.child("role").getValue() != null
                                            ? String.valueOf(snap.child("role").getValue()) : "farmer";
                                    callback.onRole(normalizeRole(role));
                                })
                                .addOnFailureListener(callback::onError));
    }

    private static void cacheUsernameEmail(String username, String email) {
        FirebaseAuth.getInstance().getApp().getApplicationContext()
                .getSharedPreferences(BaseActivity.PREFS_NAME, android.content.Context.MODE_PRIVATE)
                .edit()
                .putString("login_email_" + username, email)
                .apply();
    }

    private static void fetchRoleFromRtdbAndRedirect(Activity activity, String uid) {
        fetchRoleFromRtdbAndRedirect(activity, uid, new AtomicBoolean(false));
    }

    private static void fetchRoleFromRtdbAndRedirect(Activity activity, String uid, AtomicBoolean redirected) {
        usersRtdb().child(uid).get()
                .addOnSuccessListener(snapshot -> {
                    if (!redirected.compareAndSet(false, true)) return;
                    String role = "farmer";
                    if (snapshot.exists() && snapshot.child("role").getValue() != null) {
                        role = normalizeRole(String.valueOf(snapshot.child("role").getValue()));
                    }
                    cacheRole(activity, uid, role, null, null, null);
                    redirectForRole(activity, role);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Profile fetch failed", e);
                    if (redirected.compareAndSet(false, true)) {
                        cacheRole(activity, uid, "farmer", null, null, null);
                        redirectForRole(activity, "farmer");
                    }
                });
    }

    private static void refreshProfileInBackground(Activity activity, String uid) {
        FirebaseFirestore.getInstance().collection("users").document(uid)
                .get(Source.SERVER)
                .addOnSuccessListener(doc -> {
                    if (doc.exists() && activity instanceof BaseActivity) {
                        applyCacheFromDoc((BaseActivity) activity, uid, doc);
                    }
                });
    }

    private static void applyProfileAndRedirect(Activity activity, String uid, DocumentSnapshot doc) {
        String role = normalizeRole(doc.getString("role"));
        String name = doc.getString("name");
        String firstName = doc.getString("firstName");
        String lastName = doc.getString("lastName");
        String photo = doc.getString("photoUrl");

        cacheRole(activity, uid, role, name, firstName, lastName);
        if (activity instanceof BaseActivity) {
            ((BaseActivity) activity).saveUserCache(role, name, photo, firstName, lastName);
        }
        redirectForRole(activity, role);
    }

    private static void applyCacheFromDoc(BaseActivity activity, String uid, DocumentSnapshot doc) {
        String role = normalizeRole(doc.getString("role"));
        String name = doc.getString("name");
        String firstName = doc.getString("firstName");
        String lastName = doc.getString("lastName");
        String photo = doc.getString("photoUrl");
        cacheRole(activity, uid, role, name, firstName, lastName);
        activity.saveUserCache(role, name, photo, firstName, lastName);
    }

    public static void redirectForRole(@NonNull Activity activity, String role) {
        if (activity.isFinishing()) return;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1
                && activity.isDestroyed()) {
            return;
        }
        Class<?> dest = isAdminRole(role)
                ? AdminDashboardActivity.class
                : FarmerDashboardActivity.class;
        Intent intent = new Intent(activity, dest);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity.startActivity(intent);
        activity.finish();
    }

    public static void cacheRole(Activity activity, String uid, String role,
                                 String name, String firstName, String lastName) {
        activity.getSharedPreferences(BaseActivity.PREFS_NAME, Activity.MODE_PRIVATE).edit()
                .putString("cached_uid", uid)
                .putString(BaseActivity.KEY_ROLE, normalizeRole(role))
                .putString(BaseActivity.KEY_NAME, name != null ? name : "")
                .putString(BaseActivity.KEY_FIRST_NAME, firstName != null ? firstName : "")
                .putString(BaseActivity.KEY_LAST_NAME, lastName != null ? lastName : "")
                .apply();
    }

    public static boolean tryFastSessionRestore(@NonNull Activity activity) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return false;

        SharedSessionCache cache = readCache(activity, user.getUid());
        if (!cache.hasRole) return false;

        redirectForRole(activity, cache.role);
        refreshProfileInBackground(activity, user.getUid());
        return true;
    }

    public static void ensureGoogleUserProfile(@NonNull FirebaseUser user, @NonNull Runnable onDone) {
        String uid = user.getUid();
        FirebaseFirestore fs = FirebaseFirestore.getInstance();

        fs.collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String existingRole = doc.getString("role");
                        if (isAdminRole(existingRole)) {
                            onDone.run();
                            return;
                        }
                        onDone.run();
                        return;
                    }
                    String displayName = user.getDisplayName() != null ? user.getDisplayName() : "User";
                    String email = user.getEmail() != null ? user.getEmail() : "";
                    String username = buildUsername(displayName, "", email);

                    java.util.HashMap<String, Object> data = new java.util.HashMap<>();
                    data.put("uid", uid);
                    data.put("name", displayName);
                    data.put("email", email);
                    data.put("username", username);
                    data.put("role", "farmer");

                    usersRtdb().child(uid).setValue(data);
                    fs.collection("users").document(uid).set(data);
                    onDone.run();
                })
                .addOnFailureListener(e -> onDone.run());
    }

    private static SharedSessionCache readCache(Activity activity, String uid) {
        android.content.SharedPreferences p = activity.getSharedPreferences(BaseActivity.PREFS_NAME, Activity.MODE_PRIVATE);
        String cachedUid = p.getString("cached_uid", "");
        String role = p.getString(BaseActivity.KEY_ROLE, "");
        boolean hasRole = uid.equals(cachedUid) && role != null && !role.isEmpty();
        return new SharedSessionCache(hasRole, normalizeRole(role));
    }

    private static String friendlyAuthError(Exception e) {
        if (e == null || e.getMessage() == null) return "Kuingia kumeshindwa. Jaribu tena.";
        String msg = e.getMessage().toLowerCase(Locale.US);
        if (msg.contains("password") || msg.contains("invalid credential") || msg.contains("wrong")) {
            return "Barua pepe/jina la mtumiaji au nenosiri si sahihi.";
        }
        if (msg.contains("network")) return "Hakuna mtandao. Angalia muunganisho wako.";
        if (msg.contains("too many")) return "Majaribio mengi. Subiri kidogo kisha jaribu tena.";
        if (msg.contains("user not found") || msg.contains("no user")) {
            return "Akaunti haipo. Jisajili kwanza.";
        }
        return "Kuingia kumeshindwa: " + e.getMessage();
    }

    private static String friendlyFirestoreError(Exception e) {
        if (e == null) return "Hitilafu ya mtandao. Jaribu tena.";
        if (e.getMessage() != null && e.getMessage().toLowerCase(Locale.US).contains("network")) {
            return "Hakuna mtandao. Angalia muunganisho wako.";
        }
        return "Hitilafu ya kutafuta akaunti. Jaribu tena.";
    }

    private static final class SharedSessionCache {
        final boolean hasRole;
        final String role;

        SharedSessionCache(boolean hasRole, String role) {
            this.hasRole = hasRole;
            this.role = role != null ? role : "farmer";
        }
    }
}
