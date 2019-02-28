package com.spde.sclauncher;

import android.app.Application;

import com.spde.sclauncher.util.WakeLock;
import com.yynie.myutils.Logger;

public class HomeApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        /**** school card start  ****/
        //init Logger global Level
        if(SCConfig.DEBUG) {
            Logger.setGlobalLevel(Logger.Level.INFO);
        }else{
            Logger.setGlobalLevel(Logger.Level.WARN);
        }

        Logger.setGlobalHead("[@SC]");

        WakeLock.getInstance().init(this);
        /**** school card end  ****/
    }
}
