package com.spde.sclauncher.net.message.GZ;

import com.spde.sclauncher.net.message.AbstractISCMessage;
import com.spde.sclauncher.net.message.BodyFormatException;
import com.spde.sclauncher.net.message.IRequest;
import com.spde.sclauncher.net.message.ISCHeader;
import com.spde.sclauncher.net.message.Type;
import com.spde.sclauncher.net.pojo.PeriodWeeklyOnOff;
import com.yynie.myutils.StringUtils;

import java.util.ArrayList;
import java.util.List;

public class SetClassMode extends AbstractISCMessage<GZProtocolHeader> implements IRequest {
    public final static String NAME = "SET_CLASS_MODEL";
    public final static Type TYPE = Type.TYPE_DOWNSTREAM_REQ;
    private boolean clearAll;
    private boolean sosIncoming; //SOS呼入标识
    private boolean sosOutgoing; //SOS呼出标识
    private List<PeriodWeeklyOnOff> periodList = new ArrayList<PeriodWeeklyOnOff>();

    public SetClassMode(ISCHeader header) {
        super(header);
    }

    public boolean isSosIncoming() {
        return sosIncoming;
    }

    public boolean isSosOutgoing() {
        return sosOutgoing;
    }

    public List<PeriodWeeklyOnOff> getPeriodList() {
        return periodList;
    }

    public boolean isClearAll() {
        return clearAll;
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
        String[] fields = body.split(SPLIT_CH);
        if(fields.length < 3){
            throw new BodyFormatException("SetClassMode", "body not completed:" + body);
        }
        sosIncoming = StringUtils.equals(fields[0].trim(), "1");
        sosOutgoing = StringUtils.equals(fields[1].trim(), "1");

        if(fields.length == 3 && StringUtils.equals(fields[2].trim(), "0")){
            //全部清除
            clearAll = true;
            return;
        }
        clearAll = false;
        for(int i = 2; i< fields.length; i++){
            String f = fields[i].trim();
            if(StringUtils.isNotBlank(f)) {
                PeriodWeeklyOnOff period = new PeriodWeeklyOnOff();
                //1=0900-1130!1+2+3!1
                int pos = f.indexOf("=");
                if(pos <= 0){  //无效的 忽略
                    continue;
                }
                String sno = f.substring(0, pos);
                try {
                    period.setNo(Integer.parseInt(sno));
                }catch (NumberFormatException e){
                    throw new BodyFormatException("SetClassMode", "Can't parse No. : " + e.getMessage());
                }
                if(pos == f.length() - 1){
                    //等号后是空的
                    continue;
                }
                f = f.substring(pos + 1);
                String[] sets = f.split("!");
                if(sets.length < 3) throw new BodyFormatException("SetClassMode", "Message body can not be parsed as SetClassMode");
                //时间段
                String time = sets[0].trim();
                String[] timesets = time.split("-");
                if(timesets.length < 2) throw new BodyFormatException("SetClassMode", "Message body can not be parsed as SetClassMode");
                try {
                    int shour = Integer.parseInt(timesets[0].trim().substring(0, 2));
                    int smin = Integer.parseInt(timesets[0].trim().substring(2));
                    int ehour = Integer.parseInt(timesets[1].trim().substring(0, 2));
                    int emin = Integer.parseInt(timesets[1].trim().substring(2));
                    period.setStartMinute(shour * 60 + smin);
                    period.setEndMinute(ehour * 60 + emin);
                }catch (NumberFormatException e){
                    throw new BodyFormatException("SetClassMode", "Can't parse time: " + e.getMessage());
                }

                //周
                String weeks = sets[1].trim();
                period.setWeeks(weeks);

                //生效
                period.setOnOrOff(StringUtils.equals(sets[2].trim(), "1"));

                periodList.add(period);
            }
        }
    }

}
