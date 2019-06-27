package com.spde.sclauncher.net.pojo;

import com.yynie.myutils.StringUtils;

import java.util.ArrayList;
import java.util.List;

public class RegionPolygon extends RegionShape {
    private List<LatLongPoint> pointList = new ArrayList<LatLongPoint>();

    public RegionPolygon() {
        super();
        setName("Polygon");
    }

    public List<LatLongPoint> getPointList() {
        return pointList;
    }

    @Override
    public boolean build(String[] field) {
        //Polygon（注：代表多边形,多边形最多8个点）
        //(24.513972491956064#117.72402112636269）*（24.513972491956064#117.724021126362696）*（24.513972491956064#117.72402112636269）*（24.513972491956064#117.72402112636269）
        if(field.length >= 3){
            for(String f:field){
                if(StringUtils.isBlank(f)) continue;
                String latLongString = cropBrackets(f.trim());
                if(StringUtils.isBlank(latLongString)) continue;

                try {
                    String[] latLong = latLongString.split("#");
                    double latitude = Double.parseDouble(latLong[0].trim());
                    double longitude = Double.parseDouble(latLong[1].trim());
                    LatLongPoint point = new LatLongPoint(latitude, longitude);
                    pointList.add(point);
                    if(pointList.size() >= 8){
                        break;
                    }
                }catch (Exception e){
                    e.printStackTrace();
                }
            }
            if(pointList.size() >= 3) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String getElementsInString() {
        StringBuilder sb = new StringBuilder();
        for(LatLongPoint p: pointList){
            sb.append("(")
                .append(p.getLatitude()).append("#").append(p.getLongitude())
                .append(")")
                .append("*");
        }
        return sb.toString();
    }
}
