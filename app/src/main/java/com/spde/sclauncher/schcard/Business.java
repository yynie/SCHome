package com.spde.sclauncher.schcard;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;

import com.sonf.core.future.IWriteFuture;
import com.sonf.core.future.IoFutureListener;
import com.sonf.core.write.WriteException;
import com.spde.sclauncher.DataSource.BatteryDataSource;
import com.spde.sclauncher.DataSource.ClassModeDataSource;
import com.spde.sclauncher.DataSource.DataFailedException;
import com.spde.sclauncher.DataSource.DataTimeOutException;
import com.spde.sclauncher.DataSource.FixedNumberDataSource;
import com.spde.sclauncher.DataSource.GpsFenceDataSource;
import com.spde.sclauncher.DataSource.IDataSourceCallBack;
import com.spde.sclauncher.DataSource.LocationDataSource;
import com.spde.sclauncher.DataSource.NmeaCallback;
import com.spde.sclauncher.DataSource.ProfileModeDataSource;
import com.spde.sclauncher.DataSource.WhiteListDataSource;
import com.spde.sclauncher.DataSource.WifiCallback;
import com.spde.sclauncher.DefaultNetCommListener;

import com.spde.sclauncher.net.LocalDevice;
import com.spde.sclauncher.net.LoginFuture;
import com.spde.sclauncher.net.NetCommClient;
import com.spde.sclauncher.net.ResponseFuture;
import com.spde.sclauncher.net.message.GZ.*;

import com.spde.sclauncher.net.message.IRequest;
import com.spde.sclauncher.net.message.ISCMessage;
import com.spde.sclauncher.net.pojo.CrossFenceInfo;
import com.spde.sclauncher.net.pojo.IncomingCallSet;
import com.spde.sclauncher.net.pojo.PeriodWeeklyOnOff;
import com.spde.sclauncher.net.pojo.RegionLimit;
import com.spde.sclauncher.SCConfig;
import com.spde.sclauncher.util.SchedTask;
import com.spde.sclauncher.util.TaskScheduler;
import com.spde.sclauncher.util.WakeLock;

import com.yynie.myutils.Logger;
import com.yynie.myutils.StringUtils;

import java.net.SocketTimeoutException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.spde.sclauncher.SchoolCardPref.*;

public class Business extends DefaultNetCommListener implements IoFutureListener<ResponseFuture> {
    private final Logger log = Logger.get(Business.class, Logger.Level.DEBUG);
    //TelephonyIntents.ACTION_SIM_STATE_CHANGED
    private final String ACTION_SIM_STATE_CHANGED = "android.intent.action.SIM_STATE_CHANGED";
    private final Context mContext;
    private NetCommClient commClient;
    private OnBussinessEventListener onEventListener;
    private BroadcastReceiver broadcastReceiver = new SysIntentReceiver();
    private AtomicInteger timeoutCounter = new AtomicInteger(0); //请求超时计数
    private Queue<DelayReport> reportRequestQueue = new ConcurrentLinkedQueue<DelayReport>(); //这个queque目前只用于开机警报, 通话记录上报
    private AtomicReference<DelayReport> locationReportDelay = new AtomicReference<DelayReport>();
    private AtomicReference<SchedTask> nextLocationTask = new AtomicReference<SchedTask>();
    private BusinessWorker businessWorker = new BusinessWorker();
    private AtomicInteger reportPowerOn = new AtomicInteger(0);
    private int GPS_PREPARE_SECONDS = 70; //定时上报位置，提前70s开gps
    //private AtomicBoolean locationReporting = new AtomicBoolean(false);  //控制

    /** 现在有三个获取配置的接口是开机要请求一次，每天不得超过10次*/
    private int CHECK_CONFIG_BUTTONS = 0x00000001;
    private int CHECK_CONFIG_CLASS   = 0x00000002;
    private int CHECK_CONFIG_CALL    = 0x00000004;
    private Map<Integer, ConfigCheck> configChecks = new HashMap<Integer, ConfigCheck>();
    private TaskScheduler taskScheduler;
    private ScheduleTaskCallback scheduleTaskCallback = new ScheduleTaskCallback();

    public Business(Context context) {
        synchronized (Business.class) {
            mContext = context;

            //初始化 计划任务
            taskScheduler = new TaskScheduler(mContext);

            commClient = new NetCommClient(context, taskScheduler);

            SharedPreferences prefs = mContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            String checkString = prefs.getString(PREF_KEY_CONFIG_CHECK_STR, "");

            if (StringUtils.isNotBlank(checkString)) {
                String[] ss = checkString.split("#");
                for (String s : ss) {
                    if (StringUtils.isNotBlank(s)) {
                        ConfigCheck cc = new ConfigCheck(s);
                        configChecks.put(cc.flag, cc);
                    }
                }
            }

            //监听sim卡,sim ready时读取IMEI和ICCID，启动 NetCommClient
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(ACTION_SIM_STATE_CHANGED);
            intentFilter.addAction("com.spde.sclauncher.shutdown_report");
            intentFilter.addAction(Intent.ACTION_BATTERY_LOW);
            mContext.registerReceiver(broadcastReceiver, intentFilter);

            commClient.addListener(this);
        }
    }

    public void setOnEventListener(OnBussinessEventListener onEventListener) {
        this.onEventListener = onEventListener;
    }

    public void netWatcherBroadCast(){
        commClient.netWatcherBroadCast();
    }

    public void onDestroy(){
        synchronized (Business.class) {
            if(commClient != null) {
                commClient.destroy();
                commClient = null;
            }
            if(taskScheduler != null) {
                taskScheduler.release();
                taskScheduler = null;
            }
            onEventListener = null;
            mContext.unregisterReceiver(broadcastReceiver);
            reportRequestQueue.clear();
            businessWorker.pause();
        }
    }

    private void notifyLogin(boolean result){
        if(onEventListener != null){
            onEventListener.onLoginStatusChanged(result);
        }
    }

    private void notifyUserRegiterRequired(){
        if(onEventListener != null){
            onEventListener.onUserRegiterRequired();
        }
    }

    private void notifyServerSms(String sms, boolean emergent, int showtimes, int showType, boolean flash, boolean ring, boolean vibrate){
        if(onEventListener != null){
            onEventListener.onSeverSmsShow(sms, emergent, showtimes, showType, flash, ring, vibrate);
        }
    }

    private void notifyDialSos(){
        if(onEventListener != null){
            onEventListener.onDialSos();
        }
    }

    private void notifyReboot(boolean recovery){
        if(onEventListener != null){
            onEventListener.onDoReboot(recovery);
        }
    }

    /** NetCommListener start */
    @Override
    public void onLoginStatusChanged(boolean loginOk) {
        notifyLogin(loginOk);
        if(loginOk){
            if(locationReportDelay.get() == null){
                scheduleLocation();//没有缓存 加一个计划发下一个位置上报
            }
            businessWorker.wakeUp(); //唤醒它处理待发送的请求
        }else{
            removeScheduledLocation();
        }
    }

    @Override
    public void onUserRegiterRequired() {
        notifyUserRegiterRequired();
    }

    @Override
    public void onSmsSendRequest(final String tag, String text, String toPort) {
        log.d("send <" + tag + "> sms : \"" + text + "\" to number:" + toPort);
        String SMS_SENT_ACTION = "com.spde.sclauncher.SMS_SENT_ACTION";
        PendingIntent smsIntent = PendingIntent.getBroadcast(mContext, 0, new Intent(SMS_SENT_ACTION), 0);
        mContext.registerReceiver(new BroadcastReceiver(){
            @Override
            public void onReceive(Context context, Intent intent) {
                if(getResultCode() == Activity.RESULT_OK) {
                    log.i("send <" + tag + "> sms ok");
                    commClient.reportSmsSent(tag, true);
                }else{
                    log.e("send <" + tag + "> sms failed");
                    commClient.reportSmsSent(tag, false);
                }
                mContext.unregisterReceiver(this);
            }
        }, new IntentFilter(SMS_SENT_ACTION));
        SmsManager.getDefault().sendTextMessage(toPort, null, text, smsIntent, null);
    }

    @Override
    public void onRemotePush(ISCMessage push) {
        log.i("onRemotePush:" + push);
        if(push instanceof GetLocationInfo) {
            //nmeaListener can be register in a thread, so we send it to handler
            businessWorker.replyLocationNow((GetLocationInfo) push);
        }else if(push instanceof RemoteOperation) {
            RemoteOperation operation = (RemoteOperation) push;
            final boolean toReboot = operation.isDoReboot();
            final boolean toRecovery = operation.isDoRecovery();
            CommonRsp rsp = new CommonRsp((IRequest) push);
            rsp.setStatus(0);
            IWriteFuture future = commClient.sendResponse(rsp);
            if(future != null) {
                future.setListener(new IoFutureListener<IWriteFuture>() {
                    @Override
                    public void onComplete(IWriteFuture future) {
                        log.d("send ok=" + future.isWritten() + ", e=" + future.getException());
                        if (toRecovery) {
                            notifyReboot(true);
                            return;
                        }
                        if (toReboot) {
                            notifyReboot(false);
                            return;
                        }
                    }
                });
            }
        }else if(push instanceof RequestCall) {
            CommonRsp rsp = new CommonRsp((IRequest) push);
            IWriteFuture future = commClient.sendResponse(rsp);
            if(future != null) {
                future.setListener(new IoFutureListener<IWriteFuture>() {
                    @Override
                    public void onComplete(IWriteFuture future) {
                        log.d("send ok=" + future.isWritten() + ", e=" + future.getException());
                        notifyDialSos();
                    }
                });
            }
        }else if(push instanceof ServerSMS){
            ServerSMS msg = (ServerSMS)push;
            String sms = msg.getSms();
            int showtimes = msg.getShowTimes();
            boolean emergent = msg.isEmergent();
            int showType = msg.getShowType();
            boolean flash = msg.isFlash();
            boolean ring = msg.isRing();
            boolean vibrate = msg.isVibrate();
            // if(showtimes > 0) {
                notifyServerSms(sms, emergent, showtimes, showType, flash, ring, vibrate);
            // }
            CommonRsp rsp = new CommonRsp((IRequest) push);
            IWriteFuture future = commClient.sendResponse(rsp);
            if(future != null) {
                future.setListener(new IoFutureListener<IWriteFuture>() {
                    @Override
                    public void onComplete(IWriteFuture future) {
                        log.d("send ok=" + future.isWritten() + ", e=" + future.getException());
                    }
                });
            }
        }else {
            dealOtherPush(push);
            CommonRsp rsp = new CommonRsp((IRequest) push);
            IWriteFuture future = commClient.sendResponse(rsp);
            if(future != null) {
                future.setListener(new IoFutureListener<IWriteFuture>() {
                    @Override
                    public void onComplete(IWriteFuture future) {
                        log.d("send ok=" + future.isWritten() + ", e=" + future.getException());
                    }
                });
            }
        }
    }

    @Override
    public void onLocalTimeCheck(int year, int month, int dayInMonth, int hourIn24, int minutes) {
        //TODO: 如果需要用服务器时间做校时，在这里实现
        Calendar c = Calendar.getInstance();
        c.set(year, month - 1, dayInMonth, hourIn24, minutes);
        long serverTime = c.getTimeInMillis();
        if (System.currentTimeMillis() < serverTime - SCConfig.TIME_CHECK_GATE * 60 * 1000) {
            SystemClock.setCurrentTimeMillis(serverTime);
            log.i("onLocalTimeCheck, set ok!");
        }
    }
    /** NetCommListener end */

    /** IoFutureListener<ResponseFuture> start */
    @Override
    public void onComplete(ResponseFuture future) {
        ISCMessage origReq = future.getRequest();
        ISCMessage response = future.getResponse();
        Throwable exception = future.getException();

        if(origReq instanceof GetButtons || origReq instanceof GetClassMode || origReq instanceof GetIncomingCall){
            dealConfigure(origReq, response, exception, future);
        }

        if(origReq instanceof AlarmPower){
            //AlarmPower 只处理低电
            dealLowBatteryRsp((AlarmPower) origReq, response, exception, future);
        }

        if(origReq instanceof ReportLocationInfo){
            dealLocationReportRsp(origReq, response, exception, future);
        }

        if(origReq instanceof ReportCrossBorder){
            dealReportCrossBorderRsp(origReq, response, exception, future);
        }

        if(origReq instanceof ReportCallLog){
            dealCallLogReportRsp(origReq, response, exception, future);
        }

        //超时计数，3个消息没有应答就重新登录
        if(exception != null && exception instanceof SocketTimeoutException){
            log.w("onComplete: " + origReq.getHeader().get$apiName() + ", seq=" + origReq.getHeader().get$sequence() + " read timeout");
            int count = timeoutCounter.incrementAndGet();
            if(count >= 3){
                log.e("onComplete:timeoutCounter = " + count + " rebuild session now!");
                boolean immediately = false; //TODO: check whether there'r something need to report
                commClient.forceRelogin(immediately);
                timeoutCounter.set(0);
            }else{
                log.w("onComplete:timeoutCounter = " + count);
            }
        }else if(response != null) {
            //log.d("onComplete [" + response.getHeader().toProtocolHeader() + "  ,  " + response.toString() + "]");
            timeoutCounter.set(0);
        }else{
            log.e("onComplete: " + origReq.getHeader().get$apiName() + ", seq=" + origReq.getHeader().get$sequence() + " faild:" + exception);
        }
    }
    /** IoFutureListener<ResponseFuture> end */

    private boolean isSimInfoReady(){
        if(StringUtils.isNoneBlank(LocalDevice.getInstance().getImei(), LocalDevice.getInstance().getIccid())){
            return true;
        }
        return false;
    }

    /** 只考虑了单sim卡 非热插拔sim卡 ，如果不是这种情况 自己改策略*/
    private void readSimInfo(){
        if(isSimInfoReady()){
            //非热插拔sim卡 只读一次就行了
            log.w("readSimInfo: IMEI and ICCID already existed!");
            return;
        }
        TelephonyManager telManager = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
        String imei = telManager.getDeviceId();
        String iccid = telManager.getSimSerialNumber();
        if(iccid != null){
            iccid = iccid.toLowerCase();  //其中有字母要转小写
        }
        LocalDevice.getInstance().setImei(imei);
        LocalDevice.getInstance().setIccid(iccid);
        log.d("readSimInfo: IMEI and ICCID :" + imei + "," + iccid);
    }

    private void readDumySimInfo(){
        //贵州测试卡 18296936571 可登录商用服务器
        LocalDevice.getInstance().setImei("867400020316677");
        LocalDevice.getInstance().setIccid("898600f2231930317938");
    }

    private boolean canDoConfigCheck(int flag){
        ConfigCheck cc = configChecks.get(flag);
        if(cc == null){
            cc = new ConfigCheck(flag);
            configChecks.put(cc.flag, cc);
            return true;
        }else{
            return cc.canDo();
        }
    }

    private void updateConfigCheck(int flag){
        synchronized (configChecks){ //这里要遍历map，锁一下
            Calendar calendar = Calendar.getInstance();
            ConfigCheck cc = configChecks.get(flag);
            cc.count ++;
            cc.dayInYear = calendar.get(Calendar.DAY_OF_YEAR);
            cc.year = calendar.get(Calendar.YEAR);

            String save = "";
            for(Map.Entry<Integer, ConfigCheck> entry:configChecks.entrySet()){
                ConfigCheck one = entry.getValue();
                save += one.toString() + "#";
            }
            if(save.endsWith("#")) save = save.substring(0, save.length() - 1);
            SharedPreferences prefs = mContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString(PREF_KEY_CONFIG_CHECK_STR, save);
            editor.commit();
        }
    }

    private void getButtonSetting(){
        if(!canDoConfigCheck(CHECK_CONFIG_BUTTONS)) return;
        GetButtons getButtons = new GetButtons();
        ResponseFuture rsp = commClient.sendRequest(getButtons);
        if(rsp == null){
            log.e("getButtonSetting send failed");
            return;
        }
        rsp.setListener(this);
    }

    private void getClassSetting(){
        if(!canDoConfigCheck(CHECK_CONFIG_CLASS)) return;
        GetClassMode getClassMode = new GetClassMode();
        ResponseFuture rsp = commClient.sendRequest(getClassMode);
        if(rsp == null){
            log.e("getClassSetting send failed");
            return;
        }
        rsp.setListener(this);
    }

    private void commitDelayReport(DelayReport delayReport){
        reportRequestQueue.add(delayReport);
        if(commClient.isLogin()){
            businessWorker.wakeUp();
        }
    }

    private void checkPowerOnReport(){
        if(reportPowerOn.compareAndSet(1, 2)) {
            AlarmPower alarmPower = new AlarmPower(4);
            DelayReport report = new DelayReport(alarmPower, -1L, 1);
            report.setPowerOnReport(true);
            commitDelayReport(report);
        }
    }

    private void getCallSetting(){
        if(!canDoConfigCheck(CHECK_CONFIG_CALL)) return;
        GetIncomingCall getIncomingCall = new GetIncomingCall();
        ResponseFuture rsp = commClient.sendRequest(getIncomingCall);
        if(rsp == null){
            log.e("getCallSetting send failed");
            return;
        }
        rsp.setListener(this);
    }

    private void doRetry(ISCMessage req, boolean retryEnable, int retryCount, int maxRetry){
        ResponseFuture rsp = commClient.sendRequest(req);
        if(rsp == null){
            log.e("doRetry send " + req.getClass().getSimpleName() + " failed");
            return;
        }
        log.w("doRetry: " + req.getClass().getSimpleName());
        if(retryEnable && maxRetry > 0) {
            rsp.setEnableRetry(retryEnable, maxRetry);
            rsp.setRetryCount(retryCount);
        }
        rsp.setListener(this);
    }

    private void dealLowBatteryRsp(AlarmPower req, ISCMessage response, Throwable exception, ResponseFuture future){
        if(exception != null && (exception instanceof WriteException) && req.isLowBattery()){
            if(future.retryCountIncrement()){
                doRetry(req, future.isEnableRetry(), future.getRetryCount(), future.getMaxRetry());
            }
        }
    }

    private void dealConfigure(ISCMessage req, ISCMessage response, Throwable exception, ResponseFuture future){
        if(response == null && exception != null){
            if(exception instanceof WriteException){
                log.e("dealConfigure:" + req.getHeader().get$apiName() + " write exception:" + exception.getMessage());
            }else if(exception instanceof SocketTimeoutException){
                if(future.retryCountIncrement()){
                    doRetry(req, future.isEnableRetry(), future.getRetryCount(), future.getMaxRetry());
                }
            }
        }else if(response != null) {
            if(response instanceof GetButtonsRsp){
                //收到回复更新ConfigCheck
                updateConfigCheck(CHECK_CONFIG_BUTTONS);
                GetButtonsRsp rsp = (GetButtonsRsp)response;
                int status = rsp.getStatus();
                if(status > 0){
                    log.e("GetButtonsRsp return an error status=" + status + " from cloud server");
                }else {
                    FixedNumberDataSource.getInstance().updateNumbers(rsp.getKeyMap());
                }
            }else if(response instanceof GetClassModeRsp){
                //收到回复更新ConfigCheck
                updateConfigCheck(CHECK_CONFIG_CLASS);
                GetClassModeRsp rsp = (GetClassModeRsp)response;
                int status = rsp.getStatus();
                if(status > 0){
                    log.e("GetClassModeRsp return an error status=" + status + " from cloud server");
                }else if(status == 0){//无设置
                    ClassModeDataSource.getInstance().reset();
                }else{
                    List<PeriodWeeklyOnOff> periods =  rsp.getPeriodList();
                    boolean isSosIncoming = rsp.isSosIncoming();
                    boolean isSosOutgoing = rsp.isSosOutgoing();
                    ClassModeDataSource.getInstance().update(isSosIncoming, isSosOutgoing, periods);
                }
            }else if(response instanceof GetIncomingCallRsp){
                //收到回复更新ConfigCheck
                updateConfigCheck(CHECK_CONFIG_CALL);
                GetIncomingCallRsp rsp = (GetIncomingCallRsp)response;
                int status = rsp.getStatus();
                if(status > 0){
                    log.e("GetIncomingCallRsp return an error status=" + status + " from cloud server");
                }else if(status == 0) {//无设置
                    WhiteListDataSource.getInstance().reset();
                }else{
                    List<IncomingCallSet> addlist = rsp.getAddPhones();
                    List<IncomingCallSet> dellist = rsp.getDeletePhones();
                    int limit = rsp.getLimitFlag();
                    String weeks = rsp.getWeeks();
                    WhiteListDataSource.getInstance().update(dellist, addlist, limit, weeks);
                }
            }else{
                assert false:"dealConfigure never deal unknown message:" + response.getClass().getSimpleName();
            }
        }
    }

    private void dealOtherPush(ISCMessage push){
        if(push instanceof SetButtons){
            FixedNumberDataSource.getInstance().updateNumbers(((SetButtons)push).getKeyMap());
        }else if(push instanceof SetClassMode){
            SetClassMode msg = (SetClassMode)push;
            List<PeriodWeeklyOnOff> periods =  msg.getPeriodList();
            boolean isSosIncoming = msg.isSosIncoming();
            boolean isSosOutgoing = msg.isSosOutgoing();
            ClassModeDataSource.getInstance().update(isSosIncoming, isSosOutgoing, periods);
        }else if(push instanceof SetIncomingCall){
            SetIncomingCall msg = (SetIncomingCall)push;
            List<IncomingCallSet> addlist = msg.getAddPhones();
            List<IncomingCallSet> dellist = msg.getDeletePhones();
            int limit = msg.getLimitFlag();
            String weeks = msg.getWeeks();
            WhiteListDataSource.getInstance().update(dellist, addlist, limit, weeks);
        }else if(push instanceof SetModel){
            SetModel msg = (SetModel)push;
            ProfileModeDataSource.getInstance().update(msg.isRing(),
                    msg.isIncomingForbidden(), msg.isOutgoingForbidden());
        }else if(push instanceof SetRegionalAlarm){
            SetRegionalAlarm msg = (SetRegionalAlarm)push;
            int operate = msg.getOperate();  //操作代码：1表示新增区域 2表示修改区域 3表示删除区域
            int opType = msg.getOpType();  //请求状态：1表示父亲卡 2表示母亲卡
            RegionLimit regionLimit = msg.getRegionLimit();
            if(operate == 3){
                GpsFenceDataSource.getInstance().remove(opType, regionLimit);
            }else {
                GpsFenceDataSource.getInstance().addOrUpdate(opType, regionLimit);
            }
        }
    }

    private void dealCallLogReportRsp(ISCMessage req, ISCMessage response, Throwable exception, ResponseFuture future){
        if(exception != null && (exception instanceof WriteException)){
            if(future.retryCountIncrement()){
                doRetry(req, future.isEnableRetry(), future.getRetryCount(), future.getMaxRetry());
            }
        }
    }

    private void dealLocationReportRsp(ISCMessage req, ISCMessage response, Throwable exception, ResponseFuture future){
        if(exception != null && (exception instanceof WriteException)){
            //位置上报写失败要重发，
            if(future.retryCountIncrement()){
                doRetry(req, future.isEnableRetry(), future.getRetryCount(), future.getMaxRetry());
            }
        }else{ //这里有肯能是应答超时，也有可能是收到应答
            // 应答超时的话 不一定表示服务器没收到，协议要求必须严格按照10分钟一个发送，否则会锁死，因此重发的风险更大，忽略应答超时
            scheduleLocation();//计划发下一个位置上报
        }
    }

    private void dealReportCrossBorderRsp(ISCMessage req, ISCMessage response, Throwable exception, ResponseFuture future){
        if(response == null && exception != null){
            log.i("ReportCrossBorder failed: exception=" + exception);
            if(future.retryCountIncrement()){
                log.i("ReportCrossBorder retry");
                doRetry(req, future.isEnableRetry(), future.getRetryCount(), future.getMaxRetry());
            }
        }else{
            log.i("ReportCrossBorder done");
        }
    }

    private void scheduleLocation(){
        log.d("scheduleLocation");
        int delaySec = commClient.getLocateRateSeconds();
        //提前开gps
        if(delaySec > GPS_PREPARE_SECONDS) {
            delaySec = delaySec - GPS_PREPARE_SECONDS;
        }
        if(taskScheduler != null) {
            SchedTask st = new SchedTask(ReportLocationInfo.NAME,
                    delaySec * 1000L, null, scheduleTaskCallback);
            if(nextLocationTask.compareAndSet(null, st)) {
                taskScheduler.offer(st);
            }
        }
    }

    private void removeScheduledLocation(){
        if(taskScheduler != null) {
            SchedTask st = nextLocationTask.getAndSet(null);
            if(st != null) {
                taskScheduler.remove(st);
            }
        }
    }

    /** 当前是否已登录(在线) */
    public boolean isLogin(){
        return commClient.isLogin();
    }

    /** 用户主动拨打SOS时 如未登录服务器 则立即登录 */
    public LoginFuture requestLoginByUser(){
        return commClient.requestLoginByUser();
    }

    /** 用户主动发起登录 如不再等待结果，需主动释放futrue
     *  否则。。。也不会怎么样 多一个object晚点释放而已
     * */
    public boolean removeLoginFuture(LoginFuture loginFuture){
        return commClient.removeLoginFuture(loginFuture);
    }

    /** 上报事件*/
    public ResponseFuture reportEvent(ISCMessage report){
        return commClient.sendRequest(report);
    }

    /** 上报通话记录*/
    public boolean reportCallLog(String phone, long startAtMillis, int durationSecs, boolean inOrOut){
        if(StringUtils.isBlank(phone)){
            return false;
        }
        long endAtMills = startAtMillis + (durationSecs * 1000L);
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(startAtMillis);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
        String startAt = sdf.format(calendar.getTime());
        calendar.setTimeInMillis(endAtMills);
        String endAt = sdf.format(calendar.getTime());
        ReportCallLog report = new ReportCallLog(phone, startAt, endAt, (int) durationSecs, inOrOut);
        DelayReport delayReport = new DelayReport(report, -1, 3);
        commitDelayReport(delayReport);
        return true;
    }

    public void resetFlagsOnPowerOn(){
        commClient.resetFlagsOnPowerOn();
    }
    /** 需要在登录后发送开机警报 */
    public void reportPowerOn(){
        if(reportPowerOn.compareAndSet(0, 1)){
            if(isSimInfoReady()){
                checkPowerOnReport();
            }
        }
    }

    // public void testGps(){
    //     WakeLock.getInstance().acquire();
    //     LocationDataSource.getInstance().openGps();
    //     LocationDataSource.getInstance().requestNMEAPeriodicUpdate(80, new IDataSourceCallBack() {
    //         @Override
    //             public void onComplete(IDataSourceCallBack wrapped, Object result, Exception exception) {
    //                 log.i("test gps result=" + result + ", exception=" + exception);
    //             }
    //     });
    // }

    private void reportLocation(int gpsSeconds, int wifiSeconds){
        //if(locationReporting.compareAndSet(false, true)){
            WakeLock.getInstance().acquire();
            LocationDataSource.getInstance().openGps();
            final int totalDelay = gpsSeconds + wifiSeconds;
            final int gpsDelay = gpsSeconds;
            final long timeMark = SystemClock.elapsedRealtime();
            final Map<String, Object> locationCache = new HashMap<String, Object>();
            LocationDataSource.getInstance().requestNMEAPeriodicUpdate(totalDelay, new IDataSourceCallBack() {
                boolean startwifi = false;

                @Override
                public void onComplete(IDataSourceCallBack wrapped, Object result, Exception exception) {
                    if (result != null) {
                        locationCache.put("nmea", result);
                    }
                    if (locationCache.get("nmea") == null) { //没有nmea数据
                        long consumedMills = SystemClock.elapsedRealtime() - timeMark;
                        if (!startwifi && (consumedMills >= gpsDelay * 1000L)) { //还剩20s 搜wifi
                            int wifiDelay = Math.max(totalDelay - (int) (consumedMills / 1000L) - 1, 10);
                            startwifi = true;
                            LocationDataSource.getInstance().requestWifiPeriodicUpdate(3, wifiDelay, new IDataSourceCallBack() {
                                @Override
                                public void onComplete(IDataSourceCallBack wrapped, Object result, Exception exception) {
                                    if (result != null) {
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
                                        locationCache.put("wifi", wifiList);
                                    }
                                    if(exception != null){
                                        log.i("reportLocation ex=" + exception.getMessage());
                                    }
                                }
                            });
                        }
                    }
                    if (exception != null) {//gps 超时
                        boolean needCheckFence = false;
                        ReportLocationInfo report = new ReportLocationInfo(null);
                        String nmea = (String) locationCache.get("nmea");
                        if (StringUtils.isNotBlank(nmea)) {
                            report.setNmea(nmea);
                            needCheckFence = true;
                        } else if (locationCache.get("wifi") != null) {
                            List<String> wifiList = (List<String>) locationCache.get("wifi");
                            report.setWifiList(wifiList);
                        }
                        LocationDataSource.LbsLocation lbs = LocationDataSource.getInstance().getLbsLocation();
                        String lbsString = (lbs == null) ? null : (lbs.getMcc() + "!" + lbs.getMnc() + "!" + lbs.getLac() + "!" + lbs.getCid() + "!" + lbs.getDb());
                        report.setLbs(lbsString);
                        log.d("toProtocolBody:" + report.toProtocolBody());
                        if (commClient.isLogin()) {
                            ResponseFuture rsp = commClient.sendRequest(report);
                            if (rsp == null) {
                                log.w("send ReportLocationInfo failed");
                                locationReportDelay.set(new DelayReport(report, -1, 3));
                            }else {
                                rsp.setListener(Business.this);
                            }
                        } else {
                            locationReportDelay.set(new DelayReport(report, -1, 3));
                        }
                        if(needCheckFence) businessWorker.checkGPSFence(nmea);
                        WakeLock.getInstance().release();
                    }
                }
            });
        //}
    }

    /************** inner class definition start *************/
    private class ScheduleTaskCallback implements SchedTask.ExpiredCallback{
        @Override
        public void onExpired(SchedTask task) {
            //ReportLocationInfo.NAME + "#Prepare",
            log.i("on ScheduledTask Expired: task = " + task.getName());
            if(task.getName().startsWith(ReportLocationInfo.NAME)){
                if(nextLocationTask.compareAndSet(task, null)) {
                    if (ClassModeDataSource.getInstance().isInClass()){
                        log.i("Do NOT report Location in class mode");
                        scheduleLocation();
                    }else{
                        //这时候离上报还有70s，50s用来搜gps， 20s用来搜wifi
                        reportLocation(GPS_PREPARE_SECONDS - 20, 20);
                    }
                }
            }
        }
    }

    private class BusinessWorker extends Handler {
        private final int BUSI_MSG_REPLY_LOCATION = 689;
        private final int BUSI_MSG_BUSINESS = 666;
        private final int BUSI_MSG_FENCE = 612;

        public void wakeUp(){
            removeMessages(BUSI_MSG_BUSINESS);
            sendEmptyMessage(BUSI_MSG_BUSINESS);
        }

        public void pause(){
            removeMessages(BUSI_MSG_BUSINESS);
        }

        public void replyLocationNow(GetLocationInfo request){
            WakeLock.getInstance().acquire();  //lock for msg loop
            obtainMessage(BUSI_MSG_REPLY_LOCATION, request).sendToTarget();
        }

        public void checkGPSFence(String nmeaString){
            WakeLock.getInstance().acquire();  //lock for msg loop
            obtainMessage(BUSI_MSG_FENCE, nmeaString).sendToTarget();
        }

        public String genTimsProtocolString(){
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
            Calendar calendar = Calendar.getInstance();
            return sdf.format(calendar.getTime());
        }

        @Override
        public void handleMessage(Message message) {
            if(message.what == BUSI_MSG_FENCE){
                String nmeaString = (String) message.obj;
                nmeaString += "T" + genTimsProtocolString();
                List<CrossFenceInfo> list = GpsFenceDataSource.getInstance().checkFence(nmeaString);
                for(CrossFenceInfo info:list){
                    ReportCrossBorder report = new ReportCrossBorder(info.getType(), info.isInFence(), info.getId());
                    report.setNmea(nmeaString);
                    DelayReport delayReport = new DelayReport(report, -1, 3);
                    commitDelayReport(delayReport);
                }
                WakeLock.getInstance().release(); //lock for msg loop
                return;
            }
            if(message.what == BUSI_MSG_REPLY_LOCATION){
                GetLocationInfo request = (GetLocationInfo) message.obj;
                if(request != null) {
                    WakeLock.getInstance().acquire(); //lock for wait location info
                    final GetLocationInfoRsp rsp = new GetLocationInfoRsp(request);
                    //至少3个wifi，总共50s时间，wifi搜10秒. 这里是立即上报 wifi优先，
                    IDataSourceCallBack icb = new IDataSourceCallBack() {
                        Map<String, Object> locationCache = new HashMap<String, Object>();
                        boolean wifiDone = false;
                        boolean gpsDone = false;
                        boolean sent = false;
                        boolean checkNmea = false;

                        @Override
                        public void onComplete(IDataSourceCallBack wrapped, Object result, Exception exception) {
                            log.d(wrapped.getClass().getSimpleName() + " onComplete result=" + result + " ,exception=" + exception);
                            if (wrapped instanceof WifiCallback) {
                                if (sent) {
                                    log.e("WifiCallback already sent, NEVER be here!!");
                                    return;
                                }
                                if (exception != null && (exception instanceof DataFailedException)) {
                                    log.e("WifiCallback failed e=" + exception.getMessage());
                                    wifiDone = true;
                                } else if (result != null) {
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
                                    locationCache.put("wifi", wifiList);
                                    wifiDone = true;
                                    log.i("WifiCallback data ready");
                                }
                                if (exception != null && (exception instanceof DataTimeOutException)) {
                                    log.w("WifiCallback timeout");
                                    wifiDone = true;
                                }
                            } else if (wrapped instanceof NmeaCallback) {
                                if (sent && checkNmea) {
                                    log.w("NmeaCallback already sent");
                                    LocationDataSource.getInstance().quit(wrapped);
                                    return;
                                }
                                if (result != null) {
                                    locationCache.put("nmea", result);
                                    gpsDone = true;
                                    log.i("NmeaCallback data ready");
                                } else if (exception != null) {//gps 超时
                                    gpsDone = true;
                                    String nmea = (String) locationCache.get("nmea");
                                    log.w("NmeaCallback timeout checkNmea=" + checkNmea + ",nmea=" + nmea);
                                    if(!checkNmea && StringUtils.isNotBlank(nmea)){
                                        checkNmea = true;
                                        businessWorker.checkGPSFence(nmea);
                                    }
                                }
                                if(sent){
                                    //log.i("NmeaCallback reply already sent checkNmea=" + checkNmea);
                                    return;
                                }
                            }
                            if (sent) {
                                log.e("GetLocationInfoRsp already sent, NEVER be here!!");
                                return;
                            }
                            if ((wifiDone && (null != locationCache.get("wifi")))  /* wifi done ok */
                                    || (wifiDone && gpsDone)   /* wifi done and gps done */
                                    ) {
                                List<String> wifiList = (List<String>) locationCache.get("wifi");
                                String nmea = (String) locationCache.get("nmea");
                                boolean needCheckFence = StringUtils.isNotBlank(nmea);

                                if (wifiList != null) {
                                    rsp.setWifiList(wifiList);
                                }
                                //nmea = (String) locationCache.get("nmea");
                                if (StringUtils.isNotBlank(nmea)) {
                                    rsp.setNmea(nmea);
                                }
                                LocationDataSource.LbsLocation lbs = LocationDataSource.getInstance().getLbsLocation();
                                String lbsString = (lbs == null) ? null : (lbs.getMcc() + "!" + lbs.getMnc() + "!" + lbs.getLac() + "!" + lbs.getCid() + "!" + lbs.getDb());
                                rsp.setLbs(lbsString);
                                rsp.reGenHeaderTime();
                                log.d("body:" + rsp.toProtocolBody());
                                IWriteFuture future = commClient.sendResponse(rsp);
                                if (future != null) {
                                    future.setListener(new IoFutureListener<IWriteFuture>() {
                                        @Override
                                        public void onComplete(IWriteFuture future) {
                                            log.d("send ok=" + future.isWritten() + ", e=" + future.getException());
                                            //如果没发成功要怎么处理？？
                                        }
                                    });
                                }
                                sent = true;
                                checkNmea = needCheckFence;
                                if(needCheckFence) businessWorker.checkGPSFence(nmea);
                                WakeLock.getInstance().release();//lock for wait location info
                            }
                        }
                    };
                    LocationDataSource.getInstance().requestWifiLocations(3, 40, icb);
                    LocationDataSource.getInstance().openGps();
                    LocationDataSource.getInstance().requestNMEAPeriodicUpdate(50, icb);
                }
                WakeLock.getInstance().release(); //lock for msg loop
                return;
            }
            if(message.what == BUSI_MSG_BUSINESS && commClient.isLogin()){
                DelayReport location = locationReportDelay.getAndSet(null);
                if(location != null){
                    reportRequestQueue.add(location);
                }
                if(!reportRequestQueue.isEmpty()) {
                    //如果是串行的就不能循环做，要在onComplete中监听前一个完成才能发下一个
                    for (; ; ) {
                        DelayReport report = reportRequestQueue.poll();
                        if (report == null)
                            break;
                        ISCMessage send = report.getMessage();
                        if (send == null) {
                            log.e("Queued msg is empty!!!");
                            continue;
                        }
                        if (report.isExpired()) {
                            log.w("Queued msg " + send.getClass().getSimpleName() + " is expired");
                            continue;
                        }
                        int retry = report.getRetry();
                        ResponseFuture rsp = commClient.sendRequest(send);
                        if (rsp == null) {
                            log.e("Queued msg " + send.getClass().getSimpleName() + " send failed");
                        } else {
                            if (retry > 0) {
                                rsp.setEnableRetry(true, retry);
                            }
                            rsp.setListener(Business.this);
                        }
                        if(report.isPowerOnReport()){
                            getButtonSetting();//GET_NORMAL_BUTTON
                            getClassSetting(); //GET_CLASS_MODEL
                            getCallSetting();//GET_INCOMING_CALL
                        }
                    }
                }
            }
        }
    }

    class SysIntentReceiver extends BroadcastReceiver{

        @Override
        public void onReceive(Context context, Intent intent) {
            if(intent.getAction().equals(ACTION_SIM_STATE_CHANGED)){
                log.i("SysIntentReceiver: " + ACTION_SIM_STATE_CHANGED);
                TelephonyManager telManager = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
                int state = telManager.getSimState();
                if(state == TelephonyManager.SIM_STATE_READY) {
                    //有卡就启动，网络有没有交给NetCommClient自己处理，
                    // 如果设备支持sim卡热插拔的话，这个机制也许要修改
                    readSimInfo();
                    if(isSimInfoReady()) {
                        commClient.startUp(); //startUp里面有保护，不用担心重复调用
                    }
                    checkPowerOnReport();
                }else if(state == TelephonyManager.SIM_STATE_ABSENT){ //不插卡可以走wifi，造一个假的sim卡imsi
                    readDumySimInfo();
                    if(isSimInfoReady()) {
                        commClient.startUp(); //startUp里面有保护，不用担心重复调用
                    }
                    checkPowerOnReport();
                }
            }else if(intent.getAction().equals("com.spde.sclauncher.shutdown_report")){
                String reason = intent.getStringExtra("reason");
                log.i("SysIntentReceiver:" + intent.getAction() + ",reason=" + reason);
                boolean batteryShutdown = StringUtils.equalsIgnoreCase(reason, "battery");
                if (commClient.isLogin()) {
                    AlarmPower powerOff = new AlarmPower(batteryShutdown ? 3 : 2);   //2=关机报警; 3=自动关机报警
                    if(batteryShutdown){
                        int percent = BatteryDataSource.getInstance().getBatteryPercent();
                        powerOff.setBatteryPercent(percent);
                    }
                    ResponseFuture rsp = commClient.sendRequestDirectly(powerOff);
                    if(rsp == null){
                        log.e("send AlarmPower(shutdown_report:" + reason + ") failed");
                        SystemProperties.set("sys.screport.marked", "true");
                        return;
                    }
                    rsp.setListener(new IoFutureListener<ResponseFuture>() {
                        @Override
                        public void onComplete(ResponseFuture future) {
                            log.i("DONE:: AlarmPower(shutdown_report) response=" + future.getResponse() + ", e=" + future.getException());
                            SystemProperties.set("sys.screport.marked", "true");
                        }
                    });
                }else{
                    log.e("send AlarmPower(shutdown_report:" + reason + ") ignored, NOT login");
                    SystemProperties.set("sys.screport.marked", "true");
                }
            }else if(intent.getAction().equals(Intent.ACTION_BATTERY_LOW)){
                log.i("SysIntentReceiver: ACTION_BATTERY_LOW");
                AlarmPower alarmPower = new AlarmPower(null);
                alarmPower.setEvent(1); //1=缺电报警
                int percent = BatteryDataSource.getInstance().getBatteryPercent();
                alarmPower.setBatteryPercent(percent);
                ResponseFuture rsp = commClient.sendRequest(alarmPower);
                if(rsp == null){
                    log.e("send AlarmPower failed");
                    return;
                }
                rsp.setEnableRetry(true, 3);
                rsp.setListener(Business.this);
            }
        }
    }

    class ConfigCheck{
        int flag;
        int count;
        int year;
        int dayInYear;

        public String toString(){
            return flag + "," + count + "," + year + "," + dayInYear;
        }

        boolean canDo(){
            if(count < 10) return true;
            Calendar calendar = Calendar.getInstance();
            int curyear = calendar.get(Calendar.YEAR);
            int curday = calendar.get(Calendar.DAY_OF_YEAR);
            if(year != curyear || dayInYear != curday){
                count = 0;
                return true;
            }
            return false;
        }

        ConfigCheck(int flag){
            this.flag = flag;
        }

        ConfigCheck (String saved){
            String[] ss = saved.split(",");
            flag = Integer.parseInt(ss[0]);
            count = Integer.parseInt(ss[1]);
            year = Integer.parseInt(ss[2]);
            dayInYear = Integer.parseInt(ss[3]);
        }
    }

}
