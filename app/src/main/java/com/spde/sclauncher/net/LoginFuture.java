package com.spde.sclauncher.net;


import com.spde.sclauncher.util.CommonFuture;

public class LoginFuture extends CommonFuture {

    public void setResult(Boolean result){
        if (result == null) {
            throw new IllegalArgumentException("result");
        }
        setValue(result);
    }

    public Boolean isLogin(){
        Object v = getValue();
        if(v instanceof Boolean){
            return (Boolean)v;
        }
        return false;
    }
}
