package com.example.coffeediseasesdetection;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

/**
 * Timed greeting banner — fade in 0.5s, visible ~28s, fade out 1.5s.
 * Shown below the dashboard header for every login / activity launch.
 */
public final class GreetingBannerHelper {

    private static final long FADE_IN_MS = 500L;
    private static final long VISIBLE_MS = 28_000L;
    private static final long FADE_OUT_MS = 1_500L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable fadeOutRunnable;
    private boolean running;

    public interface NameCallback {
        void onNameReady(String fullName);
    }

    public void show(Activity activity, TextView banner) {
        if (activity == null || banner == null || activity.isFinishing()) return;
        cancel();

        resolveFullName(activity, fullName -> {
            if (activity.isFinishing() || fullName == null || fullName.trim().isEmpty()) return;
            String greeting = buildGreeting(activity, fullName.trim());
            banner.setText(greeting);
            banner.setAlpha(0f);
            banner.setVisibility(View.VISIBLE);
            banner.animate().alpha(1f).setDuration(FADE_IN_MS).start();

            fadeOutRunnable = () -> banner.animate()
                    .alpha(0f)
                    .setDuration(FADE_OUT_MS)
                    .withEndAction(() -> {
                        if (!activity.isFinishing()) {
                            banner.setVisibility(View.GONE);
                        }
                        running = false;
                    })
                    .start();
            handler.postDelayed(fadeOutRunnable, VISIBLE_MS);
            running = true;
        });
    }

    public void cancel() {
        if (fadeOutRunnable != null) {
            handler.removeCallbacks(fadeOutRunnable);
            fadeOutRunnable = null;
        }
        running = false;
    }

    public boolean isRunning() {
        return running;
    }

    public static String buildGreeting(Context context, String fullName) {
        return context.getString(R.string.greeting_format, fullName);
    }

    private static void resolveFullName(Context context, NameCallback callback) {
        SharedPreferences prefs = context.getSharedPreferences(BaseActivity.PREFS_NAME, Context.MODE_PRIVATE);
        String first = prefs.getString(BaseActivity.KEY_FIRST_NAME, "");
        String last = prefs.getString(BaseActivity.KEY_LAST_NAME, "");
        String cached = (first + " " + last).trim();
        if (!cached.isEmpty()) {
            callback.onNameReady(cached);
        }

        String displayName = prefs.getString(BaseActivity.KEY_NAME, "");
        if (!displayName.isEmpty() && cached.isEmpty()) {
            callback.onNameReady(displayName);
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            if (cached.isEmpty() && displayName.isEmpty()) callback.onNameReady("");
            return;
        }

        FirebaseFirestore.getInstance().collection("users").document(user.getUid())
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc == null || !doc.exists()) {
                        if (cached.isEmpty()) callback.onNameReady(displayName);
                        return;
                    }
                    String f = safe(doc.getString("firstName"));
                    String l = safe(doc.getString("lastName"));
                    String full = (f + " " + l).trim();
                    if (full.isEmpty()) {
                        full = safe(doc.getString("name"));
                    }
                    if (full.isEmpty()) full = displayName;
                    callback.onNameReady(full);
                })
                .addOnFailureListener(e -> {
                    if (cached.isEmpty()) callback.onNameReady(displayName);
                });
    }

    private static String safe(@Nullable String value) {
        return value != null ? value.trim() : "";
    }
}
