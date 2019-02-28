package com.spde.sclauncher.DataSource;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

public class BatteryDataSource extends AbstractDataSource{
    private static BatteryDataSource sInstance;

    public static BatteryDataSource getInstance(){
        synchronized (BatteryDataSource.class){
            if(sInstance == null){
                sInstance = new BatteryDataSource();
            }
            return sInstance;
        }
    }

    private BatteryDataSource(){
        super();
    }

    @Override
    protected void prepareOnInit() {
    }


    public void release(){
    }

    @Override
    public void restore() {
    }

    public int getBatteryPercent(){
        Intent intent = registerAndGet();
        int level = intent.getIntExtra("level", 0); //剩余电量
        int scale = intent.getIntExtra("scale", 0); //满电量
//        int voltage = intent.getIntExtra("voltage", 0); //除1000
//        int temp = intent.getIntExtra("temperature", -1); //除10
        return (level * 100)/scale;
    }

    private Intent registerAndGet(){
        IntentFilter intentFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent intentNow = getContext().registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                context.unregisterReceiver(this);
            }
        }, intentFilter);
        return intentNow;
    }


}
