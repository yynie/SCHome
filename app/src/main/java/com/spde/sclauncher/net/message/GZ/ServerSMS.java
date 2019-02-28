package com.spde.sclauncher.net.message.GZ;

import com.spde.sclauncher.net.message.AbstractISCMessage;
import com.spde.sclauncher.net.message.BodyFormatException;
import com.spde.sclauncher.net.message.IRequest;
import com.spde.sclauncher.net.message.ISCHeader;
import com.spde.sclauncher.net.message.Type;
import com.yynie.myutils.StringUtils;

public class ServerSMS extends AbstractISCMessage<GZProtocolHeader> implements IRequest {
    public final static String NAME = "SEND_SMS";
    public final static Type TYPE = Type.TYPE_DOWNSTREAM_REQ;

    private boolean emergent;// 短信类型  false 普通短信  true 紧急消息
    private int showType; //播报还是屏显：0语音播报1屏幕显示2语音播报并屏幕显示
    private int showTimes;  //播报报次数 播报时默认1次，不播报则0
    private boolean flash; //是否闪烁灯光
    private boolean vibrate; //是否震动
    private boolean ring; //是否响铃
    private String sms;

    public ServerSMS(ISCHeader header) {
        super(header);
    }

    public boolean isEmergent() {
        return emergent;
    }

    public int getShowType() {
        return showType;
    }

    public int getShowTimes() {
        return showTimes;
    }

    public boolean isFlash() {
        return flash;
    }

    public boolean isVibrate() {
        return vibrate;
    }

    public boolean isRing() {
        return ring;
    }

    public String getSms() {
        return sms;
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
        //短信类型@播报还是屏显@播报报次数@是否闪烁灯光@是否震动@是否响铃@短信内容编码UTF-8,70个字
        //1      @   2       @   1      @   0       @    0   @   1   @今天下午会下大雨，请记得带伞！
        String[] fields = body.split(SPLIT_CH);
        if(fields.length < 7){
            throw new BodyFormatException("ServerSMS", "Message body can not be parsed as ServerSMS");
        }
        emergent = !StringUtils.equals(fields[0].trim(), "0"); // 短信类型 0普通短信1紧急消息
        showType = Integer.parseInt(fields[1].trim());  //播报还是屏显：0语音播报1屏幕显示2语音播报并屏幕显示
        showTimes = Integer.parseInt(fields[2].trim());  //播报报次数 播报时默认1次，不播报则0
        flash = !StringUtils.equals(fields[3].trim(), "0"); //是否闪烁灯光：0否 1是
        vibrate = !StringUtils.equals(fields[4].trim(), "0");  //是否震动：0否 1是
        ring = !StringUtils.equals(fields[5].trim(), "0");   //是否响铃：0否 1是
        sms = fields[6].trim();
    }
}
