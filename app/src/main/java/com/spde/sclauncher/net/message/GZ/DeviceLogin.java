package com.spde.sclauncher.net.message.GZ;

import com.spde.sclauncher.net.LocalDevice;
import com.spde.sclauncher.net.NetCommClient;
import com.spde.sclauncher.net.message.AbstractISCMessage;
import com.spde.sclauncher.net.message.IRequest;
import com.spde.sclauncher.net.message.ISCHeader;
import com.spde.sclauncher.net.message.Type;
import com.yynie.myutils.StringUtils;

public class DeviceLogin extends AbstractISCMessage<GZProtocolHeader> implements IRequest {
    public final static String NAME = "DEVICE_LOGIN";
    public final static Type TYPE = Type.TYPE_UPSTREAM_REQ;

    private int keyNumber; //终端普通键数量
    private boolean sosKey; //终端有没有SOS键
    private int devType = 1; //终端类型   1=GPS, 2=CellID, 3=AGPS
    private boolean zoneAlarm; //终端是否具备区域报警功能
    private boolean setIncommingPhone;   //终端是否具备设置呼入号码功能
    private String protocolVer = "21"; //终端软件协议版本

    public DeviceLogin(){
        super(null);
        keyNumber = LocalDevice.getInstance().getKeyNumber();
        sosKey = LocalDevice.getInstance().isSosKey();
        devType = LocalDevice.getInstance().getDevType();
        zoneAlarm = LocalDevice.getInstance().isZoneAlarm();
        setIncommingPhone = LocalDevice.getInstance().isSetIncommingPhone();
        protocolVer = NetCommClient.PROTOCOL_VERSION;
    }

    public DeviceLogin(ISCHeader header){
        super(header);
    }

    @Override
    public String toProtocolBody() {
        StringBuilder sb = new StringBuilder();
        sb.append(keyNumber).append(SPLIT_CH).append(sosKey ? 1 : 0).append(SPLIT_CH)
                .append(devType).append(SPLIT_CH).append(zoneAlarm ? 1 : 0).append(SPLIT_CH)
                .append(setIncommingPhone ? 1 : 0).append(SPLIT_CH).append(protocolVer);
        return sb.toString();
    }

    @Override
    public void setProtocolBody(String body, int length) {
        //测试用  TYPE_UPSTREAM_REQ 的消息 不需要实现这个方法
        String[] fields = body.split(SPLIT_CH);
        keyNumber = Integer.parseInt(fields[0].trim());
        sosKey = !StringUtils.equals(fields[1].trim(), "0");
        devType = Integer.parseInt(fields[2].trim());
        zoneAlarm = !StringUtils.equals(fields[3].trim(), "0");
        setIncommingPhone = !StringUtils.equals(fields[4].trim(), "0");
        protocolVer = fields[5].trim();
    }

    @Override
    protected GZProtocolHeader generateHeader(){
        GZProtocolHeader header = new GZProtocolHeader();
        return header;
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
    public String toString(){
        return toProtocolBody();
    }
}
