package com.spde.sclauncher.provider;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DatabaseHelper extends SQLiteOpenHelper {

    public DatabaseHelper(Context context) {
        super(context, SCDB.DATABASE_NAME, null, SCDB.DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        createContactsTable(db);
        createClassModeTable(db);
        createServerSmsTable(db);
        createWhiteListTable(db);
        createProfileTable(db);
        createFenceTable(db);
        createCrossFenceTable(db);
    }

    private void createContactsTable(SQLiteDatabase db){
        String SQL =
                "create table if not exists " + SCDB.TABLE_CONTACTS +
                        " (_id integer primary key,"
                        + SCDB.Contacts.NAME + " text,"
                        + SCDB.Contacts.PHONE + " text"
                        + ");";

        db.execSQL(SQL);
    }

    private void createClassModeTable(SQLiteDatabase db){
        String SQL =
                "create table if not exists " + SCDB.TABLE_CLASSMODE +
                        " (_id integer primary key,"
                        + SCDB.ClassMode.STARTMIN + " INTEGER,"
                        + SCDB.ClassMode.ENDMIN + " INTEGER,"
                        + SCDB.ClassMode.DAY + " text,"
                        + SCDB.ClassMode.ONOFF + " INTEGER,"
                        + SCDB.ClassMode.SOS_IN + " INTEGER,"
                        + SCDB.ClassMode.SOS_OUT + " INTEGER"
                        + ");";

        db.execSQL(SQL);
    }

    private void createServerSmsTable(SQLiteDatabase db){
        String SQL =
                "create table if not exists " + SCDB.TABLE_SERVER_SMS +
                        " (_id integer primary key autoincrement,"
                        + SCDB.ServerSms.MESSAGE + " text,"
                        + SCDB.ServerSms.EMERGENT + " INTEGER,"
                        + SCDB.ServerSms.SHOWTIMES + " text,"
                        + SCDB.ServerSms.SHOWTYPE + " INTEGER,"
                        + SCDB.ServerSms.FLASH + " INTEGER,"
                        + SCDB.ServerSms.RING + " INTEGER,"
                        + SCDB.ServerSms.VIBRATE + " INTEGER,"
                        + SCDB.ServerSms.UPDATETIME + " INTEGER"
                        + ");";

        db.execSQL(SQL);
    }

    private void createWhiteListTable(SQLiteDatabase db){
        String SQL =
                "create table if not exists " + SCDB.TABLE_WHTTELIST +
                        " (_id integer primary key autoincrement,"
                        + SCDB.WhiteList.PHONE + " text,"
                        + SCDB.WhiteList.STARTMIN + " INTEGER,"
                        + SCDB.WhiteList.ENDMIN + " INTEGER,"
                        + SCDB.WhiteList.CALL_IN + " INTEGER,"
                        + SCDB.WhiteList.DAY + " text"
                        + ");";

        db.execSQL(SQL);
    }

    private void createProfileTable(SQLiteDatabase db){
        String SQL =
                "create table if not exists " + SCDB.TABLE_PROFILE +
                        " (_id integer primary key autoincrement,"
                        + SCDB.Profile.RING + " INTEGER,"
                        + SCDB.Profile.CALL_IN_FORBID + " INTEGER,"
                        + SCDB.Profile.CALL_OUT_FORBID + " INTEGER"
                        + ");";

        db.execSQL(SQL);
    }

    private void createFenceTable(SQLiteDatabase db){
        String SQL =
                "create table if not exists " + SCDB.TABLE_FENCE + " ("
                        + SCDB.Fence.REGID + " INTEGER primary key,"
                        + SCDB.Fence.REGTYPE + " INTEGER,"
                        + SCDB.Fence.SHAPE + " text,"
                        + SCDB.Fence.ELES + " text,"
                        + SCDB.Fence.TIME + " text,"
                        + SCDB.Fence.DAY + " text"
                        + ");";
        db.execSQL(SQL);
    }

    private void createCrossFenceTable(SQLiteDatabase db){
        String SQL =
                "create table if not exists " + SCDB.TABLE_CROSS_FENCE + " ("
                        + SCDB.CrossFence.REGID + " INTEGER primary key,"
                        + SCDB.CrossFence.UPDATETIME + " INTEGER"
                        + ");";
        db.execSQL(SQL);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + SCDB.TABLE_CONTACTS);
        db.execSQL("DROP TABLE IF EXISTS " + SCDB.TABLE_CLASSMODE);
        db.execSQL("DROP TABLE IF EXISTS " + SCDB.TABLE_SERVER_SMS);
        db.execSQL("DROP TABLE IF EXISTS " + SCDB.TABLE_WHTTELIST);
        db.execSQL("DROP TABLE IF EXISTS " + SCDB.TABLE_PROFILE);
        db.execSQL("DROP TABLE IF EXISTS " + SCDB.TABLE_FENCE);
        db.execSQL("DROP TABLE IF EXISTS " + SCDB.TABLE_CROSS_FENCE);
        onCreate(db);
    }
}
