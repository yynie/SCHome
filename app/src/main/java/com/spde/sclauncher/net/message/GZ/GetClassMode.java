package com.spde.sclauncher.net.message.GZ;

import com.spde.sclauncher.net.message.AbstractISCMessage;
import com.spde.sclauncher.net.message.BodyFormatException;
import com.spde.sclauncher.net.message.IRequest;
import com.spde.sclauncher.net.message.ISCHeader;
import com.spde.sclauncher.net.message.Type;

public class GetClassMode extends AbstractISCMessage<GZProtocolHeader> implements IRequest {
    public final static String NAME = "GET_CLASS_MODEL";
    public final static Type TYPE = Type.TYPE_UPSTREAM_REQ;

    public GetClassMode(ISCHeader header) {
        super(header);
    }

    public GetClassMode(){
        super(null);
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
        return "1";
    }

    @Override
    public void setProtocolBody(String body, int length) throws BodyFormatException {
        //TYPE_UPSTREAM_REQ 的上报消息无需实现此方法
    }
}
