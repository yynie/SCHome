package com.spde.sclauncher;

import android.app.Application;

import com.spde.sclauncher.util.WakeLock;
import com.yynie.myutils.Logger;

public class HomeApplication extends Application {
    private final Logger log = Logger.get(HomeApplication.class, Logger.Level.DEBUG);

    private DebugDynamic debugDynamic;
    @Override
    public void onCreate() {
        super.onCreate();

        /**** school card start  ****/
        debugDynamic = new DebugDynamic(this);
         //init Logger global Level
        if(debugDynamic.isDebug()) {
            Logger.setGlobalLevel(Logger.Level.DEBUG);
        }else{
            Logger.setGlobalLevel(Logger.Level.INFO);
        }

        Logger.setGlobalHead("[@SC]"); 

        WakeLock.getInstance().init(this);
        /**** school card end  ****/
    }
}
