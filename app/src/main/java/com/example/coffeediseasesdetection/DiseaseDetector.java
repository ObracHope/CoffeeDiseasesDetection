package com.example.coffeediseasesdetection;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

/**
 * Single disease model (7 classes). Input pixels as float32 in [0, 255] — matches training Rescaling(1/255).
 * Optional gate only rejects clear non-coffee; never forces "Healthy".
 */
public class DiseaseDetector {

    private static final String TAG = "DiseaseDetector";
    private static final String DISEASE_MODEL_PATH = "coffee_disease_model.tflite";
    private static final String GATE_MODEL_PATH = "coffee_gate.tflite";

    private static final int IMG_SIZE = 224;
    private static final float MIN_CONFIDENCE_PERCENT = 50f;
    /** Gate: reject only when clearly not coffee (strict). */
    private static final float GATE_REJECT_COFFEE = 0.50f;
    /** Disease model: IsNotCoffee must win clearly. */
    private static final float NOT_COFFEE_MIN = 0.48f;

    private static final int NOT_COFFEE_CLASS_INDEX = 6;
    private static final int FIRST_DISEASE_INDEX = 1;
    private static final int LAST_DISEASE_INDEX = 5;

    public static final String NOT_COFFEE_LABEL = "IsNotCoffee";
    public static final String NOT_COFFEE_MESSAGE = DiseaseLabels.NOT_COFFEE_TITLE;

    private static Interpreter diseaseTflite;
    private static Interpreter gateTflite;
    private static boolean modelsLoadAttempted;

    private static final String[] LABELS = {
            "Healthy",
            "Rust",
            "BerryDisease",
            "Wilt",
            "LeafMiner",
            "RootRot",
            NOT_COFFEE_LABEL
    };

    public interface DetectionCallback {
        void onDetected(DetectionResult result);
    }

    public static void loadModel(Context context) {
        if (modelsLoadAttempted) return;
        modelsLoadAttempted = true;

        try {
            Interpreter.Options options = new Interpreter.Options();
            options.setNumThreads(Math.max(2, Runtime.getRuntime().availableProcessors()));
            diseaseTflite = new Interpreter(loadModelFile(context, DISEASE_MODEL_PATH), options);
            Log.d(TAG, "Disease model loaded");
        } catch (Exception e) {
            Log.w(TAG, "Disease model not available: " + e.getMessage());
            diseaseTflite = null;
        }

        try {
            Interpreter.Options options = new Interpreter.Options();
            options.setNumThreads(2);
            gateTflite = new Interpreter(loadModelFile(context, GATE_MODEL_PATH), options);
            Log.d(TAG, "Coffee gate loaded");
        } catch (Exception e) {
            Log.w(TAG, "Gate model not available: " + e.getMessage());
            gateTflite = null;
        }
    }

    private static MappedByteBuffer loadModelFile(Context context, String assetName) throws IOException {
        try (FileInputStream inputStream = new FileInputStream(
                context.getAssets().openFd(assetName).getFileDescriptor())) {
            FileChannel fileChannel = inputStream.getChannel();
            long startOffset = context.getAssets().openFd(assetName).getStartOffset();
            long declaredLength = context.getAssets().openFd(assetName).getDeclaredLength();
            return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
        }
    }

    public static void detect(Context context, Bitmap bitmap, DetectionCallback callback) {
        loadModel(context);
        Bitmap workBitmap = scaleBitmap(bitmap, 512);
        Context appCtx = context.getApplicationContext();

        if (diseaseTflite == null) {
            detectWithHeuristics(appCtx, workBitmap, callback);
            return;
        }

        if (gateTflite != null) {
            float coffeeProb = runGateModel(workBitmap);
            Log.d(TAG, String.format(java.util.Locale.US, "Gate coffee=%.3f", coffeeProb));
            if (coffeeProb < GATE_REJECT_COFFEE) {
                callback.onDetected(DetectionResult.notCoffee(appCtx, (1f - coffeeProb) * 100f));
                return;
            }
        }

        try {
            ByteBuffer input = bitmapToModelInput(workBitmap);
            float[][] output = new float[1][7];
            diseaseTflite.run(input, output);
            deliverFromScores(appCtx, output[0], callback);
        } catch (Exception e) {
            Log.e(TAG, "Disease inference failed", e);
            callback.onDetected(DetectionResult.error("Imeshindwa kutambua ugonjwa. Jaribu tena."));
        }
    }

    /** Model trained with Rescaling(1/255) — feed RGB floats in 0..255. */
    private static ByteBuffer bitmapToModelInput(Bitmap bitmap) {
        Bitmap resized = Bitmap.createScaledBitmap(bitmap, IMG_SIZE, IMG_SIZE, true);
        ByteBuffer buffer = ByteBuffer.allocateDirect(4 * IMG_SIZE * IMG_SIZE * 3);
        buffer.order(ByteOrder.nativeOrder());

        int[] pixels = new int[IMG_SIZE * IMG_SIZE];
        resized.getPixels(pixels, 0, IMG_SIZE, 0, 0, IMG_SIZE, IMG_SIZE);
        for (int pixel : pixels) {
            buffer.putFloat((pixel >> 16) & 0xFF);
            buffer.putFloat((pixel >> 8) & 0xFF);
            buffer.putFloat(pixel & 0xFF);
        }
        buffer.rewind();
        if (resized != bitmap) resized.recycle();
        return buffer;
    }

    private static float runGateModel(Bitmap bitmap) {
        try {
            ByteBuffer input = bitmapToModelInput(bitmap);
            float[][] output = new float[1][2];
            gateTflite.run(input, output);
            return output[0][0];
        } catch (Exception e) {
            Log.e(TAG, "Gate inference failed", e);
            return 1f;
        }
    }

    private static void deliverFromScores(Context ctx, float[] scores, DetectionCallback callback) {
        int argmax = 0;
        float maxScore = scores[0];
        for (int i = 1; i < scores.length; i++) {
            if (scores[i] > maxScore) {
                maxScore = scores[i];
                argmax = i;
            }
        }

        int bestDiseaseIdx = FIRST_DISEASE_INDEX;
        float bestDiseaseScore = scores[FIRST_DISEASE_INDEX];
        for (int i = FIRST_DISEASE_INDEX + 1; i <= LAST_DISEASE_INDEX; i++) {
            if (scores[i] > bestDiseaseScore) {
                bestDiseaseScore = scores[i];
                bestDiseaseIdx = i;
            }
        }

        float healthyScore = scores[0];
        float notCoffeeScore = scores[NOT_COFFEE_CLASS_INDEX];

        Log.d(TAG, String.format(java.util.Locale.US,
                "argmax=%s %.2f healthy=%.2f bestDis=%s %.2f notCoffee=%.2f",
                LABELS[argmax], maxScore, healthyScore, LABELS[bestDiseaseIdx],
                bestDiseaseScore, notCoffeeScore));

        if (argmax == NOT_COFFEE_CLASS_INDEX && notCoffeeScore >= NOT_COFFEE_MIN) {
            callback.onDetected(DetectionResult.notCoffee(ctx, notCoffeeScore * 100f));
            return;
        }

        if (argmax == 0 && healthyScore >= NOT_COFFEE_MIN) {
            float conf = healthyScore * 100f;
            if (conf < MIN_CONFIDENCE_PERCENT) {
                callback.onDetected(DetectionResult.uncertain(ctx, conf));
            } else {
                callback.onDetected(DetectionResult.healthyCoffee(ctx, conf));
            }
            return;
        }

        if (bestDiseaseIdx >= FIRST_DISEASE_INDEX && bestDiseaseScore >= healthyScore
                && bestDiseaseScore >= NOT_COFFEE_MIN) {
            float conf = bestDiseaseScore * 100f;
            if (conf < MIN_CONFIDENCE_PERCENT) {
                callback.onDetected(DetectionResult.uncertain(ctx, conf));
            } else {
                callback.onDetected(DetectionResult.diseasedCoffee(ctx, LABELS[bestDiseaseIdx], conf));
            }
            return;
        }

        if (notCoffeeScore >= NOT_COFFEE_MIN && notCoffeeScore >= healthyScore
                && notCoffeeScore >= bestDiseaseScore) {
            callback.onDetected(DetectionResult.notCoffee(ctx, notCoffeeScore * 100f));
            return;
        }

        callback.onDetected(DetectionResult.uncertain(ctx, maxScore * 100f));
    }

    private static Bitmap scaleBitmap(Bitmap bitmap, int maxSize) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        if (w <= maxSize && h <= maxSize) {
            return bitmap.copy(Bitmap.Config.ARGB_8888, false);
        }
        float scale = maxSize / (float) Math.max(w, h);
        int nw = Math.max(1, Math.round(w * scale));
        int nh = Math.max(1, Math.round(h * scale));
        return Bitmap.createScaledBitmap(bitmap, nw, nh, true);
    }

    private static void detectWithHeuristics(Context ctx, Bitmap bitmap, DetectionCallback callback) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        int step = Math.max(1, Math.min(w, h) / 64);

        int green = 0, coffeeGreen = 0, total = 0;

        for (int y = 0; y < h; y += step) {
            for (int x = 0; x < w; x += step) {
                int pixel = bitmap.getPixel(x, y);
                int r = Color.red(pixel);
                int g = Color.green(pixel);
                int b = Color.blue(pixel);
                total++;
                if (g > 55 && g > r + 10 && g > b + 6) green++;
                if (g > 65 && g >= r && g >= b && r < 150) coffeeGreen++;
            }
        }

        if (total == 0) {
            callback.onDetected(DetectionResult.error("Imeshindwa kusoma picha."));
            return;
        }

        float coffeeGreenRatio = coffeeGreen / (float) total;
        float greenRatio = green / (float) total;

        if (coffeeGreenRatio < 0.04f && greenRatio < 0.05f) {
            callback.onDetected(DetectionResult.notCoffee(ctx, 70f));
            return;
        }

        callback.onDetected(DetectionResult.uncertain(ctx, 45f));
    }

    public static int getDrawableForDisease(String disease) {
        if (disease == null) return R.drawable.coffee_leaf_sample;
        switch (disease) {
            case "Healthy":      return R.drawable.photo_healthy_coffee;
            case "Rust":         return R.drawable.photo_rust_disease;
            case "BerryDisease": return R.drawable.photo_berry_disease;
            case "Wilt":         return R.drawable.photo_wilt_disease;
            case "LeafMiner":    return R.drawable.photo_leaf_miner;
            case "RootRot":      return R.drawable.photo_root_rot;
            case NOT_COFFEE_LABEL: return R.drawable.coffee;
            default:             return R.drawable.coffee_leaf_sample;
        }
    }
}
