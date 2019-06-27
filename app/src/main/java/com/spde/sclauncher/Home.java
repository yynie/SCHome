package com.spde.sclauncher;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
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
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.Vibrator;
import android.provider.CallLog;
import android.provider.Telephony;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.android.featureoption.FeatureOption;
import com.spde.sclauncher.DataSource.AbstractDataSource;
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
import static com.spde.sclauncher.SchoolCardPref.PREF_KEY_CONFIG_CHECK_STR;
import static com.spde.sclauncher.SchoolCardPref.PREF_KEY_HEARTBEAT;
import static com.spde.sclauncher.SchoolCardPref.PREF_KEY_LAST_CALLLOG;
import static com.spde.sclauncher.SchoolCardPref.PREF_KEY_LOCRATE;
import static com.spde.sclauncher.SchoolCardPref.PREF_KEY_SERVERADDR;
import static com.spde.sclauncher.SchoolCardPref.PREF_NAME;

public class Home extends Activity implements OnBussinessEventListener {
    private final Logger log = Logger.get(Home.class, Logger.Level.INFO);
    private TextView status;
    private LocalDataObserver localDataObserver;
    private Business scBusiness;
    private CallLogObserver callLogObserver;
    private WorkHandler workHandler = new WorkHandler();
    private AtomicReference<SOSAsyncTask> sosAsyncTaskRef = new AtomicReference<SOSAsyncTask>();
    private BroadcastReceiver noPauseReceiver; //receive something even user not stay in launcher UI
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
        /** NoPauseReceiver used to listen to something even if not in HOME. don't unregister it until onDestroy() called. */
        IntentFilter noPauseIF = new IntentFilter();
        noPauseIF.addAction("com.spde.sclauncher.sms_instruction");
        noPauseIF.addAction(Intent.ACTION_SCREEN_ON);
        noPauseIF.addAction(Intent.ACTION_SCREEN_OFF);
        noPauseReceiver = new NoPauseReceiver();
        registerReceiver(noPauseReceiver, noPauseIF);

        /** --START--  school card start  --START-- */
        // Intent intent = getIntent();
        // boolean isPoweron = intent.getBooleanExtra("isPowerOn", false);
        boolean isPoweron = !SystemProperties.getBoolean("sys.scpoweron.marked", false);
        log.i("onCreate: isPoweron= " + isPoweron);

        scBusiness = new Business(this);
        scBusiness.setOnEventListener(this);
        if(isPoweron) {
            scBusiness.reportPowerOn();
            SystemProperties.set("sys.scpoweron.marked", "true");
        }
        scBusiness.resetFlagsOnPowerOn();

        openMobileData(true);

        initLocalDevice();

        initDataSources();
        /** --END--  school card end  --END-- */

        /** --START--  Listen to call log changing  &  Report new in/out call to cloud  --START-- */
        callLogObserver = new CallLogObserver(workHandler);
        getContentResolver().registerContentObserver(CallLog.Calls.CONTENT_URI, true, callLogObserver);
        /** --END--  Listen to call log changing  &  Report new in/out call to cloud  --END-- */
        localDataObserver = new LocalDataObserver(workHandler);
        getContentResolver().registerContentObserver(AbstractDataSource.CONTENT_URI, true, localDataObserver);

    }

    @Override
    protected void onResume() {
        super.onResume();
        if(DebugDynamic.getInstance().isUseTestServer()){
            showToast("!Test Server Enabled");
        }
        /**
         * In any case u back to home screen (call ending or powerkey or etc)
         * there must be no need to maintain INVISIBLE, so try to release the lock.
         * this may not be necessary but to assure of release it.
         */
        log.i("SOS release lock caused by onResume");

        checkOtherFlags();
    }

    private void checkOtherFlags(){
        if(FeatureOption.PRJ_FEATURE_ANSWER_MACHINE){
            if(true == SystemProperties.getBoolean("sys.sc_answer.marked", false)){
                SystemProperties.set("sys.sc_answer.marked", "false");
            }
        }
    }

    @Override
    public void onNewIntent(Intent intent){
        super.onNewIntent(intent);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        log.d("onConfigurationChanged:" + newConfig);
        super.onConfigurationChanged(newConfig);
    }

    @Override
    protected void onDestroy() {
        if(callLogObserver != null){
            getContentResolver().unregisterContentObserver(callLogObserver);
        }
        if(localDataObserver != null){
            getContentResolver().unregisterContentObserver(localDataObserver);
        }
        if(noPauseReceiver != null){
            unregisterReceiver(noPauseReceiver);
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
        String sn2 = SystemProperties.get("ro.boot.serialno2","");
        if(StringUtils.isBlank(sn2)) sn2 = "0";
        localDev.setRFIDNumber(sn2);
    }

    private void initDataSources(){
        BatteryDataSource.getInstance().init(this);
        FixedNumberDataSource.getInstance().init(this);
        ClassModeDataSource.getInstance().init(this);
        WhiteListDataSource.getInstance().init(this);
        ProfileModeDataSource.getInstance().init(this);
        GpsFenceDataSource.getInstance().init(this);
        LocationDataSource.getInstance().init(this);
        log.d("initDataSources done");
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
        log.d("insertNewServerSms insertedItemUri=" + insertedItemUri);
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
        log.d("removeOldServerSms total=" + total);
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
            log.d("removeOldServerSms delete id=" + id + ", delnum=" + delnum);
        }
    }

    private void removeAllServerSms(){
        Uri uri = Uri.parse("content://" + SCDB.AUTHORITY + "/serversms");
        int delnum = getContentResolver().delete(uri, null, null);
        log.d("removeAllServerSms  delnum=" + delnum);
    }

    private void removeAllCallLogs() {
        ContentResolver resolver = getContentResolver();
//        Cursor cursor = resolver.query(CallLog.Calls.CONTENT_URI, null, null, null, null);
//        while (cursor.moveToNext()) {
//            String number = cursor.getString(cursor.getColumnIndex("number"));
//            log.i("removeAllCallLogs, number=" + number);
//        }
        int result = resolver.delete(CallLog.Calls.CONTENT_URI, null, null);
        log.i("removeAllCallLogs, result=" + result);
        //removeCallLogsNotification();
    }


    private void removeAllPhoneSms() {
        ContentResolver resolver = getContentResolver();
//        Cursor cursor = resolver.query(Telephony.Sms.CONTENT_URI, null, null, null, null);
//        while (cursor.moveToNext()) {
//            String body = cursor.getString(cursor.getColumnIndex("body"));
//            log.i("removeAllPhoneSms, body=" + body);
//        }
        int result = resolver.delete(Telephony.Sms.CONTENT_URI, null, null);
        log.i("removeAllPhoneSms, result=" + result);
    }

    private void clearPreference() {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.remove(PREF_KEY_HEARTBEAT);
        editor.remove(PREF_KEY_LOCRATE);
        editor.remove(PREF_KEY_CONFIG_CHECK_STR);
        editor.remove(PREF_KEY_LAST_CALLLOG);
        editor.commit();
    }

    private void showToast(String info){
        Toast.makeText(this, info ,Toast.LENGTH_LONG).show();
        // Toast toast = Toast.makeText(this, null,Toast.LENGTH_LONG);
        // LayoutInflater inflater = LayoutInflater.from(this);
        // View view = inflater.inflate(R.layout.mini_toast, null);
        // TextView textView = (TextView) view.findViewById(R.id.mini_toast_message);
        // textView.setText(info);
        // toast.setView(view);
        // toast.setGravity(Gravity.CENTER, 0, 0);
        // toast.show();
    }

    private void dialFixedNumber(int keyIndex){
        if(keyIndex <= 0){
            throw new RuntimeException("Unknown keyIndex = " + keyIndex + " Fixed Number bound on key 1 ~ 3");
        }
        String number = FixedNumberDataSource.getInstance().getKeyNumber(keyIndex);
        String rejectReason = canDialingACall(number, false, true);
        if(rejectReason != null){
            showToast(rejectReason);
        }else{
            log.d("dailFixedNumber go");
            Vibrator vibrator=(Vibrator)getApplication().getSystemService(Context.VIBRATOR_SERVICE);
            vibrator.vibrate(100);
            Intent intent =  new Intent(Intent.ACTION_CALL,Uri.parse("tel:" + number));
            startActivity(intent);
        }
    }
    private void showSOSDialog(){

    }

    private void hideSOSDialog(){

    }

    private void dialSOSByUser(){
        String errhint = checkSIM();
        if(errhint == null){
            errhint = canProcessSOS();
        }

        if(errhint != null){
            Vibrator vibrator=(Vibrator)getApplication().getSystemService(Context.VIBRATOR_SERVICE);
            vibrator.vibrate(100);
            showToast(errhint);
            return;
        }

        SOSAsyncTask sosTask = new SOSAsyncTask(scBusiness, workHandler);
        if(sosAsyncTaskRef.compareAndSet(null, sosTask)) {
            SystemProperties.set("sys.sc_sos.marked", "true");
            Vibrator vibrator=(Vibrator)getApplication().getSystemService(Context.VIBRATOR_SERVICE);
            vibrator.vibrate(100);
            String sos = FixedNumberDataSource.getInstance().getKeyNumber(0);

            WakeLock.getInstance().acquire();
            //the acquired cpu lock so PowerManager.goToSleep() will not really make the device falling into sleep.
            // PowerManager pManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            // boolean sreenOn = pManager.isScreenOn();
            // if(sreenOn) pManager.goToSleep(SystemClock.uptimeMillis());

            showSOSDialog();
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
        hideSOSDialog();
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
        String result = canDialingACall(sos, true, false);
        if(null == result){
            log.d("dialSOS go");

            Intent intent =  new Intent(Intent.ACTION_CALL,Uri.parse("tel:" + sos));
            intent.putExtra("sosflag", "secure");
            startActivity(intent);
        }else {
            log.i("dialSOS: " + result);
        }
    }

    private String checkSIM(){
        TelephonyManager telManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        int state = telManager.getSimState();
        switch(state){
            case TelephonyManager.SIM_STATE_READY:
                return null; //SIM OK
            default:
                return getResources().getString(R.string.hint_sim_unavailable);
        }
    }

    private String canProcessSOS(){
        // boolean isNumberExisted = false;
        // List<String> numberList = FixedNumberDataSource.getInstance().getAllKeyNumbers();
        // for (String n : numberList) {
        //     if(StringUtils.isNotBlank(n)){
        //         isNumberExisted = true;
        //         break;
        //     }
        // }
        // if(!isNumberExisted){
        //     return getResources().getString(R.string.phone_number_none);
        // }

        if (ClassModeDataSource.getInstance().isSosOutgoingForbidden()) {
            return getResources().getString(R.string.sos_call_forbid_classmode);
        }
        return null;
    }

    /** if can not dail to sos number, return the reason string*/
    private String canDialingACall(String phone, boolean isSOS, boolean hintOrLog){
        log.d("canDialingACall: dail to phone number=" + phone);
        if(StringUtils.isBlank(phone)){
            log.e("canDialingACall: phone number is empty");
            if (hintOrLog) {
                return getResources().getString(isSOS ? (R.string.sos_none) : (R.string.phone_number_none));
            }else {
                return "phone number is empty";
            }
        }else{
            if(isSOS) {
                if (ClassModeDataSource.getInstance().isSosOutgoingForbidden()) {
                    log.d("canDialingACall: SOS forbiden in ClassModeDataSource");
                    if (hintOrLog) {
                        return getResources().getString(R.string.sos_call_forbid_classmode);
                    }else {
                        return "SOS forbiden in ClassModeDataSource";
                    }
                }
            }else{
                if(ProfileModeDataSource.getInstance().isOutgoingCallForbidden()){
                    log.d("canDialingACall: outgoing forbiden in ProfileModeDataSource");
                    if (hintOrLog) {
                        return getResources().getString(R.string.out_call_forbid);
                    }else {
                        return "outgoing forbiden in ProfileModeDataSource";
                    }
                }
                if (ClassModeDataSource.getInstance().isInClass()){
                    log.d("canDialingACall: forbiden in ClassMode");
                    if (hintOrLog) {
                        return getResources().getString(R.string.call_forbid_classmode);
                    }else {
                        return "forbiden in ClassMode";
                    }
                }
            }
            return null;
        }
    }

    /** OnBussinessEventListener start */
    @Override
    public void onLoginStatusChanged(boolean loginOk) {
        log.i("onLoginStatusChanged:" + loginOk);
        workHandler.broadcastToStatusBar();
        mHandler.obtainMessage(1, loginOk).sendToTarget();
    }

    @Override
    public void onUserRegiterRequired() {
        //new user, clear all user data
        clearPreference();
        clearUserDatas();
        removeAllCallLogs();
        removeAllPhoneSms();
    }

    private void clearUserDatas(){
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
//          sendBroadcast(new Intent("android.intent.action.MASTER_CLEAR"));
            clearPreference();
            clearUserDatas();
            removeAllCallLogs();
            removeAllPhoneSms();
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

    private boolean isScreenOn(){
        PowerManager pManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        boolean on = pManager.isScreenOn();
        log.d("isScreenOn :" + on);
        return on;
    }

    /** --START--  Listen to local data changing  &  broadcast to status bar  --START-- */
    class LocalDataObserver extends ContentObserver {
        public LocalDataObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            super.onChange(selfChange, uri);
            if(uri.toString().contains("profile") || uri.toString().contains("classmode")) {
                log.d("LocalDataObserver: " + uri);
                if(isScreenOn()) {
                    workHandler.broadcastToStatusBar();
                }else{
                    log.i("LocalDataObserver when screen off, do nothing");
                }
            }
        }
    }

    class WorkHandler extends Handler{
        private final int MSG_OBSERVE = 6590; //obsgo
        private final int MSG_BROADCAST_NOW = 6591;
        private final int MSG_LOGIC_SOS_DIAL_TIMEOUT = 6592;
        boolean memInClass = false;  //class mode need to be checked per minute

        private void startObserveLocalData(){
            log.i("WorkHandler startObserveLocalData");
            removeMessages(MSG_OBSERVE);
            sendEmptyMessage(MSG_OBSERVE);
        }

        private void stopObserveLocalData(){
            log.i("WorkHandler stopObserveLocalData");
            removeMessages(MSG_OBSERVE);
        }

        private void broadcastToStatusBar(){
            removeMessages(MSG_BROADCAST_NOW);
            sendEmptyMessage(MSG_BROADCAST_NOW);
        }

        private void sosDialTimeoutDelay(long delay){
            removeMessages(MSG_LOGIC_SOS_DIAL_TIMEOUT);
            sendEmptyMessageDelayed(MSG_LOGIC_SOS_DIAL_TIMEOUT, delay);
        }

        private void broadcastInternal(){
            Intent sbIntent = new Intent("com.spde.sclauncher.update_statusbar");
            boolean login = (scBusiness == null)? false: scBusiness.isLogin();
            memInClass = ClassModeDataSource.getInstance().isInClass();
            int ringMode = ProfileModeDataSource.getInstance().getIncomingCallRingMode();
            sbIntent.putExtra("login", login);
            sbIntent.putExtra("in_class", memInClass);
            sbIntent.putExtra("ring_mode", ringMode);

            Home.this.sendBroadcast(sbIntent);
            log.i("WorkHandler broadcastToStatusBar logIn=" + login + ",inClass=" + memInClass + ", ringMode=" + ringMode);
        }

        @Override
        public void handleMessage(Message msg) {
            if(msg.what == SOSAsyncTask.MSG_SOS_BY_USER_GO){
                log.i("MSG_SOS_BY_USER_GO go go go");
                hideSOSDialog();

                //Dail one by one
                sosAsyncTaskRef.set(null);
                WakeLock.getInstance().release();
                return;
            }else if(msg.what == MSG_LOGIC_SOS_DIAL_TIMEOUT){
                //platform code
            }else if(msg.what == MSG_BROADCAST_NOW){
                broadcastInternal();
            }else if(msg.what == MSG_OBSERVE){
                if(isScreenOn()) {
                    boolean inClass = ClassModeDataSource.getInstance().isInClass();
                    log.d("WorkHandler MSG_OBSERVE inClass=" + inClass + ", memInClass=" + memInClass);
                    if (memInClass != inClass) {
                        broadcastInternal();
                    }
                    sendEmptyMessageDelayed(MSG_OBSERVE, 10 * 1000L);
                }else{
                    log.w("WorkHandler MSG_OBSERVE but sreen off");
                }
                return;
            }
            super.handleMessage(msg);
        }
    }
    /** --END--  Listen to local data changing  &  broadcast to status bar  --END-- */

    /** NoPauseReceiver used to listen to something even if not in HOME. don't unregister it until onDestroy() called. */
    private boolean isFirstScreenOn = true;
    class NoPauseReceiver extends BroadcastReceiver{

        @Override
        public void onReceive(Context context, Intent intent) {
            log.d("NoPauseReceiver" + intent.getAction());
            if(intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
                workHandler.broadcastToStatusBar();
                workHandler.startObserveLocalData();
                if(isFirstScreenOn){
                    isFirstScreenOn = false;
                }else{
                    scBusiness.netWatcherBroadCast();
                }
            }else if(intent.getAction().equals(Intent.ACTION_SCREEN_OFF)){
                workHandler.stopObserveLocalData();
            }else if(intent.getAction().equals("com.spde.sclauncher.sms_instruction")){
                //adb shell am broadcast -a com.spde.sclauncher.sms_instruction --es phone "13900000000" --es cmd "dlcx"
                String phone = intent.getStringExtra("phone");
                String cmd = intent.getStringExtra("cmd");
                log.d("phone="+phone+",cmd="+cmd);
                if(cmd.equalsIgnoreCase("DLCX")){
                    int battery = BatteryDataSource.getInstance().getBatteryPercent();
                    String text = getResources().getString(R.string.dlcx_reply, String.valueOf(battery) + "%");
                    SmsManager.getDefault().sendTextMessage(phone, null, text, null, null);
                }else if(cmd.equalsIgnoreCase("QQSFE")){
                    long elapsedSeconds = SystemClock.elapsedRealtime()/1000;
                    if(elapsedSeconds < 60){
                         log.e(cmd + " received too early, phone system not ready yet!");
                         return;
                    }
                    String sos = FixedNumberDataSource.getInstance().getKeyNumber(0);
                    if(phone.length() > 3 && phone.startsWith("+86")){
                        phone = phone.substring(3);
                    }else if(phone.length() > 2 && phone.startsWith("86")){
                        phone = phone.substring(2);
                    }else if(phone.length() > 4 && phone.startsWith("0086")){
                        phone = phone.substring(4);
                    }
                    String result = canDialingACall(phone, StringUtils.equals(sos, phone), false);
                    if (null == result) {
                        Intent dialIntent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + phone));
                        dialIntent.putExtra("sosflag", "secure");
                        startActivity(dialIntent);
                    }else {
                        log.i("QQSFE:" + result);
                    }
                }else if(cmd.toUpperCase().startsWith("SERVER")) {
                    String[] parts = cmd.split("[,，]");
                    boolean ok = false;
                    if(parts.length == 3 && parts[0].trim().equalsIgnoreCase("SERVER")){
                        try {
                            String server = parts[1].trim();
                            int port = Integer.parseInt(parts[2].trim());
                            if(StringUtils.isNotBlank(server)) {
                                String addr = server.trim() + ":" + port;
                                SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
                                SharedPreferences.Editor editor = prefs.edit();
                                editor.putString(PREF_KEY_SERVERADDR, addr);
                                ok =editor.commit();
                            }
                        }catch (NumberFormatException e){
                            e.printStackTrace();
                        }
                    }
                    String text = getResources().getString(ok? R.string.server_cmd_reply_ok:R.string.server_cmd_reply_err);
                    SmsManager.getDefault().sendTextMessage(phone, null, text, null, null);
                }
            }
        }
    }
}
