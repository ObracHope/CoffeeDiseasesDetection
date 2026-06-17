package com.example.coffeediseasesdetection.weather;

import androidx.annotation.NonNull;

/** Current weather snapshot for dashboard header. */
public class WeatherInfo {

    public static final double DEFAULT_LAT = -3.3474;
    public static final double DEFAULT_LON = 37.3404;
    public static final String DEFAULT_LOCATION = "Moshi, Kilimanjaro";
    public static final int DEFAULT_TEMP_C = 28;
    public static final int DEFAULT_HUMIDITY = 60;
    public static final String DEFAULT_CONDITION = "Clear";

    public final String location;
    public final int tempC;
    public final int humidity;
    public final String condition;
    public final boolean fromFallback;

    public WeatherInfo(String location, int tempC, int humidity, String condition, boolean fromFallback) {
        this.location = location != null && !location.isEmpty() ? location : DEFAULT_LOCATION;
        this.tempC = tempC;
        this.humidity = humidity;
        this.condition = condition != null ? condition : DEFAULT_CONDITION;
        this.fromFallback = fromFallback;
    }

    public static WeatherInfo fallback() {
        return new WeatherInfo(DEFAULT_LOCATION, DEFAULT_TEMP_C, DEFAULT_HUMIDITY, DEFAULT_CONDITION, true);
    }

    @NonNull
    public String emoji() {
        if (condition == null) return "\u2600\uFE0F";
        switch (condition) {
            case "Clear": return "\u2600\uFE0F";
            case "Clouds": return "\u2601\uFE0F";
            case "Rain": return "\uD83C\uDF27\uFE0F";
            case "Drizzle": return "\uD83C\uDF26\uFE0F";
            case "Thunderstorm": return "\u26C8\uFE0F";
            case "Snow": return "\u2744\uFE0F";
            case "Mist":
            case "Fog":
            case "Haze": return "\uD83C\uDF2B\uFE0F";
            default: return "\u2600\uFE0F";
        }
    }

    @NonNull
    public String formatLine() {
        return emoji() + " " + location + "  \u2022  " + tempC + "\u00B0C  \u2022  Humidity " + humidity + "%";
    }

    @NonNull
    public String formatDetails() {
        return location + "  \u2022  " + tempC + "\u00B0C  \u2022  Humidity " + humidity + "%";
    }
}
