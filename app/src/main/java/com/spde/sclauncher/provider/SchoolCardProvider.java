package com.spde.sclauncher.provider;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

public class SchoolCardProvider extends ContentProvider {
    private DatabaseHelper databaseHelper;
    private static final UriMatcher MATCHER = new UriMatcher(UriMatcher.NO_MATCH);
    private static final int CONTACTS = 1;
    private static final int CONTACT = 2;
    private static final int CLASSMODE = 3;
    private static final int SERVERSMS = 4;
    private static final int WHITELIST = 5;
    private static final int PROFILE = 6;
    static {
        MATCHER.addURI(SCDB.AUTHORITY, "contacts", CONTACTS);
        MATCHER.addURI(SCDB.AUTHORITY, "contact/#", CONTACT);
        MATCHER.addURI(SCDB.AUTHORITY, "classmode", CLASSMODE);
        MATCHER.addURI(SCDB.AUTHORITY, "serversms", SERVERSMS);
        MATCHER.addURI(SCDB.AUTHORITY, "whitelist", WHITELIST);
        MATCHER.addURI(SCDB.AUTHORITY, "profile", PROFILE);
    }

    @Override
    public boolean onCreate() {
        databaseHelper = new DatabaseHelper(getContext());
        return true;
    }

    private void closeDB() {
        databaseHelper.close();
    }

    @Override
    public void shutdown() {
        super.shutdown();
        closeDB();
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        SQLiteDatabase db = databaseHelper.getWritableDatabase();
        switch (MATCHER.match(uri)) {
            case CONTACTS:{
                return db.query(SCDB.TABLE_CONTACTS, projection, selection, selectionArgs,
                        null, null, sortOrder);
            }
            case CONTACT:{
                long id = ContentUris.parseId(uri);
                String where = "_id=" + id;
                if (selection != null && selection.trim().length() > 0) {
                    where = selection + " AND (" + where + ")";
                }
                return db.query(SCDB.TABLE_CONTACTS, projection, where, selectionArgs, null,
                        null, sortOrder);
            }
            case CLASSMODE:{
                return db.query(SCDB.TABLE_CLASSMODE, projection, selection, selectionArgs,
                        null, null, sortOrder);
            }
            case SERVERSMS:{
                return db.query(SCDB.TABLE_SERVER_SMS, projection, selection, selectionArgs,
                        null, null, sortOrder);
            }
            case WHITELIST:{
                return db.query(SCDB.TABLE_WHTTELIST, projection, selection, selectionArgs,
                        null, null, sortOrder);
            }
            case PROFILE:{
                return db.query(SCDB.TABLE_PROFILE, projection, selection, selectionArgs,
                        null, null, sortOrder);
            }
            default:
                throw new IllegalArgumentException("query unknown Uri:" + uri.toString());
        }
    }

    @Override
    public String getType(Uri uri) {
        switch (MATCHER.match(uri)) {
            case CONTACTS:
                return "vnd.android.cursor.dir/contacts";
            case CONTACT:
                return "vnd.android.cursor.item/contact";
            case CLASSMODE:{
                return "vnd.android.cursor.item/classmode";
            }
            case SERVERSMS:{
                return "vnd.android.cursor.item/serversms";
            }
            case WHITELIST:{
                return "vnd.android.cursor.item/whitelist";
            }
            case PROFILE:{
                return "vnd.android.cursor.item/profile";
            }
            default:
                throw new IllegalArgumentException("getType unknown Uri:" + uri.toString());
        }
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        SQLiteDatabase db = databaseHelper.getWritableDatabase();
        switch (MATCHER.match(uri)) {
            case CONTACTS:{
                long rowid = db.insert(SCDB.TABLE_CONTACTS, null, values);
                if(rowid >= 0){
                    Uri insertUri = ContentUris.withAppendedId(uri, rowid);
                    return insertUri;
                }
                return null;
            }
            case CLASSMODE:{
                long rowid = db.insert(SCDB.TABLE_CLASSMODE, null, values);
                if(rowid >= 0){
                    Uri insertUri = ContentUris.withAppendedId(uri, rowid);
                    return insertUri;
                }
                return null;
            }
            case SERVERSMS:{
                long rowid = db.insert(SCDB.TABLE_SERVER_SMS, null, values);
                if(rowid >= 0){
                    Uri insertUri = ContentUris.withAppendedId(uri, rowid);
                    return insertUri;
                }
                return null;
            }
            case WHITELIST:{
                long rowid = db.insert(SCDB.TABLE_WHTTELIST, null, values);
                if(rowid >= 0){
                    Uri insertUri = ContentUris.withAppendedId(uri, rowid);
                    return insertUri;
                }
                return null;
            }
            case PROFILE:{
                long rowid = db.insert(SCDB.TABLE_PROFILE, null, values);
                if(rowid >= 0){
                    Uri insertUri = ContentUris.withAppendedId(uri, rowid);
                    return insertUri;
                }
                return null;
            }
            default:
                throw new IllegalArgumentException("insert unknown Uri:" + uri.toString());
        }
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        SQLiteDatabase db = databaseHelper.getWritableDatabase();
        switch (MATCHER.match(uri)) {
            case CONTACTS:{
                return db.delete(SCDB.TABLE_CONTACTS, selection, selectionArgs);
            }
            case CONTACT:{
                long id = ContentUris.parseId(uri);
                String where = "_id=" + id;
                if (selection != null && selection.trim().length() > 0) {
                    where = selection + " AND (" + where + ")";
                }
                return db.delete(SCDB.TABLE_CONTACTS, where, selectionArgs);
            }
            case CLASSMODE:{
                return db.delete(SCDB.TABLE_CLASSMODE, selection, selectionArgs);
            }
            case SERVERSMS:{
                return db.delete(SCDB.TABLE_SERVER_SMS, selection, selectionArgs);
            }
            case WHITELIST:{
                return db.delete(SCDB.TABLE_WHTTELIST, selection, selectionArgs);
            }
            case PROFILE:{
                return db.delete(SCDB.TABLE_PROFILE, selection, selectionArgs);
            }
            default:
                throw new IllegalArgumentException("delete unknown Uri:" + uri.toString());
        }
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        SQLiteDatabase db = databaseHelper.getWritableDatabase();
        switch (MATCHER.match(uri)) {
            case CONTACTS:{
                return db.update(SCDB.TABLE_CONTACTS, values, selection, selectionArgs);
            }
            case CONTACT:{
                long id = ContentUris.parseId(uri);
                String where = "_id=" + id;
                if (selection != null && selection.trim().length() > 0) {
                    where = selection + " AND (" + where + ")";
                }
                return db.update(SCDB.TABLE_CONTACTS, values, where, selectionArgs);
            }
            case CLASSMODE:{
                return db.update(SCDB.TABLE_CLASSMODE, values, selection, selectionArgs);
            }
            case SERVERSMS:{
                return db.update(SCDB.TABLE_SERVER_SMS, values, selection, selectionArgs);
            }
            case WHITELIST:{
                return db.update(SCDB.TABLE_WHTTELIST, values, selection, selectionArgs);
            }
            case PROFILE:{
                return db.update(SCDB.TABLE_PROFILE, values, selection, selectionArgs);
            }
            default:
                throw new IllegalArgumentException("Unkwon Uri:" + uri.toString());
        }
    }
}
