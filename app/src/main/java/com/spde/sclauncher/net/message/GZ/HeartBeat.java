package com.spde.sclauncher.net.message.GZ;

import com.spde.sclauncher.net.message.AbstractISCMessage;
import com.spde.sclauncher.net.message.IRequest;
import com.spde.sclauncher.net.message.ISCHeader;
import com.spde.sclauncher.net.message.Type;

public class HeartBeat extends AbstractISCMessage<GZProtocolHeader> implements IRequest {
    public final static String NAME = "REPORT_HEARTBEAT";
    public final static Type TYPE = Type.TYPE_UPSTREAM_REQ;

    private int batteryPercent;

    public HeartBeat(ISCHeader header) {
        super(header);
    }

    public void setBatteryPercent(int batteryPercent) {
        this.batteryPercent = batteryPercent;
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
        return String.valueOf(batteryPercent) + "%";
    }

    @Override
    public void setProtocolBody(String body, int length) {
        //TYPE_UPSTREAM_REQ 的上报消息无需实现此方法
    }
}
