package com.example.coffeediseasesdetection;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class NotificationDbHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "notifications.db";
    private static final int DATABASE_VERSION = 2;

    public static final String TABLE_NAME = "notifications";
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_TITLE = "title";
    public static final String COLUMN_MESSAGE = "message";
    public static final String COLUMN_TIMESTAMP = "timestamp";
    public static final String COLUMN_SCAN_ID = "scan_id";
    public static final String COLUMN_IMAGE_URL = "image_url";
    public static final String COLUMN_IMAGE_PATH = "image_path";
    public static final String COLUMN_DISEASE = "disease_name";
    public static final String COLUMN_CONFIDENCE = "confidence";

    private static final String TABLE_CREATE =
            "CREATE TABLE " + TABLE_NAME + " (" +
                    COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COLUMN_TITLE + " TEXT, " +
                    COLUMN_MESSAGE + " TEXT, " +
                    COLUMN_TIMESTAMP + " DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                    COLUMN_SCAN_ID + " TEXT, " +
                    COLUMN_IMAGE_URL + " TEXT, " +
                    COLUMN_IMAGE_PATH + " TEXT, " +
                    COLUMN_DISEASE + " TEXT, " +
                    COLUMN_CONFIDENCE + " TEXT);";

    public NotificationDbHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(TABLE_CREATE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
        onCreate(db);
    }

    public void insertNotification(String title, String message) {
        insertScanNotification(title, message, null, null, null, null, null);
    }

    public void insertScanNotification(String title, String message, String scanId,
                                       String imageUrl, String imagePath,
                                       String diseaseName, String confidence) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_TITLE, title);
        values.put(COLUMN_MESSAGE, message);
        if (scanId != null) values.put(COLUMN_SCAN_ID, scanId);
        if (imageUrl != null) values.put(COLUMN_IMAGE_URL, imageUrl);
        if (imagePath != null) values.put(COLUMN_IMAGE_PATH, imagePath);
        if (diseaseName != null) values.put(COLUMN_DISEASE, diseaseName);
        if (confidence != null) values.put(COLUMN_CONFIDENCE, confidence);
        db.insert(TABLE_NAME, null, values);
        db.execSQL("DELETE FROM " + TABLE_NAME + " WHERE " + COLUMN_TIMESTAMP + " <= date('now','-30 day')");
        db.close();
    }

    public List<NotificationModel> getAllNotifications() {
        List<NotificationModel> notifications = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_NAME, null, null, null, null, null, COLUMN_TIMESTAMP + " DESC");

        if (cursor.moveToFirst()) {
            do {
                notifications.add(new NotificationModel(
                        cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TITLE)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_MESSAGE)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SCAN_ID)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_IMAGE_URL)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_IMAGE_PATH)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DISEASE)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CONFIDENCE)),
                        false
                ));
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return notifications;
    }
}
