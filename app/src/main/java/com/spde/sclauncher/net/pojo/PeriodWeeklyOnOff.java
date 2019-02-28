package com.spde.sclauncher.net.pojo;

public class PeriodWeeklyOnOff extends PeriodWeekly {
    private int no; //序号
    private boolean onOrOff; //是否生效

    public PeriodWeeklyOnOff() {
        super();
    }

    public PeriodWeeklyOnOff(int startMinute, int endMinute, String weeks, int no, boolean onOrOff) {
        super(startMinute, endMinute, weeks);
        this.no = no;
        this.onOrOff = onOrOff;
    }

    public int getNo() {
        return no;
    }

    public void setNo(int no) {
        this.no = no;
    }

    public boolean isOnOrOff() {
        return onOrOff;
    }

    public void setOnOrOff(boolean onOrOff) {
        this.onOrOff = onOrOff;
    }

}
