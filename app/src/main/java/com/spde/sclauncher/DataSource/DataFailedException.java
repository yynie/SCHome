package com.spde.sclauncher.DataSource;

public class DataFailedException extends Exception {
    public DataFailedException() {
    }

    public DataFailedException(String detailMessage) {
        super(detailMessage);
    }

    public DataFailedException(String detailMessage, Throwable throwable) {
        super(detailMessage, throwable);
    }

    public DataFailedException(Throwable throwable) {
        super(throwable);
    }
}
