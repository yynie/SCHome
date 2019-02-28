package com.spde.sclauncher.net.pojo;

import java.io.Serializable;

public class LatLongPoint implements Serializable {
    private double Latitude;
    private double Longitude;

    public LatLongPoint() {
    }

    public LatLongPoint(double latitude, double longitude) {
        Latitude = latitude;
        Longitude = longitude;
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
