package com.example.coffeediseasesdetection;

public class NotificationModel {
    private final String title;
    private final String message;
    private final String timestamp;
    private final String scanId;
    private final String imageUrl;
    private final String imagePath;
    private final String diseaseName;
    private final String confidence;
    private final boolean fromScan;

    public NotificationModel(String title, String message, String timestamp) {
        this(title, message, timestamp, null, null, null, null, null, false);
    }

    public NotificationModel(String title, String message, String timestamp,
                             String scanId, String imageUrl, String imagePath,
                             String diseaseName, String confidence, boolean fromScan) {
        this.title = title;
        this.message = message;
        this.timestamp = timestamp;
        this.scanId = scanId;
        this.imageUrl = imageUrl;
        this.imagePath = imagePath;
        this.diseaseName = diseaseName;
        this.confidence = confidence;
        this.fromScan = fromScan;
    }

    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public String getTimestamp() { return timestamp; }
    public String getScanId() { return scanId; }
    public String getImageUrl() { return imageUrl; }
    public String getImagePath() { return imagePath; }
    public String getDiseaseName() { return diseaseName; }
    public String getConfidence() { return confidence; }
    public boolean isFromScan() { return fromScan; }
}
