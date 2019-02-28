package com.spde.sclauncher.net.pojo;

import java.io.Serializable;

public class Period implements Serializable {
    int startMinute; //起始时间换算成分钟
    private int endMinute;  //结束时间换算成分钟

    public Period() {
    }

    public Period(int startMinute, int endMinute) {
        this.startMinute = startMinute;
        this.endMinute = endMinute;
    }

    public int getStartMinute() {
        return startMinute;
    }

    public void setStartMinute(int startMinute) {
        this.startMinute = startMinute;
    }

    public int getEndMinute() {
        return endMinute;
    }

    public void setEndMinute(int endMinute) {
        this.endMinute = endMinute;
    }
}
