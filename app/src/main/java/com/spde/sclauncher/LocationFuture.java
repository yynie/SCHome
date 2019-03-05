package com.spde.sclauncher;

import com.spde.sclauncher.net.message.GZ.ReportLocationInfo;
import com.spde.sclauncher.util.CommonFuture;

public class LocationFuture extends CommonFuture {
    public void setReport(ReportLocationInfo report){
        if (report == null) {
            throw new IllegalArgumentException("report");
        }
        setValue(report);
    }

    public ReportLocationInfo getReport(){
        Object v = getValue();
        if(v instanceof ReportLocationInfo){
            return (ReportLocationInfo)v;
        }
        return null;
    }
}
