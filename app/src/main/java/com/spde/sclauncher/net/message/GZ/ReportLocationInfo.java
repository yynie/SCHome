package com.spde.sclauncher.net.message.GZ;

import com.spde.sclauncher.net.message.AbstractISCMessage;
import com.spde.sclauncher.net.message.IRequest;
import com.spde.sclauncher.net.message.ISCHeader;
import com.spde.sclauncher.net.message.Type;
import com.yynie.myutils.StringUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * 定时定位：
 * 定位优先级为GPS>WIFI>LBS，LBS每次必传，WiFi和GPS二者选其一。
 * 	WIFI信号至少需要传三组，最多五组，并包含信号强度，信号强度依据国标为负数。
 * 按10分钟一次的规律，提前启动GPS，进行信号的搜索，搜索到GPS上报GPS信号，如果无GPS信号上报WIFI信号。GPS和wifi信号都没有则上报LBS信息，上报至平台的定位数据时间相差为10分钟
 * */
public class ReportLocationInfo extends AbstractISCMessage<GZProtocolHeader> implements IRequest {
    public final static String NAME = "REPORT_LOCATION_INFO";
    public final static Type TYPE = Type.TYPE_UPSTREAM_REQ;

    /** gps位置信息@LBS数据@Wi-Fi数据 */
    private String nmea;
    private String lbs;

    private List<String> wifiList = new ArrayList<String>();

    public ReportLocationInfo(ISCHeader header) {
        super(header);
    }

    public void setNmea(String nmea) {
        this.nmea = nmea;
    }

    public void setLbs(String lbs) {
        this.lbs = lbs;
    }

    public void setWifiList(List<String> wifiList) {
        this.wifiList = wifiList;
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
        String wifiString = "";
        if(!wifiList.isEmpty()){
            for(String one:wifiList){
                if(StringUtils.isNotBlank(one)) {
                    wifiString += one + "#";
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        String timeStr = "T" + genTimsProtocolString();
        String defaultNmea = "1E0.000000N0.000000";
        sb.append( StringUtils.isBlank(nmea) ? defaultNmea : nmea ).append(timeStr).append(SPLIT_CH)
                .append(StringUtils.isBlank(lbs) ? "0" :lbs).append(SPLIT_CH).
                append(StringUtils.isBlank(wifiString) ? "0" : wifiString);
        return sb.toString();
    }

    @Override
    public void setProtocolBody(String body, int length) {
        //TYPE_UPSTREAM_REQ 的上报消息无需实现此方法
    }

}
