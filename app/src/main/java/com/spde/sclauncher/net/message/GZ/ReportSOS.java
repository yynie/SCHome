package com.spde.sclauncher.net.message.GZ;

import com.spde.sclauncher.net.message.AbstractISCMessage;
import com.spde.sclauncher.net.message.IRequest;
import com.spde.sclauncher.net.message.ISCHeader;
import com.spde.sclauncher.net.message.Type;

public class ReportSOS extends AbstractISCMessage<GZProtocolHeader> implements IRequest {
    public final static String NAME = "REPORT_SOS";
    public final static Type TYPE = Type.TYPE_UPSTREAM_REQ;

    public ReportSOS(ISCHeader header) {
        super(header);
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
        //SOS触发后立即通过位置上报一条当前位置，然后上报此接口  body固定为"1"
        return "1";
    }

    @Override
    public void setProtocolBody(String body, int length) {
        //TYPE_UPSTREAM_REQ 的上报消息无需实现此方法
    }

}
