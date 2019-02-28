package com.spde.sclauncher.net.message;

import com.spde.sclauncher.net.message.GZ.GZProtocolHeader;

public class UnknownMessage extends AbstractISCMessage<GZProtocolHeader> implements IRequest{
    private String body;
    public final static Type TYPE = Type.TYPE_DOWNSTREAM_REQ;

    public UnknownMessage(ISCHeader header) {
        super(header);
    }

    @Override
    protected GZProtocolHeader generateHeader() {
        return null;
    }

    @Override
    protected String getName() {
        return "UnknownMessage";
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
    public void setProtocolBody(String body, int length) throws BodyFormatException {
        this.body = body;
    }
}
