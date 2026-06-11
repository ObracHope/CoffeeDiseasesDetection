package com.example.coffeediseasesdetection.data;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class HistoryDbHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "coffee_deseases.db";
    private static final int DATABASE_VERSION = 2;

    public static final String TABLE_HISTORY = "history";
    public static final String COLUMN_ID = "_id";
    public static final String COLUMN_SCAN_ID = "scan_id";
    public static final String COLUMN_USER_ID = "user_id";
    public static final String COLUMN_DISEASE = "disease";
    public static final String COLUMN_DISEASE_NAME = "disease_name";
    public static final String COLUMN_DESCRIPTION = "description";
    public static final String COLUMN_IMAGE_PATH = "image_path";
    public static final String COLUMN_IMAGE_URL = "image_url";
    public static final String COLUMN_ACCURACY = "accuracy";
    public static final String COLUMN_TIMESTAMP = "timestamp";

    public HistoryDbHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_HISTORY + " (" +
                COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COLUMN_SCAN_ID + " TEXT UNIQUE, " +
                COLUMN_USER_ID + " TEXT, " +
                COLUMN_DISEASE + " TEXT, " +
                COLUMN_DISEASE_NAME + " TEXT, " +
                COLUMN_DESCRIPTION + " TEXT, " +
                COLUMN_IMAGE_PATH + " TEXT, " +
                COLUMN_IMAGE_URL + " TEXT, " +
                COLUMN_ACCURACY + " REAL, " +
                COLUMN_TIMESTAMP + " INTEGER" +
                ");");
        db.execSQL("CREATE INDEX idx_history_user ON " + TABLE_HISTORY + "(" + COLUMN_USER_ID + ");");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_HISTORY);
        onCreate(db);
    }
}
