package com.example.coffeediseasesdetection.weather;

import android.content.Context;
import android.location.Geocoder;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.coffeediseasesdetection.LocationHelper;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Fetches live weather via Open-Meteo (free, no API key) using GPS; fallback: Moshi. */
public final class WeatherService {

    private static final String TAG = "WeatherService";
    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public interface Callback {
        void onResult(WeatherInfo info);
    }

    private WeatherService() {}

    public static void fetch(Context context, Callback callback) {
        Context app = context.getApplicationContext();
        EXEC.execute(() -> {
            WeatherInfo info = load(app);
            MAIN.post(() -> {
                if (callback != null) callback.onResult(info);
            });
        });
    }

    private static WeatherInfo load(Context context) {
        double lat = WeatherInfo.DEFAULT_LAT;
        double lon = WeatherInfo.DEFAULT_LON;
        boolean usedGps = false;

        if (LocationHelper.hasPermission(context)) {
            LocationHelper.LatLng coords = fetchCoordsBlocking(context);
            if (coords != null) {
                lat = coords.latitude;
                lon = coords.longitude;
                usedGps = true;
            }
        }

        try {
            String url = "https://api.open-meteo.com/v1/forecast?latitude=" + lat
                    + "&longitude=" + lon
                    + "&current=temperature_2m,relative_humidity_2m,weather_code";
            HttpURLConnection conn = openGet(url);
            if (conn == null) {
                return fallbackWithLocation(context, lat, lon, usedGps);
            }

            int code = conn.getResponseCode();
            if (code != 200) {
                Log.w(TAG, "Open-Meteo HTTP " + code);
                conn.disconnect();
                return fallbackWithLocation(context, lat, lon, usedGps);
            }

            String body = readBody(conn);
            conn.disconnect();

            JSONObject root = new JSONObject(body);
            JSONObject current = root.getJSONObject("current");
            int temp = (int) Math.round(current.getDouble("temperature_2m"));
            int humidity = current.getInt("relative_humidity_2m");
            int weatherCode = current.getInt("weather_code");
            String condition = mapWeatherCode(weatherCode);
            String location = resolveLocationName(context, lat, lon);

            return new WeatherInfo(location, temp, humidity, condition, false);
        } catch (Exception e) {
            Log.w(TAG, "Weather fetch failed", e);
            return fallbackWithLocation(context, lat, lon, usedGps);
        }
    }

    private static String mapWeatherCode(int code) {
        if (code == 0) return "Clear";
        if (code == 1 || code == 2) return "Clear";
        if (code == 3) return "Clouds";
        if (code == 45 || code == 48) return "Fog";
        if (code >= 51 && code <= 57) return "Drizzle";
        if (code >= 61 && code <= 67) return "Rain";
        if (code >= 71 && code <= 77) return "Snow";
        if (code >= 80 && code <= 82) return "Rain";
        if (code >= 85 && code <= 86) return "Snow";
        if (code >= 95) return "Thunderstorm";
        return "Clear";
    }

    private static WeatherInfo fallbackWithLocation(Context context, double lat, double lon, boolean usedGps) {
        String loc = usedGps ? resolveLocationName(context, lat, lon) : WeatherInfo.DEFAULT_LOCATION;
        return new WeatherInfo(loc, WeatherInfo.DEFAULT_TEMP_C, WeatherInfo.DEFAULT_HUMIDITY,
                WeatherInfo.DEFAULT_CONDITION, true);
    }

    private static LocationHelper.LatLng fetchCoordsBlocking(Context context) {
        final LocationHelper.LatLng[] holder = new LocationHelper.LatLng[1];
        final Object lock = new Object();
        LocationHelper.fetchLocation(context, coords -> {
            synchronized (lock) {
                holder[0] = coords;
                lock.notifyAll();
            }
        });
        synchronized (lock) {
            try {
                lock.wait(8000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return holder[0];
    }

    private static String resolveLocationName(Context context, double lat, double lon) {
        String fromGeocoder = resolveFromGeocoder(context, lat, lon);
        if (fromGeocoder != null) return fromGeocoder;

        String fromOsm = resolveFromOpenStreetMap(lat, lon);
        if (fromOsm != null) return fromOsm;

        return WeatherInfo.DEFAULT_LOCATION;
    }

    private static String resolveFromGeocoder(Context context, double lat, double lon) {
        try {
            if (!Geocoder.isPresent()) return null;
            Geocoder geocoder = new Geocoder(context, Locale.getDefault());
            List<android.location.Address> list = geocoder.getFromLocation(lat, lon, 1);
            if (list == null || list.isEmpty()) return null;
            return formatAddress(list.get(0));
        } catch (Exception e) {
            Log.w(TAG, "Geocoder failed", e);
            return null;
        }
    }

    private static String resolveFromOpenStreetMap(double lat, double lon) {
        try {
            String url = "https://nominatim.openstreetmap.org/reverse?lat=" + lat
                    + "&lon=" + lon + "&format=json&accept-language=en";
            HttpURLConnection conn = openGet(url);
            if (conn == null) return null;
            conn.setRequestProperty("User-Agent", "CoffeeDiseasesDetection/1.0");
            if (conn.getResponseCode() != 200) {
                conn.disconnect();
                return null;
            }
            String body = readBody(conn);
            conn.disconnect();

            JSONObject root = new JSONObject(body);
            JSONObject address = root.optJSONObject("address");
            if (address == null) return null;

            String city = firstNonEmpty(
                    address.optString("city", null),
                    address.optString("town", null),
                    address.optString("village", null),
                    address.optString("municipality", null)
            );
            String region = firstNonEmpty(
                    address.optString("state", null),
                    address.optString("region", null),
                    address.optString("state_district", null)
            );

            if (city != null && region != null && !city.equalsIgnoreCase(region)) {
                if (city.equalsIgnoreCase("Moshi")) return "Moshi, Kilimanjaro";
                return city + ", " + region;
            }
            if (city != null) {
                if (city.equalsIgnoreCase("Moshi")) return "Moshi, Kilimanjaro";
                return city;
            }
            if (region != null) return region;
        } catch (Exception e) {
            Log.w(TAG, "OSM reverse geocode failed", e);
        }
        return null;
    }

    private static String formatAddress(android.location.Address a) {
        String city = firstNonEmpty(a.getLocality(), a.getSubAdminArea(), a.getAdminArea());
        String region = firstNonEmpty(a.getAdminArea(), a.getSubAdminArea());
        if (city != null && city.equalsIgnoreCase("Moshi")) return "Moshi, Kilimanjaro";
        if (city != null && region != null && !city.equalsIgnoreCase(region)) {
            return city + ", " + region;
        }
        if (city != null) return city;
        if (region != null) return region;
        return null;
    }

    private static HttpURLConnection openGet(String urlString) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();
            conn.setConnectTimeout(12_000);
            conn.setReadTimeout(12_000);
            conn.setRequestMethod("GET");
            return conn;
        } catch (Exception e) {
            Log.w(TAG, "Connection failed: " + urlString, e);
            return null;
        }
    }

    private static String readBody(HttpURLConnection conn) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }

    private static String firstNonEmpty(String... values) {
        if (values == null) return null;
        for (String v : values) {
            if (v != null && !v.trim().isEmpty()) return v.trim();
        }
        return null;
    }
}
