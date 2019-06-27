package com.spde.sclauncher.provider;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import java.util.Calendar;
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
    private static final int LOGIC_INCALL_ALLOWED = 9;
    private static final int FENCE = 10;
    private static final int CROSS_FENCE = 11;
    private static final int LOGIC_KEEP_SLIENT = 12;
    static {
        MATCHER.addURI(SCDB.AUTHORITY, "contacts", CONTACTS);
        MATCHER.addURI(SCDB.AUTHORITY, "contact/#", CONTACT);
        MATCHER.addURI(SCDB.AUTHORITY, "classmode", CLASSMODE);
        MATCHER.addURI(SCDB.AUTHORITY, "serversms", SERVERSMS);
        MATCHER.addURI(SCDB.AUTHORITY, "whitelist", WHITELIST);
        MATCHER.addURI(SCDB.AUTHORITY, "profile", PROFILE);
        MATCHER.addURI(SCDB.AUTHORITY, "fence", FENCE);
        MATCHER.addURI(SCDB.AUTHORITY, "crossfence", CROSS_FENCE);

        /***/
        MATCHER.addURI(SCDB.AUTHORITY, "sms_allowed", LOGIC_SMS_ALLOWED);
        MATCHER.addURI(SCDB.AUTHORITY, "fa_instruct", LOGIC_FA_INSTRUCT);
        MATCHER.addURI(SCDB.AUTHORITY, "incall_allowed", LOGIC_INCALL_ALLOWED);
        MATCHER.addURI(SCDB.AUTHORITY, "keep_slient", LOGIC_KEEP_SLIENT);
    }

    Pattern phonePattern = Pattern.compile("phone\\s{0,1}=\\s{0,1}'(\\d{0,20})'");
    Pattern cmdPattern = Pattern.compile("cmd\\s{0,1}=\\s{0,1}'([\\s\\S]+)'");
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
            case FENCE:{
                return db.query(SCDB.TABLE_FENCE, projection, selection, selectionArgs,
                        null, null, sortOrder);
            }
            case CROSS_FENCE:{
                return db.query(SCDB.TABLE_CROSS_FENCE, projection, selection, selectionArgs,
                        null, null, sortOrder);
            }
            case LOGIC_SMS_ALLOWED:{
                return smsAllowedInWhiteList(db, selection);
            }
            case LOGIC_FA_INSTRUCT:{
                return isFAInstruction(db, selection);
            }
            case LOGIC_INCALL_ALLOWED:{
                return isInCallAllowed(db, selection);
            }
            case LOGIC_KEEP_SLIENT:{
                return isKeepSlient(db);
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
            // throw new IllegalArgumentException("query unknown selection item:" + selection);
            String[] columns = new String[] {"phone", "cmd", "status"};
            MatrixCursor matrixCursor = new MatrixCursor(columns);
            return matrixCursor;
        }

        if(phone == null || phone.isEmpty() || cmd == null || cmd.isEmpty()){
            throw new IllegalArgumentException("query unknown selection item:" + selection);
        }
        String[] columns = new String[] {"phone", "cmd", "status"};
        MatrixCursor matrixCursor = new MatrixCursor(columns);
        boolean isSupportedCmd = false;
        if(cmd.equalsIgnoreCase("DLCX") || cmd.equalsIgnoreCase("QQSFE")){
            isSupportedCmd = true;
        }else if(cmd.toUpperCase().startsWith("SERVER")) {
            String[] parts = cmd.split("[,，]");
            if(parts.length == 3 && parts[0].trim().equalsIgnoreCase("SERVER")){
                try {
                    int port = Integer.parseInt(parts[2].trim());
                    isSupportedCmd = true;
                }catch (NumberFormatException e){
                    //not a port
                    isSupportedCmd = false;
                }
            }
        }
        if(isSupportedCmd){
            Cursor cursor = db.query(SCDB.TABLE_CONTACTS, null,
                    SCDB.Contacts.PHONE + "!=''", null, null, null, null);
            if(cursor.getCount() > 0){
                while(cursor.moveToNext()){
                    String rphone = cursor.getString(cursor.getColumnIndex(SCDB.Contacts.PHONE));
                    if(phone.equals(rphone)){
                        String row[] = new String[]{phone, cmd, "found"};
                        matrixCursor.addRow(row);
                        break;
                    }
                }
            }else{
                String row[] = new String[]{phone, cmd, "not set"};
                matrixCursor.addRow(row);
            }
            cursor.close();
        }
        return matrixCursor;
    }

    private Cursor isKeepSlient(SQLiteDatabase db){
        String[] columns = new String[] {"ringmode"};
        CallInfo callInfo = new CallInfo();
        MatrixCursor matrixCursor = new MatrixCursor(columns);
        checkInCallForbiddenInProfile(db, callInfo);
        if(callInfo.ringmode == 0){
            Object[] row = new Object[]{callInfo.ringmode};
            matrixCursor.addRow(row);
            return matrixCursor;
        }
        checkInCallForbiddenInClass(db, false, callInfo);
        if(callInfo.inclass){
            Object[] row = new Object[]{0};
            matrixCursor.addRow(row);
        }else{
            Object[] row = new Object[]{callInfo.ringmode};
            matrixCursor.addRow(row);
        }
        return matrixCursor;
    }

    class CallInfo {
        boolean forbid = false;
        String status = "";
        int ringmode = 1;
        boolean inclass = false;
    }

    private Cursor isInCallAllowed(SQLiteDatabase db, String selection){
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
//        if(phone == null || phone.isEmpty()) {
//            throw new IllegalArgumentException("query unknown selection item:" + selection);
//        }
        String[] columns = new String[] {"phone", "allow", "status", "ring", "inclass"};
        CallInfo callInfo = new CallInfo();
        MatrixCursor matrixCursor = new MatrixCursor(columns);
        checkInCallForbiddenInProfile(db, callInfo);
        if(callInfo.forbid){
            Object[] row = new Object[]{phone, 0, "profile_forbid", callInfo.ringmode, 0};
            matrixCursor.addRow(row);
            return matrixCursor;
        }
        int faIndex = getIndexInFANumbers(db, phone);
        boolean isSos = (faIndex == 0);//isCallFromSOS(db, phone);
        checkInCallForbiddenInClass(db, isSos, callInfo);
        if(callInfo.forbid){
            Object[] row = new Object[]{phone, 0, "classmode_forbid" + (isSos?"_sos":""), callInfo.ringmode, (callInfo.inclass?1:0)};
            matrixCursor.addRow(row);
            return matrixCursor;
        }
        if(faIndex >= 0){//亲情号码放行
            Object[] row = new Object[]{phone, 1, "allow_fa", callInfo.ringmode, (callInfo.inclass?1:0)};
            matrixCursor.addRow(row);
            return matrixCursor;
        }
        checkCallAllowedInWhiteList(db, phone, callInfo);
        if(callInfo.forbid){
            Object[] row = new Object[]{phone, 0, callInfo.status, callInfo.ringmode, (callInfo.inclass?1:0)};
            matrixCursor.addRow(row);
            return matrixCursor;
        }
        Object[] row = new Object[]{phone, 1, callInfo.status, callInfo.ringmode, (callInfo.inclass?1:0)};
        matrixCursor.addRow(row);
        return matrixCursor;
    }

    private void checkInCallForbiddenInClass(SQLiteDatabase db, boolean isSos, CallInfo callInfo){
        Calendar calendar = Calendar.getInstance();
        int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int min = calendar.get(Calendar.MINUTE);
        int minOfDay = hour * 60 + min;
        String dayOfWeekString = String.valueOf(dayOfWeek -1);
        String selectSql = SCDB.ClassMode.ONOFF + "=1 AND " +
                SCDB.ClassMode.STARTMIN + "<=" + minOfDay + " AND " + SCDB.ClassMode.ENDMIN + ">" + minOfDay +
                " AND " + SCDB.ClassMode.DAY + " like '%" + dayOfWeekString + "%'";
        Cursor cursor = null;
        try {
            cursor =  db.query(SCDB.TABLE_CLASSMODE, null, selectSql, null, null, null, null, null);
            while (cursor.moveToNext()) {
                int id = cursor.getInt(cursor.getColumnIndex("_id"));
                int sosin = cursor.getInt(cursor.getColumnIndex(SCDB.ClassMode.SOS_IN));
                callInfo.inclass = true;
                if (isSos) {
                    if(sosin != 1){
                        callInfo.forbid = true;
                        return;
                    }
                }else{
                    callInfo.forbid = true;
                    return;
                }
            }
        }finally {
            if(cursor != null) cursor.close();
        }
    }

//    private boolean isCallFromSOS(SQLiteDatabase db, String phone){
//        Cursor cursor = null;
//        try {
//            cursor =  db.query(SCDB.TABLE_CONTACTS, new String[]{"_id"}, "phone='" + phone + "'", null, null, null, null, null);
//            if (cursor.moveToNext()) {
//                int index = cursor.getInt(cursor.getColumnIndex("_id"));
//                if (index == 0) {
//                    return true;
//                }
//            }
//        }finally {
//            if(cursor != null) cursor.close();
//        }
//        return false;
//    }

    private int getIndexInFANumbers(SQLiteDatabase db, String phone){
        Cursor cursor = null;
        try {
            cursor =  db.query(SCDB.TABLE_CONTACTS, new String[]{"_id"}, "phone='" + phone + "'", null, null, null, null, null);
            if (cursor.moveToNext()) {
                int index = cursor.getInt(cursor.getColumnIndex("_id"));
                return index;
            }
        }finally {
            if(cursor != null) cursor.close();
        }
        return -1;
    }

    private void checkInCallForbiddenInProfile(SQLiteDatabase db, CallInfo callInfo){
        Cursor cursor = null;
        try {
            cursor =  db.query(SCDB.TABLE_PROFILE,null, null, null, null, null, null, null);
            if (cursor.moveToNext()) {
                int inForbid = cursor.getInt(cursor.getColumnIndex(SCDB.Profile.CALL_IN_FORBID));
                callInfo.ringmode = cursor.getInt(cursor.getColumnIndex(SCDB.Profile.RING));
                callInfo.forbid = (inForbid == 1);
            }
        }finally {
            if(cursor != null) cursor.close();
        }
    }

    private void checkCallAllowedInWhiteList(SQLiteDatabase db, String phone, CallInfo callInfo){
        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int min = calendar.get(Calendar.MINUTE);
        int minOfDay = hour * 60 + min;
        int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
        String dayOfWeekString = String.valueOf(dayOfWeek -1);

        Cursor cursor = null;
        try {
            cursor = db.query(SCDB.TABLE_WHTTELIST, null, "phone='master'", null,
                    null, null, null);
            if (cursor.moveToNext()) {
                String rphone = cursor.getString(cursor.getColumnIndex(SCDB.WhiteList.PHONE));
                int callInLimit = cursor.getInt(cursor.getColumnIndex(SCDB.WhiteList.CALL_IN));
                String weeks = cursor.getString(cursor.getColumnIndex(SCDB.WhiteList.DAY));
                if (callInLimit == 1) {
                    callInfo.status = "no_limit";
                    return;
                } else if (callInLimit == 3) {
                    callInfo.status = "limit_all";
                    callInfo.forbid = true;
                    return;
                } else {
                    if (weeks == null || !weeks.contains(dayOfWeekString)) {
                        callInfo.status = "limit_day";
                        callInfo.forbid = true;
                        return;
                    }
                }
            } else {
                callInfo.status = "not_set";
                callInfo.forbid = true;
                return;
            }
        }finally {
            if(cursor != null) cursor.close();
        }

        Cursor cursor2 = null;
        try {
            String selectSql = SCDB.WhiteList.PHONE + "='" + phone + "' AND " + SCDB.WhiteList.STARTMIN + "<=" + minOfDay + " AND " + SCDB.WhiteList.ENDMIN + ">" + minOfDay;
            cursor2 = db.query(SCDB.TABLE_WHTTELIST, null, selectSql, null,
                    null, null, null);
            if(cursor2.moveToNext()){
                callInfo.status = "allow";
                return;
            }else{
                callInfo.status = "limit_phone";
                callInfo.forbid = true;
                return;
            }
        }finally {
            if(cursor2 != null) cursor2.close();
        }
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

        String[] columns = new String[] {"phone", "status"};
        MatrixCursor matrixCursor = new MatrixCursor(columns);

        int faIndex = getIndexInFANumbers(db, phone);
        if(faIndex >= 0){//亲情号码放行
            String row[] = new String[]{phone, "in_fa"};
            matrixCursor.addRow(row);
            return matrixCursor;
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
            case FENCE:{
                return "vnd.android.cursor.item/fence";
            }
            case CROSS_FENCE:{
                return "vnd.android.cursor.item/crossfence";
            }
            case LOGIC_SMS_ALLOWED:{
                return "vnd.android.cursor.item/sms_allowed";
            }
            case LOGIC_FA_INSTRUCT:{
                return "vnd.android.cursor.item/fa_instruct";
            }
            case LOGIC_INCALL_ALLOWED:{
                return "vnd.android.cursor.item/incall_allowed";
            }
            case LOGIC_KEEP_SLIENT:{
                return "vnd.android.cursor.item/keep_slient";
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
            case FENCE:{
                long rowid = db.insert(SCDB.TABLE_FENCE, null, values);
                if(rowid >= 0){
                    Uri insertUri = ContentUris.withAppendedId(uri, rowid);
                    return insertUri;
                }
                return null;
            }
            case CROSS_FENCE:{
                long rowid = db.insert(SCDB.TABLE_CROSS_FENCE, null, values);
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
            case FENCE:{
                return db.delete(SCDB.TABLE_FENCE, selection, selectionArgs);
            }
            case CROSS_FENCE:{
                return db.delete(SCDB.TABLE_CROSS_FENCE, selection, selectionArgs);
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
            case FENCE:{
                return db.update(SCDB.TABLE_FENCE, values, selection, selectionArgs);
            }
            case CROSS_FENCE:{
                return db.update(SCDB.TABLE_CROSS_FENCE, values, selection, selectionArgs);
            }
            default:
                throw new IllegalArgumentException("Unkwon Uri:" + uri.toString());
        }
    }
}
