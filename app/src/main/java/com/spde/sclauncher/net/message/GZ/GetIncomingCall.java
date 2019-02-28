package com.spde.sclauncher.net.message.GZ;

import com.spde.sclauncher.net.message.AbstractISCMessage;
import com.spde.sclauncher.net.message.BodyFormatException;
import com.spde.sclauncher.net.message.IRequest;
import com.spde.sclauncher.net.message.ISCHeader;
import com.spde.sclauncher.net.message.Type;
import com.spde.sclauncher.net.pojo.IncomingCallSet;

import java.util.List;

public class GetIncomingCall extends AbstractISCMessage<GZProtocolHeader> implements IRequest {
    public final static String NAME = "GET_INCOMING_CALL";
    public final static Type TYPE = Type.TYPE_UPSTREAM_REQ;

    public GetIncomingCall(ISCHeader header) {
        super(header);
    }

    public GetIncomingCall(){
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
