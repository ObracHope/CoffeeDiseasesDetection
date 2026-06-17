package com.example.coffeediseasesdetection;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import java.util.Locale;

/** Opens DiseaseResultActivity with three-step scan extras. */
public final class DetectionResultLauncher {

    private DetectionResultLauncher() {}

    public static void open(Activity activity, DetectionResult result, String imagePath) {
        open(activity, result, imagePath, ScanRepository.SOURCE_CAMERA);
    }

    public static void open(Activity activity, DetectionResult result, String imagePath, String scanSource) {
        if ("Error".equals(result.diseaseKey)) {
            Toast.makeText(activity, result.description, Toast.LENGTH_LONG).show();
            return;
        }
        if (!result.isCoffee) {
            Toast.makeText(activity, activity.getString(R.string.not_coffee_title), Toast.LENGTH_LONG).show();
        }
        activity.startActivity(buildIntent(activity, result, imagePath, scanSource));
    }

    public static Intent buildIntent(Context context, DetectionResult result, String imagePath) {
        return buildIntent(context, result, imagePath, ScanRepository.SOURCE_CAMERA);
    }

    public static Intent buildIntent(Context context, DetectionResult result, String imagePath, String scanSource) {
        Intent intent = new Intent(context, DiseaseResultActivity.class);
        intent.putExtra("isCoffee", result.isCoffee);
        intent.putExtra("isHealthy", result.isHealthy);
        intent.putExtra("disease", result.diseaseKey);
        intent.putExtra("confidence", String.format(Locale.getDefault(), "%.1f%%", result.confidence));
        intent.putExtra("description", result.description);
        intent.putExtra("symptoms", result.symptoms);
        intent.putExtra("treatment", result.treatment);
        intent.putExtra("step1", result.step1Text(context));
        intent.putExtra("step2", result.step2Text(context));
        intent.putExtra("step3", result.step3Text(context));
        intent.putExtra("imagePath", imagePath);
        intent.putExtra("scanSource", scanSource != null ? scanSource : ScanRepository.SOURCE_CAMERA);
        return intent;
    }
}
