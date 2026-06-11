package com.example.coffeediseasesdetection.ml;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

/**
 * CoffeeDetector
 * Binary classifier to detect if an image contains a coffee leaf or not.
 * Model: coffee_detector.tflite
 * Classes: [0: Coffee, 1: NotCoffee]
 */
public class CoffeeDetector {

    private static final String MODEL_PATH = "coffee_detector.tflite";
    private static final int    IMG_SIZE   = 224;
    private static final int    NUM_CLASSES = 7; // Matches the disease model placeholder

    private final Interpreter tflite;

    public interface Callback {
        void onResult(String label, float confidence);
    }

    public CoffeeDetector(Context context) throws IOException {
        tflite = new Interpreter(loadModelFile(context));
    }

    private MappedByteBuffer loadModelFile(Context context) throws IOException {
        AssetFileDescriptor fileDescriptor = context.getAssets().openFd(MODEL_PATH);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    public void detect(Bitmap bitmap, Callback callback) {
        // 1. Resize
        Bitmap resized = Bitmap.createScaledBitmap(bitmap, IMG_SIZE, IMG_SIZE, true);

        // 2. Preprocess to ByteBuffer (Float32, [0,1])
        ByteBuffer inputBuffer = ByteBuffer.allocateDirect(4 * IMG_SIZE * IMG_SIZE * 3);
        inputBuffer.order(ByteOrder.nativeOrder());

        int[] pixels = new int[IMG_SIZE * IMG_SIZE];
        resized.getPixels(pixels, 0, IMG_SIZE, 0, 0, IMG_SIZE, IMG_SIZE);

        for (int pixel : pixels) {
            inputBuffer.putFloat(((pixel >> 16) & 0xFF) / 255.0f);
            inputBuffer.putFloat(((pixel >> 8) & 0xFF) / 255.0f);
            inputBuffer.putFloat((pixel & 0xFF) / 255.0f);
        }

        // 3. Run Inference
        float[][] output = new float[1][NUM_CLASSES];
        tflite.run(inputBuffer, output);

        // 4. Parse Results
        // Index 6 is "IsNotCoffee" in the 7-class disease model
        float notCoffeeProb = output[0][6];
        
        // Find if NOT COFFEE is the winner OR if it has high confidence
        int maxIdx = 0;
        float maxProb = 0;
        for (int i = 0; i < NUM_CLASSES; i++) {
            if (output[0][i] > maxProb) {
                maxProb = output[0][i];
                maxIdx = i;
            }
        }

        if (maxIdx == 6 || notCoffeeProb > 0.4f) {
            callback.onResult("NotCoffee", notCoffeeProb);
        } else {
            // Treat as coffee (one of the 6 disease/healthy classes)
            // We sum original probabilities for "Coffee" or just take the inverse
            callback.onResult("Coffee", 1.0f - notCoffeeProb);
        }
    }

    public void close() {
        if (tflite != null) {
            tflite.close();
        }
    }
}
