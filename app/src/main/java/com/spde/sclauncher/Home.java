package com.spde.sclauncher;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.provider.CallLog;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.spde.sclauncher.DataSource.BatteryDataSource;
import com.spde.sclauncher.DataSource.ClassModeDataSource;
import com.spde.sclauncher.DataSource.FixedNumberDataSource;
import com.spde.sclauncher.DataSource.GpsFenceDataSource;
import com.spde.sclauncher.DataSource.IDataSourceCallBack;
import com.spde.sclauncher.DataSource.LocationDataSource;
import com.spde.sclauncher.DataSource.NmeaCallback;
import com.spde.sclauncher.DataSource.ProfileModeDataSource;
import com.spde.sclauncher.DataSource.WhiteListDataSource;
import com.spde.sclauncher.DataSource.WifiCallback;
import com.spde.sclauncher.net.LocalDevice;

import com.spde.sclauncher.net.message.GZ.ReportLocationInfo;

import com.spde.sclauncher.provider.SCDB;
import com.spde.sclauncher.schcard.Business;
import com.spde.sclauncher.schcard.OnBussinessEventListener;
import com.spde.sclauncher.util.WakeLock;
import com.yynie.myutils.Logger;
import com.yynie.myutils.StringUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.spde.sclauncher.SCConfig.MAX_SERVER_SMS;
import static com.spde.sclauncher.SCConfig.USE_WIFI_NETWORK;
import static com.spde.sclauncher.SchoolCardPref.PREF_KEY_LAST_CALLLOG;
import static com.spde.sclauncher.SchoolCardPref.PREF_NAME;

public class Home extends Activity implements OnBussinessEventListener {
    private final Logger log = Logger.get(Home.class, Logger.Level.INFO);
    private TextView status;
    private Business scBusiness;
    private CallLogObserver callLogObserver;
    private AtomicReference<SOSAsyncTask> sosAsyncTaskRef = new AtomicReference<SOSAsyncTask>();
    private Handler mHandler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            if(msg.what == SOSAsyncTask.MSG_SOS_BY_USER_GO){
                String sos = (String) msg.obj;
                log.i("MSG_SOS_BY_USER_GO");
                Intent intent =  new Intent(Intent.ACTION_CALL,Uri.parse("tel:" + sos));
                intent.putExtra("sosflag", "byuser");
                startActivity(intent);
                sosAsyncTaskRef.set(null);
                return true;
            }
            Boolean login = (Boolean) msg.obj;
            status.setText("Login = " + login);
            return false;
        }
    });

    private void test(){
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        status = (TextView) findViewById(R.id.status);
        Button power = (Button) findViewById(R.id.poweron);
        power.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                scBusiness.reportPowerOn();
            }
        });
        Button sos = (Button) findViewById(R.id.sos);
        sos.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialSOSByUser();
            }
        });
        Button fnA = (Button) findViewById(R.id.fn_a);
        fnA.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialFixedNumber(FixedNumberDataSource.A_KEY_ID);
            }
        });
        Button fnB = (Button) findViewById(R.id.fn_b);
        fnB.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialFixedNumber(FixedNumberDataSource.B_KEY_ID);
            }
        });
        Button fnC = (Button) findViewById(R.id.fn_c);
        fnC.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialFixedNumber(FixedNumberDataSource.C_KEY_ID);
            }
        });
        Button test = (Button) findViewById(R.id.test);
        test.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                test();
            }
        });

        /** --START--  school card start  --START-- */
        Intent intent = getIntent();
        boolean isPoweron = intent.getBooleanExtra("isPowerOn", false);
        log.i("onCreate: isPoweron= " + isPoweron);

        scBusiness = new Business(this);
        scBusiness.setOnEventListener(this);
        if(isPoweron) {
            scBusiness.reportPowerOn();
        }
        scBusiness.resetFlagsOnPowerOn();

        openMobileData(true);

        initLocalDevice();

        initDataSources();
        /** --END--  school card end  --END-- */

        /** --START--  Listen to call log changing  &  Report new in/out call to cloud  --START-- */
        callLogObserver = new CallLogObserver(new Handler());
        getContentResolver().registerContentObserver(CallLog.Calls.CONTENT_URI, true, callLogObserver);
        /** --END--  Listen to call log changing  &  Report new in/out call to cloud  --END-- */
    }

    @Override
    public void onNewIntent(Intent intent){
        super.onNewIntent(intent);
        boolean isPoweron = intent.getBooleanExtra("isPowerOn", false);
        log.i("onNewIntent: isPoweron= " + isPoweron);

        /** In real phone environment, new intent with poweron=true may be sent by BootReceiver
         *  which listened for Intent.ACTION_BOOT_COMPLETED from android system
         * */
        if(isPoweron) {
            scBusiness.reportPowerOn();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        log.i("onConfigurationChanged:" + newConfig);
        super.onConfigurationChanged(newConfig);
    }

    @Override
    protected void onDestroy() {
        if(callLogObserver != null){
            getContentResolver().unregisterContentObserver(callLogObserver);
        }
        stopSOSProcess();
        /** --START--  school card start  --START-- */
        scBusiness.onDestroy();
        releaseDataSources();
        WakeLock.getInstance().finalCheck();//
        /** --END--  school card end  --END-- */
        super.onDestroy();
    }

    /** --START--  school card start  --START-- */
    private void initLocalDevice(){
        LocalDevice localDev = LocalDevice.getInstance();
        localDev.setDevType(2);
        localDev.setKeyNumber(3);
        localDev.setSosKey(true);
        localDev.setZoneAlarm(false);
        localDev.setSetIncommingPhone(true);
    }

    private void initDataSources(){
        BatteryDataSource.getInstance().init(this);
        FixedNumberDataSource.getInstance().init(this);
        ClassModeDataSource.getInstance().init(this);
        WhiteListDataSource.getInstance().init(this);
        ProfileModeDataSource.getInstance().init(this);
        GpsFenceDataSource.getInstance().init(this);
        LocationDataSource.getInstance().init(this);
    }

    private void releaseDataSources(){
        BatteryDataSource.getInstance().release();
        FixedNumberDataSource.getInstance().release();
        ClassModeDataSource.getInstance().release();
        WhiteListDataSource.getInstance().release();
        ProfileModeDataSource.getInstance().release();
        GpsFenceDataSource.getInstance().release();
        LocationDataSource.getInstance().release();
    }

    /** !WARN:: this method may be crashed if running on devices higher then API 19*/
    private void openMobileData(boolean onoff){
        //USE_WIFI_NETWORK flag used only for network debug via WIFI connection
        if(USE_WIFI_NETWORK) return;

        ConnectivityManager connManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        Class clazz = connManager.getClass();
        try {
            Method methodCheck = clazz.getMethod("getMobileDataEnabled");
            Boolean opened = (Boolean) methodCheck.invoke(connManager);
            if(opened != onoff){
                log.i("openMobileData :now is  " + opened + ", set to  " + onoff );
                Class[] argsClass = new Class[1];
                argsClass[0] = boolean.class;
                Method methodSet = clazz.getMethod("setMobileDataEnabled", argsClass);
                methodSet.invoke(connManager, onoff);
            }else{
                log.i("openMobileData: expect " + onoff + ", already be " + opened);
            }
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    private void insertNewServerSms(String sms, boolean emergent, int showtimes, int showType, boolean flash, boolean ring, boolean vibrate, long curTimeInSeconds){
        Uri uri = Uri.parse("content://" + SCDB.AUTHORITY + "/serversms");
        ContentValues values = new ContentValues();
        values.put(SCDB.ServerSms.MESSAGE, sms);
        values.put(SCDB.ServerSms.EMERGENT, emergent?1:0);
        values.put(SCDB.ServerSms.SHOWTIMES, showtimes);
        values.put(SCDB.ServerSms.SHOWTYPE, showType);
        values.put(SCDB.ServerSms.FLASH, flash?1:0);
        values.put(SCDB.ServerSms.RING, ring?1:0);
        values.put(SCDB.ServerSms.VIBRATE, vibrate?1:0);
        values.put(SCDB.ServerSms.UPDATETIME, curTimeInSeconds);
        Uri insertedItemUri = getContentResolver().insert(uri, values);
        log.i("insertNewServerSms insertedItemUri=" + insertedItemUri);
    }

    private void removeOldServerSms(){
        Uri uri = Uri.parse("content://" + SCDB.AUTHORITY + "/serversms");
        Cursor cursor = getContentResolver().query(uri, new String[]{"_id"},
                null,null, "_id ASC");
        int total = cursor.getCount();
        if(total <= MAX_SERVER_SMS){
            cursor.close();
            return;
        }
        log.i("removeOldServerSms total=" + total);
        List<Integer> removeIds = new ArrayList<Integer>();
        while(cursor.moveToNext()){
            int id = cursor.getInt(cursor.getColumnIndex("_id"));
            removeIds.add(id);
            total --;
            if(total <= MAX_SERVER_SMS){
                break;
            }
        }
        cursor.close();
        for(Integer id: removeIds){
            int delnum = getContentResolver().delete(uri, "_id=" + id, null);
            log.i("removeOldServerSms delete id=" + id + ", delnum=" + delnum);
        }
    }

    private void removeAllServerSms(){
        Uri uri = Uri.parse("content://" + SCDB.AUTHORITY + "/serversms");
        int delnum = getContentResolver().delete(uri, null, null);
        log.i("removeAllServerSms  delnum=" + delnum);
    }

    private void showToast(String info){
        Toast toast = Toast.makeText(this, null,Toast.LENGTH_LONG);
        LayoutInflater inflater = LayoutInflater.from(this);
        View view = inflater.inflate(R.layout.mini_toast, null);
        TextView textView = (TextView) view.findViewById(R.id.mini_toast_message);
        textView.setText(info);
        toast.setView(view);
        toast.setGravity(Gravity.CENTER, 0, 0);
        toast.show();
    }

    private void dialFixedNumber(int keyIndex){
        if(keyIndex <= 0){
            throw new RuntimeException("Unknown keyIndex = " + keyIndex + " Fixed Number bound on key 1 ~ 3");
        }
        String number = FixedNumberDataSource.getInstance().getKeyNumber(keyIndex);
        String rejectReason = canDialingACall(number, false);
        if(rejectReason != null){
            showToast(rejectReason);
        }else{
            log.i("dailFixedNumber go");
            Intent intent =  new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + number));
            startActivity(intent);
        }
    }

    private void dialSOSByUser(){
        SOSAsyncTask sosTask = new SOSAsyncTask(scBusiness, mHandler);
        if(sosAsyncTaskRef.compareAndSet(null, sosTask)) {
            String sos = FixedNumberDataSource.getInstance().getKeyNumber(0);
            String rejectReason = canDialingACall(sos, true);
            if (rejectReason != null) {
                showToast(rejectReason);
                sosAsyncTaskRef.set(null);
                log.i("dialSOSByUser reject:" + rejectReason);
                return;
            }
            LocationFuture locFutrue = buildLocationReport();
            sosTask.execute(sos, locFutrue, Long.valueOf(80 * 1000L));
        }else{
            log.e("dialSOSByUser SOSAsyncTask already run");
        }
    }

    private void stopSOSProcess(){
        SOSAsyncTask sosTask = sosAsyncTaskRef.getAndSet(null);
        if(sosTask != null){
            sosTask.stop();
        }
    }

    private LocationFuture buildLocationReport(){
        WakeLock.getInstance().acquire();
        final LocationFuture future = new LocationFuture();
        IDataSourceCallBack icb = new IDataSourceCallBack() {
            Exception wifiExp = null, gpsExp = null;
            boolean done = false, wifiQuit = false, gpsQuit = false;
            @Override
            public void onComplete(IDataSourceCallBack wrapped, Object result, Exception exception) {
                log.d(wrapped.getClass().getSimpleName() + " onComplete result=" + result + " ,exception=" + exception);
                if(done){
                    LocationDataSource.getInstance().quit(wrapped);
                    if(wrapped instanceof WifiCallback){
                        log.i("onComplete done, wifi quit");
                        wifiQuit = true;
                    }else{
                        log.i("onComplete done, gps quit");
                        gpsQuit = true;
                    }
                    if(wifiQuit && gpsQuit) {
                        log.i("onComplete done, release wakelock");
                        WakeLock.getInstance().release();
                    }
                    return;
                }
                if (result != null) {
                    ReportLocationInfo report = new ReportLocationInfo(null);
                    if (wrapped instanceof WifiCallback) {
                        List<String> wifiList = new ArrayList<String>();
                        List<LocationDataSource.WifiLocation> list = (List<LocationDataSource.WifiLocation>) result;
                        int pickNum = 5; //最多5个
                        for (LocationDataSource.WifiLocation loc : list) {
                            String wifi = loc.getSsid() + "!" + loc.getBssid() + "!" + loc.getmRssi();
                            wifiList.add(wifi);
                            pickNum--;
                            if (pickNum <= 0)
                                break;
                        }
                        report.setWifiList(wifiList);
                    } else if (wrapped instanceof NmeaCallback) {
                        report.setNmea((String) result);
                    }
                    LocationDataSource.LbsLocation lbs = LocationDataSource.getInstance().getLbsLocation();
                    String lbsString = (lbs == null) ? null : (lbs.getMcc() + "!" + lbs.getMnc() + "!" + lbs.getLac() + "!" + lbs.getCid() + "!" + lbs.getDb());
                    report.setLbs(lbsString);
                    future.setReport(report);
                    done = true;
                }else if(exception != null){
                    if (wrapped instanceof WifiCallback) {
                        wifiExp = exception;
                    }else if (wrapped instanceof NmeaCallback) {
                        gpsExp = exception;
                    }
                }
                if(wifiExp != null && gpsExp != null){
                    ReportLocationInfo reportLBSOnly = new ReportLocationInfo(null);
                    LocationDataSource.LbsLocation lbs = LocationDataSource.getInstance().getLbsLocation();
                    String lbsString = (lbs == null) ? null : (lbs.getMcc() + "!" + lbs.getMnc() + "!" + lbs.getLac() + "!" + lbs.getCid() + "!" + lbs.getDb());
                    reportLBSOnly.setLbs(lbsString);
                    future.setReport(reportLBSOnly);
                    done = true;
                    log.e("wifi and gps all failed!");
                    WakeLock.getInstance().release();
                }
            }
        };
        LocationDataSource.getInstance().openGps();
        LocationDataSource.getInstance().requestWifiPeriodicUpdate(3, 30, icb);
        LocationDataSource.getInstance().requestNMEAPeriodicUpdate(40, icb);
        return future;
    }

    private void dialSOS(){
        String sos = FixedNumberDataSource.getInstance().getKeyNumber(0);
        if(null == canDialingACall(sos, true)){
            log.i("dialSOS go");
            Intent intent =  new Intent(Intent.ACTION_CALL,Uri.parse("tel:" + sos));
            intent.putExtra("sosflag", "secure");
            startActivity(intent);
        }
    }

    /** if can not dail to sos number, return the reason string*/
    private String canDialingACall(String phone, boolean isSOS){
        log.i("canDialingACall: dail to phone number=" + phone);
        if(StringUtils.isBlank(phone)){
            log.e("canDialingACall: phone number is empty");
            return getResources().getString(isSOS?(R.string.sos_none):(R.string.phone_number_none));
        }else{
            if(ProfileModeDataSource.getInstance().isOutgoingCallForbidden()){
                log.i("canDialingACall: outgoing forbiden in ProfileModeDataSource");
                return getResources().getString(R.string.out_call_forbid);
            }
            if(isSOS) {
                if (ClassModeDataSource.getInstance().isSosOutgoingForbidden()) {
                    log.i("canDialingACall: SOS forbiden in ClassModeDataSource");
                    return getResources().getString(R.string.sos_call_forbid_classmode);
                }
            }else{
                if (ClassModeDataSource.getInstance().isInClass()){
                    log.i("canDialingACall: forbiden in ClassMode");
                    return getResources().getString(R.string.call_forbid_classmode);
                }
            }
            return null;
        }
    }

    /** OnBussinessEventListener start */
    @Override
    public void onLoginStatusChanged(boolean loginOk) {
        log.i("onLoginStatusChanged:" + loginOk);
        mHandler.obtainMessage(1, loginOk).sendToTarget();
    }

    @Override
    public void onUserRegiterRequired() {
        //new user, clear all user data
        FixedNumberDataSource.getInstance().restore();
        ClassModeDataSource.getInstance().restore();
        WhiteListDataSource.getInstance().restore();
        ProfileModeDataSource.getInstance().restore();
        GpsFenceDataSource.getInstance().restore();
        removeAllServerSms();
    }

    @Override
    public void onSeverSmsShow(String sms, boolean emergent, int showtimes, int showType, boolean flash, boolean ring, boolean vibrate) {
        // emergent false:normal msg, true:emergent msg
        // showType  0:TTS, 1:display, 2:TTS adn display
        // showTimes 0:do NOT show, >0:show $showTimes times
        //  flash    whether or not flash led
        //  vibrate   whether or not vibrate
        //  ring     whether or notring a sound
        log.d("onSeverSmsShow : " + sms);
        long curTime = System.currentTimeMillis()/1000L;
        insertNewServerSms(sms, emergent, showtimes, showType, flash, ring, vibrate, curTime);
        removeOldServerSms();
        //TODO: show this msg to User
    }

    @Override
    public void onDialSos() {
        log.i("onDialSos from server push");
        dialSOS();
    }

    @Override
    public void onDoReboot(boolean recovery) {
        log.i("onDoReboot: recovery=" + recovery);
        if(recovery){
            sendBroadcast(new Intent("android.intent.action.MASTER_CLEAR"));
        }else{
            PowerManager pManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
            pManager.reboot("");
        }
    }
    /** OnBussinessEventListener end */
    /** --END--  school card end  --END-- */

    /** --START--  Listen to call log changing  &  Report new in/out call to cloud  --START-- */
    class CallLogObserver extends ContentObserver {
        private AtomicReference<String> memCallLogRef = new AtomicReference<String>();
        public CallLogObserver(Handler handler) {
            super(handler);
            readMemory();
            checkCallLogs();
        }

        private void readMemory(){
            SharedPreferences pref = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            String last = pref.getString(PREF_KEY_LAST_CALLLOG, null);
            memCallLogRef.set(last);
        }

        private void saveMemory(String key){
            SharedPreferences pref = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = pref.edit();
            editor.putString(PREF_KEY_LAST_CALLLOG, key);
            editor.commit();
            memCallLogRef.set(key);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            checkCallLogs();
        }

        private void checkCallLogs(){
            Cursor cursor = null;
            try {
                long curDate = System.currentTimeMillis();
                String where = CallLog.Calls.TYPE + "=" + CallLog.Calls.INCOMING_TYPE + " OR (" + CallLog.Calls.TYPE + "=" + CallLog.Calls.OUTGOING_TYPE
                        + " AND " + CallLog.Calls.DURATION + ">0)";
                cursor = getContentResolver().query(CallLog.Calls.CONTENT_URI,
                        new String[]{CallLog.Calls.NUMBER, CallLog.Calls.DATE, CallLog.Calls.DURATION, CallLog.Calls.TYPE},
                        where, null, CallLog.Calls.DEFAULT_SORT_ORDER);

                if (cursor.moveToNext()) { //check latest one only
                    String number = cursor.getString(cursor.getColumnIndex(CallLog.Calls.NUMBER));
                    int duration = cursor.getInt(cursor.getColumnIndex(CallLog.Calls.DURATION));
                    int type = cursor.getInt(cursor.getColumnIndex(CallLog.Calls.TYPE));
                    long date = cursor.getLong(cursor.getColumnIndex(CallLog.Calls.DATE));
                    String key = number + type + date + duration;
                    if (StringUtils.equals(memCallLogRef.get(), key)) {
                        log.w("checkCallLogs calllog " + number + " is sent already, ignored");
                        return;
                    } else {
                        long endDate = date + duration * 1000L;
                        if( Math.abs(curDate - endDate) > (4 * 60 * 60 * 1000L)){
                            log.w("checkCallLogs calllog " + number + " is too old, ignored");
                            return;
                        }
                        log.i("checkCallLogs: send calllog " + number);
                        if(scBusiness.reportCallLog(number, date, duration, (type == CallLog.Calls.INCOMING_TYPE))) {
                            saveMemory(key);
                        }
                    }
                }else {
                    log.w("checkCallLogs no calllog");
                    return;
                }
            } finally {
                if(cursor != null) {
                    cursor.close();
                }
            }
        }
    }
    /** --END--  Listen to call log changing  &  Report new in/out call to cloud  --END-- */
}
