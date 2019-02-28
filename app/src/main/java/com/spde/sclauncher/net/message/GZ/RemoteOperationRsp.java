package com.spde.sclauncher.net.message.GZ;

import com.spde.sclauncher.net.message.AbstractISCMessage;
import com.spde.sclauncher.net.message.BodyFormatException;
import com.spde.sclauncher.net.message.IRequest;
import com.spde.sclauncher.net.message.IResponse;
import com.spde.sclauncher.net.message.ISCHeader;
import com.spde.sclauncher.net.message.ISCMessage;
import com.spde.sclauncher.net.message.Type;

public class RemoteOperationRsp extends AbstractISCMessage<GZProtocolHeader> implements IResponse {
    public final static String NAME = RemoteOperation.NAME;
    public final static Type TYPE = Type.TYPE_DOWNSTREAM_RSP;

    //重启终端 @ 恢复除平台地址以外的出厂设置
    private boolean rebootDone; //bit1
    private boolean recoveryDone; //bit0

    public RemoteOperationRsp(ISCHeader header) {
        super(header);
    }

    public RemoteOperationRsp(IRequest request){
        super();
        initHeader();
        String seqno = ((ISCMessage)request).getHeader().get$sequence();
        this.header.set$sequence(seqno);
    }

    public void setRebootDone(boolean rebootDone) {
        this.rebootDone = rebootDone;
    }

    public void setRecoveryDone(boolean recoveryDone) {
        this.recoveryDone = recoveryDone;
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

        int ret = 0;
        if(!rebootDone){
            ret &= 2; //0000 0000 0000 0000 0000 0000 0000 0010
        }
        if(!recoveryDone){
            ret &= 1; //0000 0000 0000 0000 0000 0000 0000 0001
        }
        return String.valueOf(ret);
    }

    @Override
    public void setProtocolBody(String body, int length) throws BodyFormatException {
        //TYPE_DOWNSTREAM_RSP 回复给服务器的应答，不用实现
    }
}
