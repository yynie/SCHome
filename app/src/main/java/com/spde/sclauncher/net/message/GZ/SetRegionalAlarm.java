package com.spde.sclauncher.net.message.GZ;


import com.spde.sclauncher.net.message.AbstractISCMessage;
import com.spde.sclauncher.net.message.BodyFormatException;
import com.spde.sclauncher.net.message.IRequest;
import com.spde.sclauncher.net.message.ISCHeader;
import com.spde.sclauncher.net.message.Type;
import com.spde.sclauncher.net.pojo.PeriodWeekly;
import com.spde.sclauncher.net.pojo.RegionLimit;
import com.spde.sclauncher.net.pojo.RegionPolygon;
import com.spde.sclauncher.net.pojo.RegionRectangle;
import com.spde.sclauncher.net.pojo.RegionRound;
import com.spde.sclauncher.net.pojo.RegionShape;
import com.yynie.myutils.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SetRegionalAlarm extends AbstractISCMessage<GZProtocolHeader> implements IRequest {
    public final static String NAME = "SET_REGIONAL_ALARM";
    public final static Type TYPE = Type.TYPE_DOWNSTREAM_REQ;
    private int operate;  //操作代码：1表示新增区域 2表示修改区域 3表示删除区域
    private int opType; //请求状态：1表示父亲卡 2表示母亲卡
    private RegionLimit regionLimit;

    public SetRegionalAlarm(ISCHeader header) {
        super(header);
    }

    public int getOperate() {
        return operate;
    }

    public int getOpType() {
        return opType;
    }

    public RegionLimit getRegionLimit() {
        return regionLimit;
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
        //操作代码 @ 请求状态 @ 形状*元素个数*元素值1*元素值2*...*元素值n @ 区域编号 @ 时间段 @ 周期
        String[] fields = body.split(SPLIT_CH);
        if(fields.length < 6) throw new BodyFormatException("SetRegionalAlarm", "body not completed:" + body);

        try {
            operate = Integer.parseInt(fields[0].trim());
            opType = Integer.parseInt(fields[1].trim());
            String shapestr = fields[2].trim();
            int regNo = Integer.parseInt(fields[3].trim());
            String timestr = fields[4].trim(); //时间段  1130-1230+1330-1430+
            String weeks = fields[5].trim();
            if(operate == 3){ //3表示删除区域
                regionLimit = new RegionLimit(regNo, null, null);
            }else{
                //时间段  1130-1230+1330-1430+
                String[] timeArr = timestr.split("\\+");
                List<PeriodWeekly> periodList = new ArrayList<PeriodWeekly>();
                for(String time:timeArr){
                    if(StringUtils.isNotBlank(time)){
                        String[] timesets = time.split("-");
                        if(timesets.length >= 2){
                            try {
                                int shour = Integer.parseInt(timesets[0].trim().substring(0, 2));
                                int smin = Integer.parseInt(timesets[0].trim().substring(2));
                                int ehour = Integer.parseInt(timesets[1].trim().substring(0, 2));
                                int emin = Integer.parseInt(timesets[1].trim().substring(2));
                                PeriodWeekly p = new PeriodWeekly(shour * 60 + smin, ehour * 60 + emin, weeks);
                                periodList.add(p);
                            }catch (NumberFormatException e){
                                throw new BodyFormatException("SetRegionalAlarm", "Can't parse time: " + e.getMessage());
                            }
                        }
                    }
                }
                //形状*元素个数*元素值1*元素值2*...*元素值n
                String[] shapeFields = shapestr.split("\\*");
                if(shapeFields.length > 2) {
                    String sp = shapeFields[0].trim();
                    int eleLength = Integer.parseInt(shapeFields[1].trim());
                    String[] eles = Arrays.copyOfRange(shapeFields, 2, shapeFields.length);
                    if (eles.length >= eleLength) {
                        RegionShape shape = createShape(sp);
                        if(shape != null && shape.build(eles)){
                            regionLimit = new RegionLimit(regNo, shape, periodList);
                        }
                    }
                }
            }
        }catch (NumberFormatException e){
            throw new BodyFormatException("SetRegionalAlarm", e.getMessage());
        }
    }

    private RegionShape createShape(String shape){
        if(StringUtils.equalsIgnoreCase(shape, "Round")){
            return new RegionRound();
        }else if(StringUtils.equalsIgnoreCase(shape, "Rectangle")){
            return new RegionRectangle();
        }else if(StringUtils.equalsIgnoreCase(shape, "Polygon")){
            return new RegionPolygon();
        }
        return null;
    }
}
