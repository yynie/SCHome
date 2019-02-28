package com.spde.sclauncher.net.message.GZ;

import com.spde.sclauncher.net.message.AbstractISCMessage;
import com.spde.sclauncher.net.message.IRequest;
import com.spde.sclauncher.net.message.ISCHeader;
import com.spde.sclauncher.net.message.Type;

public class ReportCrossBorder extends AbstractISCMessage<GZProtocolHeader> implements IRequest {
    public final static String NAME = "REPORT_CROSS_BORDER";
    public final static Type TYPE = Type.TYPE_UPSTREAM_REQ;
    private int opType; //请求状态：1表示父亲卡 2表示母亲卡
    private boolean inOrOut; //1或者0(1:在区域内  0：在区域外) true = in , false = out
    private int regionNo; //区域编号
    private String nmea;

    public ReportCrossBorder(ISCHeader header) {
        super(header);
    }

    public ReportCrossBorder(int opType, boolean inOrOut, int regionNo) {
        super(null);
        this.opType = opType;
        this.inOrOut = inOrOut;
        this.regionNo = regionNo;
    }

    public void setOpType(int opType) {
        this.opType = opType;
    }

    public void setInOrOut(boolean inOrOut) {
        this.inOrOut = inOrOut;
    }

    public void setRegionNo(int regionNo) {
        this.regionNo = regionNo;
    }

    public void setNmea(String nmea) {
        this.nmea = nmea;
    }

    @Override
    protected GZProtocolHeader generateHeader() {
        return new GZProtocolHeader();
    }

    @Override
    protected String getName() {
        return NAME;
    }

    @Override
    protected Type getType() {
        return TYPE;
    }

    @Override
    public String toProtocolBody() {
        //父母卡类型@进入某区域或离开某区域@位置信息@区域编号
        StringBuilder sb = new StringBuilder();
        sb.append(opType).append(SPLIT_CH).append((inOrOut?"1":"0"))
                .append(SPLIT_CH).append(nmea).append(SPLIT_CH).append(regionNo);
        return sb.toString();
    }

    @Override
    public void setProtocolBody(String body, int length) {
        //TYPE_UPSTREAM_REQ 的上报消息无需实现此方法
    }

}
