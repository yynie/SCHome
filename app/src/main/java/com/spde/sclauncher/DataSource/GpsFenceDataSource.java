package com.spde.sclauncher.DataSource;

import com.spde.sclauncher.net.pojo.RegionLimit;

public class GpsFenceDataSource extends AbstractDataSource {
    private static GpsFenceDataSource sInstance;

    public static GpsFenceDataSource getInstance(){
        synchronized (GpsFenceDataSource.class){
            if(sInstance == null){
                sInstance = new GpsFenceDataSource();
            }
            return sInstance;
        }
    }

    @Override
    protected void prepareOnInit() {
        //TODO: 读出数据？？
    }

    @Override
    public void release() {

    }

    @Override
    public void restore() {
        //TODO: 删掉所有数据
    }

    public void remove(int opType, RegionLimit regionLimit){
        //opType 请求状态：1表示父亲卡 2表示母亲卡
        int deleteNo = regionLimit.getNo();
        //TODO: 删掉deleteNo编号的区域
    }

    public void addOrUpdate(int opType, RegionLimit regionLimit){
        //opType 请求状态：1表示父亲卡 2表示母亲卡
        //TODO: 新增或修改
    }
}
