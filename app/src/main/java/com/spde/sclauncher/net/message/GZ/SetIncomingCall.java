package com.spde.sclauncher.net.message.GZ;

import com.spde.sclauncher.net.message.AbstractISCMessage;
import com.spde.sclauncher.net.message.BodyFormatException;
import com.spde.sclauncher.net.message.IRequest;
import com.spde.sclauncher.net.message.ISCHeader;
import com.spde.sclauncher.net.message.Type;
import com.spde.sclauncher.net.pojo.IncomingCallSet;
import com.spde.sclauncher.net.pojo.Period;
import com.yynie.myutils.StringUtils;

import java.util.ArrayList;
import java.util.List;

public class SetIncomingCall extends AbstractISCMessage<GZProtocolHeader> implements IRequest {
    public final static String NAME = "SET_INCOMING_CALL";
    public final static Type TYPE = Type.TYPE_DOWNSTREAM_REQ;
    private List<IncomingCallSet> deletePhones = new ArrayList<IncomingCallSet>();  //删除号码
    private List<IncomingCallSet> addPhones = new ArrayList<IncomingCallSet>();  //添加号码和时段
    private int limitFlag;  //呼入限制  1、无限制 2、限制白名单以外的号码呼入 3、限制所有号码呼入
    private String weeks; //周期 周一至周六用1-6表示，周日用0表示，如1+2+3+4+表示周一至周四

    public SetIncomingCall(ISCHeader header) {
        super(header);
    }

    @Override
    protected GZProtocolHeader generateHeader() {
        //TYPE_DOWNSTREAM_REQ 的消息无需实现此方法
        return null;
    }

    public final List<IncomingCallSet> getDeletePhones() {
        return deletePhones;
    }

    public final List<IncomingCallSet> getAddPhones() {
        return addPhones;
    }

    public int getLimitFlag() {
        return limitFlag;
    }

    public String getWeeks() {
        return weeks;
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
        String[] fields = body.split(SPLIT_CH);
        if(fields.length < 4){
            throw new BodyFormatException("SetIncomingCall", "body not completed:" + body);
        }
        //delete
        String delstr = fields[0].trim();
        if(!StringUtils.equals(delstr, "0")){
            String[] dels = delstr.split("!");
            for(String del:dels){
                if(StringUtils.isNotBlank(del)){
                    int pos = del.indexOf("=");
                    if(pos >= 0){
                        del = del.substring(0, pos);
                    }
                    deletePhones.add(new IncomingCallSet(del, null));
                }
            }
        }
        //add   13900000004=0600-2230+  !  13900000005=0300-0600+0900-1130+!     @2@0+1+2+3+4+5+6
        String addstr = fields[1].trim();
        if(!StringUtils.equals(addstr, "0")){
            String[] adds = addstr.split("!");
            for(String add:adds){
                if(StringUtils.isNotBlank(add)){
                    int pos = add.indexOf("=");
                    if(pos <= 0) continue;
                    String phone = add.substring(0, pos);
                    add = add.substring(pos + 1);
                    String[] periods = add.split("\\+");
                    List<Period> periodlist = new ArrayList<Period>();
                    for(int i = 0; i < periods.length; i++){
                        String time = periods[i].trim();
                        String[] timesets = time.split("-");
                        if(timesets.length != 2) continue;
                        try {
                            int shour = Integer.parseInt(timesets[0].trim().substring(0, 2));
                            int smin = Integer.parseInt(timesets[0].trim().substring(2));
                            int ehour = Integer.parseInt(timesets[1].trim().substring(0, 2));
                            int emin = Integer.parseInt(timesets[1].trim().substring(2));
                            Period period = new Period(shour * 60 + smin, ehour * 60 + emin);
                            periodlist.add(period);
                        }catch (NumberFormatException e){
                            e.printStackTrace();
                        }
                    }
                    addPhones.add(new IncomingCallSet(phone, periodlist));
                }
            }
        }

        //limit
        limitFlag = Integer.parseInt(fields[2].trim());
        //weeks
        weeks = fields[3].trim();
    }
}
