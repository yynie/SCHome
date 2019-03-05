package com.spde.sclauncher.DataSource;

import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;

import com.spde.sclauncher.provider.SCDB;
import com.yynie.myutils.Logger;

public class ProfileModeDataSource extends AbstractDataSource{
    private final Logger log = Logger.get(ProfileModeDataSource.class, Logger.Level.INFO);
    private static ProfileModeDataSource sInstance;

    public static ProfileModeDataSource getInstance(){
        synchronized (ProfileModeDataSource.class){
            if(sInstance == null){
                sInstance = new ProfileModeDataSource();
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

    public boolean isIncommingCallForbidden(){
        return isCallForbidden(true);
    }

    public boolean isOutgoingCallForbidden(){
        return isCallForbidden(false);
    }

    public int getIncomingCallRingMode(){
        Cursor cursor = null;
        try {
            Uri uri = Uri.parse("content://" + SCDB.AUTHORITY + "/profile");
            cursor = getContext().getContentResolver().query(uri, null, null, null, null);
            if (cursor.moveToNext()) {
                int ring = cursor.getInt(cursor.getColumnIndex(SCDB.Profile.RING));
                return ring;
            }
        }finally {
            if(cursor != null) cursor.close();
        }
        return 1; //ring
    }

    private boolean isCallForbidden(boolean inOrOut){
        Cursor cursor = null;
        try {
            Uri uri = Uri.parse("content://" + SCDB.AUTHORITY + "/profile");
            cursor = getContext().getContentResolver().query(uri, null, null, null, null);
            if (cursor.moveToNext()) {
                int inForbid = cursor.getInt(cursor.getColumnIndex(SCDB.Profile.CALL_IN_FORBID));
                int outForbid = cursor.getInt(cursor.getColumnIndex(SCDB.Profile.CALL_OUT_FORBID));
                if(inOrOut){
                    return (inForbid == 1);
                }else{
                    return (outForbid == 1);
                }
            }
        }finally {
            if(cursor != null) cursor.close();
        }
        return false;
    }

    public void update(boolean isRing, boolean isIncomingForbidden, boolean isOutgoingForbidden){
        Uri uri = Uri.parse("content://" + SCDB.AUTHORITY + "/profile");
        Cursor cursor = getContext().getContentResolver().query(uri, new String[]{"_id"}, null, null, null);
        Integer updateId = null;
        if(cursor.moveToNext()){
            updateId = cursor.getInt(cursor.getColumnIndex("_id"));
        }
        cursor.close();
        ContentValues values = new ContentValues();
        values.put(SCDB.Profile.RING, isRing?1:0);
        values.put(SCDB.Profile.CALL_IN_FORBID, isIncomingForbidden?1:0);
        values.put(SCDB.Profile.CALL_OUT_FORBID, isOutgoingForbidden?1:0);
        if(updateId == null){
            Uri insertedItemUri = getContext().getContentResolver().insert(uri, values);
            log.i("update insertedItemUri=" + insertedItemUri);
        }else{
            int updateRow = getContext().getContentResolver().update(uri, values, "_id="+updateId, null);
            log.i("update updateRow=" + updateRow);
        }
    }

    private void deleteAll(){
        Uri uri = Uri.parse("content://" + SCDB.AUTHORITY + "/profile");
        int delnum = getContext().getContentResolver().delete(uri, null, null);
        log.i("deleteAll delnum=" + delnum);
    }
}
