package com.spde.sclauncher.net.message.GZ;

import com.spde.sclauncher.net.message.AbstractISCMessage;
import com.spde.sclauncher.net.message.IRequest;
import com.spde.sclauncher.net.message.ISCHeader;
import com.spde.sclauncher.net.message.Type;

public class ReportCallLog extends AbstractISCMessage<GZProtocolHeader> implements IRequest {
    public final static String NAME = "REPORT_CALL_LOG";
    public final static Type TYPE = Type.TYPE_UPSTREAM_REQ;
    private String phone; //目标电话号码
    private String startAt; //开始时间 yyyyMMddhhmmss
    private String endAt; //结束时间 yyyyMMddhhmmss
    private int durationSec; //通话时长单位：秒
    private boolean inOrOut; //0呼入1呼出  true = in , false = out;

    public ReportCallLog(ISCHeader header) {
        super(header);
    }

    public ReportCallLog(String phone, String startAt, String endAt, int durationSec, boolean inOrOut) {
        super(null);
        this.phone = phone;
        this.startAt = startAt;
        this.endAt = endAt;
        this.durationSec = durationSec;
        this.inOrOut = inOrOut;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public void setStartAt(String startAt) {
        this.startAt = startAt;
    }

    public void setEndAt(String endAt) {
        this.endAt = endAt;
    }

    public void setDurationSec(int durationSec) {
        this.durationSec = durationSec;
    }

    public void setInOrOut(boolean inOrOut) {
        this.inOrOut = inOrOut;
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
        //目标电话号码@开始时间!结束时间@通话时长@呼入呼出
        StringBuilder sb = new StringBuilder();
        sb.append(phone).append(SPLIT_CH).
                append(startAt).append("!").append(endAt).append(SPLIT_CH)
                .append(durationSec).append(SPLIT_CH)
                .append((inOrOut?"0":"1"));
        return sb.toString();
    }

    @Override
    public void setProtocolBody(String body, int length) {
        //TYPE_UPSTREAM_REQ 的上报消息无需实现此方法
    }
}
