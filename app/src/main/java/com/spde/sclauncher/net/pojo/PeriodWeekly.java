package com.spde.sclauncher.net.pojo;

public class PeriodWeekly extends Period {
    private String weeks; //0-周日，1-6

    public PeriodWeekly() {
        super();
    }

    public PeriodWeekly(int startMinute, int endMinute, String weeks) {
        super(startMinute, endMinute);
        this.weeks = weeks;
    }

    public String getWeeks() {
        return weeks;
    }

    public void setWeeks(String weeks) {
        this.weeks = weeks;
    }
}
