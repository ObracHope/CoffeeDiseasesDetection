package com.example.coffeediseasesdetection.admin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Aggregated admin dashboard metrics — mirrors web firebase-data.js computeStats(). */
public class AdminOverview {

    // Primary overview cards (web dashboard top row)
    public int totalFarmers;
    public int totalScans;
    public int diseasesDetected;
    public int activityLogsCount;

    // System overview panel
    public int onlineUsers;
    public int todayScans;
    public int activeFarmers;
    public String systemHealth = "Good";

    // Secondary metrics
    public int imagesUploaded;
    public int healthyCount;
    public int infectedCount;
    public int pendingChallenges;
    public int reportsCount;
    public int farmersOnMap;

    public boolean liveSync;

    /** Last 6 month labels e.g. "Jan 26" */
    public final List<String> monthLabels = new ArrayList<>();
    /** Scan counts per month (same order as monthLabels) */
    public final List<Integer> monthCounts = new ArrayList<>();

    /** Disease name → count for charts */
    public final List<Map.Entry<String, Integer>> topDiseases = new ArrayList<>();

    /** Region/district → diseased scan count */
    public final List<Map.Entry<String, Integer>> topRegions = new ArrayList<>();

    public final List<Map<String, Object>> recentScans = new ArrayList<>();
    public final List<Map<String, Object>> recentFarmers = new ArrayList<>();

    /** Map markers: [latitude, longitude] */
    public final List<double[]> mapMarkers = new ArrayList<>();

    /** @deprecated use topRegions */
    public final List<String> topDiseasedAreas = new ArrayList<>();
}
