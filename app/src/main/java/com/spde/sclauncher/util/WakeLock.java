package com.spde.sclauncher.util;

import android.content.Context;
import android.os.PowerManager;

import com.yynie.myutils.Logger;

import java.util.concurrent.atomic.AtomicInteger;

import static com.spde.sclauncher.SCConfig.KEEP_SCREEN_ON;

public class WakeLock {
    private static Logger log = Logger.get(WakeLock.class, Logger.Level.DEBUG);
    private static WakeLock sInstance = null;
    private android.os.PowerManager.WakeLock cpuLock;
    private AtomicInteger lockCounter = new AtomicInteger(0);

    public static WakeLock getInstance(){
        synchronized (WakeLock.class){
            if(sInstance == null) {
                sInstance = new WakeLock();
            }
            return sInstance;
        }
    }

    /** 使用前先初始化，推荐在 application 或 第一个activity的onCreate()中调用 */
    public void init(Context context) {
        if(cpuLock != null){
            if(cpuLock.isHeld()){
                cpuLock.release();
                log.e("release CPU LOCK on init!!!");
            }
        }
        android.os.PowerManager pm = (android.os.PowerManager) context.getSystemService(Context.POWER_SERVICE);
        String name = context.getPackageName();
        if(KEEP_SCREEN_ON){
            cpuLock = pm.newWakeLock(android.os.PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, name); 
        }else{
            cpuLock = pm.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK | android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP, name);
        }
    }

    /** 获取 */
    public void acquire(){
        if (lockCounter.getAndIncrement() == 0) {
            cpuLock.acquire();
            log.i("acquire : acquire CPU LOCK");
        }
    }

    /** 释放 */
    public void release(){
        if (lockCounter.decrementAndGet() <= 0) {
            if(cpuLock.isHeld()) {
                cpuLock.release();
                log.i("release : release CPU LOCK");
            }else{
                log.w("release but CPU LOCK might be release by finalCheck");
            }
        }
    }

    /** 如果需要 可以再退出程序前检查一下锁是否被释放了 */
    public void finalCheck(){
        if(lockCounter.get() != 0){
            log.e("lockCounter is not zero, you might do something wrong with this WakeLock");
        }
        if(cpuLock.isHeld()){
            cpuLock.release();
            log.e("release CPU LOCK on finalCheck");
        }
    }
}
