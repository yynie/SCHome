package com.spde.sclauncher;


import android.os.AsyncTask;
import android.os.Handler;
import android.os.SystemClock;

import com.sonf.core.future.IWriteFuture;
import com.spde.sclauncher.net.LoginFuture;
import com.spde.sclauncher.net.ResponseFuture;
import com.spde.sclauncher.net.message.GZ.ReportLocationInfo;
import com.spde.sclauncher.net.message.GZ.ReportSOS;
import com.spde.sclauncher.schcard.Business;
import com.yynie.myutils.Logger;

import java.lang.ref.WeakReference;
import java.util.concurrent.TimeoutException;

class SOSAsyncTask extends AsyncTask<Object, Void, String> {
    public static final int MSG_SOS_BY_USER_GO = 50590;//sosgo
    private static Logger log = Logger.get(SOSAsyncTask.class, Logger.Level.INFO);
    private final WeakReference<Business> businessRef;
    private final WeakReference<Handler> handlerRef;
    private final Object lock;
    private long deadLineMillis = 80 * 1000L;
    private long memElapsedMillis = 0;
    private int flag;
    private int F_LOCATION_GET = 0x01;
    private int F_LOGIN = 0x02;
    private int F_LOCATION_SENT = 0x04;
    private int F_SOS_REPORT = 0x08;
    private int F_ALL_DONE = F_LOCATION_GET | F_LOGIN | F_LOCATION_SENT | F_SOS_REPORT;
    private int F_TIMEOUT = 0x10;
    private int F_ABORT = 0x20;

    private boolean isTimeOut(){
        long old = memElapsedMillis;
        memElapsedMillis = SystemClock.elapsedRealtime();
        deadLineMillis -= memElapsedMillis - old;

        if(deadLineMillis <= 0){
            flag |= F_TIMEOUT;
            return true;
        }
        return false;
    }

    private String flagInString(){
        if(flag == 0){
            return "0";
        }
        StringBuilder sb = new StringBuilder();
        if((flag & F_LOCATION_GET) == F_LOCATION_GET){
            sb.append("-LOCATION_GET");
        }
        if((flag & F_LOGIN) == F_LOGIN){
            sb.append("-LOGIN");
        }
        if((flag & F_LOCATION_SENT) == F_LOCATION_SENT){
            sb.append("-LOCATION_SENT");
        }
        if((flag & F_SOS_REPORT) == F_SOS_REPORT){
            sb.append("-SOS_REPORT");
        }
        if((flag & F_TIMEOUT) == F_TIMEOUT){
            sb.append("-TIMEOUT");
        }
        if((flag & F_ABORT) == F_ABORT){
            sb.append("-ABORT");
        }
        return sb.toString();
    }

    public SOSAsyncTask(Business business, Handler handler) {
        handlerRef = new WeakReference<Handler>(handler);
        businessRef = new WeakReference<Business>(business);
        lock = this;
    }

    public void stop(){
        synchronized (lock) {
            businessRef.clear();
            handlerRef.clear();
        }
    }

    @Override
    protected void onPostExecute(String flag) {
        if(handlerRef != null){
            Handler handler = handlerRef.get();
            if(handler != null){
                handler.obtainMessage(MSG_SOS_BY_USER_GO, flag).sendToTarget();
            }
        }
    }

    private Business getBusiness(){
        synchronized (lock) {
            Business business = (businessRef == null) ? null : businessRef.get();
            return business;
        }
    }

    @Override
    protected String doInBackground(Object... objects) {
        String sos = (String) objects[0];
        LocationFuture locFuture = (LocationFuture) objects[1];
        deadLineMillis = Math.max(20 * 1000L, (Long) objects[2]);
        memElapsedMillis = SystemClock.elapsedRealtime();
        ReportLocationInfo locationReport = null;
        for(;;){
            Business business = getBusiness();
            if(business == null){
                log.e("business is NULL, stoped!");
                return sos;
            }
            if((flag & F_ALL_DONE) == F_ALL_DONE ||
                    (flag & F_ABORT) == F_ABORT || (flag & F_TIMEOUT) == F_TIMEOUT){
                log.i("done: flag = " + flagInString());
                return sos;
            }else if((flag & F_LOCATION_SENT) == F_LOCATION_SENT){
                ReportSOS reportSOS = new ReportSOS(null);
                ResponseFuture future = business.reportEvent(reportSOS);
                if(future == null){
                    flag |= F_ABORT;  //not send
                    continue;
                }
                log.i("SOS wait");
                while(true) {
                    try {
                        future.await(100L);
                        if(future.isDone()){ // just done we don't care about the response
                            log.i("SOS done");
                            flag |= F_SOS_REPORT;
                            break;
                        }if(isTimeOut()){
                            log.i("SOS time out");
                            break;
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }else if((flag & F_LOGIN) == F_LOGIN){
                ResponseFuture future = business.reportEvent(locationReport);
                if(future == null){
                    flag |= F_ABORT;  //not send
                    continue;
                }
                IWriteFuture writeFuture = future.getWriteFuture();
                while(true) {
                    try {
                        if(writeFuture == null){//not send yet
                            log.i("locationReport not send yet");
                            future.await(100L);
                            if(isTimeOut()){
                                log.i("locationReport not send and timeout");
                                break;
                            }
                            writeFuture = future.getWriteFuture();
                            if(writeFuture == null && future.isDone()) {
                                log.i("locationReport done but, not send");
                                flag |= F_ABORT;  //not send but done, means error
                                break;
                            }else if(writeFuture != null){
                                log.i("locationReport sendddddd");
                                continue; //send now wait for send ok
                            }
                        }else if(writeFuture.isWritten()) {
                            log.i("locationReport write ok");
                            flag |= F_LOCATION_SENT;
                            break;
                        }else{
                            log.i("locationReport wait write");
                            writeFuture.await(100L);
                            if(isTimeOut()){
                                log.i("locationReport wait write time out");
                                break;
                            }
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            } else if((flag & F_LOCATION_GET) == F_LOCATION_GET){
                log.i("check login wait");
                LoginFuture future = business.requestLoginByUser();
                while(true) {
                    try {
                        future.await(300L);
                        if(future.isDone()){
                            log.i("check login done");
                            flag |= future.isLogin()?F_LOGIN:F_ABORT;
                            break;
                        }else if(isTimeOut()){
                            log.i("check login timeout");
                            future.setException(new TimeoutException("Wait for Login Time out"));
                            business.removeLoginFuture(future);
                            break;
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            } else if(flag == 0){
                log.i("wait for locFuture");
                while(true) {
                    try {
                        locFuture.await(300L);
                        if(locFuture.isDone()){
                            log.i("locFuture done");
                            locationReport = locFuture.getReport();
                            flag |= F_LOCATION_GET;
                            break;
                        }else if(isTimeOut()){
                            log.i("locFuture timeout");
                            break;
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
}
