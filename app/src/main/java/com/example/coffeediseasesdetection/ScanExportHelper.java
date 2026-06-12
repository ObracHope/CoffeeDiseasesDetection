package com.example.coffeediseasesdetection;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;

import com.google.firebase.Timestamp;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Export scan records to CSV for admin. */
public final class ScanExportHelper {

    public interface Callback {
        void onSuccess(File file, Uri uri);

        void onError(Exception e);
    }

    private ScanExportHelper() {}

    public static void exportScansCsv(Context context, List<Map<String, Object>> scans,
                                      @NonNull Callback callback) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("id,userId,userName,userEmail,disease,confidence,region,district,timestamp\n");
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

            for (Map<String, Object> s : scans) {
                sb.append(csv(s.get("id")));
                sb.append(',').append(csv(s.get("userId")));
                sb.append(',').append(csv(s.get("userName")));
                sb.append(',').append(csv(s.get("userEmail")));
                String disease = s.get("diseaseName") != null ? String.valueOf(s.get("diseaseName"))
                        : String.valueOf(s.get("disease"));
                sb.append(',').append(csv(disease));
                sb.append(',').append(csv(s.get("confidence")));
                sb.append(',').append(csv(s.get("region")));
                sb.append(',').append(csv(s.get("district")));
                sb.append(',').append(csv(formatTs(s.get("timestamp"), sdf)));
                sb.append('\n');
            }

            String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            File dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
            if (dir == null) dir = context.getFilesDir();
            File file = new File(dir, "scan_records_" + stamp + ".csv");
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(sb.toString().getBytes(StandardCharsets.UTF_8));
            }
            Uri uri = FileProvider.getUriForFile(context,
                    context.getPackageName() + ".fileprovider", file);
            callback.onSuccess(file, uri);
        } catch (Exception e) {
            callback.onError(e);
        }
    }

    public static void shareCsv(Context context, Uri uri) {
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/csv");
        share.putExtra(Intent.EXTRA_STREAM, uri);
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        context.startActivity(Intent.createChooser(share, context.getString(R.string.export_csv)));
    }

    private static String csv(Object value) {
        if (value == null) return "";
        String s = String.valueOf(value).replace("\"", "\"\"");
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s + "\"";
        }
        return s;
    }

    private static String formatTs(Object ts, SimpleDateFormat sdf) {
        if (ts instanceof Timestamp) return sdf.format(((Timestamp) ts).toDate());
        if (ts instanceof Number) return sdf.format(new Date(((Number) ts).longValue()));
        return ts != null ? String.valueOf(ts) : "";
    }
}
