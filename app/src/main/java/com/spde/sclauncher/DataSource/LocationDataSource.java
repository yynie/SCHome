package com.spde.sclauncher.DataSource;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
//import android.os.SystemProperties;
import android.os.SystemProperties;
import android.provider.Settings;
import android.telephony.CellIdentityGsm;
import android.telephony.CellIdentityLte;
import android.telephony.CellInfo;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellSignalStrengthGsm;
import android.telephony.CellSignalStrengthLte;

import android.telephony.TelephonyManager;
import android.telephony.gsm.GsmCellLocation;

import com.android.featureoption.FeatureOption;
import com.yynie.myutils.Logger;
import com.yynie.myutils.StringUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import static com.spde.sclauncher.SCConfig.USE_WIFI_NETWORK;

public class LocationDataSource extends AbstractDataSource {
    private static Logger log = Logger.get(LocationDataSource.class, Logger.Level.DEBUG);
    private static LocationDataSource sInstance;
    private WifiManager wifiManager;
    private WifiScanner wifiScanner;
    private LocationManager locManager;
    private MyLocationListener gpsListener;
    private MyNmeaListener nmeaListener;
    private NMEAHandler nmeaHandler;
    private SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");

    public static LocationDataSource getInstance(){
        synchronized (LocationDataSource.class){
            if(sInstance == null){
                sInstance = new LocationDataSource();
            }
            return sInstance;
        }
    }

    @Override
    protected void prepareOnInit() {
        wifiManager = (WifiManager) getContext().getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if(wifiScanner != null){
            log.w("prepareOnInit wifiScanner not cleared");
            wifiScanner.stop();
            wifiScanner = null;
        }
        wifiScanner = new WifiScanner();
        if(locManager != null){
            log.w("prepareOnInit locManager not cleared");
            if(nmeaListener != null) {
                log.w("prepareOnInit nmeaListener not cleared");
                locManager.removeNmeaListener(nmeaListener);
                nmeaListener = null;
            }
            if(gpsListener != null) {
                log.w("prepareOnInit gpsListener not cleared");
                locManager.removeUpdates(gpsListener);
                gpsListener = null;
            }
        }
        locManager = (LocationManager) getContext().getSystemService(Context.LOCATION_SERVICE);

        if(nmeaHandler != null){
            log.w("prepareOnInit nmeaHandler not cleared");
            nmeaHandler.stop();
            nmeaHandler = null;
        }
        nmeaHandler = new NMEAHandler();
    }

    @Override
    public void release() {

    }

    @Override
    public void restore() {

    }

    public void openGps(){
        log.i("openGps");
        if(!locManager.isProviderEnabled(LocationManager.GPS_PROVIDER)){
            log.d("openGps: setLocationProviderEnabled true");
            //Settings.Secure.setLocationProviderEnabled(getContext().getContentResolver(), LocationManager.GPS_PROVIDER, true);
        }
        
        if(nmeaListener == null) {
            log.i("openGps: add Nmea Listener");
            nmeaListener = new MyNmeaListener();
            locManager.addNmeaListener(nmeaListener);
        }
        if(gpsListener == null) {
            log.i("openGps: locaction update");
            gpsListener = new MyLocationListener();
            locManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, gpsListener);
        }
    }

    public void closeGps(){
        if(nmeaHandler.isStoped()) {
            log.i("closeGps: do close");
            if(nmeaListener != null) {
                log.i("closeGps remove nmeaListener");
                locManager.removeNmeaListener(nmeaListener);
                nmeaListener = null;
            }
            if(gpsListener != null) {
                log.i("closeGps remove gpsListener");
                locManager.removeUpdates(gpsListener);
                gpsListener = null;
            }
            log.d("closeGps: setLocationProviderEnabled false");
            //Settings.Secure.setLocationProviderEnabled(getContext().getContentResolver(), LocationManager.GPS_PROVIDER, false);
        }else{
            log.i("closeGps: nmeaHandler is still working, do Not close ");
        }
    }

    private class MyNmeaListener implements GpsStatus.NmeaListener {
        @Override
        public void onNmeaReceived(long timestamp, String nmea) {
            if(nmea.trim().startsWith("$GPGGA,") || nmea.trim().startsWith("$GNGGA,") ||
                    nmea.trim().startsWith("$BDGGA,")){
                log.d("NMEA::" + nmea.trim() + ",timestamp=" + timestamp);
                long sys = System.currentTimeMillis();
                if(Math.abs(sys - timestamp) >= (1 * 60 * 60 * 1000)){
                    log.e("NMEA time error systime=" + sys + ",timestamp=" + timestamp);
                }
               
                String[] ndatas =  nmea.split(",");
                if(ndatas.length > 8){
                    String latstring = ndatas[2].trim();  //格式为ddmm.mmmm
                    String latNS = ndatas[3].trim();
                    String longstring = ndatas[4].trim();
                    String longEW = ndatas[5].trim();
                    String status = ndatas[6].trim();  //0=未定位，1=非差分定位，2=差分定位，6=正在估算
                    //String satellite = ndatas[7].trim(); //00~12
                    if((StringUtils.equals(status, "1") || StringUtils.equals(status, "2")) &&
                            /*!StringUtils.equals(satellite, "00") &&*/
                            StringUtils.isNoneBlank(latstring, latNS, longstring, longEW)){
                        StringBuilder sb = new StringBuilder();
                        double lat = convertNmeaLatLong(latstring, 2);
                        double lon = convertNmeaLatLong(longstring, 3);
                        sb.append("0").append(longEW).append(String.format("%.6f", lon)).
                                append(latNS).append(String.format("%.6f", lat));
                                //append("T").append(genTimsProtocolString());
                        if(nmeaHandler != null) {
                            nmeaHandler.notifyReady(sb.toString());
                        }
                    }
                }
            }
        }
    }


    private double convertNmeaLatLong(String data, int ddlen){
        double d = Double.parseDouble(data.substring(0,ddlen));
        double m = Double.parseDouble(data.substring(ddlen));
        return (d + m/60.0);
    }

    private class MyLocationListener implements LocationListener {
        @Override
        public void onLocationChanged(Location location) {
            log.d("Longitude=" + location.getLongitude() + ",Latitude="
                    + location.getLatitude() + "@" + SystemClock.elapsedRealtime());
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
            log.d("MyLocationListener onStatusChanged: provider=" + provider + ",status=" + status + "@" + SystemClock.elapsedRealtime());
        }

        @Override
        public void onProviderEnabled(String provider) {
            log.i("MyLocationListener onProviderEnabled: provider=" + provider + "@" + SystemClock.elapsedRealtime());
        }

        @Override
        public void onProviderDisabled(String provider) {
            log.i("MyLocationListener onProviderDisabled: provider=" + provider + "@" + SystemClock.elapsedRealtime());
        }
    }

    public void requestNMEA(int timeOutSeconds, IDataSourceCallBack callBack){
        openGps();
        NmeaCallback callback = new NmeaCallback(timeOutSeconds, callBack, false);
        nmeaHandler.start(callback);
    }

    public void requestNMEAPeriodicUpdate(int timeOutSeconds, IDataSourceCallBack callBack){
        openGps();
        NmeaCallback callback = new NmeaCallback(timeOutSeconds, callBack, true);
        nmeaHandler.start(callback);
    }

    public void quit(IDataSourceCallBack wrapped){
        if(wrapped instanceof NmeaCallback){
            nmeaHandler.removeNmeaCallback((NmeaCallback) wrapped);
        }
        if(wrapped instanceof WifiCallback){
            wifiScanner.removeWifiCallback((WifiCallback) wrapped);
        }
    }

    public void requestWifiLocations(int min, int timeOutSeconds, IDataSourceCallBack callBack){
        WifiCallback callback = new WifiCallback(min, timeOutSeconds, callBack, false);
        wifiScanner.start(callback);
    }

    public void requestWifiPeriodicUpdate(int min, int timeOutSeconds, IDataSourceCallBack callBack){
        WifiCallback callback = new WifiCallback(min, timeOutSeconds, callBack, true);
        wifiScanner.start(callback);
    }

    public LbsLocation getLbsLocation(){
        TelephonyManager teleManager = (TelephonyManager) getContext().getSystemService(Context.TELEPHONY_SERVICE);
        int phonetype = teleManager.getPhoneType();
        if(phonetype == TelephonyManager.PHONE_TYPE_GSM) {
            GsmCellLocation gsmCellLocation = (GsmCellLocation) teleManager.getCellLocation();
            if(gsmCellLocation == null)
                return null; //即便无sim卡也会有一个cell
            int servedCid = gsmCellLocation.getCid(); //获取gsm基站识别标号
            int servedLac = gsmCellLocation.getLac(); //获取gsm网络编号

            List<CellInfo> allCellList = teleManager.getAllCellInfo();
            if(allCellList == null)
                return null; // 这种情况一般是无sim卡
            log.d("allCellList size:" + allCellList.size());

            List<LbsLocation> canlist = new ArrayList<LbsLocation>();
            for (CellInfo cell : allCellList) {
                log.d("cell of " + cell.getClass().getSimpleName());
                if(cell instanceof CellInfoGsm){
                    CellInfoGsm gsm = (CellInfoGsm)cell;
                    CellIdentityGsm identity = gsm.getCellIdentity();
                    CellSignalStrengthGsm strength = gsm.getCellSignalStrength();
                    int mnc = identity.getMnc();
                    int mcc = identity.getMcc();
                    int cid = identity.getCid();
                    int lac = identity.getLac();
                    int db = strength.getDbm();
                    log.d("mnc="+mnc+", mcc="+mcc+", cid="+cid+", lac="+lac + ", db=" + db);
                    LbsLocation one = new LbsLocation(mcc, mnc, cid, lac, db);
                    canlist.add(one);
                    if(servedCid == cid && servedLac == lac){
                        log.d("found current cid=" + cid + ",lac=" + lac);
                        break;
                    }
                }else if(cell instanceof CellInfoLte){
                    CellInfoLte lte = (CellInfoLte)cell;
                    CellIdentityLte identity = lte.getCellIdentity();
                    CellSignalStrengthLte strength = lte.getCellSignalStrength();
                    int mnc = identity.getMnc();
                    int mcc = identity.getMcc();
                    int cid = identity.getCi();
                    int tac = identity.getTac();
                    int db = strength.getDbm();
                    log.d("mnc="+mnc+", mcc="+mcc+", cid="+cid+", pcid="+identity.getPci()+", tac="+ tac + ", db=" + db);
                    LbsLocation one = new LbsLocation(mcc, mnc, cid, tac, db);
                    canlist.add(one);
                    if(servedCid == cid && servedLac == tac){
                        log.d("found current cid=" + cid + ",tac=" + tac);
                        break;
                    }
                }
            }
            if(!canlist.isEmpty()){
                for(LbsLocation one:canlist){
                    if(servedCid == one.cid && servedLac == one.lac){
                        return one;
                    }
                }
                log.w("NOT found current cid=" + servedCid + ",lac=" + servedLac + ", return the first one in candidate list!");
                return canlist.get(0);
            }
            log.e("NOT found any CellInfo!");
            return null;
        }else{
            throw new RuntimeException("getLbsLocation: add your code to process PhoneType:" + phonetype);
        }
    }

    private class NMEAHandler extends Handler{
        private final int TRY_NMEA_DATA = 101;
        private final int NMEA_DATA_READY = 102;
        private final int REMOVE_NMEA_CALLBACK = 103;
        private final List<NmeaCallback> callbackList = new CopyOnWriteArrayList<NmeaCallback>();

        void start(NmeaCallback callback){
            callbackList.add(callback);
            if(!hasMessages(TRY_NMEA_DATA)) {
                sendEmptyMessage(TRY_NMEA_DATA);
            }
        }

        void stop(){
            closeGps();
            removeMessages(TRY_NMEA_DATA);
            removeMessages(NMEA_DATA_READY);
        }

        void removeNmeaCallback(NmeaCallback callback){
            obtainMessage(REMOVE_NMEA_CALLBACK, callback).sendToTarget();
        }

        void notifyReady(String nmea){
            Message m = obtainMessage(NMEA_DATA_READY, nmea);
            sendMessage(m);
        }

        boolean isStoped(){
            return callbackList.isEmpty();
        }

        private void notifyCallbacks(String data){
            if(callbackList.isEmpty())
                return;

            List<NmeaCallback> toRemoveList = new ArrayList<NmeaCallback>();
            for (NmeaCallback callback : callbackList) {
                if(StringUtils.isNotBlank(data)){
                    callback.onComplete(data, null);
                    if(!callback.isPeriodic()) {
                        toRemoveList.add(callback);
                    }
                }
            }
            for(NmeaCallback remove: toRemoveList){
                log.d("NMEAHandler remove ,callback=" + remove);
                callbackList.remove(remove);
            }
        }

        private void checkExpired(){
            List<NmeaCallback> toRemoveList = new ArrayList<NmeaCallback>();
            for (NmeaCallback callback : callbackList) {
                if(SystemClock.elapsedRealtime() >= callback.getExpiredAt()){
                    callback.onComplete(null, new DataTimeOutException("get NMEA Time Out"));
                    toRemoveList.add(callback);
                }else{
                    if(callback.isPeriodic()) {
                        callback.onComplete(null, null);
                    }
                }
            }
            for(NmeaCallback remove: toRemoveList){
                log.d("NMEAHandler Expired remove ,callback=" + remove);
                callbackList.remove(remove);
            }
        }

        @Override
        public void handleMessage(Message message) {
            switch (message.what) {
                case NMEA_DATA_READY:{
                    log.d("NMEA_DATA_READY");
                    String data = (String) message.obj;
                    if(StringUtils.isNotBlank(data)){
                        notifyCallbacks(data);
                    }
                    break;
                }
                case TRY_NMEA_DATA:{
                    //log.i("TRY_NMEA_DATA");
                    if(!callbackList.isEmpty()){
                        checkExpired();
                    }
                    if(!callbackList.isEmpty()) {
                        sendEmptyMessageDelayed(TRY_NMEA_DATA, 1000L);
                    }else{
                        closeGps();
                    }
                    break;
                }
                case REMOVE_NMEA_CALLBACK:{
                    log.d("REMOVE_NMEA_CALLBACK for quit");
                    NmeaCallback callback = (NmeaCallback) message.obj;
                    callbackList.remove(callback);
                    break;
                }
            }
        }
    }

    private class WifiScanner extends Handler{
        private final int START_SCAN = 111;
        private final int TRY_ACCESSPOINT = 112;
        private final int REMOVE_WIFI_CALLBACK = 113;
        private int mRetry = 0;
        private final List<WifiCallback> callbackList = new CopyOnWriteArrayList<WifiCallback>();
        private BroadcastReceiver receiver;
        private volatile boolean started = false;

        private boolean wifiForbidden(){
            if(FeatureOption.PRJ_FEATURE_ANSWER_MACHINE){
                boolean inAnswerMode =  SystemProperties.getBoolean("sys.sc_answer.marked", false);
                return (inAnswerMode);
            }
            return false;
        }

        void start(WifiCallback callback) {
            if(started){
                callbackList.add(callback);
                return;
            }
            started = true;
            callbackList.add(callback);
            if(wifiForbidden()){
                notifyCallbacks(null, new DataFailedException("WIFI Forbidden"));
                return;
            }
            final int wifiState = wifiManager.getWifiState();
            if(wifiState == WifiManager.WIFI_STATE_DISABLED || wifiState == WifiManager.WIFI_STATE_DISABLING){
                if (!wifiManager.setWifiEnabled(true)) {
                    notifyCallbacks(null, new DataFailedException("WIFI open failed"));
                    return;
                }
            }

            mRetry = 0;
            removeMessages(START_SCAN);
            removeMessages(TRY_ACCESSPOINT);
            sendEmptyMessage(START_SCAN);

            IntentFilter filter = new IntentFilter();
            filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
            filter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
            receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    updateApInfo();
                }
            };
            LocationDataSource.this.getContext().registerReceiver(receiver, filter);
        }

        void removeWifiCallback(WifiCallback callback){
            obtainMessage(REMOVE_WIFI_CALLBACK, callback).sendToTarget();
        }

        void stop() {
            log.i("WifiScanner stop");
            if(receiver != null){
                LocationDataSource.this.getContext().unregisterReceiver(receiver);
            }
            receiver = null;
            if(!USE_WIFI_NETWORK) {
                wifiManager.setWifiEnabled(false);//关掉wifi
            }

            mRetry = 0;
            removeMessages(TRY_ACCESSPOINT);
            removeMessages(START_SCAN);
            started = false;
        }

        private void notifyCallbacks(List<WifiLocation> data, Exception exception){
            if(callbackList.isEmpty())
                return;

            List<WifiCallback> toRemoveList = new ArrayList<WifiCallback>();
            for (WifiCallback callback : callbackList) {
                if(exception != null){
                    callback.onComplete(null, exception);
                    toRemoveList.add(callback);
                    continue;
                }
                if(data.size() >= callback.getMinItems()){
                    callback.onComplete(data, null);
                    if(!callback.isPeriodic()) {
                        toRemoveList.add(callback);
                    }
                }else{
                    if(callback.isPeriodic()) {
                        callback.onComplete(null, null);
                    }
                }
            }
            for(WifiCallback remove: toRemoveList){
                log.d("WifiScanner remove ,callback=" + remove);
                callbackList.remove(remove);
            }
        }

        private void checkExpired(){
            List<WifiCallback> toRemoveList = new ArrayList<WifiCallback>();
            for (WifiCallback callback : callbackList) {
                if(SystemClock.elapsedRealtime() >= callback.getExpiredAt()){
                    callback.onComplete(null, new DataTimeOutException("WIFI scan Time Out"));
                    toRemoveList.add(callback);
                }
            }
            for(WifiCallback remove: toRemoveList){
                log.d("WifiScanner Expired remove ,callback=" + remove);
                callbackList.remove(remove);
            }
        }

        private void updateApInfo(){
            final int wifiState = wifiManager.getWifiState();
            List<WifiLocation> wifiLocationList = new ArrayList<WifiLocation>();
            if(wifiForbidden()){
                notifyCallbacks(null, new DataFailedException("WIFI Forbidden"));
            }
            if(wifiState == WifiManager.WIFI_STATE_ENABLED && !callbackList.isEmpty()){
                /** Lookup table to more quickly update AccessPoints by only considering objects with the
                 * correct SSID.  Maps SSID -> List of AccessPoints with the given SSID.  */
                Map<String, WifiLocation> apMap = new HashMap<String, WifiLocation>();

                final List<ScanResult> results = wifiManager.getScanResults();
                if (results != null) {
                    for (ScanResult result : results) {
                        log.d("updateApInfo SSID=" + result.SSID);
                        // Ignore hidden and ad-hoc networks.
                        if (result.SSID == null || result.SSID.length() == 0 ||
                                result.capabilities.contains("[IBSS]")) {
                            continue;
                        }
                        boolean found = false;
                        WifiLocation existOne = apMap.get(result.SSID);
                        if(existOne != null && existOne.update(result)){
                            continue;
                        }
                        WifiLocation newOne = new WifiLocation(result);
                        wifiLocationList.add(newOne);
                        apMap.put(newOne.ssid, newOne);
                    }
                }
            }

            notifyCallbacks(wifiLocationList, null);
            if(callbackList.isEmpty()){
                stop();  //满足要求后停
            }
        }

        @Override
        public void handleMessage(Message message) {
            switch (message.what){
                case START_SCAN:{
                    if(wifiForbidden()){
                        notifyCallbacks(null, new DataFailedException("WIFI Forbidden"));
                        stop();
                        break;
                    }
                    if (wifiManager.startScan()) {
                        log.i("WifiScanner start scan");
                        mRetry = 0;
                        sendEmptyMessage(TRY_ACCESSPOINT);
                    } else if (++mRetry >= 3) {
                        mRetry = 0;
                        log.e("WifiScanner scan failed");
                        notifyCallbacks(null, new DataFailedException("WIFI scan failed"));
                        stop();
                    }else{
                        log.i("WifiScanner retry scan after 2 seconds");
                        sendEmptyMessageDelayed(START_SCAN, 1 * 1000L);
                    }
                    break;
                }
                case TRY_ACCESSPOINT:{
                    updateApInfo();
                    if(!callbackList.isEmpty()){
                        checkExpired();
                    }
                    if(!callbackList.isEmpty()){
                        sendEmptyMessageDelayed(TRY_ACCESSPOINT, 1 * 1000L);
                    }else{
                        stop(); //超时后停
                    }
                    break;
                }
                case REMOVE_WIFI_CALLBACK:{
                    log.d("REMOVE_WIFI_CALLBACK for quit");
                    WifiCallback callback = (WifiCallback) message.obj;
                    callbackList.remove(callback);
                    break;
                }
            }
        }
    }

    public class WifiLocation{
        /** These values are matched in string arrays -- changes must be kept in sync */
        static final int SECURITY_NONE = 0;
        static final int SECURITY_WEP = 1;
        static final int SECURITY_PSK = 2;
        static final int SECURITY_EAP = 3;
        // Broadcom, WAPI
        static final int SECURITY_WAPI_PSK = 4;
        static final int SECURITY_WAPI_CERT = 5;

        private String ssid;
        private int security;
        private String bssid;
        private int mRssi;

        public String getSsid() {
            return ssid;
        }

        public String getBssid() {
            return bssid;
        }

        public int getmRssi() {
            return mRssi;
        }

        private WifiLocation(ScanResult result) {
            ssid = result.SSID;
            security = getSecurity(result);
            mRssi = result.level;
            bssid = result.BSSID;
        }

        private boolean update(ScanResult result) {
            if (ssid.equals(result.SSID) && security == getSecurity(result)) {
                if (WifiManager.compareSignalLevel(result.level, mRssi) > 0) {
                    mRssi = result.level;
                }
                return true;
            }
            return false;
        }

        private int getSecurity(ScanResult result) {
            // Broadcom, WAPI
            if (result.capabilities.contains("WAPI-PSK")) {
                return SECURITY_WAPI_PSK;
            } else if (result.capabilities.contains("WAPI-CERT")) {
                return SECURITY_WAPI_CERT;
            } else
                // Broadcom, WAPI
                if (result.capabilities.contains("WEP")) {
                    return SECURITY_WEP;
                } else if (result.capabilities.contains("PSK")) {
                    return SECURITY_PSK;
                } else if (result.capabilities.contains("EAP")) {
                    return SECURITY_EAP;
                }
            return SECURITY_NONE;
        }
    }

    public class LbsLocation{
        private int mcc;
        private int mnc;
        private int cid;
        private int lac;
        private int db;

        public LbsLocation(int mcc, int mnc, int cid, int lac, int db) {
            this.mcc = mcc;
            this.mnc = mnc;
            this.cid = cid;
            this.lac = lac;
            this.db = db;
        }

        public int getMcc() {
            return mcc;
        }

        public int getMnc() {
            return mnc;
        }

        public int getCid() {
            return cid;
        }

        public int getLac() {
            return lac;
        }

        public int getDb() {
            return db;
        }
    }
}

