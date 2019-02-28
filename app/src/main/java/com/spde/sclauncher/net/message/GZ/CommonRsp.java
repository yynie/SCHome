package com.spde.sclauncher.net.message.GZ;

import com.spde.sclauncher.net.message.AbstractISCMessage;
import com.spde.sclauncher.net.message.BodyFormatException;
import com.spde.sclauncher.net.message.IRequest;
import com.spde.sclauncher.net.message.IResponse;
import com.spde.sclauncher.net.message.ISCHeader;
import com.spde.sclauncher.net.message.ISCMessage;
import com.spde.sclauncher.net.message.Type;

public class CommonRsp extends AbstractISCMessage<GZProtocolHeader> implements IResponse {
    private String name;
    private Type type;
    private int status; //应答状态(0=设置成功;1=非和校园用户;2=设置异常;)

    public CommonRsp(ISCHeader header) {
        super(header);
    }

    //为入参的请求构造一个通用应答
    public CommonRsp(IRequest request){
        super();
        this.name = ((ISCMessage)request).getHeader().get$apiName();
        this.type = Type.TYPE_DOWNSTREAM_RSP;
        initHeader();
        String seqno = ((ISCMessage)request).getHeader().get$sequence();
        this.header.set$sequence(seqno);
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public int getStatus() {
        return status;
    }

    @Override
    protected GZProtocolHeader generateHeader() {
        return new GZProtocolHeader();
    }

    @Override
    protected String getName() {
        return name;
    }

    public void setName(String name){
        this.name = name;
    }

    @Override
    protected Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    @Override
    public String toProtocolBody() {
        return String.valueOf(status);
    }

    @Override
    public void setProtocolBody(String body, int length) throws BodyFormatException {
        try {
            status = Integer.parseInt(body);
            if(status < 0 || status > 2){
                throw new BodyFormatException("CommonRsp","Unknown Response status");
            }
        }catch (NumberFormatException e){
            throw new BodyFormatException("CommonRsp", e.getMessage());
        }
    }
}
