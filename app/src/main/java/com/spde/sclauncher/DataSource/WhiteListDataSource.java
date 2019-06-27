package com.spde.sclauncher.DataSource;

import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;

import com.spde.sclauncher.net.pojo.IncomingCallSet;
import com.spde.sclauncher.net.pojo.Period;
import com.spde.sclauncher.provider.SCDB;
import com.yynie.myutils.Logger;
import com.yynie.myutils.StringUtils;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class WhiteListDataSource extends AbstractDataSource {
    private final Logger log = Logger.get(WhiteListDataSource.class, Logger.Level.DEBUG);
    private static WhiteListDataSource sInstance;
    private List<WhiteListItem> itemList = new ArrayList<WhiteListItem>();

    class WhiteListItem extends Period{
        private final String phone;
        private final int callInLimit;
        private final String dayInWeek;

        public WhiteListItem(String phone, int callInLimit, String dayInWeek, int startMinute, int endMinute) {
            super(startMinute, endMinute);
            this.phone = phone;
            this.callInLimit = callInLimit;
            this.dayInWeek = dayInWeek;
        }
    }

    public static WhiteListDataSource getInstance(){
        synchronized (WhiteListDataSource.class){
            if(sInstance == null){
                sInstance = new WhiteListDataSource();
            }
            return sInstance;
        }
    }

    @Override
    protected void prepareOnInit() {
        //TODO: 读出数据？？
    }

    @Override
    public void release() {

    }

    @Override
    public void restore() {
        deleteAll();
    }

    public void reset(){
        deleteAll();
    }

    public boolean isIncommingCallForbidden(String phone){
        List<WhiteListItem> list = getWhiteListItemList();
        if(list.isEmpty()){ //master item should be exist
            return false; // not forbidden
        }
        Calendar calendar = Calendar.getInstance();
        WhiteListItem master = list.get(0);//the first on should be master
        if(StringUtils.equals(master.phone, "master")){ //the first on should be master
            if(master.callInLimit == 1){  //whitelist do not work
                return false; // not forbidden
            }else if(master.callInLimit == 3) { // all incoming call forbidden
                return true; //forbidden
            }else if(master.callInLimit == 2){ // more check
                int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
                String dayOfWeekString = String.valueOf(dayOfWeek -1);
                if(master.dayInWeek == null || !master.dayInWeek.contains(dayOfWeekString)){
                    return true; //forbidden
                }
            }else{
                log.e("isIncommingCallForbidden unknown callInLimit=" + master.callInLimit);
                return false; // not forbidden
            }
        }else{
            return false; // not forbidden
        }
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int min = calendar.get(Calendar.MINUTE);
        int minOfDay = hour * 60 + min;
        int size = list.size();

        for(int i = 1; i < size; i++){
            WhiteListItem one = list.get(i);
            if(StringUtils.equals(one.phone, phone)) {
                if(minOfDay > one.getStartMinute() && minOfDay < one.getEndMinute()) {
                    return false; // not forbidden
                }
            }
        }
        return true;
    }

    public boolean isPhoneInWhiteList(String phone){
        List<WhiteListItem> list = getWhiteListItemList();
        for(WhiteListItem one : list){
            if(StringUtils.equals(one.phone, phone)){
                return true;
            }
        }
        return false;
    }

    private final List<WhiteListItem> getWhiteListItemList(){
        synchronized (itemList) {
            if(itemList.isEmpty()){
                Uri uri = Uri.parse("content://" + SCDB.AUTHORITY + "/whitelist");
                Cursor cursor = getContext().getContentResolver().query(uri, null,
                        null,null, null);
                while(cursor.moveToNext()){
                    String phone = cursor.getString(cursor.getColumnIndex(SCDB.WhiteList.PHONE));
                    int callInLimit = cursor.getInt(cursor.getColumnIndex(SCDB.WhiteList.CALL_IN));
                    int start = cursor.getInt(cursor.getColumnIndex(SCDB.WhiteList.STARTMIN));
                    int end = cursor.getInt(cursor.getColumnIndex(SCDB.WhiteList.ENDMIN));
                    String weeks = cursor.getString(cursor.getColumnIndex(SCDB.WhiteList.DAY));
                    itemList.add(new WhiteListItem(phone, callInLimit, weeks, start, end));
                }
                cursor.close();
            }
            List<WhiteListItem> retlist = new ArrayList<WhiteListItem>();
            WhiteListItem master = null;
            for(WhiteListItem item:itemList){
                if(StringUtils.equals(item.phone, "master")){
                    master = item;
                }else{
                    retlist.add(item);
                }
            }
            if(master != null){
                retlist.add(0, master);
            }
            return retlist;
        }
    }

    public void update(List<IncomingCallSet> deleteList, List<IncomingCallSet> addList, int limit, String weeks){
        synchronized (itemList) {
            List<String> delphones = new ArrayList<String>();
            for (IncomingCallSet del : deleteList) {
                String phone = del.getPhone();
                if (StringUtils.isNotBlank(phone)) {
                    delphones.add(phone);
                    log.d("update to delete phone=" + phone);
                }
            }
            if (!delphones.isEmpty()) {
                deleteItemsByPhone(delphones);
            }

            insertOrUpdateMasterItem(limit, weeks);
            for(IncomingCallSet add: addList){
                String phone = add.getPhone();
                if (StringUtils.isNotBlank(phone)) {
                    insertOrUpdate(phone, add.getPeriods());
                }
            }

            itemList.clear();
        }
    }

    private void insertOrUpdateMasterItem(int callInLimit, String dayInWeek){
        boolean insert = true;
        Uri uri = Uri.parse("content://" + SCDB.AUTHORITY + "/whitelist");
        Cursor cursor = getContext().getContentResolver().query(uri, null,
                SCDB.WhiteList.PHONE + "='master'",null, null);
        if(cursor.moveToNext()){
            insert = false;
        }
        cursor.close();
        ContentValues values = new ContentValues();
        values.put(SCDB.WhiteList.PHONE, "master");
        values.put(SCDB.WhiteList.STARTMIN, 0);
        values.put(SCDB.WhiteList.ENDMIN, 0);
        values.put(SCDB.WhiteList.CALL_IN, callInLimit);
        values.put(SCDB.WhiteList.DAY, dayInWeek);
        if(insert){
            Uri insertedItemUri = getContext().getContentResolver().insert(uri, values);
            log.d("insertOrUpdateMasterItem insertedItemUri=" + insertedItemUri);
        }else{
            int updateRow = getContext().getContentResolver().update(uri, values, SCDB.WhiteList.PHONE + "='master'", null);
            log.d("insertOrUpdateMasterItem updateRow=" + updateRow);
        }
    }

    private void insertOrUpdate(String phone, List<Period> periodList){
        Uri uri = Uri.parse("content://" + SCDB.AUTHORITY + "/whitelist");

        Cursor cursor = getContext().getContentResolver().query(uri, new String[]{"_id"},
                SCDB.WhiteList.PHONE + "='" + phone + "'" ,null, null);
        List<Integer> existIds = new ArrayList<Integer>();
        while(cursor.moveToNext()){
            int id = cursor.getInt(cursor.getColumnIndex("_id"));
            existIds.add(id);
        }
        cursor.close();

        for(Period period : periodList){
            ContentValues values = new ContentValues();
            values.put(SCDB.WhiteList.PHONE, phone);
            values.put(SCDB.WhiteList.STARTMIN, period.getStartMinute());
            values.put(SCDB.WhiteList.ENDMIN, period.getEndMinute());
            values.put(SCDB.WhiteList.CALL_IN, 0);
            values.put(SCDB.WhiteList.DAY, "");
            if(existIds.isEmpty()){
                Uri insertedItemUri = getContext().getContentResolver().insert(uri, values);
                log.d("updateOrInsert insertedItemUri=" + insertedItemUri);
            }else {
                int updateId = existIds.remove(0);
                int updateRow = getContext().getContentResolver().update(uri, values, "_id="+updateId, null);
                log.d("updateOrInsert updateRow=" + updateRow);
            }
        }
        if(!existIds.isEmpty()){
            for(Integer id : existIds){
                if(id != null){
                    int delnum = getContext().getContentResolver().delete(uri, "_id="+id, null);
                    log.d("updateOrInsert delete unused item id=" + id + ", delnum="+delnum);
                }
            }
        }
    }

    private void deleteItemsByPhone(List<String> dels){
        Uri uri = Uri.parse("content://" + SCDB.AUTHORITY + "/whitelist");
        String phones = "";
        for(String p : dels){
            phones += "'" + p + "',";
        }

        String where = SCDB.WhiteList.PHONE + " IN (" + phones.substring(0, phones.length() -1) + ")";

        int delnum = getContext().getContentResolver().delete(uri, where, null);
        log.d("update deleted delnum=" + delnum);
    }

    private void deleteAll(){
        synchronized (itemList) {
            Uri uri = Uri.parse("content://" + SCDB.AUTHORITY + "/whitelist");
            int delnum = getContext().getContentResolver().delete(uri, null, null);
            log.d("deleteAll delnum=" + delnum);

            itemList.clear();
        }
    }
}
