package com.spde.sclauncher.net.pojo;

import com.yynie.myutils.StringUtils;

public class RegionRectangle extends RegionShape {
    private LatLongPoint leftTop;
    private LatLongPoint rightBottom;

    public RegionRectangle() {
        super();
        setName("Rectangle");
    }



    @Override
    public boolean build(String[] field) {
        //（39.123456，136.123456）(注：矩形左上角坐标)*（39.823456，136.823456）（注：矩形右下角坐标）
        if(field.length >= 2 && StringUtils.isNoneBlank(field[0], field[1])){
            String leftTopStr = cropBrackets(field[0].trim());
            String rightBottomStr = cropBrackets(field[1].trim());
            if(StringUtils.isNoneBlank(leftTopStr, rightBottomStr)) {
                try {
                    String[] leftTopLatLong = leftTopStr.split(",");
                    double leftTopLat = Double.parseDouble(leftTopLatLong[0].trim());
                    double leftTopLong = Double.parseDouble(leftTopLatLong[1].trim());
                    String[] rightBottomLatLong = rightBottomStr.split(",");
                    double rightBottomLat = Double.parseDouble(rightBottomLatLong[0].trim());
                    double rightBottomLong = Double.parseDouble(rightBottomLatLong[1].trim());
                    leftTop = new LatLongPoint(leftTopLat, leftTopLong);
                    rightBottom = new LatLongPoint(rightBottomLat, rightBottomLong);
                    return true;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        return false;
    }
}
