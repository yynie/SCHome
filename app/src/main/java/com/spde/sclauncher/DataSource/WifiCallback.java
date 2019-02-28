package com.spde.sclauncher.DataSource;

import android.os.SystemClock;

public class WifiCallback implements IDataSourceCallBack {
    private int minItems;
    private final long expiredAt;
    private IDataSourceCallBack userCallBack;
    private volatile boolean done;
    private final boolean periodic;

    public WifiCallback(int minItems, int timeOutSeconds, IDataSourceCallBack userCallBack, boolean periodic) {
        this.minItems = minItems;
        expiredAt = SystemClock.elapsedRealtime() + timeOutSeconds * 1000L;
        this.userCallBack = userCallBack;
        this.periodic = periodic;
    }

    public int getMinItems() {
        return minItems;
    }

    public long getExpiredAt() {
        return expiredAt;
    }

    public boolean isPeriodic() {
        return periodic;
    }

    public boolean isDone() {
        return done;
    }

    public void onComplete(Object result, Exception exception){
        onComplete(this, result, exception);
    }

    @Override
    public void onComplete(IDataSourceCallBack self, Object result, Exception exception) {
        if(!done || periodic){
            done = true;
            userCallBack.onComplete(self, result, exception);
        }
    }
}
