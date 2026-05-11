package com.herobot;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.content.ContentValues;
import android.database.Cursor;

public class DBHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "chatbot.db";
    private static final int DB_VERSION = 1;

    public static final String TABLE = "chatbot";
    public static final String COL_Q = "question";
    public static final String COL_A = "answer";

    public DBHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(
            "CREATE TABLE " + TABLE + " (" +
            COL_Q + " TEXT PRIMARY KEY, " +
            COL_A + " TEXT NOT NULL)"
        );
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE);
        onCreate(db);
    }

    // INSERT / UPDATE
    public void insertQA(String q, String a) {
        SQLiteDatabase db = getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(COL_Q, q.toLowerCase());
        values.put(COL_A, a);

        db.insertWithOnConflict(TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    // GET ALL DATA
    public Cursor getAll() {
        SQLiteDatabase db = getReadableDatabase();
        return db.rawQuery("SELECT * FROM " + TABLE, null);
    }

    // GET ANSWER (exact match fallback)
    public String getAnswer(String question) {
        SQLiteDatabase db = getReadableDatabase();

        Cursor c = db.rawQuery(
            "SELECT answer FROM " + TABLE + " WHERE question=?",
            new String[]{question.toLowerCase()}
        );

        if (c.moveToFirst()) {
            String ans = c.getString(0);
            c.close();
            return ans;
        }

        c.close();
        return null;
    }
}
