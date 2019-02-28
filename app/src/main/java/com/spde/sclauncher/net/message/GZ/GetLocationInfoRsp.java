package com.spde.sclauncher.net.message.GZ;

import com.spde.sclauncher.net.message.AbstractISCMessage;
import com.spde.sclauncher.net.message.BodyFormatException;
import com.spde.sclauncher.net.message.IRequest;
import com.spde.sclauncher.net.message.IResponse;
import com.spde.sclauncher.net.message.ISCHeader;
import com.spde.sclauncher.net.message.ISCMessage;
import com.spde.sclauncher.net.message.Type;
import com.yynie.myutils.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 立即定位（平台等待定位时长1分钟）：
 * 定位优先级为WIFI>GPS>LBS，LBS每次必传，WiFi和GPS二者选其一。
 * 	WIFI信号至少需要传三组，最多五组，并包含信号强度，信号强度依据国标为负数。
 * 1.	有wifi信号，立即上报wifi数据，则不用再上报GPS信号。
 * 2.	没有wifi信号，需要立即打开GPS开关进行搜索，并将GPS信号在50秒之内上报。
 * 3.	如果50秒之内无wifi且无GPS信号，则只需要按照协议上报LBS数据。
 * */
public class GetLocationInfoRsp extends AbstractISCMessage<GZProtocolHeader> implements IResponse {
    public final static String NAME = GetLocationInfo.NAME;
    public final static Type TYPE = Type.TYPE_DOWNSTREAM_RSP;
    /** gps位置信息@LBS数据@Wi-Fi数据 */
    private String nmea;
    private String lbs;

    private List<String> wifiList = new ArrayList<String>();

    public GetLocationInfoRsp(ISCHeader header) {
        super(header);
    }

    public GetLocationInfoRsp(IRequest request){
        super();
        initHeader();
        String seqno = ((ISCMessage)request).getHeader().get$sequence();
        this.header.set$sequence(seqno);
    }

    public void setNmea(String nmea) {
        this.nmea = nmea;
    }

    public void setLbs(String lbs){
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
        String defaultNmea = "1E0.000000N0.000000" + "T" + genTimsProtocolString();
        sb.append( StringUtils.isBlank(nmea) ? defaultNmea : nmea ).append(SPLIT_CH)
                .append(StringUtils.isBlank(lbs) ? "0" :lbs).append(SPLIT_CH).
                append(StringUtils.isBlank(wifiString) ? "0" : wifiString);
        return sb.toString();
    }

    @Override
    public void setProtocolBody(String body, int length) throws BodyFormatException {
        //TYPE_DOWNSTREAM_RSP 回复给服务器的应答，不用实现
    }
}
