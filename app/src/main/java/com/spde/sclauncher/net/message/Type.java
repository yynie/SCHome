package com.spde.sclauncher.net.message;

public enum Type {
    TYPE_DOWNSTREAM_REQ(1),//下发请求  服务器下发
    TYPE_DOWNSTREAM_RSP(2), //下发响应   客户端回复给服务器
    TYPE_UPSTREAM_REQ(3), //上报请求  客户端上报
    TYPE_UPSTREAM_RSP(4); //上报响应   服务器回复给客户端

    int value;
    Type(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }

    public static Type fromIntValue(int v){
        for (int i = 0; i < values().length; i++) {
            Type lev = Type.values()[i];
            if (v == lev.value()) {
                return lev;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return "Type(" + value + ")";
    }
}
