package com.spde.sclauncher.net.message.GZ;

import com.spde.sclauncher.net.message.AbstractISCMessage;
import com.spde.sclauncher.net.message.BodyFormatException;
import com.spde.sclauncher.net.message.IRequest;
import com.spde.sclauncher.net.message.ISCHeader;
import com.spde.sclauncher.net.message.Type;

public class SetHeartBeat extends AbstractISCMessage<GZProtocolHeader> implements IRequest {
    public final static String NAME = "SET_HEARTBEAT";
    public final static Type TYPE = Type.TYPE_DOWNSTREAM_REQ;
    private int heartBeatMinutes;

    public SetHeartBeat(ISCHeader header) {
        super(header);
    }

    public int getHeartBeatMinutes() {
        return heartBeatMinutes;
    }

    @Override
    protected GZProtocolHeader generateHeader() {
        //TYPE_DOWNSTREAM_REQ 的消息无需实现此方法
        return null;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Type getType() {
        return TYPE;
    }

    @Override
    public String toProtocolBody() {
        return null;
    }

    @Override
    public void setProtocolBody(String body, int length) throws BodyFormatException {
        try {
            heartBeatMinutes = Integer.parseInt(body.trim());
        }catch (NumberFormatException e){
            throw new BodyFormatException("SetHeartBeat", e.getMessage());
        }
    }
}
