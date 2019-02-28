package com.spde.sclauncher.schcard;

import android.os.SystemClock;

import com.spde.sclauncher.net.message.IRequest;
import com.spde.sclauncher.net.message.ISCMessage;

public class DelayReport implements IRequest{
    private final ISCMessage message;
    private long expiredAt = -1L;   //SystemClock.elapsedRealtime(), -1 表示从不过期
    private int retry = -1; //-1 表示发送失败或无应答不重试
    private boolean isPowerOnReport;

    public DelayReport(ISCMessage message){
        if(message instanceof IRequest){
            this.message = message;
        }else{
            throw new RuntimeException("message type error");
        }
    }
    /**
     * timeout 有效时间，超过这个时间没排到发送就不发了     -1 表示从不过期
     * retryTimes 应答超时后重试的次数
     */
    public DelayReport(ISCMessage message, long timeOutMs, int retryTimes){
        this(message);
        if(timeOutMs >= 0) {
            this.expiredAt = SystemClock.elapsedRealtime() + timeOutMs;
        }
        if(retryTimes >= 0){
            this.retry = retryTimes;
        }
    }

    public boolean isPowerOnReport() {
        return isPowerOnReport;
    }

    public void setPowerOnReport(boolean powerOnReport) {
        isPowerOnReport = powerOnReport;
    }

    public ISCMessage getMessage() {
        return message;
    }

    public boolean isExpired(){
        if(expiredAt < 0){
            return false;
        }
        long cur = SystemClock.elapsedRealtime();
        return (expiredAt <= cur);
    }

    public void setTimeOut(long timeOutMs) {
        this.expiredAt = SystemClock.elapsedRealtime() + timeOutMs;
    }

    public long getExpiredAt() {
        return this.expiredAt;
    }

    public int getRetry() {
        return retry;
    }

    public void setRetry(int retry) {
        this.retry = retry;
    }
}
