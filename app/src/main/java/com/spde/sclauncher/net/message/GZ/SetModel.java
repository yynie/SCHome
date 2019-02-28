package com.spde.sclauncher.net.message.GZ;

import com.spde.sclauncher.net.message.AbstractISCMessage;
import com.spde.sclauncher.net.message.BodyFormatException;
import com.spde.sclauncher.net.message.IRequest;
import com.spde.sclauncher.net.message.ISCHeader;
import com.spde.sclauncher.net.message.Type;
import com.yynie.myutils.StringUtils;

public class SetModel extends AbstractISCMessage<GZProtocolHeader> implements IRequest {
    public final static String NAME = "SET_MODEL";
    public final static Type TYPE = Type.TYPE_DOWNSTREAM_REQ;
    private boolean ring;
    private boolean incomingForbidden;
    private boolean outgoingForbidden;

    public SetModel(ISCHeader header) {
        super(header);
    }

    public boolean isRing() {
        return ring;
    }

    public boolean isIncomingForbidden() {
        return incomingForbidden;
    }

    public boolean isOutgoingForbidden() {
        return outgoingForbidden;
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
        //静音@响铃@限制呼入@限制呼出   0=不启用，1=启用
        String[] fields = body.split(SPLIT_CH);
        if(fields.length > 0){
            ring = !StringUtils.equals(fields[0], "1");
        }
        if(fields.length > 1){
            ring = StringUtils.equals(fields[1], "1");
        }
        if(fields.length > 2){
            incomingForbidden = StringUtils.equals(fields[2], "1");
        }
        if(fields.length > 3){
            outgoingForbidden = StringUtils.equals(fields[3], "1");
        }
    }

}
