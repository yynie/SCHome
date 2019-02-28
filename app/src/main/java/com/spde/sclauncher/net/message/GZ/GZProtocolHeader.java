package com.spde.sclauncher.net.message.GZ;

import com.spde.sclauncher.net.LocalDevice;
import com.spde.sclauncher.net.message.ISCHeader;
import com.spde.sclauncher.net.message.Type;

public class GZProtocolHeader implements ISCHeader {
    //起始标识符 [
    //协议字段参数以$开头
    String $devId; //设备号
    String $iccid; //ICCID
    String $sequence; //交易流水号  yyyyMMddHHmmss0000
    String $apiName; //接口唯一标识
    Type $type; //报文类型
    String $time; //本地时间(current time zone) 格式  yyyyMMddHHmmss
    int $contentLength; //报文长度
    //分隔符 ,

    public GZProtocolHeader() {
        $devId = LocalDevice.getInstance().getImei();
        $iccid = LocalDevice.getInstance().getIccid();
    }

    @Override
    public void fillFields(String[] fields) throws Exception {
        if(fields.length > 0) set$devId(fields[0]);
        if(fields.length > 1) set$iccid(fields[1]);
        if(fields.length > 2) set$sequence(fields[2]);
        if(fields.length > 3) set$apiName(fields[3]);
        if(fields.length > 4){
            try {
                int value = Integer.parseInt(fields[4]);
                set$type(Type.fromIntValue(value));
            }catch (NumberFormatException e){
                throw new Exception(e);
            }
        }
        if(fields.length > 5) set$time(fields[5]);
        if(fields.length > 6){
            try {
                set$contentLength(Integer.parseInt(fields[6]));
            }catch (NumberFormatException e){
                throw new Exception(e);
            }
        }
    }

    public String get$devId() {
        return $devId;
    }

    public void set$devId(String $devId) {
        this.$devId = $devId;
    }

    public String get$iccid() {
        return $iccid;
    }

    public void set$iccid(String $iccid) {
        this.$iccid = $iccid;
    }

    @Override
    public String get$sequence() {
        return $sequence;
    }

    @Override
    public void set$sequence(String $sequence) {
        this.$sequence = $sequence;
    }

    @Override
    public String get$apiName() {
        return $apiName;
    }

    @Override
    public void set$apiName(String $apiName) {
        this.$apiName = $apiName;
    }

    @Override
    public Type get$type() {
        return $type;
    }

    @Override
    public void set$type(Type $type) {
        this.$type = $type;
    }

    @Override
    public String get$time() {
        return $time;
    }

    @Override
    public void set$time(String $time) {
        this.$time = $time;
    }

    @Override
    public int get$contentLength() {
        return $contentLength;
    }

    @Override
    public String toProtocolHeader() {
        String SPLIT_CH = ",";
        StringBuilder sb = new StringBuilder();
        sb.append($devId).append(SPLIT_CH).append($iccid).append(SPLIT_CH)
                .append($sequence).append(SPLIT_CH).append($apiName).append(SPLIT_CH)
                .append($type.value()).append(SPLIT_CH).append($time).append(SPLIT_CH)
                .append($contentLength);
        return sb.toString();
    }

    @Override
    public void set$contentLength(int $contentLength) {
        this.$contentLength = $contentLength;
    }
}
