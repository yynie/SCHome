package com.spde.sclauncher.DataSource;

import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;

import com.spde.sclauncher.net.pojo.PeriodWeeklyOnOff;
import com.spde.sclauncher.provider.SCDB;
import com.yynie.myutils.Logger;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class ClassModeDataSource extends AbstractDataSource {
    private final Logger log = Logger.get(ClassModeDataSource.class, Logger.Level.INFO);
    private static ClassModeDataSource sInstance;
    private List<ClassItem> classItemList = new ArrayList<ClassItem>();

    class ClassItem extends PeriodWeeklyOnOff{
        boolean isSosIncoming;
        boolean isSosOutgoing;

        public ClassItem(int startMinute, int endMinute, String weeks, int no, boolean onOrOff, boolean isSosIncoming, boolean isSosOutgoing) {
            super(startMinute, endMinute, weeks, no, onOrOff);
            this.isSosIncoming = isSosIncoming;
            this.isSosOutgoing = isSosOutgoing;
        }

        public boolean isSosIncoming() {
            return isSosIncoming;
        }

        public boolean isSosOutgoing() {
            return isSosOutgoing;
        }
    }

    public static ClassModeDataSource getInstance(){
        synchronized (ClassModeDataSource.class){
            if(sInstance == null){
                sInstance = new ClassModeDataSource();
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
        //If response of GET_CLASS_MODEL is status=0 that means class mode is not set yet.
        // so we need to clear local data ?
        deleteAll();
    }

    public void update(boolean isSosIncoming, boolean isSosOutgoing, List<PeriodWeeklyOnOff> classList){
        //isSosIncoming=true : incomming call from sos number is allowed
        //isSosOutgoing=true : dialing to sos number is allowed
        //cover all local data when this method is called
        deleteAll();
        synchronized (classItemList) {
            for (PeriodWeeklyOnOff period : classList) {
                updateOrInsert(isSosIncoming, isSosOutgoing, period);
            }
            classItemList.clear();
        }
    }

    public boolean isInClass(){
        List<ClassItem> list = getActiveClassModeList();
        Calendar calendar = Calendar.getInstance();
        int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int min = calendar.get(Calendar.MINUTE);
        int minOfDay = hour * 60 + min;
        String dayOfWeekString = String.valueOf(dayOfWeek -1);
        for(ClassItem one:list){
            String weeks = one.getWeeks();
            if(weeks != null && weeks.contains(dayOfWeekString)
                    && minOfDay >= one.getStartMinute() && minOfDay <= one.getEndMinute()){
                return true;
            }
        }
        return false;
    }

    public boolean isSosIncomingForbidden(){
        List<ClassItem> list = getActiveClassModeList();
        Calendar calendar = Calendar.getInstance();
        int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int min = calendar.get(Calendar.MINUTE);
        int minOfDay = hour * 60 + min;
        String dayOfWeekString = String.valueOf(dayOfWeek -1);
        for(ClassItem one:list){
            String weeks = one.getWeeks();
            if(weeks != null && weeks.contains(dayOfWeekString)
                    && minOfDay >= one.getStartMinute() && minOfDay <= one.getEndMinute()
                    && !one.isSosIncoming()){
                return true;
            }
        }
        return false;
    }

    public boolean isSosOutgoingForbidden(){
        List<ClassItem> list = getActiveClassModeList();
        Calendar calendar = Calendar.getInstance();
        int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int min = calendar.get(Calendar.MINUTE);
        int minOfDay = hour * 60 + min;
        String dayOfWeekString = String.valueOf(dayOfWeek -1);
        for(ClassItem one:list){
            String weeks = one.getWeeks();
            if(weeks != null && weeks.contains(dayOfWeekString)
                    && minOfDay >= one.getStartMinute() && minOfDay <= one.getEndMinute()
                    && !one.isSosOutgoing()){
                return true;
            }
        }
        return false;
    }

    private final List<ClassItem> getActiveClassModeList(){
        synchronized (classItemList) {
            if (classItemList.isEmpty()) {
                Uri uri = Uri.parse("content://" + SCDB.AUTHORITY + "/classmode");
                Cursor cursor = getContext().getContentResolver().query(uri, null, null, null, null);
                while (cursor.moveToNext()) {
                    int id = cursor.getInt(cursor.getColumnIndex("_id"));
                    int startmin = cursor.getInt(cursor.getColumnIndex(SCDB.ClassMode.STARTMIN));
                    int endmin = cursor.getInt(cursor.getColumnIndex(SCDB.ClassMode.ENDMIN));
                    String weeks = cursor.getString(cursor.getColumnIndex(SCDB.ClassMode.DAY));
                    int onoff = cursor.getInt(cursor.getColumnIndex(SCDB.ClassMode.ONOFF));
                    int sosin = cursor.getInt(cursor.getColumnIndex(SCDB.ClassMode.SOS_IN));
                    int sosout = cursor.getInt(cursor.getColumnIndex(SCDB.ClassMode.SOS_OUT));
                    ClassItem item = new ClassItem(startmin, endmin, weeks, id, (onoff == 1), (sosin == 1), (sosout == 1));
                    classItemList.add(item);
                }
            }
            List<ClassItem> retlist = new ArrayList<ClassItem>();
            for(ClassItem item:classItemList){
                if(item.isOnOrOff()){
                    retlist.add(item);
                }
            }
            return retlist;
        }
    }

    private void updateOrInsert(boolean isSosIncoming, boolean isSosOutgoing, PeriodWeeklyOnOff period){
        Uri uri = Uri.parse("content://" + SCDB.AUTHORITY + "/classmode");
        boolean insert = true;
        int id = period.getNo();
        Cursor cursor = getContext().getContentResolver().query(uri, null,
                "_id=" + id,null, null);
        if(cursor.moveToNext()){
            insert = false;
        }
        cursor.close();
        if(insert){
            ContentValues values = new ContentValues();
            values.put("_id", id);
            values.put(SCDB.ClassMode.STARTMIN, period.getStartMinute());
            values.put(SCDB.ClassMode.ENDMIN, period.getEndMinute());
            values.put(SCDB.ClassMode.DAY, period.getWeeks());
            values.put(SCDB.ClassMode.ONOFF, period.isOnOrOff()?1:0);
            values.put(SCDB.ClassMode.SOS_IN, isSosIncoming?1:0);
            values.put(SCDB.ClassMode.SOS_OUT, isSosOutgoing?1:0);
            Uri insertedItemUri = getContext().getContentResolver().insert(uri, values);
            log.i("updateOrInsert insertedItemUri=" + insertedItemUri);
        }else{
            ContentValues values = new ContentValues();
            values.put(SCDB.ClassMode.STARTMIN, period.getStartMinute());
            values.put(SCDB.ClassMode.ENDMIN, period.getEndMinute());
            values.put(SCDB.ClassMode.DAY, period.getWeeks());
            values.put(SCDB.ClassMode.ONOFF, period.isOnOrOff()?1:0);
            values.put(SCDB.ClassMode.SOS_IN, isSosIncoming?1:0);
            values.put(SCDB.ClassMode.SOS_OUT, isSosOutgoing?1:0);
            int updateRow = getContext().getContentResolver().update(uri, values, "_id="+id, null);
            log.i("updateOrInsert updateRow=" + updateRow);
        }
    }

    private void deleteAll(){
        synchronized (classItemList) {
            Uri uri = Uri.parse("content://" + SCDB.AUTHORITY + "/classmode");
            int delnum = getContext().getContentResolver().delete(uri, null, null);
            log.i("deleteAll delnum=" + delnum);
            classItemList.clear();//
        }
    }
}
