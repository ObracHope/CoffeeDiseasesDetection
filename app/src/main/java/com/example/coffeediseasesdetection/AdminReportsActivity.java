package com.example.coffeediseasesdetection;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.example.coffeediseasesdetection.admin.AdminOverview;
import com.example.coffeediseasesdetection.admin.AdminRepository;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AdminReportsActivity extends BaseActivity {

    private AdminOverview cachedOverview;
    private List<Map<String, Object>> cachedScans;
    private boolean exportInProgress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_reports);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        TextView tvSummary = findViewById(R.id.tvReportSummary);
        BarChart chart = findViewById(R.id.barChartReports);

        View.OnClickListener exportClick = v -> showExportFormatDialog();
        findViewById(R.id.btnExportReport).setOnClickListener(exportClick);
        findViewById(R.id.btnExportReportMain).setOnClickListener(exportClick);

        new AdminRepository().loadOverview(new AdminRepository.OverviewCallback() {
            @Override
            public void onSuccess(AdminOverview o) {
                if (isFinishing()) return;
                cachedOverview = o;
                String summary = getString(R.string.admin_report_summary,
                        o.totalScans, o.diseasesDetected, o.imagesUploaded,
                        o.totalFarmers, o.pendingChallenges);
                tvSummary.setText(summary);
                setupChart(chart, o);
                new AdminRepository().logActivity(AdminReportsActivity.this,
                        "view_report", "Opened admin reports");
            }

            @Override
            public void onError(Exception e) {
                if (!isFinishing()) {
                    tvSummary.setText(R.string.error_loading_data);
                }
            }
        });

        findViewById(R.id.btnOpenAnalytics).setOnClickListener(v ->
                startActivity(new android.content.Intent(this, AdminStatisticsActivity.class)));
    }

    private void showExportFormatDialog() {
        if (cachedOverview == null) {
            Toast.makeText(this, R.string.error_loading_data, Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.export_choose_format)
                .setItems(new CharSequence[]{
                        getString(R.string.export_as_pdf),
                        getString(R.string.export_as_excel)
                }, (d, which) -> {
                    String format = which == 0
                            ? ReportExportHelper.FORMAT_PDF
                            : ReportExportHelper.FORMAT_EXCEL;
                    startExport(format);
                })
                .show();
    }

    private void startExport(String format) {
        if (exportInProgress) return;
        exportInProgress = true;
        Toast.makeText(this, R.string.export_in_progress, Toast.LENGTH_SHORT).show();

        Runnable doExport = () -> ReportExportHelper.exportAdminReport(
                AdminReportsActivity.this, cachedOverview, cachedScans, format,
                new ReportExportHelper.ExportCallback() {
                    @Override
                    public void onSuccess(java.io.File file, android.net.Uri uri, String mimeType) {
                        exportInProgress = false;
                        if (isFinishing()) return;
                        Toast.makeText(AdminReportsActivity.this,
                                R.string.export_success, Toast.LENGTH_LONG).show();
                        ReportExportHelper.openShareSheet(AdminReportsActivity.this, uri, mimeType,
                                getString(R.string.export_report_title));
                        new AdminRepository().logActivity(AdminReportsActivity.this,
                                "export_report", "Exported report as " + format);
                    }

                    @Override
                    public void onError(Exception e) {
                        exportInProgress = false;
                        if (!isFinishing()) {
                            String msg = e.getMessage() != null ? e.getMessage() : "Error";
                            Toast.makeText(AdminReportsActivity.this,
                                    getString(R.string.export_failed, msg), Toast.LENGTH_LONG).show();
                        }
                    }
                });

        if (cachedScans != null) {
            doExport.run();
            return;
        }

        new AdminRepository().loadAllScans(500, new AdminRepository.ScansCallback() {
            @Override
            public void onSuccess(List<Map<String, Object>> list) {
                cachedScans = list;
                doExport.run();
            }

            @Override
            public void onError(Exception e) {
                exportInProgress = false;
                if (!isFinishing()) {
                    Toast.makeText(AdminReportsActivity.this,
                            R.string.error_loading_data, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void setupChart(BarChart chart, AdminOverview o) {
        if (chart == null) return;
        chart.getDescription().setEnabled(false);
        chart.getAxisRight().setEnabled(false);
        chart.getLegend().setEnabled(false);

        List<BarEntry> entries = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        entries.add(new BarEntry(0, o.totalScans));
        labels.add(getString(R.string.admin_total_scans));
        entries.add(new BarEntry(1, o.diseasesDetected));
        labels.add(getString(R.string.diseases_detected));
        entries.add(new BarEntry(2, o.imagesUploaded));
        labels.add(getString(R.string.images_uploaded));
        entries.add(new BarEntry(3, o.totalFarmers));
        labels.add(getString(R.string.total_farmers));

        BarDataSet set = new BarDataSet(entries, "");
        set.setColors(Color.parseColor("#2E7D32"), Color.parseColor("#E65100"),
                Color.parseColor("#558B2F"), Color.parseColor("#4E342E"));
        chart.setData(new BarData(set));
        chart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        chart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));
        chart.getXAxis().setGranularity(1f);
        chart.animateY(600);
        chart.invalidate();
    }
}
