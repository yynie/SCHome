package com.spde.sclauncher.net.pojo;

import java.io.Serializable;
import java.math.BigDecimal;

public class LatLongPoint implements Serializable {
    private double Latitude;
    private double Longitude;

    public LatLongPoint() {
    }

    public LatLongPoint(double latitude, double longitude) {
        BigDecimal bigLat = new BigDecimal(latitude);
        Latitude = bigLat.setScale(6, BigDecimal.ROUND_HALF_UP).doubleValue();
        BigDecimal bigLong = new BigDecimal(longitude);
        Longitude = bigLong.setScale(6, BigDecimal.ROUND_HALF_UP).doubleValue();
    }

    public double getLatitude() {
        return Latitude;
    }

    public void setLatitude(double latitude) {
        Latitude = latitude;
    }

    public double getLongitude() {
        return Longitude;
    }

    public void setLongitude(double longitude) {
        Longitude = longitude;
    }
}
