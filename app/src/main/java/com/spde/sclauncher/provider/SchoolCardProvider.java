package com.spde.sclauncher.provider;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;


import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SchoolCardProvider extends ContentProvider {
    private DatabaseHelper databaseHelper;
    private static final UriMatcher MATCHER = new UriMatcher(UriMatcher.NO_MATCH);
    private static final int CONTACTS = 1;
    private static final int CONTACT = 2;
    private static final int CLASSMODE = 3;
    private static final int SERVERSMS = 4;
    private static final int WHITELIST = 5;
    private static final int PROFILE = 6;
    private static final int LOGIC_SMS_ALLOWED = 7;
    private static final int LOGIC_FA_INSTRUCT = 8;
    static {
        MATCHER.addURI(SCDB.AUTHORITY, "contacts", CONTACTS);
        MATCHER.addURI(SCDB.AUTHORITY, "contact/#", CONTACT);
        MATCHER.addURI(SCDB.AUTHORITY, "classmode", CLASSMODE);
        MATCHER.addURI(SCDB.AUTHORITY, "serversms", SERVERSMS);
        MATCHER.addURI(SCDB.AUTHORITY, "whitelist", WHITELIST);
        MATCHER.addURI(SCDB.AUTHORITY, "profile", PROFILE);

        /***/
        MATCHER.addURI(SCDB.AUTHORITY, "sms_allowed", LOGIC_SMS_ALLOWED);
        MATCHER.addURI(SCDB.AUTHORITY, "fa_instruct", LOGIC_FA_INSTRUCT);
    }

    Pattern phonePattern = Pattern.compile("phone\\s{0,1}=\\s{0,1}'(\\d{1,20})'");
    Pattern cmdPattern = Pattern.compile("cmd\\s{0,1}=\\s{0,1}'(\\S{1,20})'");
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
            case LOGIC_SMS_ALLOWED:{
                return smsAllowedInWhiteList(db, selection);
            }
            case LOGIC_FA_INSTRUCT:{
                return isFAInstruction(db, selection);
            }
            default:
                throw new IllegalArgumentException("query unknown Uri:" + uri.toString());
        }
    }

    private Cursor isFAInstruction(SQLiteDatabase db ,String selection){
        if(selection == null || selection.trim().isEmpty()){
            throw new IllegalArgumentException("query unknown selection item:" + selection);
        }
        String phone,cmd;
        Matcher matcherPhone = phonePattern.matcher(selection.trim());
        if (matcherPhone.find()) {
            phone = matcherPhone.group(1);
        }else{
            throw new IllegalArgumentException("query unknown selection item:" + selection);
        }

        Matcher matcherCmd = cmdPattern.matcher(selection.trim());
        if (matcherCmd.find()) {
            cmd = matcherCmd.group(1);
        }else{
            throw new IllegalArgumentException("query unknown selection item:" + selection);
        }

        if(phone == null || phone.isEmpty() || cmd == null || cmd.isEmpty()){
            throw new IllegalArgumentException("query unknown selection item:" + selection);
        }
        String[] columns = new String[] {"phone", "cmd"};
        MatrixCursor matrixCursor = new MatrixCursor(columns);
        if(cmd.equalsIgnoreCase("DLCX") || cmd.equalsIgnoreCase("QQSFE")){
            Cursor cursor = db.query(SCDB.TABLE_CONTACTS, null,
                    SCDB.Contacts.PHONE + "='" + phone + "'", null,
                    null, null, null);
            if(cursor.moveToNext()){
                String row[] = new String[]{phone, cmd};
                matrixCursor.addRow(row);
            }
            cursor.close();
        }
        return matrixCursor;
    }

    private Cursor smsAllowedInWhiteList(SQLiteDatabase db ,String selection){
        if(selection == null || !selection.startsWith(SCDB.WhiteList.PHONE)){
            throw new IllegalArgumentException("query unknown selection item:" + selection);
        }
        String phone;
        Matcher matcher = phonePattern.matcher(selection.trim());
        if (matcher.find()) {
            phone = matcher.group(1);
        }else{
            throw new IllegalArgumentException("query unknown selection item:" + selection);
        }
        if(phone == null || phone.isEmpty()) {
            throw new IllegalArgumentException("query unknown selection item:" + selection);
        }
        boolean found = false;
        boolean notSet = false;
        Cursor cursor = db.query(SCDB.TABLE_WHTTELIST, null, null, null,
                null, null, null);
        if(cursor.getCount() > 0){
            while(cursor.moveToNext()){
                String rphone = cursor.getString(cursor.getColumnIndex(SCDB.WhiteList.PHONE));
                if(phone.equals(rphone)){
                    found = true;
                    break;
                }
            }
        }else{
            notSet = true;
            //not white list set ,so any phone number can pass in
        }
        cursor.close();

        String[] columns = new String[] {"phone", "status"};
        MatrixCursor matrixCursor = new MatrixCursor(columns);
        if(found){
            String row[] = new String[]{phone, "in"};
            matrixCursor.addRow(row);
        }else if(notSet){
            String row[] = new String[]{phone, "not_set"};
            matrixCursor.addRow(row);
        }
        return matrixCursor;
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
            case LOGIC_SMS_ALLOWED:{
                return "vnd.android.cursor.item/sms_allowed";
            }
            case LOGIC_FA_INSTRUCT:{
                return "vnd.android.cursor.item/fa_instruct";
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
