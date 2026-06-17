package com.example.coffeediseasesdetection;

import android.widget.ImageView;

import com.bumptech.glide.Glide;

public final class ProfileAvatarHelper {

    private ProfileAvatarHelper() {
    }

    public static void load(ImageView target, String photoUrl) {
        if (target == null) return;
        if (photoUrl != null && !photoUrl.isEmpty()) {
            Glide.with(target)
                    .load(photoUrl)
                    .placeholder(R.drawable.placeholder_user)
                    .circleCrop()
                    .into(target);
        } else {
            target.setImageResource(R.drawable.placeholder_user);
        }
    }
}
