package com.spde.sclauncher.net.message.GZ;

import com.spde.sclauncher.net.message.AbstractISCMessage;
import com.spde.sclauncher.net.message.BodyFormatException;
import com.spde.sclauncher.net.message.IRequest;
import com.spde.sclauncher.net.message.ISCHeader;
import com.spde.sclauncher.net.message.Type;

public class SetLocationFrequency extends AbstractISCMessage<GZProtocolHeader> implements IRequest {
    public final static String NAME = "FREQUENCY_LOCATION_SET";
    public final static Type TYPE = Type.TYPE_DOWNSTREAM_REQ;
    private int locateFreqMinutes;

    public SetLocationFrequency(ISCHeader header) {
        super(header);
    }

    public int getLocateFreqMinutes() {
        return locateFreqMinutes;
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
            locateFreqMinutes = Integer.parseInt(body.trim());
        }catch (NumberFormatException e){
            throw new BodyFormatException("SetLocationFrequency", e.getMessage());
        }
    }
}
