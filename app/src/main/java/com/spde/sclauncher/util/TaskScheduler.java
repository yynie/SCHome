package com.spde.sclauncher.util;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.SystemClock;

import com.yynie.myutils.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class TaskScheduler{
    private static Logger log = Logger.get(TaskScheduler.class, Logger.Level.INFO);
    private final static String INTENT_WAKE_UP = "com.spde.sclauncher.SCHEDULED_WAKE_UP";
    private Context context;
    private AlarmManager alarmManager;
    private PendingIntent pendingIntent;
    private List<SchedTask> queue = new ArrayList<SchedTask>(); //要做排序，数目也不会很多，直接用sync块就好了
    private long lastDelayTo = -1L;
    public static final long MIN_DELAY_ALW = 3 * 1000L; //延时容差3s
    private final long MIN_WAKEUP_DELAY = 1 * 60 * 1000L; //最小唤醒闹钟1分钟
    private BroadcastReceiver broadcastReceiver;

    public TaskScheduler(Context context){
        this.context = context;
        queue.clear();
        alarmManager = (AlarmManager) this.context.getSystemService(Context.ALARM_SERVICE);
        pendingIntent = PendingIntent.getBroadcast(this.context, 0, new Intent(INTENT_WAKE_UP), 0);

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(INTENT_WAKE_UP);
        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equals(INTENT_WAKE_UP)) {
                    WakeLock.getInstance().acquire();
                    invoke();
                    schedule();
                    WakeLock.getInstance().release();
                }
            }
        };
        this.context.registerReceiver(broadcastReceiver, intentFilter);
    }

    public void release(){
        if(broadcastReceiver != null) {
            this.context.unregisterReceiver(broadcastReceiver);
        }
        broadcastReceiver = null;
        alarmManager.cancel(pendingIntent);
        queue.clear();
    }

    public boolean offer(SchedTask task){
        synchronized (queue) {
            queue.add(task);
            //按时间asc排序
            Comparator<SchedTask> comp = new Comparator<SchedTask>() {
                @Override
                public int compare(SchedTask lhs, SchedTask rhs) {
                    if (lhs.getDelayToMillis() > rhs.getDelayToMillis())
                        return 1;
                    else
                        return -1;
                }
            };
            Collections.sort(queue, comp);
            schedule();
            return true;
        }
    }

    public void remove(SchedTask task){
        synchronized (queue) {
            queue.remove(task);
        }
    }

    private long getDelayTo(){
        synchronized (queue) {
            SchedTask task = (queue.isEmpty()) ? null : queue.get(0);  //任务是按时间升序的，只需要取第一个的时间就行
            if (task != null) {
                log.i("getDelayTo:" + task.getName());
                return task.getDelayToMillis();
            }
            return -1L;
        }
    }

    private void schedule(){
        long delayTo = getDelayTo();
        if(delayTo > 0) {
            long cur = SystemClock.elapsedRealtime();
            if(delayTo < cur){
                log.i("schedule invoke now");
                invoke();
                long newdelayTo = getDelayTo();
                assert (newdelayTo == delayTo);
                delayTo = newdelayTo;
            }
            if(delayTo > 0){
                cur = SystemClock.elapsedRealtime();
                if(delayTo < cur){
                    delayTo = cur + MIN_WAKEUP_DELAY;
                }
                log.i("schedule delayTo=" + delayTo + ", lastDelayTo=" + lastDelayTo);
                boolean reset = false;
                if(lastDelayTo < 0) {
                    lastDelayTo = delayTo;
                    reset = true;
                }else{
                    if(Math.abs(delayTo - lastDelayTo) >= MIN_DELAY_ALW){
                        lastDelayTo = delayTo;
                        reset = true;
                    }
                }
                if(reset) {
                    log.i("schedule set alarm cur=" + cur/1000 + ",delayTo=" + lastDelayTo/1000);
                    alarmManager.cancel(pendingIntent);
                    alarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, lastDelayTo, pendingIntent);
                }
            }
        }
    }

    private List<SchedTask> getExpiredTasks(){
        synchronized (queue) {
            List<SchedTask> ret = new ArrayList<SchedTask>();
            while (!queue.isEmpty()) {
                SchedTask task = queue.get(0);
                if (task.expired()) { //到期任务从队列中取出
                    task = queue.remove(0);
                    ret.add(task);
                } else {
                    break; //队列是时间升序的 如果当前任务没到期就可以break了
                }
            }
            return ret;
        }
    }

    private void invoke(){
        List<SchedTask> tasks = getExpiredTasks();
        for(SchedTask t:tasks){
            t.invoke();
        }
    }
}
