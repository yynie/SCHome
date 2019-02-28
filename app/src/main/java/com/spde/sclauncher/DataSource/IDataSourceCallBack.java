package com.spde.sclauncher.DataSource;

public interface IDataSourceCallBack {
    void onComplete(IDataSourceCallBack wrapped, Object result, Exception exception);
}
