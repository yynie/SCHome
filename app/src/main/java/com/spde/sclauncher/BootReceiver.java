package com.spde.sclauncher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        //adb shell dumpsys activity activities
        Log.i("SCLauncher BootReceiver", intent.getAction());
        if(intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)
                || intent.getAction().equals(Intent.ACTION_MEDIA_MOUNTED)){
            Intent startMainIntent = new Intent(Intent.ACTION_MAIN);
            startMainIntent.setClass(context, Home.class);
            startMainIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startMainIntent.putExtra("isPowerOn", true);
            context.startActivity(startMainIntent);
        }
    }
}
