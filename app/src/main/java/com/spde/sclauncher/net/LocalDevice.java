package com.spde.sclauncher.net;

/**
 *  这里的数据有可能跨线程访问 全部接口都要加同步锁，除非你确定某项数据不会被跨线程使用
 * */
public class LocalDevice {
    private static LocalDevice sInstance;
    private String imei;
    private String iccid; //25位？
    private int keyNumber; //终端普通键数量
    private boolean sosKey; //终端有没有SOS键
    private int devType = 2; //终端类型   1=GPS, 2=CellID, 3=AGPS
    private boolean zoneAlarm; //终端是否具备区域报警功能
    private boolean setIncommingPhone;   //终端是否具备设置呼入号码功能

    public static LocalDevice getInstance(){
        synchronized (LocalDevice.class){
            if(sInstance == null) {
                sInstance = new LocalDevice();
            }
            return sInstance;
        }
    }

    public String getImei() {
        synchronized (LocalDevice.class) {
            return imei;
        }
    }

    public void setImei(String imei) {
        synchronized (LocalDevice.class) {
            this.imei = imei;
        }
    }

    public String getIccid() {
        synchronized (LocalDevice.class) {
            return iccid;
        }
    }

    public void setIccid(String iccid) {
        synchronized (LocalDevice.class) {
            this.iccid = iccid;
        }
    }

    public int getKeyNumber() {
        synchronized (LocalDevice.class) {
            return keyNumber;
        }
    }

    public void setKeyNumber(int keyNumber) {
        synchronized (LocalDevice.class) {
            this.keyNumber = keyNumber;
        }
    }

    public boolean isSosKey() {
        synchronized (LocalDevice.class) {
            return sosKey;
        }
    }

    public void setSosKey(boolean sosKey) {
        synchronized (LocalDevice.class) {
            this.sosKey = sosKey;
        }
    }

    public int getDevType() {
        synchronized (LocalDevice.class) {
            return devType;
        }
    }

    public void setDevType(int devType) {
        synchronized (LocalDevice.class) {
            this.devType = devType;
        }
    }

    public boolean isZoneAlarm() {
        synchronized (LocalDevice.class) {
            return zoneAlarm;
        }
    }

    public void setZoneAlarm(boolean zoneAlarm) {
        synchronized (LocalDevice.class) {
            this.zoneAlarm = zoneAlarm;
        }
    }

    public boolean isSetIncommingPhone() {
        synchronized (LocalDevice.class) {
            return setIncommingPhone;
        }
    }

    public void setSetIncommingPhone(boolean setIncommingPhone) {
        synchronized (LocalDevice.class) {
            this.setIncommingPhone = setIncommingPhone;
        }
    }
}
