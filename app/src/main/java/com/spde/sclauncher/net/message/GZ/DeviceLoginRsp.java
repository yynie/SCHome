package com.spde.sclauncher.net.message.GZ;

import com.spde.sclauncher.net.message.AbstractISCMessage;
import com.spde.sclauncher.net.message.IResponse;
import com.spde.sclauncher.net.message.ISCHeader;
import com.spde.sclauncher.net.message.Type;
import com.yynie.myutils.StringUtils;

public class DeviceLoginRsp extends AbstractISCMessage<GZProtocolHeader> implements IResponse {
    public final static String NAME = DeviceLogin.NAME;
    public final static Type TYPE = Type.TYPE_UPSTREAM_RSP;

    private int status; //应答状态(0=设置成功;1=非和校园用户;2=设置异常;)
    private String smsPort; //发送短信端口
    private boolean needSms;  //发送短信1发送0不发送

    public DeviceLoginRsp(ISCHeader header) {
        super(header);
    }

    @Override
    protected GZProtocolHeader generateHeader() {
        //TYPE_UPSTREAM_RSP 的消息无需实现此方法
        return null;
    }

    public int getStatus() {
        return status;
    }

    public String getSmsPort() {
        return smsPort;
    }

    public boolean isNeedSms() {
        return needSms;
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
        //TYPE_UPSTREAM_RSP 的消息无需实现此方法
//        return null;
        return "" + status + "@" + smsPort + "@" + (needSms?1:0);
    }

    @Override
    public void setProtocolBody(String body, int length) {
        String[] fields = body.split(SPLIT_CH);
        status = Integer.parseInt(fields[0].trim());
        smsPort = fields[1].trim();
        needSms = !StringUtils.equals(fields[2].trim(), "0");
    }

    @Override
    public String toString(){
        return "[status=" + status + ",smsPort=" + smsPort + ",needSms=" + needSms + "]";
    }
}
