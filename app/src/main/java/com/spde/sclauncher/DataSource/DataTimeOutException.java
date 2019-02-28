package com.spde.sclauncher.DataSource;

public class DataTimeOutException extends Exception {
    public DataTimeOutException() {
    }

    public DataTimeOutException(String detailMessage) {
        super(detailMessage);
    }

    public DataTimeOutException(String detailMessage, Throwable throwable) {
        super(detailMessage, throwable);
    }

    public DataTimeOutException(Throwable throwable) {
        super(throwable);
    }
}
