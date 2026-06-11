package com.example.coffeediseasesdetection.model;

public class CoffeeTip {
    private String title;
    private String description;
    private String symptoms;
    private String treatment;
    private int imageRes; // For local resource images

    public CoffeeTip(String title, String description, String symptoms, String treatment, int imageRes) {
        this.title = title;
        this.description = description;
        this.symptoms = symptoms;
        this.treatment = treatment;
        this.imageRes = imageRes;
    }

    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getSymptoms() { return symptoms; }
    public String getTreatment() { return treatment; }
    public int getImageRes() { return imageRes; }
}
