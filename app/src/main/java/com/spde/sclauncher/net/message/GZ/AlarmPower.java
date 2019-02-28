package com.spde.sclauncher.net.message.GZ;

import com.spde.sclauncher.net.message.AbstractISCMessage;
import com.spde.sclauncher.net.message.IRequest;
import com.spde.sclauncher.net.message.ISCHeader;
import com.spde.sclauncher.net.message.Type;

public class AlarmPower extends AbstractISCMessage<GZProtocolHeader> implements IRequest {
    public final static String NAME = "ALARM_POWER";
    public final static Type TYPE = Type.TYPE_UPSTREAM_REQ;
    private int event; //上报类型(1=缺电报警; 2=关机报警; 3=自动关机报警; 4=开机报警)
    private int batteryPercent;

    public AlarmPower(ISCHeader header) {
        super(header);
    }

    //上报类型(1=缺电报警; 2=关机报警; 3=自动关机报警; 4=开机报警)
    public AlarmPower(int event){
        super(null);
        this.event = event;
    }

    public void setEvent(int event) {
        this.event = event;
    }

    public boolean isLowBattery(){
        return (event == 1);
    }

    public void setBatteryPercent(int batteryPercent) {
        this.batteryPercent = batteryPercent;
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
        StringBuilder sb = new StringBuilder();
        sb.append(event).append(SPLIT_CH);
        if(event == 1 || event == 3) {
            sb.append(batteryPercent + "%");
        }
        return sb.toString();
    }

    @Override
    public void setProtocolBody(String body, int length) {
        //TYPE_UPSTREAM_REQ 的上报消息无需实现此方法
    }
}
