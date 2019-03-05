package com.spde.sclauncher.util;

public interface IFuture {
    void setException(Throwable exception);
    Throwable getException();
    boolean isDone();
    boolean await(long timeoutMillis) throws InterruptedException;
}
