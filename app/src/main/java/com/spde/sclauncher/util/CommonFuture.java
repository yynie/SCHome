package com.spde.sclauncher.util;

import android.os.SystemClock;

public class CommonFuture implements IFuture {
    private final Object lock;
    private boolean ready;
    private int waiters;
    private Object result;

    public CommonFuture() {
        this.lock = this;
    }

    @Override
    public void setException(Throwable exception) {
        if (exception == null) {
            throw new IllegalArgumentException("exception");
        }
        setValue(exception);
    }

    @Override
    public Throwable getException() {
        Object v = getValue();

        if (v instanceof Throwable) {
            return (Throwable) v;
        } else {
            return null;
        }
    }

    @Override
    public boolean isDone() {
        synchronized (lock) {
            return ready;
        }
    }

    @Override
    public boolean await(long timeoutMillis) throws InterruptedException {
        long endTime = SystemClock.elapsedRealtime() + timeoutMillis;
        if (endTime < 0) {
            endTime = Long.MAX_VALUE;
        }
        synchronized (lock) {
            if (ready||(timeoutMillis <= 0)) {
                return ready;
            }
            waiters++;
            try {
                for (;;) {
                    try {
                        long timeOut = Math.min(timeoutMillis, 5000L);
                        lock.wait(timeOut);
                    } catch (InterruptedException e) {
                        throw e;
                    }
                    if (ready || (endTime <= SystemClock.elapsedRealtime())) {
                        return ready;
                    } else {
                        // Take a chance, detect a potential deadlock
                        //checkDeadLock();
                    }
                }
            } finally {
                waiters--;
                if (!ready) {
                    //checkDeadLock();
                }
            }
        }
    }

    protected boolean setValue(Object newValue) {
        synchronized (lock) {
            // Allowed only once.
            if (ready) {
                return false;
            }
            result = newValue;
            ready = true;
            // Now, if we have waiters, notify them that the operation has completed
            if (waiters > 0) {
                lock.notifyAll();
            }
        }
        return true;
    }

    protected Object getValue() {
        synchronized (lock) {
            return result;
        }
    }
}
