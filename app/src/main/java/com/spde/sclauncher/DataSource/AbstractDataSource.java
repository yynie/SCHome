package com.spde.sclauncher.DataSource;

import android.content.Context;
import android.net.Uri;

public abstract class AbstractDataSource {
    protected static final String AUTHORITY = "com.spde.sclauncher.data";
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY );
    private Context context;

    public void init(Context context){
        this.context = context;
        prepareOnInit();
    }

    protected Context getContext(){
        return context;
    }

    /** 从存储中读出数据 及其他初始化 */
    protected abstract void prepareOnInit();

    /** 如果有需要，在app退出时释放资源 */
    public abstract void release();

    /** 清除所有用户数据 回复出厂状态*/
    public abstract void restore();
}
