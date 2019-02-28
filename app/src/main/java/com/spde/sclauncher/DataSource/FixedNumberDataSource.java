package com.spde.sclauncher.DataSource;


import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;

import com.spde.sclauncher.provider.SCDB;
import com.yynie.myutils.Logger;
import com.yynie.myutils.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FixedNumberDataSource extends AbstractDataSource {
    private final Logger log = Logger.get(FixedNumberDataSource.class, Logger.Level.INFO);
    private static FixedNumberDataSource sInstance;
    public static int SOS_KEY_ID = 0;
    public static int A_KEY_ID = 1;
    public static int B_KEY_ID = 2;
    public static int C_KEY_ID = 3;
    public static int TOTAL_KEYS = 4;

    public static FixedNumberDataSource getInstance(){
        synchronized (FixedNumberDataSource.class){
            if(sInstance == null){
                sInstance = new FixedNumberDataSource();
            }
            return sInstance;
        }
    }

    @Override
    protected void prepareOnInit() {
        //sos编号0, home编号1, father编号2, mother编号3

    }

    @Override
    public void release() {

    }

    @Override
    public void restore() {
        for(int i = 0; i < TOTAL_KEYS; i++) {
            updateOrInsert(i, null);
        }
    }

    private void updateOrInsert(int id, String phone){
        log.i("updateOrInsert id=" + id + ",phone=" + phone);
        boolean insert = true;
        Uri uri = Uri.parse("content://" + SCDB.AUTHORITY + "/contact/" + id);
        Cursor cursor = getContext().getContentResolver().query(uri, null,null,null, null);
        if(cursor.moveToNext()){
            insert = false;
        }
        cursor.close();
        if(StringUtils.isBlank(phone)){
            phone = "";
        }
        if(insert){
            Uri insertUri = Uri.parse("content://" + SCDB.AUTHORITY + "/contacts");
            ContentValues values = new ContentValues();
            values.put("_id", id);
            values.put(SCDB.Contacts.PHONE, phone);
            values.put(SCDB.Contacts.NAME, "");
            Uri insertedItemUri = getContext().getContentResolver().insert(insertUri, values);
            log.i("updateOrInsert insertedItemUri=" + insertedItemUri);
        }else{
            ContentValues values = new ContentValues();
            values.put(SCDB.Contacts.PHONE, phone);
            values.put(SCDB.Contacts.NAME, "");
            int updateRow = getContext().getContentResolver().update(uri, values, null, null);
            log.i("updateOrInsert updateRow=" + updateRow);
        }
    }

    public void updateNumbers(Map<String, String> keyNumberMap){
        for(int i = 0; i < TOTAL_KEYS; i++) {
            updateOrInsert(i, keyNumberMap.get(String.valueOf(i)));
        }
    }

    public final String getKeyNumber(int keyId) {
        String phone = null;
        Uri uri = Uri.parse("content://" + SCDB.AUTHORITY + "/contact/" + keyId);
        Cursor cursor = getContext().getContentResolver().query(uri, null,null,null, null);
        if(cursor.moveToNext()){
            phone = cursor.getString(cursor.getColumnIndex(SCDB.Contacts.PHONE));
        }
        cursor.close();
        return phone;
    }

    public final List<String> getAllKeyNumbers(){
        Map<String, String> map = new HashMap<String, String>();
        Uri uri = Uri.parse("content://" + SCDB.AUTHORITY + "/contacts");
        Cursor cursor = getContext().getContentResolver().query(uri, null,null,null, null);
        while(cursor.moveToNext()){
            int id = cursor.getInt(cursor.getColumnIndex("_id"));
            String phone = cursor.getString(cursor.getColumnIndex(SCDB.Contacts.PHONE));
            map.put(String.valueOf(id), phone);
        }
        cursor.close();
        List<String> list = new ArrayList<String>();
        for(int i = 0; i < TOTAL_KEYS; i++){
            String phone = map.get(String.valueOf(i));
            list.add(phone == null ? "" : phone);
        }
        return list;
    }

    public Integer getFNIndexByPhoneNumber(String phone){
        if(StringUtils.isBlank(phone)) return null;
        Integer index = null;
        Uri uri = Uri.parse("content://" + SCDB.AUTHORITY + "/contacts");
        Cursor cursor = getContext().getContentResolver().query(uri, new String[]{"_id"},"phone='"+phone+"'",null, null);
        if(cursor.moveToNext()){
            index = cursor.getInt(cursor.getColumnIndex("_id"));
        }
        cursor.close();
        return index;
    }
}
