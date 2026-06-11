package com.example.coffeediseasesdetection;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.widget.ImageView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class ImageUtils {

    /**
     * Loads an image from a URL into an ImageView using a background thread and the UI thread.
     * Note: In a production app, use a library like Glide or Picasso.
     */
    public static void loadImage(Activity activity, String urlString, ImageView imageView) {
        if (activity == null || urlString == null || urlString.isEmpty() || imageView == null) return;

        new Thread(() -> {
            try {
                URL url = new URL(urlString);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setDoInput(true);
                connection.connect();
                InputStream input = connection.getInputStream();
                Bitmap bmp = BitmapFactory.decodeStream(input);
                
                activity.runOnUiThread(() -> {
                    if (!activity.isFinishing() && !activity.isDestroyed() && bmp != null) {
                        imageView.setImageBitmap(bmp);
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
}
