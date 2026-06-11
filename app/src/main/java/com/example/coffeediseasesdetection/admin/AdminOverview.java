package com.example.coffeediseasesdetection.admin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Aggregated admin dashboard metrics from Firestore / RTDB. */
public class AdminOverview {

    public int onlineUsers;
    public int todayScans;
    public int activeFarmers;
    public String systemHealth = "Good";

    public int totalFarmers;
    public int totalScans;
    public int imagesUploaded;
    public int diseasesDetected;
    public int reportsCount;
    public int pendingChallenges;

    public final List<Map<String, Object>> recentScans = new ArrayList<>();
    public final List<String> topDiseasedAreas = new ArrayList<>();
}
