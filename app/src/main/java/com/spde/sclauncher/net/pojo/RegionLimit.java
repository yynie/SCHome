package com.spde.sclauncher.net.pojo;

import java.io.Serializable;
import java.util.List;

public class RegionLimit implements Serializable {
    private int no; // 编号
    private RegionShape shape; //Round  Rectangle  Polygon（8）
    private List<PeriodWeekly> periodList;

    public RegionLimit() {
    }

    public int getNo() {
        return no;
    }

    public RegionShape getShape() {
        return shape;
    }

    public List<PeriodWeekly> getPeriodList() {
        return periodList;
    }

    public RegionLimit(int no, RegionShape shape, List<PeriodWeekly> periodList) {
        this.no = no;
        this.shape = shape;
        this.periodList = periodList;
    }
}
