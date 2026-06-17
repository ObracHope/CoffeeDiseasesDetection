package com.example.coffeediseasesdetection;

import android.app.Activity;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
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
    public static final String DEMO_ADMIN_USERNAME = "admin";
    public static final String OBEID_ADMIN_EMAIL = "obeid@coffee.com";
    public static final String OBEID_ADMIN_USERNAME = "obeid";

    public interface LoginCallback {
        void onSuccess();
        void onError(String message);
    }

    public interface GoogleLoginCallback {
        void onFarmerReady();
        void onAdminBlocked();
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

    public interface PasswordChangeCallback {
        void onSuccess();
        void onError(String message);
    }

    private AuthHelper() {}

    public static DatabaseReference usersRtdb() {
        return FirebaseDatabase.getInstance(RTDB_URL).getReference("users");
    }

    public static String normalizeRole(String role) {
        if (role == null || role.trim().isEmpty()) return "farmer";
        String r = role.trim().toLowerCase(Locale.US);
        if ("super_admin".equals(r)) return "superadmin";
        if ("bwana_shamba".equals(r) || "bwana_mifugo".equals(r)) return "bwana_kilimo";
        if ("waziri".equals(r)) return "waziri_wa_kilimo";
        return r;
    }

    public static boolean isStaffRole(String role) {
        if (role == null || role.trim().isEmpty()) return false;
        String r = normalizeRole(role);
        return r.equals("admin") || r.equals("main") || r.equals("superadmin")
                || r.equals("system_admin") || r.equals("it") || r.equals("technician")
                || r.equals("bwana_kilimo") || r.equals("waziri_wa_kilimo");
    }

    /** Staff / admin panel roles (Web + Mobile admin dashboard). */
    public static boolean isAdminRole(String role) {
        return isStaffRole(role);
    }

    /** Human-readable role label for drawer / profile headers. */
    public static String displayRoleLabel(android.content.Context ctx, String role) {
        String r = normalizeRole(role);
        if ("farmer".equals(r)) return ctx.getString(R.string.role_farmer);
        if ("admin".equals(r)) return ctx.getString(R.string.role_admin);
        if ("system_admin".equals(r)) return ctx.getString(R.string.role_system_admin);
        if ("superadmin".equals(r)) return ctx.getString(R.string.role_super_admin);
        if ("technician".equals(r)) return ctx.getString(R.string.role_technician);
        if ("it".equals(r)) return ctx.getString(R.string.role_it);
        if ("main".equals(r)) return ctx.getString(R.string.role_main);
        if ("bwana_kilimo".equals(r)) return ctx.getString(R.string.role_bwana_kilimo);
        if ("waziri_wa_kilimo".equals(r)) return ctx.getString(R.string.role_waziri_kilimo);
        if (r.isEmpty()) return ctx.getString(R.string.role_farmer);
        String spaced = r.replace('_', ' ');
        return spaced.substring(0, 1).toUpperCase(Locale.US) + spaced.substring(1);
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
        if (OBEID_ADMIN_USERNAME.equals(username)) {
            callback.onResolved(OBEID_ADMIN_EMAIL);
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

    private static boolean isObeidAdminEmail(String email) {
        return email != null && OBEID_ADMIN_EMAIL.equalsIgnoreCase(email.trim());
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

    /** Ensure Firestore + RTDB profile exists with correct role for known staff accounts. */
    private static void ensureDemoAdminProfile(@NonNull FirebaseUser user, @Nullable Runnable onDone) {
        String email = user.getEmail();
        if (!isDemoAdminEmail(email) && !isObeidAdminEmail(email)) {
            if (onDone != null) onDone.run();
            return;
        }

        String uid = user.getUid();
        java.util.HashMap<String, Object> data = new java.util.HashMap<>();
        data.put("uid", uid);
        if (isDemoAdminEmail(email)) {
            data.put("email", DEMO_ADMIN_EMAIL);
            data.put("name", "Admin");
            data.put("username", DEMO_ADMIN_USERNAME);
            data.put("role", "system_admin");
            cacheUsernameEmail(DEMO_ADMIN_USERNAME, DEMO_ADMIN_EMAIL.toLowerCase(Locale.US));
        } else {
            data.put("email", OBEID_ADMIN_EMAIL);
            data.put("name", "Obeid Tumaini");
            data.put("firstName", "Obeid");
            data.put("lastName", "Tumaini");
            data.put("username", OBEID_ADMIN_USERNAME);
            data.put("role", "admin");
            cacheUsernameEmail(OBEID_ADMIN_USERNAME, OBEID_ADMIN_EMAIL.toLowerCase(Locale.US));
        }

        usersRtdb().child(uid).setValue(data);
        FirebaseFirestore.getInstance().collection("users").document(uid)
                .set(data, com.google.firebase.firestore.SetOptions.merge())
                .addOnCompleteListener(t -> {
                    if (onDone != null) onDone.run();
                });
    }

    /** Email/password login — resolve role from profile, then redirect. */
    private static volatile String pendingLoginMethod;

    public static void completeEmailLoginAndRedirect(@NonNull Activity activity, @NonNull FirebaseUser user) {
        SessionManager.onLoginSuccess(activity, user);
        pendingLoginMethod = "email";
        FcmTokenHelper.refreshAndSave();
        String uid = user.getUid();

        if (isDemoAdminEmail(user.getEmail())) {
            cacheRole(activity, uid, "system_admin", "Admin", null, null);
            if (activity instanceof BaseActivity) {
                ((BaseActivity) activity).saveUserCache("system_admin", "Admin", null, null, null);
            }
            ensureDemoAdminProfile(user, () -> redirectForRole(activity, "system_admin"));
            return;
        }

        if (isObeidAdminEmail(user.getEmail())) {
            cacheRole(activity, uid, "admin", "Obeid Tumaini", "Obeid", "Tumaini");
            if (activity instanceof BaseActivity) {
                ((BaseActivity) activity).saveUserCache("admin", "Obeid Tumaini", null, "Obeid", "Tumaini");
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
     * If the same email exists as a farmer (email/password account), farmer access is allowed.
     */
    public static void completeGoogleLoginAndRedirect(@NonNull Activity activity,
                                                      @NonNull FirebaseUser user,
                                                      @NonNull Runnable onAdminBlocked) {
        completeGoogleLogin(activity, user, new GoogleLoginCallback() {
            @Override
            public void onFarmerReady() {
                // already redirected
            }

            @Override
            public void onAdminBlocked() {
                onAdminBlocked.run();
            }

            @Override
            public void onError(String message) {
                FirebaseAuth.getInstance().signOut();
                android.widget.Toast.makeText(activity, message, android.widget.Toast.LENGTH_LONG).show();
            }
        });
    }

    public static void completeGoogleLogin(@NonNull Activity activity,
                                           @NonNull FirebaseUser user,
                                           @NonNull GoogleLoginCallback callback) {
        FcmTokenHelper.refreshAndSave();
        String uid = user.getUid();

        fetchUserRole(uid, new RoleCallback() {
            @Override
            public void onRole(String role) {
                if (isStaffRole(role)) {
                    verifyGoogleNotFarmerAccount(user, isFarmer -> {
                        if (isFarmer) {
                            proceedGoogleFarmer(activity, user, callback);
                        } else {
                            FirebaseAuth.getInstance().signOut();
                            activity.getSharedPreferences(BaseActivity.PREFS_NAME, Activity.MODE_PRIVATE)
                                    .edit().clear().apply();
                            callback.onAdminBlocked();
                        }
                    });
                    return;
                }
                proceedGoogleFarmer(activity, user, callback);
            }

            @Override
            public void onError(Exception e) {
                proceedGoogleFarmer(activity, user, callback);
            }
        });
    }

    private static void proceedGoogleFarmer(@NonNull Activity activity,
                                            @NonNull FirebaseUser user,
                                            @NonNull GoogleLoginCallback callback) {
        ensureGoogleUserProfile(user, () -> {
            SessionManager.onLoginSuccess(activity, user);
            pendingLoginMethod = "google";
            String uid = user.getUid();
            cacheRole(activity, uid, "farmer", user.getDisplayName(), null, null);
            if (activity instanceof BaseActivity) {
                ((BaseActivity) activity).saveUserCache("farmer",
                        user.getDisplayName(),
                        user.getPhotoUrl() != null ? user.getPhotoUrl().toString() : null,
                        null, null);
            }
            redirectForRole(activity, "farmer");
            callback.onFarmerReady();
        });
    }

    /** Link Google to an existing email/password farmer account, then continue login. */
    public static void linkGoogleWithPassword(@NonNull Activity activity,
                                              @NonNull String email,
                                              @NonNull String password,
                                              @NonNull com.google.firebase.auth.AuthCredential googleCredential,
                                              @NonNull GoogleLoginCallback callback) {
        FirebaseAuth.getInstance().signInWithEmailAndPassword(email.trim(), password)
                .addOnCompleteListener(activity, emailTask -> {
                    if (!emailTask.isSuccessful() || FirebaseAuth.getInstance().getCurrentUser() == null) {
                        callback.onError(friendlyAuthError(emailTask.getException()));
                        return;
                    }
                    FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                    user.linkWithCredential(googleCredential)
                            .addOnCompleteListener(activity, linkTask -> {
                                if (linkTask.isSuccessful() && FirebaseAuth.getInstance().getCurrentUser() != null) {
                                    completeGoogleLogin(activity,
                                            FirebaseAuth.getInstance().getCurrentUser(), callback);
                                } else {
                                    // Already linked or link failed — continue if signed in
                                    if (FirebaseAuth.getInstance().getCurrentUser() != null) {
                                        completeGoogleLogin(activity,
                                                FirebaseAuth.getInstance().getCurrentUser(), callback);
                                    } else {
                                        callback.onError(friendlyGoogleAuthError(linkTask.getException()));
                                    }
                                }
                            });
                });
    }

    public static String emailFromGoogleIdToken(@Nullable GoogleIdTokenCredential credential) {
        if (credential == null || credential.getIdToken() == null) return null;
        try {
            String[] parts = credential.getIdToken().split("\\.");
            if (parts.length < 2) return null;
            byte[] decoded = android.util.Base64.decode(parts[1],
                    android.util.Base64.URL_SAFE | android.util.Base64.NO_PADDING | android.util.Base64.NO_WRAP);
            org.json.JSONObject payload = new org.json.JSONObject(new String(decoded, java.nio.charset.StandardCharsets.UTF_8));
            if (payload.has("email")) {
                return payload.getString("email");
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not parse Google ID token email", e);
        }
        return null;
    }

    public static String emailFromGoogleAuthError(@Nullable Exception e,
                                                  @Nullable GoogleIdTokenCredential credential) {
        if (e instanceof com.google.firebase.auth.FirebaseAuthUserCollisionException) {
            String collisionEmail = ((com.google.firebase.auth.FirebaseAuthUserCollisionException) e).getEmail();
            if (collisionEmail != null && !collisionEmail.trim().isEmpty()) {
                return collisionEmail.trim();
            }
        }
        return emailFromGoogleIdToken(credential);
    }

    public static boolean isGoogleAccountCollision(@Nullable Exception e) {
        if (e == null) return false;
        if (e instanceof com.google.firebase.auth.FirebaseAuthUserCollisionException) return true;
        String msg = e.getMessage() != null ? e.getMessage().toLowerCase(Locale.US) : "";
        return msg.contains("already in use")
                || msg.contains("different sign-in")
                || msg.contains("account exists");
    }

    public static String friendlyGoogleAuthError(@Nullable Exception e) {
        if (isGoogleAccountCollision(e)) {
            return "Barua pepe hii tayari imesajiliwa kwa nenosiri. Ingiza nenosiri lako kuunganisha Google, au tumia Login kwa barua pepe.";
        }
        return friendlyAuthError(e);
    }

    private static void verifyGoogleNotFarmerAccount(@NonNull FirebaseUser user,
                                                     @NonNull java.util.function.Consumer<Boolean> callback) {
        String email = user.getEmail();
        if (email == null || email.trim().isEmpty()) {
            callback.accept(false);
            return;
        }
        FirebaseFirestore.getInstance().collection("users")
                .whereEqualTo("email", email.trim())
                .limit(5)
                .get()
                .addOnSuccessListener(snap -> {
                    boolean farmer = false;
                    for (com.google.firebase.firestore.QueryDocumentSnapshot doc : snap) {
                        String role = doc.getString("role");
                        if (!isStaffRole(role)) {
                            farmer = true;
                            break;
                        }
                    }
                    callback.accept(farmer);
                })
                .addOnFailureListener(e -> callback.accept(false));
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
        String email = doc.getString("email");
        String username = doc.getString("username");
        if (email != null && username != null && !username.isEmpty()) {
            cacheUsernameEmail(normalizeUsername(username), email.trim().toLowerCase(Locale.US));
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
        if (pendingLoginMethod != null) {
            ActivityLogHelper.logAuthLogin(activity, normalizeRole(role), pendingLoginMethod);
            pendingLoginMethod = null;
        }
        Class<?> dest = isStaffRole(role)
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

        if (!SessionManager.isSessionValid(activity)) {
            SessionManager.clearSession(activity);
            FirebaseAuth.getInstance().signOut();
            return false;
        }

        SharedSessionCache cache = readCache(activity, user.getUid());
        if (!cache.hasRole) return false;

        SessionManager.touchActivity(activity);
        redirectForRole(activity, cache.role);
        refreshProfileInBackground(activity, user.getUid());
        return true;
    }

    public static void ensureGoogleUserProfile(@NonNull FirebaseUser user, @NonNull Runnable onDone) {
        String uid = user.getUid();
        String googlePhoto = user.getPhotoUrl() != null ? user.getPhotoUrl().toString() : null;
        FirebaseFirestore fs = FirebaseFirestore.getInstance();

        fs.collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String existingRole = doc.getString("role");
                        if (isAdminRole(existingRole)) {
                            onDone.run();
                            return;
                        }
                        syncGooglePhotoIfNeeded(uid, googlePhoto, doc.getString("photoUrl"), onDone);
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
                    if (googlePhoto != null && !googlePhoto.isEmpty()) {
                        data.put("photoUrl", googlePhoto);
                    }

                    usersRtdb().child(uid).setValue(data);
                    fs.collection("users").document(uid).set(data);
                    onDone.run();
                })
                .addOnFailureListener(e -> onDone.run());
    }

    private static void syncGooglePhotoIfNeeded(@NonNull String uid,
                                                @Nullable String googlePhoto,
                                                @Nullable String existingPhoto,
                                                @NonNull Runnable onDone) {
        if (googlePhoto == null || googlePhoto.isEmpty()) {
            onDone.run();
            return;
        }
        if (existingPhoto != null && !existingPhoto.isEmpty()) {
            onDone.run();
            return;
        }
        java.util.HashMap<String, Object> updates = new java.util.HashMap<>();
        updates.put("photoUrl", googlePhoto);
        usersRtdb().child(uid).updateChildren(updates);
        FirebaseFirestore.getInstance().collection("users").document(uid)
                .set(updates, com.google.firebase.firestore.SetOptions.merge())
                .addOnCompleteListener(t -> onDone.run());
    }

    /**
     * Change password via Firebase Auth — same account used by web admin panel.
     * Password is never written to Firestore/RTDB (metadata timestamp only).
     */
    public static void changeAdminPassword(@NonNull Activity activity,
                                           @NonNull String currentPassword,
                                           @NonNull String newPassword,
                                           @NonNull PasswordChangeCallback callback) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || user.getEmail() == null) {
            callback.onError("Hakuna mtumiaji aliyeingia.");
            return;
        }
        if (newPassword.length() < 6) {
            callback.onError("Nenosiri jipya lazima liwe angalau herufi 6.");
            return;
        }

        com.google.firebase.auth.AuthCredential credential =
                com.google.firebase.auth.EmailAuthProvider.getCredential(user.getEmail(), currentPassword);
        user.reauthenticate(credential).addOnCompleteListener(activity, reauthTask -> {
            if (!reauthTask.isSuccessful()) {
                callback.onError("Nenosiri la sasa si sahihi.");
                return;
            }
            user.updatePassword(newPassword).addOnCompleteListener(activity, updateTask -> {
                if (!updateTask.isSuccessful()) {
                    callback.onError(friendlyAuthError(updateTask.getException()));
                    return;
                }
                recordPasswordChangeMetadata(user.getUid(), "mobile", () -> callback.onSuccess());
            });
        });
    }

    private static void recordPasswordChangeMetadata(String uid, String changedVia, @Nullable Runnable onDone) {
        java.util.HashMap<String, Object> meta = new java.util.HashMap<>();
        meta.put("passwordChangedAt", System.currentTimeMillis());
        meta.put("passwordChangedVia", changedVia);
        usersRtdb().child(uid).updateChildren(meta);
        FirebaseFirestore.getInstance().collection("users").document(uid)
                .set(meta, com.google.firebase.firestore.SetOptions.merge())
                .addOnCompleteListener(t -> {
                    if (onDone != null) onDone.run();
                });
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
