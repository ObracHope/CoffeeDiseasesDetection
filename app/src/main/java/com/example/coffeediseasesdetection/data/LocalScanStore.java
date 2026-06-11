package com.example.coffeediseasesdetection.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

import androidx.annotation.Nullable;
import android.database.sqlite.SQLiteDatabase;

import com.google.firebase.Timestamp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Offline mirror of scan history — works when Firestore is slow or unavailable. */
public final class LocalScanStore {

    private LocalScanStore() {}

    public static void save(Context context, String scanId, Map<String, Object> data) {
        if (context == null || scanId == null || scanId.isEmpty() || data == null) return;

        HistoryDbHelper helper = new HistoryDbHelper(context.getApplicationContext());
        SQLiteDatabase db = helper.getWritableDatabase();

        ContentValues cv = new ContentValues();
        cv.put(HistoryDbHelper.COLUMN_SCAN_ID, scanId);
        cv.put(HistoryDbHelper.COLUMN_USER_ID, stringVal(data.get("userId")));
        cv.put(HistoryDbHelper.COLUMN_DISEASE, stringVal(data.get("disease")));
        cv.put(HistoryDbHelper.COLUMN_DISEASE_NAME, stringVal(data.get("diseaseName")));
        cv.put(HistoryDbHelper.COLUMN_DESCRIPTION, stringVal(data.get("description")));
        cv.put(HistoryDbHelper.COLUMN_IMAGE_PATH, stringVal(data.get("imagePath")));
        cv.put(HistoryDbHelper.COLUMN_IMAGE_URL, stringVal(data.get("imageUrl")));
        cv.put(HistoryDbHelper.COLUMN_ACCURACY, numberVal(data.get("confidence")));
        cv.put(HistoryDbHelper.COLUMN_TIMESTAMP, timestampVal(data.get("timestamp")));

        db.insertWithOnConflict(HistoryDbHelper.TABLE_HISTORY, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    @Nullable
    public static Map<String, Object> getById(Context context, String userId, String scanId) {
        if (context == null || userId == null || scanId == null || scanId.isEmpty()) return null;
        HistoryDbHelper helper = new HistoryDbHelper(context.getApplicationContext());
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor c = db.query(
                HistoryDbHelper.TABLE_HISTORY,
                null,
                HistoryDbHelper.COLUMN_USER_ID + "=? AND " + HistoryDbHelper.COLUMN_SCAN_ID + "=?",
                new String[]{userId, scanId},
                null, null, null, "1")) {
            if (c.moveToFirst()) return cursorToRow(c);
        }
        return null;
    }

    public static List<Map<String, Object>> loadAll(Context context, String userId) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (context == null || userId == null || userId.isEmpty()) return rows;

        HistoryDbHelper helper = new HistoryDbHelper(context.getApplicationContext());
        SQLiteDatabase db = helper.getReadableDatabase();

        try (Cursor c = db.query(
                HistoryDbHelper.TABLE_HISTORY,
                null,
                HistoryDbHelper.COLUMN_USER_ID + "=?",
                new String[]{userId},
                null, null,
                HistoryDbHelper.COLUMN_TIMESTAMP + " DESC")) {
            while (c.moveToNext()) {
                rows.add(cursorToRow(c));
            }
        }
        return rows;
    }

    private static Map<String, Object> cursorToRow(Cursor c) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", c.getString(c.getColumnIndexOrThrow(HistoryDbHelper.COLUMN_SCAN_ID)));
        row.put("userId", c.getString(c.getColumnIndexOrThrow(HistoryDbHelper.COLUMN_USER_ID)));
        row.put("disease", c.getString(c.getColumnIndexOrThrow(HistoryDbHelper.COLUMN_DISEASE)));
        row.put("diseaseName", c.getString(c.getColumnIndexOrThrow(HistoryDbHelper.COLUMN_DISEASE_NAME)));
        row.put("description", c.getString(c.getColumnIndexOrThrow(HistoryDbHelper.COLUMN_DESCRIPTION)));
        row.put("imagePath", c.getString(c.getColumnIndexOrThrow(HistoryDbHelper.COLUMN_IMAGE_PATH)));
        row.put("imageUrl", c.getString(c.getColumnIndexOrThrow(HistoryDbHelper.COLUMN_IMAGE_URL)));
        row.put("confidence", c.getDouble(c.getColumnIndexOrThrow(HistoryDbHelper.COLUMN_ACCURACY)));
        long ts = c.getLong(c.getColumnIndexOrThrow(HistoryDbHelper.COLUMN_TIMESTAMP));
        row.put("timestamp", new Timestamp(new java.util.Date(ts)));
        return row;
    }

    private static String stringVal(Object o) {
        return o != null ? o.toString() : "";
    }

    private static double numberVal(Object o) {
        if (o instanceof Number) return ((Number) o).doubleValue();
        return 0.0;
    }

    private static long timestampVal(Object o) {
        if (o instanceof Timestamp) return ((Timestamp) o).toDate().getTime();
        if (o instanceof Long) return (Long) o;
        return System.currentTimeMillis();
    }
}
