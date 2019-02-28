package com.spde.sclauncher.net.message.GZ;

import com.spde.sclauncher.net.message.AbstractISCMessage;
import com.spde.sclauncher.net.message.BodyFormatException;
import com.spde.sclauncher.net.message.IRequest;
import com.spde.sclauncher.net.message.ISCHeader;
import com.spde.sclauncher.net.message.ISCMessage;
import com.spde.sclauncher.net.message.Type;
import com.yynie.myutils.StringUtils;

public class RemoteOperation extends AbstractISCMessage<GZProtocolHeader> implements IRequest {
    public final static String NAME = "REMOTE_OPERATE_TERMINAL";
    public final static Type TYPE = Type.TYPE_DOWNSTREAM_REQ;
    private boolean doReboot;
    private boolean doRecovery;

    public RemoteOperation(ISCHeader header) {
        super(header);
    }

    public boolean isDoReboot() {
        return doReboot;
    }

    public boolean isDoRecovery() {
        return doRecovery;
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
        //重启终端@恢复除平台地址以外的出厂设置     0=不执行，1=执行
        String[] fields = body.split(SPLIT_CH);
        if(fields.length > 0){
            doReboot = StringUtils.equals(fields[0].trim(), "1");
        }

        if(fields.length > 1){
            doRecovery = StringUtils.equals(fields[1].trim(), "1");
        }
    }
}
