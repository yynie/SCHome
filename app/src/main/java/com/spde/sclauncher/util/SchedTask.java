package com.spde.sclauncher.util;

import android.os.SystemClock;

public class SchedTask{
    private final long MIN_DELAY_ALW = TaskScheduler.MIN_DELAY_ALW;
    public interface ExpiredCallback{
        void onExpired(SchedTask task);
    }

    private final String name;
    private final long delayToMillis;
    private Object arg;
    private ExpiredCallback expiredCallback;

    public SchedTask(String name, long delayMillis, Object arg, ExpiredCallback callback) {
        this.name = name;
        this.delayToMillis = SystemClock.elapsedRealtime() + delayMillis;
        this.arg = arg;
        expiredCallback = callback;
    }

    public void invoke(){
        if(expiredCallback != null) expiredCallback.onExpired(this);
    }

    public boolean expired(){
        long now = SystemClock.elapsedRealtime();
        return (delayToMillis <= (now + MIN_DELAY_ALW)); //时间已到 或 还差3秒 都认为时间到
    }

    public String getName() {
        return name;
    }

    public long getDelayToMillis() {
        return delayToMillis;
    }

    public Object getArg() {
        return arg;
    }

    public void setArg(Object arg) {
        this.arg = arg;
    }
}
