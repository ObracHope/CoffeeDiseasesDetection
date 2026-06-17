package com.example.coffeediseasesdetection;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;

import com.example.coffeediseasesdetection.admin.AdminOverview;
import com.google.firebase.Timestamp;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Export admin reports and disease catalog to PDF or Excel (.xls). */
public final class ReportExportHelper {

    public static final String FORMAT_PDF = "pdf";
    public static final String FORMAT_EXCEL = "excel";

    private static final int PAGE_W = 595;
    private static final int PAGE_H = 842;
    private static final int MARGIN = 40;
    private static final int LINE_H = 16;

    private ReportExportHelper() {}

    public static void exportAdminReport(Context context, AdminOverview overview,
                                         List<Map<String, Object>> scans, String format,
                                         @NonNull ExportCallback callback) {
        try {
            String stamp = fileStamp();
            File file;
            String mime;
            if (FORMAT_PDF.equals(format)) {
                file = writePdf(context, buildAdminReportPdf(context, overview, scans),
                        "coffee_report_" + stamp + ".pdf");
                mime = "application/pdf";
            } else {
                file = writeExcelHtml(context, buildAdminReportHtml(context, overview, scans),
                        "coffee_report_" + stamp + ".xls");
                mime = "application/vnd.ms-excel";
            }
            Uri uri = saveAndGetUri(context, file, mime);
            callback.onSuccess(file, uri, mime);
        } catch (Exception e) {
            callback.onError(e);
        }
    }

    public static void exportDiseaseCatalog(Context context, String format,
                                            @NonNull ExportCallback callback) {
        try {
            String stamp = fileStamp();
            File file;
            String mime;
            if (FORMAT_PDF.equals(format)) {
                file = writePdf(context, buildDiseaseCatalogPdf(context), "disease_database_" + stamp + ".pdf");
                mime = "application/pdf";
            } else {
                file = writeExcelHtml(context, buildDiseaseCatalogHtml(context),
                        "disease_database_" + stamp + ".xls");
                mime = "application/vnd.ms-excel";
            }
            Uri uri = saveAndGetUri(context, file, mime);
            callback.onSuccess(file, uri, mime);
        } catch (Exception e) {
            callback.onError(e);
        }
    }

    public static void openShareSheet(Context context, Uri uri, String mimeType, String title) {
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType(mimeType);
        share.putExtra(Intent.EXTRA_STREAM, uri);
        share.putExtra(Intent.EXTRA_SUBJECT, title);
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        context.startActivity(Intent.createChooser(share, context.getString(R.string.export_share_title)));
    }

    public interface ExportCallback {
        void onSuccess(File file, Uri uri, String mimeType);

        void onError(Exception e);
    }

    private static String fileStamp() {
        return new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
    }

    private static File writePdf(Context context, List<String> lines, String fileName) throws IOException {
        PdfDocument doc = new PdfDocument();
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextSize(11f);
        paint.setColor(Color.BLACK);

        Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        titlePaint.setTextSize(16f);
        titlePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        titlePaint.setColor(Color.parseColor("#33691E"));

        int pageNum = 1;
        int y = MARGIN;
        PdfDocument.Page page = startPage(doc, pageNum);
        Canvas canvas = page.getCanvas();

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            boolean isTitle = i == 0;
            Paint p = isTitle ? titlePaint : paint;
            int lh = isTitle ? 24 : LINE_H;

            if (y + lh > PAGE_H - MARGIN) {
                doc.finishPage(page);
                pageNum++;
                page = startPage(doc, pageNum);
                canvas = page.getCanvas();
                y = MARGIN;
            }

            if (line.startsWith("## ")) {
                paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
                canvas.drawText(line.substring(3), MARGIN, y, paint);
                paint.setTypeface(Typeface.DEFAULT);
            } else {
                canvas.drawText(line, MARGIN, y, p);
            }
            y += lh;
        }
        doc.finishPage(page);

        File out = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName);
        if (out.getParentFile() != null) out.getParentFile().mkdirs();
        try (FileOutputStream fos = new FileOutputStream(out)) {
            doc.writeTo(fos);
        }
        doc.close();
        return out;
    }

    private static PdfDocument.Page startPage(PdfDocument doc, int pageNum) {
        PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNum).create();
        return doc.startPage(info);
    }

    private static File writeExcelHtml(Context context, String html, String fileName) throws IOException {
        File out = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName);
        if (out.getParentFile() != null) out.getParentFile().mkdirs();
        try (FileOutputStream fos = new FileOutputStream(out)) {
            fos.write(html.getBytes(StandardCharsets.UTF_8));
        }
        return out;
    }

    private static Uri saveAndGetUri(Context context, File file, String mimeType) throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentResolver resolver = context.getContentResolver();
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, file.getName());
            values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/CoffeeDiseases");
            Uri collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
            Uri uri = resolver.insert(collection, values);
            if (uri != null) {
                try (OutputStream os = resolver.openOutputStream(uri)) {
                    if (os != null) copyFileToStream(file, os);
                }
            }
        } else {
            File downloads = new File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "CoffeeDiseases");
            if (!downloads.exists()) downloads.mkdirs();
            File dest = new File(downloads, file.getName());
            copyFileToStream(file, new FileOutputStream(dest));
        }
        return FileProvider.getUriForFile(context,
                context.getPackageName() + ".fileprovider", file);
    }

    private static void copyFileToStream(File source, OutputStream os) throws IOException {
        java.io.FileInputStream fis = new java.io.FileInputStream(source);
        byte[] buf = new byte[8192];
        int n;
        while ((n = fis.read(buf)) > 0) {
            os.write(buf, 0, n);
        }
        fis.close();
        os.close();
    }

    private static List<String> buildAdminReportPdf(Context context, AdminOverview o,
                                                    List<Map<String, Object>> scans) {
        List<String> lines = new java.util.ArrayList<>();
        String now = new SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()).format(new Date());
        lines.add(context.getString(R.string.export_report_title));
        lines.add(context.getString(R.string.export_generated, now));
        lines.add("");
        lines.add("## " + context.getString(R.string.export_summary_section));
        lines.add(context.getString(R.string.admin_total_scans) + ": " + o.totalScans);
        lines.add(context.getString(R.string.diseases_detected) + ": " + o.diseasesDetected);
        lines.add(context.getString(R.string.admin_camera_scans) + ": " + o.cameraScans);
        lines.add(context.getString(R.string.admin_upload_scans) + ": " + o.uploadScans);
        lines.add(context.getString(R.string.admin_health_coffee) + ": " + o.healthCoffeeCount);
        lines.add(context.getString(R.string.admin_not_coffee) + ": " + o.notCoffeeCount);
        lines.add(context.getString(R.string.total_farmers) + ": " + o.totalFarmers);
        lines.add(context.getString(R.string.export_pending_feedback) + ": " + o.pendingChallenges);
        lines.add(context.getString(R.string.export_today_scans) + ": " + o.todayScans);
        if (!o.scansByRole.isEmpty()) {
            lines.add("");
            lines.add("## " + context.getString(R.string.admin_scan_by_role));
            for (Map.Entry<String, Integer> e : o.scansByRole.entrySet()) {
                int cam = o.cameraByRole.getOrDefault(e.getKey(), 0);
                int up = o.uploadByRole.getOrDefault(e.getKey(), 0);
                lines.add("  - " + e.getKey() + ": " + e.getValue()
                        + " (" + cam + " cam · " + up + " up)");
            }
        }
        if (!o.topDiseasedAreas.isEmpty()) {
            lines.add("");
            lines.add("## " + context.getString(R.string.export_top_areas));
            for (String area : o.topDiseasedAreas) {
                lines.add("  - " + area);
            }
        }
        if (!o.monthLabels.isEmpty() && !o.monthCounts.isEmpty()) {
            lines.add("");
            lines.add("## " + context.getString(R.string.export_chart_section));
            lines.add("## " + context.getString(R.string.export_monthly_trend));
            for (int i = 0; i < o.monthLabels.size() && i < o.monthCounts.size(); i++) {
                lines.add("  " + o.monthLabels.get(i) + ": " + o.monthCounts.get(i) + " scans");
            }
        }
        if (!o.topDiseases.isEmpty()) {
            lines.add("");
            lines.add("## " + context.getString(R.string.export_disease_breakdown));
            for (Map.Entry<String, Integer> e : o.topDiseases) {
                lines.add("  - " + e.getKey() + ": " + e.getValue());
            }
        }
        lines.add("");
        lines.add("## " + context.getString(R.string.export_scan_history));
        lines.add(context.getString(R.string.export_scan_header));
        for (Map<String, Object> scan : scans) {
            lines.add(formatScanLine(context, scan));
        }
        return lines;
    }

    private static String buildAdminReportHtml(Context context, AdminOverview o,
                                               List<Map<String, Object>> scans) {
        String now = new SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()).format(new Date());
        StringBuilder sb = new StringBuilder();
        sb.append("<html><head><meta charset=\"UTF-8\"><style>")
                .append("table{border-collapse:collapse;width:100%}th,td{border:1px solid #999;padding:6px}")
                .append("th{background:#33691E;color:#fff}</style></head><body>");
        sb.append("<h2>").append(escape(context.getString(R.string.export_report_title))).append("</h2>");
        sb.append("<p>").append(escape(context.getString(R.string.export_generated, now))).append("</p>");
        sb.append("<h3>").append(escape(context.getString(R.string.export_summary_section))).append("</h3><table>");
        appendHtmlRow(sb, context.getString(R.string.admin_total_scans), String.valueOf(o.totalScans));
        appendHtmlRow(sb, context.getString(R.string.diseases_detected), String.valueOf(o.diseasesDetected));
        appendHtmlRow(sb, context.getString(R.string.admin_camera_scans), String.valueOf(o.cameraScans));
        appendHtmlRow(sb, context.getString(R.string.admin_upload_scans), String.valueOf(o.uploadScans));
        appendHtmlRow(sb, context.getString(R.string.admin_health_coffee), String.valueOf(o.healthCoffeeCount));
        appendHtmlRow(sb, context.getString(R.string.admin_not_coffee), String.valueOf(o.notCoffeeCount));
        appendHtmlRow(sb, context.getString(R.string.total_farmers), String.valueOf(o.totalFarmers));
        appendHtmlRow(sb, context.getString(R.string.export_pending_feedback), String.valueOf(o.pendingChallenges));
        appendHtmlRow(sb, context.getString(R.string.export_today_scans), String.valueOf(o.todayScans));
        sb.append("</table>");
        if (!o.scansByRole.isEmpty()) {
            sb.append("<h3>").append(escape(context.getString(R.string.admin_scan_by_role))).append("</h3><table>");
            sb.append("<tr><th>Role</th><th>Total</th><th>Camera</th><th>Upload</th></tr>");
            for (Map.Entry<String, Integer> e : o.scansByRole.entrySet()) {
                sb.append("<tr>")
                        .append("<td>").append(escape(e.getKey())).append("</td>")
                        .append("<td>").append(e.getValue()).append("</td>")
                        .append("<td>").append(o.cameraByRole.getOrDefault(e.getKey(), 0)).append("</td>")
                        .append("<td>").append(o.uploadByRole.getOrDefault(e.getKey(), 0)).append("</td>")
                        .append("</tr>");
            }
            sb.append("</table>");
        }
        if (!o.topDiseasedAreas.isEmpty()) {
            sb.append("<h3>").append(escape(context.getString(R.string.export_top_areas))).append("</h3><ul>");
            for (String area : o.topDiseasedAreas) {
                sb.append("<li>").append(escape(area)).append("</li>");
            }
            sb.append("</ul>");
        }
        if (!o.monthLabels.isEmpty() && !o.monthCounts.isEmpty()) {
            sb.append("<h3>").append(escape(context.getString(R.string.export_chart_section))).append("</h3>");
            sb.append("<h4>").append(escape(context.getString(R.string.export_monthly_trend))).append("</h4><table>");
            sb.append("<tr><th>Month</th><th>Scans</th></tr>");
            for (int i = 0; i < o.monthLabels.size() && i < o.monthCounts.size(); i++) {
                sb.append("<tr><td>").append(escape(o.monthLabels.get(i))).append("</td>")
                        .append("<td>").append(o.monthCounts.get(i)).append("</td></tr>");
            }
            sb.append("</table>");
        }
        if (!o.topDiseases.isEmpty()) {
            sb.append("<h4>").append(escape(context.getString(R.string.export_disease_breakdown))).append("</h4><table>");
            sb.append("<tr><th>Disease</th><th>Count</th></tr>");
            for (Map.Entry<String, Integer> e : o.topDiseases) {
                sb.append("<tr><td>").append(escape(e.getKey())).append("</td>")
                        .append("<td>").append(e.getValue()).append("</td></tr>");
            }
            sb.append("</table>");
        }
        sb.append("<h3>").append(escape(context.getString(R.string.export_scan_history))).append("</h3>");
        sb.append("<table><tr><th>Date</th><th>Farmer</th><th>Disease</th><th>Confidence</th><th>Location</th></tr>");
        for (Map<String, Object> scan : scans) {
            sb.append("<tr>")
                    .append("<td>").append(escape(formatTimestamp(scan.get("timestamp")))).append("</td>")
                    .append("<td>").append(escape(scanField(scan, "userEmail", "userName", "userId"))).append("</td>")
                    .append("<td>").append(escape(scanField(scan, "diseaseName", "disease"))).append("</td>")
                    .append("<td>").append(escape(formatConfidence(scan.get("confidence")))).append("</td>")
                    .append("<td>").append(escape(formatLocation(scan))).append("</td>")
                    .append("</tr>");
        }
        sb.append("</table></body></html>");
        return sb.toString();
    }

    private static List<String> buildDiseaseCatalogPdf(Context context) {
        List<String> lines = new java.util.ArrayList<>();
        String now = new SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()).format(new Date());
        lines.add(context.getString(R.string.disease_database));
        lines.add(context.getString(R.string.export_generated, now));
        lines.add("");
        for (String key : DiseaseCatalog.ALL_CONDITIONS) {
            lines.add("## " + DiseaseTextProvider.displayName(context, key));
            lines.add(context.getString(R.string.export_scientific) + ": "
                    + DiseaseCatalog.scientificName(context, key));
            lines.add(context.getString(R.string.symptoms_label) + " "
                    + DiseaseTextProvider.symptoms(context, key));
            lines.add(context.getString(R.string.treatment_label) + " "
                    + DiseaseTextProvider.treatment(context, key));
            lines.add("");
        }
        return lines;
    }

    private static String buildDiseaseCatalogHtml(Context context) {
        String now = new SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()).format(new Date());
        StringBuilder sb = new StringBuilder();
        sb.append("<html><head><meta charset=\"UTF-8\"><style>")
                .append("table{border-collapse:collapse;width:100%}th,td{border:1px solid #999;padding:6px}")
                .append("th{background:#33691E;color:#fff}</style></head><body>");
        sb.append("<h2>").append(escape(context.getString(R.string.disease_database))).append("</h2>");
        sb.append("<p>").append(escape(context.getString(R.string.export_generated, now))).append("</p>");
        sb.append("<table><tr><th>Disease</th><th>Scientific</th><th>Symptoms</th><th>Treatment</th></tr>");
        for (String key : DiseaseCatalog.ALL_CONDITIONS) {
            sb.append("<tr>")
                    .append("<td>").append(escape(DiseaseTextProvider.displayName(context, key))).append("</td>")
                    .append("<td>").append(escape(DiseaseCatalog.scientificName(context, key))).append("</td>")
                    .append("<td>").append(escape(DiseaseTextProvider.symptoms(context, key))).append("</td>")
                    .append("<td>").append(escape(DiseaseTextProvider.treatment(context, key))).append("</td>")
                    .append("</tr>");
        }
        sb.append("</table></body></html>");
        return sb.toString();
    }

    private static void appendHtmlRow(StringBuilder sb, String label, String value) {
        sb.append("<tr><td><b>").append(escape(label)).append("</b></td><td>")
                .append(escape(value)).append("</td></tr>");
    }

    private static String formatScanLine(Context context, Map<String, Object> scan) {
        return formatTimestamp(scan.get("timestamp")) + " | "
                + scanField(scan, "userEmail", "userName", "userId") + " | "
                + scanField(scan, "diseaseName", "disease") + " | "
                + formatConfidence(scan.get("confidence")) + " | "
                + formatLocation(scan);
    }

    private static String scanField(Map<String, Object> scan, String... keys) {
        for (String key : keys) {
            Object v = scan.get(key);
            if (v != null && !String.valueOf(v).trim().isEmpty()) {
                return String.valueOf(v).trim();
            }
        }
        return "-";
    }

    private static String formatLocation(Map<String, Object> scan) {
        String region = scanField(scan, "region");
        String district = scanField(scan, "district");
        String ward = scanField(scan, "ward");
        if ("-".equals(region) && "-".equals(district) && "-".equals(ward)) return "-";
        StringBuilder sb = new StringBuilder();
        if (!"-".equals(region)) sb.append(region);
        if (!"-".equals(district)) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(district);
        }
        if (!"-".equals(ward)) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(ward);
        }
        return sb.toString();
    }

    private static String formatConfidence(Object conf) {
        if (conf == null) return "-";
        if (conf instanceof Number) {
            float v = ((Number) conf).floatValue();
            return v <= 1f ? String.format(Locale.US, "%.0f%%", v * 100f)
                    : String.format(Locale.US, "%.0f%%", v);
        }
        return String.valueOf(conf);
    }

    private static String formatTimestamp(Object ts) {
        if (ts instanceof Timestamp) {
            return new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                    .format(((Timestamp) ts).toDate());
        }
        if (ts instanceof Date) {
            return new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format((Date) ts);
        }
        return ts != null ? String.valueOf(ts) : "-";
    }

    private static String escape(String s) {
        if (TextUtils.isEmpty(s)) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
