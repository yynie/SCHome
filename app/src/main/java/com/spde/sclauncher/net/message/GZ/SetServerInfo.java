package com.spde.sclauncher.net.message.GZ;

import com.spde.sclauncher.net.message.AbstractISCMessage;
import com.spde.sclauncher.net.message.BodyFormatException;
import com.spde.sclauncher.net.message.IRequest;
import com.spde.sclauncher.net.message.ISCHeader;
import com.spde.sclauncher.net.message.Type;

public class SetServerInfo extends AbstractISCMessage<GZProtocolHeader> implements IRequest {
    public final static String NAME = "SET_SERVER_INFO";
    public final static Type TYPE = Type.TYPE_DOWNSTREAM_REQ;
    private String serverAddr;
    private int serverPort = -1;

    public SetServerInfo(ISCHeader header) {
        super(header);
    }

    public String getServerAddr() {
        return serverAddr;
    }

    public int getServerPort() {
        return serverPort;
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
        //平台服务地址URL@端口号
        String[] fields = body.split("@");
        if(fields.length >= 2){
            serverAddr = fields[0].trim();
            try {
                serverPort = Integer.parseInt(fields[1].trim());
            }catch (NumberFormatException e){
                e.printStackTrace();
                serverPort = -1;
            }

        }
    }

}
