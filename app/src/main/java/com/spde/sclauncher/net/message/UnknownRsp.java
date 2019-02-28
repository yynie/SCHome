package com.spde.sclauncher.net.message;

import com.spde.sclauncher.net.message.GZ.GZProtocolHeader;

public class UnknownRsp extends AbstractISCMessage<GZProtocolHeader> implements IResponse {
    private String body;
    public final static Type TYPE = Type.TYPE_UPSTREAM_RSP;

    public UnknownRsp(ISCHeader header) {
        super(header);
    }

    public String getBody() {
        return body;
    }

    @Override
    protected GZProtocolHeader generateHeader() {
        return null;
    }

    @Override
    protected String getName() {
        return "UnknownRsp";
    }

    @Override
    protected Type getType() {
        return TYPE;
    }

    @Override
    public String toProtocolBody() {
        return body;
    }

    @Override
    public void setProtocolBody(String body, int length){
        this.body = body;
    }
}
