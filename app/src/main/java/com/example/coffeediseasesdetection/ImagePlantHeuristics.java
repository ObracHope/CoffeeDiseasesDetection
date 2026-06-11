package com.example.coffeediseasesdetection;

import android.graphics.Bitmap;
import android.graphics.Color;

/**
 * Fast pre-check only when ML models are unavailable.
 * Must NOT reject real coffee leaves held in hand or field photos.
 */
public final class ImagePlantHeuristics {

    private ImagePlantHeuristics() {}

    public static boolean likelyNotCoffee(Bitmap bitmap) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        int step = Math.max(1, Math.min(w, h) / 48);

        int total = 0;
        int green = 0;
        int coffeeGreen = 0;
        int unnatural = 0;
        int uiBright = 0;

        for (int y = 0; y < h; y += step) {
            for (int x = 0; x < w; x += step) {
                int pixel = bitmap.getPixel(x, y);
                int r = Color.red(pixel);
                int g = Color.green(pixel);
                int b = Color.blue(pixel);
                total++;

                if (g > 55 && g > r + 12 && g > b + 8) green++;
                if (g > 65 && g >= r && g >= b && r < 150 && b < 140) coffeeGreen++;

                if ((r > 150 && b > 150 && g < 120) || (r > 180 && g < 100) || (b > 160 && g < 100)) {
                    unnatural++;
                }
                if (r > 200 && g > 200 && b > 200) uiBright++;
            }
        }

        if (total == 0) return true;

        float greenRatio = green / (float) total;
        float coffeeGreenRatio = coffeeGreen / (float) total;
        float unnaturalRatio = unnatural / (float) total;
        float uiRatio = uiBright / (float) total;

        // Clear coffee / plant green → always allow (hand, leaf close-up, farm photos)
        if (coffeeGreenRatio >= 0.05f || greenRatio >= 0.08f) {
            return false;
        }

        // Only reject obvious non-plant scenes
        if (uiRatio > 0.35f && greenRatio < 0.04f) return true;
        if (unnaturalRatio > 0.28f && greenRatio < 0.05f) return true;
        if (greenRatio < 0.03f && coffeeGreenRatio < 0.02f) return true;

        return false;
    }
}
