package com.spde.sclauncher.net.pojo;

import com.yynie.myutils.StringUtils;

public class RegionRound extends RegionShape {
    private LatLongPoint center;
    private int radius;  //半径，单位：米

    public RegionRound() {
        super();
        setName("Round");
    }

    public LatLongPoint getCenter() {
        return center;
    }

    public void setCenter(LatLongPoint center) {
        this.center = center;
    }

    public int getRadius() {
        return radius;
    }

    public void setRadius(int radius) {
        this.radius = radius;
    }

    @Override
    public boolean build(String[] field) {
        //元素值1*元素值2*...*元素值n
        //（24.513972491956064#117.72402112636269）（注：圆心经纬度）*（1000）（注：半径，单位：米）
        if(field.length >= 2 && StringUtils.isNoneBlank(field[0], field[1])){
            String centStr = cropBrackets(field[0].trim());
            String radiusStr = cropBrackets(field[1].trim());
            if(StringUtils.isNoneBlank(centStr, radiusStr)) {
                try {
                    radius = Integer.parseInt(radiusStr);
                    String[] latLong = centStr.split("#");
                    double latitude = Double.parseDouble(latLong[0].trim());
                    double longitude = Double.parseDouble(latLong[1].trim());
                    center = new LatLongPoint(latitude, longitude);
                    return true;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        return false;
    }
}
