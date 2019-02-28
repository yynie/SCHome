package com.spde.sclauncher.net.pojo;


import java.io.Serializable;
import java.util.List;

public class IncomingCallSet implements Serializable {
    private String phone;
    private List<Period> periods;

    //必须有一个无参的构造器，如想用序列化传参的话
    public IncomingCallSet() {
    }

    public IncomingCallSet(String phone, List<Period> periods) {
        this.phone = phone;
        this.periods = periods;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public List<Period> getPeriods() {
        return periods;
    }

    public void setPeriods(List<Period> periods) {
        this.periods = periods;
    }
}
