package com.spde.sclauncher.net;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;

import com.sonf.core.future.IOFuture;
import com.sonf.core.future.IWriteFuture;
import com.sonf.core.future.IoFutureListener;
import com.sonf.core.session.IOHandlerAdapter;
import com.sonf.core.session.IOSession;
import com.sonf.core.session.IdleStatus;
import com.sonf.core.write.WriteException;
import com.sonf.future.ConnectFuture;
import com.sonf.nio.NioChannelController;
import com.sonf.nio.NioSession;
import com.sonf.nio.NioSocketConfig;
import com.spde.sclauncher.DataSource.ClassModeDataSource;
import com.spde.sclauncher.DataSource.FixedNumberDataSource;
import com.spde.sclauncher.DataSource.GpsFenceDataSource;
import com.spde.sclauncher.DataSource.ProfileModeDataSource;
import com.spde.sclauncher.DataSource.WhiteListDataSource;
import com.spde.sclauncher.net.codec.AesCipherFilter;
import com.spde.sclauncher.net.codec.SCProtocolCodecFilter;
import com.spde.sclauncher.net.message.GZ.*;
import com.spde.sclauncher.net.message.IRequest;
import com.spde.sclauncher.net.message.IResponse;
import com.spde.sclauncher.net.message.ISCMessage;
import com.spde.sclauncher.net.message.Type;
import com.spde.sclauncher.net.message.UnknownMessage;
import com.spde.sclauncher.net.message.UnknownRsp;
import com.spde.sclauncher.util.SchedTask;
import com.spde.sclauncher.util.TaskScheduler;
import com.spde.sclauncher.util.WakeLock;
import com.yynie.myutils.Logger;
import com.yynie.myutils.StringUtils;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.spde.sclauncher.SCConfig.PERMANENT_FILE;
import static com.spde.sclauncher.SCConfig.SERIAL_REQ_LIMIT;
import static com.spde.sclauncher.SchoolCardPref.*;
import static com.spde.sclauncher.SCConfig.SERVER_ADDRESS;
import static com.spde.sclauncher.SCConfig.CLOSE_NETWORK_SESSION;
/**
 * 这里面处理 登录，心跳，以及其他周期性数据上报(有些需要从外部得到数据)
 * 服务器下发数据 如果不需要通知外部模块 也在这里处理
 * 需要通知外部的，通过注册监听来实现
 * */
public class NetCommClient {
    private final Logger log = Logger.get(NetCommClient.class, Logger.Level.INFO);
    private final Pattern timePattern = Pattern.compile("(\\d{4})(\\d{2})(\\d{2})(\\d{2})(\\d{2})"); //一个解析服务器时间的正则
    /** 默认值 */
    private final String MENUFACTURE_TAG = "zywl";
    private final String AES_IV = "0102030405060708";
    private final String AES_KEY = "gzxjy201805sdrgz";
    public static final String PROTOCOL_VERSION = "171"; //终端软件协议版本

    public final static int DEFAULT_HEARTBEAT_DURATION_SEC = 5 * 60;  //心跳默认10分钟，登录后5分钟发第一个心跳
    private final int DEFAULT_LOCATE_DURATION_SEC = 10 * 60; //位置上报默认10分钟，需提前准备数据

    /** 登录成功后第一次心跳延时5分钟 */
    private final long DELAY_FIRST_HEARTBEAT_MS = 5 * 60 * 1000L;
    /** 发送注册短信后延时2分钟尝试再次登录 */
    private final long DELAY_AFTER_SMS_MS = 2 * 60 * 1000L;
    /** 等待应答 40 秒 */
    public static final long RESP_DELAY_MS = 45 * 1000L;

    /** 参数 */
    private AtomicReference<String> serverRef = new AtomicReference<String>();
    private volatile int heartBeatSeconds;
    private volatile int locateRateSeconds;

    private Context context;
    private NioChannelController controller;
    private KeepAliveFilter keepAliveFilter;
    private List<INetCommListener> listenerList = new CopyOnWriteArrayList<INetCommListener>();
    private volatile boolean started;
    private NetReceiver netReceiver;
    private ScheduleTaskCallback scheduleTaskCallback = new ScheduleTaskCallback();
    private AtomicReference<IOSession> sessionRef = new AtomicReference<IOSession>();
    private Worker worker = new Worker();  //UI 线程工作的Handler
    private Map<String , WrappedMessage> waitRspMap = new ConcurrentHashMap<String , WrappedMessage>();
    private Queue<WrappedMessage> waitRspQueue = new ConcurrentLinkedQueue<WrappedMessage>();
    private Queue<WrappedMessage> writeFutureQueue = new ConcurrentLinkedQueue<WrappedMessage>();
    private SerialRequesQueue serialRequesQueue = (SERIAL_REQ_LIMIT)?new SerialRequesQueue():null;
    private volatile boolean loginOk = false;
    private AtomicBoolean loginScheduled = new AtomicBoolean(false);  //用来保护login不重复
    private volatile boolean needRegister = false;
    /** 要求重启机器后重置的变量 要写入 SharedPreferences ,app重启后读出。 在系统重启后清零
     * 考虑 luncher 能稳定的话，就不用这么麻烦  */
    private volatile int smsTryCount = 0;  //注册短信最多发10次 重启后恢复计数
    private volatile boolean frozen = false; //登录返回状态2时 冻结 重启后恢复
    private TaskScheduler taskScheduler;

    /******** */
    public NetCommClient(Context context, TaskScheduler taskScheduler){
        synchronized (NetCommClient.class){
            createController();
            init(context);
            this.taskScheduler = taskScheduler;
        }
    }

    private void createController(){
        /** 创建controller。
         *  默认构造器 将使用 Executors.newCachedThreadPool() 线程池，这是内存比较优化的选择。
         */
        log.i("Your CPU cores : " + Runtime.getRuntime().availableProcessors());
        controller = new NioChannelController();
        /** 做些配置 */
        NioSocketConfig config = (NioSocketConfig) controller.getConfig();
        config.setConnectTimeoutMs(60 * 1000L); //连接超时，默认就是60秒，应该够了
        /** !! nio socket 不要设置SO_LINGER, 据说会close不掉，在 www.stackoverflow.com上 有人踩过坑*/

        /** READER_IDLE 用来做心跳 和 心跳响应超时检测，时间粒度越小越精确。
         *  项目要求的心跳是分钟级的，我们定的请求应答超时是40s，所以用20s时间粒度是合适的。
         */
        config.setIdleTimeInMillis(IdleStatus.READER_IDLE, 20 * 1000L);

        config.setWriteTimeoutInMillis(40 * 1000L); //写超时
    }

    private void init(Context context){
        this.context = context;
        String server = readServerAddress();
        serverRef.set(server);
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        heartBeatSeconds = prefs.getInt(PREF_KEY_HEARTBEAT, DEFAULT_HEARTBEAT_DURATION_SEC);
        locateRateSeconds = prefs.getInt(PREF_KEY_LOCRATE, DEFAULT_LOCATE_DURATION_SEC);

        smsTryCount = prefs.getInt(PREF_KEY_REGISTER_SMS_COUNT, 0);
        frozen = prefs.getBoolean(PREF_KEY_FROZEN_FLAG, false);

        /** 自己定义的通讯协议解析链，一个负责AES加解密， 一个负责明文协议解析 */
        AesCipherFilter aesFilter = new AesCipherFilter();
        aesFilter.setIV(AES_IV);
        aesFilter.setKEY(AES_KEY);
        aesFilter.setMTAG(MENUFACTURE_TAG);
        aesFilter.setVER(PROTOCOL_VERSION);
        //屏掉的这几个都是默认值
//        aesFilter.setMode("CBC");
//        aesFilter.setPadding("PKCS5Padding");
//        aesFilter.setCharset(Charset.forName("UTF-8"));
        controller.getFilterChainBuilder().add("aes", aesFilter);

        //设置报文头的格式，现在用的是贵州版的，如果要做其他版本需要自己定义这个类
        SCProtocolCodecFilter scFilter = new SCProtocolCodecFilter();
        scFilter.setHeaderType(GZProtocolHeader.class);
        //注册报文体 也是贵州版的
        registerProtocolMessageTypes(scFilter);
        controller.getFilterChainBuilder().add("schoolcard", scFilter);

        /** 贵州版要求登录后5分钟发第一个心跳，这个需要手动发，我用worker调度。
         * 之后的心跳是根据链路上有无数据来决定的，可以构造一个Filter处理 */
        keepAliveFilter = new KeepAliveFilter();
        keepAliveFilter.setRateMs(heartBeatSeconds * 1000L);
        keepAliveFilter.setResponseTimeOutMs(RESP_DELAY_MS);
        controller.getFilterChainBuilder().add("heartbeat", keepAliveFilter); //心跳数据比较简单的，直接用filter做心跳

        /** ！！！！！
         * 如果有多个会话同时存在于这个 controller 上，他们是共用filter链的！！！！！！！！！！！！
         * 所以如果你有什么session私有的数据，请用 session.setAttribute(...), 千万别用filter实例的私有成员变量。
         * 除非你确定这些数据对每个session都是一样的，比如说一些通用的配置。
         * 虽然目前我们只会同时存在一个会话，但建议还是按原则来做，免点以后扩展时麻烦。
         * */

        controller.setHandler(new CommHandler()); //设置handler监听: 1发送成功，2接收到数据，3读超时
    }

    public void resetFlagsOnPowerOn(){
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

        smsTryCount = 0;
        frozen = false;
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(PREF_KEY_REGISTER_SMS_COUNT, smsTryCount);
        editor.putBoolean(PREF_KEY_FROZEN_FLAG, false);
        editor.commit();
    }

    private void registerProtocolMessageTypes(SCProtocolCodecFilter filter){
        filter.clearMessageMap();

        /** 这里只需要注册从服务器发到客户端的消息，
         * TYPE_UPSTREAM_RSP 是服务器的应答， YPE_DOWNSTREAM_REQ 是服务器的推送*/
        /** 上行消息的应答 */
        filter.registerMessageType(DeviceLoginRsp.NAME, DeviceLoginRsp.TYPE, DeviceLoginRsp.class);
        filter.registerMessageType(GetButtonsRsp.NAME, GetButtonsRsp.TYPE, GetButtonsRsp.class);
        filter.registerMessageType(GetClassModeRsp.NAME, GetClassModeRsp.TYPE, GetClassModeRsp.class);
        filter.registerMessageType(GetIncomingCallRsp.NAME, GetIncomingCallRsp.TYPE, GetIncomingCallRsp.class);

        /** 下行消息 */
        filter.registerMessageType(SetIncomingCall.NAME, SetIncomingCall.TYPE, SetIncomingCall.class);
        filter.registerMessageType(SetButtons.NAME, SetButtons.TYPE, SetButtons.class);
        filter.registerMessageType(SetServerInfo.NAME, SetServerInfo.TYPE, SetServerInfo.class);
        filter.registerMessageType(GetLocationInfo.NAME, GetLocationInfo.TYPE, GetLocationInfo.class);
        filter.registerMessageType(SetLocationFrequency.NAME, SetLocationFrequency.TYPE, SetLocationFrequency.class);
        filter.registerMessageType(RemoteOperation.NAME, RemoteOperation.TYPE, RemoteOperation.class);
        filter.registerMessageType(SetRegionalAlarm.NAME, SetRegionalAlarm.TYPE, SetRegionalAlarm.class);
        filter.registerMessageType(SetHeartBeat.NAME, SetHeartBeat.TYPE, SetHeartBeat.class);
        filter.registerMessageType(SetModel.NAME, SetModel.TYPE, SetModel.class);
        filter.registerMessageType(RequestCall.NAME, RequestCall.TYPE, RequestCall.class);
        filter.registerMessageType(SetClassMode.NAME, SetClassMode.TYPE, SetClassMode.class);
        filter.registerMessageType(ServerSMS.NAME, ServerSMS.TYPE, ServerSMS.class);

        /** 通用应答 */
        filter.registerMessageType(CommonRsp.class.getSimpleName(), Type.TYPE_UPSTREAM_RSP, CommonRsp.class);

        /** 识别不出来的 也放一个兜底的 */
        filter.registerMessageType(UnknownMessage.class.getSimpleName(), UnknownMessage.TYPE, UnknownMessage.class);
        filter.registerMessageType(UnknownRsp.class.getSimpleName(), UnknownRsp.TYPE, UnknownRsp.class);
    }

    public void addListener(INetCommListener listener){
        if(listener != null){
            listenerList.add(listener);
        }
    }

    public void removeListener(INetCommListener listener){
        if(listener != null){
            listenerList.remove(listener);
        }
    }

    public void startUp(){
        if(started){
            log.i("already start!");
            return;
        }
        //注册监听网络状态，网络ok就开始登录，否则等待网络连接通知
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        netReceiver = new NetReceiver();
        context.registerReceiver(netReceiver, intentFilter);

        started = true;
    }

    public void destroy(){
        //注销监听
        synchronized (NetCommClient.class) {
            if (netReceiver != null) {
                context.unregisterReceiver(netReceiver);
                netReceiver = null;
            }
            worker.quit();
            waitRspMap.clear();
            waitRspQueue.clear();
            writeFutureQueue.clear();
            sessionRef.set(null);
            listenerList.clear();
            loginScheduled.set(false);
            taskScheduler = null;

            //销毁controller
            if (controller != null) {
                controller.dispose();
                controller = null;
            }
        }
    }

    public int getLocateRateSeconds() {
        return locateRateSeconds;
    }

    public void reportSmsSent(String tag, boolean ok){
        if(tag.equals("REGISTER")){
            if(ok) {
                smsTryCount ++;
                savePreference(PREF_KEY_REGISTER_SMS_COUNT, smsTryCount);
                scheduleLoginAfterRegisterSMS();
            }else{
                log.e(" ~O~!  REGISTER SMS sent failed, How dose it happen? what can i do for that!!!!");
            }
        }
    }

    private void savePreference(String key, Object value){
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        if(value instanceof Integer) {
            editor.putInt(key, (Integer)value);
        }else if(value instanceof Boolean){
            editor.putBoolean(key, (Boolean) value);
        }else{
            editor.putString(key, (String) value);
        }
        editor.commit();
    }

    private boolean isSessionExist(){
        return (sessionRef.get() != null);
    }

    private boolean isNetworkAvailable(){
        ConnectivityManager connManager = (ConnectivityManager)NetCommClient.this.context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = connManager.getActiveNetworkInfo();
        if(info != null && info.isConnected()) {
            log.i("isNetworkAvailable: "+ info.getTypeName() + " network ok.");
            return true;
        } else {
            log.i("isNetworkAvailable: network not available");
            return false;
        }
    }

    private void startUpSession(){
        if(CLOSE_NETWORK_SESSION){
            log.w("startUpSession CLOSE_NETWORK_SESSION = true, it's for TEST and NO network bussiness will be start!");
            return;
        } 
        if(frozen){
            log.w("startUpSession but frozen = true, it will be reset after next reboot");
            return;
        }
        synchronized (sessionRef){
            if(sessionRef.get() != null)
                return;
            WakeLock.getInstance().acquire(); //发起连接时拿锁
            String[] spilts = serverRef.get().split(":");
            String host = spilts[0];
            int port = Integer.parseInt(spilts[1]);
            log.i("startUpSession: server is " + host + " : " + port);
            NioSession s = controller.buildSession(host, port); /* 创建会话 */
            sessionRef.set(s);//保存会话实例
            loginOk = false;
            IOFuture future = sessionRef.get().connect(); //发起连接
            future.setListener(new IoFutureListener() {
                @Override
                public void onComplete(IOFuture future) {
                    boolean ok = ((ConnectFuture)future).isConnected();
                    if(ok){
                        log.i("connect ok");
                        //连接成功 发送登录
                        send(new DeviceLogin(), null);
                    }else{
                        Throwable e = future.getException();
                        log.e("connect fialed:" + e.getMessage());
                        sessionRef.set(null); //连接失败 清掉
                        //下一个心跳时间重试
                        scheduleNextLogin();
                        WakeLock.getInstance().release();
                    }
                }
            });
        }
    }

    private void scheduleNextLogin(){
        if(loginScheduled.compareAndSet(false, true)) {
            if(needRegister || frozen){ //需要注册，注册成功后再试, frozen 下次重启后复位
                loginScheduled.set(false);
                return;
            }
            if(taskScheduler != null) {
                taskScheduler.offer(
                        new SchedTask(DeviceLogin.NAME,
                                DELAY_FIRST_HEARTBEAT_MS, null, scheduleTaskCallback)
                );
            }
        }
    }

    private void scheduleLoginAfterRegisterSMS(){
        if(loginScheduled.compareAndSet(false, true)) {
            if(frozen){ //frozen 下次重启后复位
                loginScheduled.set(false);
                return;
            }
            if(needRegister && (taskScheduler != null)){
                taskScheduler.offer(
                        new SchedTask(DeviceLogin.NAME,
                                DELAY_AFTER_SMS_MS, null, scheduleTaskCallback));
                needRegister = false;
            }else{
                loginScheduled.set(false);
            }
        }
    }

    private void delayToLogin(long mills){
        if(loginScheduled.compareAndSet(false, true)) {
            worker.delayToLogin(mills);
        }
    }

    private void notifyLogin(boolean result){
        for(INetCommListener l:listenerList){
            l.onLoginStatusChanged(result);
        }
    }

    private void notifySmsRequest(String tag, String text, String port){
        for(INetCommListener l:listenerList){
            l.onSmsSendRequest(tag, text, port);
        }
    }

    private void notifyLocalTimeCheck(int year, int month, int dayInMonth, int hourIn24, int minute){
        for(INetCommListener l:listenerList){
            l.onLocalTimeCheck(year, month, dayInMonth, hourIn24, minute);
        }
    }

    private void notifyRemotePush(ISCMessage push){
        for(INetCommListener l:listenerList){
            l.onRemotePush(push);
        }
    }

    private IWriteFuture send(ISCMessage message, ResponseFuture rspFuture){
        if(sessionRef.get() != null && (sessionRef.get().isReady() || sessionRef.get().isConnecting())){
            WrappedMessage wrapped = new WrappedMessage(message);
            if(wrapped.isRequest()) {
                wrapped.setResponseFuture(rspFuture);
                wrapped.start2waitResponse();
                waitRspMap.put(wrapped.getKey(), wrapped);
            }
            IWriteFuture wf = sessionRef.get().write(message);
            wrapped.setWriteFuture(wf);
            writeFutureQueue.add(wrapped);

            worker.forceWakeUp();
            return wf;
        }
        return null;
    }

    private void closeSession(boolean now){
        IOSession session = sessionRef.getAndSet(null);
        if(session != null){
            session.closeNow();
        }
        loginOk = false;
        notifyLogin(false);
    }

    public void forceRelogin(boolean immediately){
        closeSession(true);
        delayToLogin(immediately?1000L:15 * 1000L);
    }

    public boolean isLogin(){
        IOSession session = sessionRef.get();
        return (loginOk && session != null &&  session.isReady());
    }

    public ResponseFuture sendRequest(ISCMessage message){
        if(!(message instanceof IRequest)){
            throw new RuntimeException("Please send IRequest message via sendRequest() method.");
        }
        IOSession session = sessionRef.get();
        if(session == null || !session.isReady() || !loginOk){
            log.e("sendRequest failed! session is not available: loginOk = " + loginOk);
            return null;
        }
        ResponseFuture responseFuture;
        if(serialRequesQueue != null){
            responseFuture = new SerialResponseFuture(session, message);
            if(!serialRequesQueue.add(responseFuture)){
                responseFuture.setException(new WriteException("Failed to add into Request Queue"));
            }
        }else {
            responseFuture = new ResponseFuture(session, message);
            IWriteFuture writeFuture = send(message, responseFuture);
            if (writeFuture == null) {
                return null;
            }
            responseFuture.setWriteFuture(writeFuture);
        }
        return responseFuture;
    }

    class SerialResponseFuture extends ResponseFuture{
        private IoFutureListener<SerialResponseFuture> serialListener;
        public SerialResponseFuture(IOSession session, ISCMessage request) {
            super(session, request);
        }

        @Override
        public boolean setValue(Object newValue) {
            boolean set = super.setValue(newValue);
            if(set){
                notifySerialListener();
            }
            return set;
        }

        public void setSerialListener(IoFutureListener<SerialResponseFuture> serialListener) {
            this.serialListener = serialListener;
            if (isDone()) {
                notifySerialListener();
            }
        }

        public void removeSerialListener() {
            this.serialListener = null;
        }

        private void notifySerialListener() {
            if (this.serialListener != null) {
                this.serialListener.onComplete(this);
            }
        }
    }

    class SerialRequesQueue extends ConcurrentLinkedQueue<ResponseFuture> implements IoFutureListener<SerialResponseFuture> {
        private Object lock = new Object();
        @Override
        public boolean add(ResponseFuture rspF){
            if(rspF == null) return false;
            if(!(rspF instanceof SerialResponseFuture)) return false;

            log.i("SerialRequesQueue add " + rspF.getRequest().getClass().getSimpleName());
            synchronized (lock){
                boolean added;
                if(isEmpty()){
                    added =  super.add(rspF);
                    log.i("SerialRequesQueue added in empty queue =" + added);
                    if(added){
                        IWriteFuture writeFuture = send(rspF.getRequest(), rspF);
                        if (writeFuture == null) {
                            rspF.setException(new WriteException("Write Failed "));
                            remove(rspF);
                            added = false;
                            log.i("SerialRequesQueue send now failed");
                        }
                        log.i("SerialRequesQueue send now ok");
                        rspF.setWriteFuture(writeFuture);
                        ((SerialResponseFuture)rspF).setSerialListener(this);
                    }
                }else{
                    added =  super.add(rspF);
                    log.i("SerialRequesQueue added =" + added);
                }
                return added;
            }
        }

        @Override
        public void onComplete(SerialResponseFuture serialResponseFuture) {
            synchronized (lock){
                SerialResponseFuture rspF = (SerialResponseFuture) this.poll();
                if(rspF == null){
                    log.e("SerialRequesQueue onComplete queue is empty");
                    return;
                }
                if(rspF != serialResponseFuture){
                    log.e("SerialRequesQueue onComplete rspF not Match");
                }else{
                    rspF.removeSerialListener();
                    for(;;) {
                        SerialResponseFuture next = (SerialResponseFuture) this.peek();
                        log.i("SerialRequesQueue onComplete peek next = " + next);
                        if (next != null) {
                            IWriteFuture writeFuture = send(next.getRequest(), next);
                            if (writeFuture == null) {
                                next.setException(new WriteException("Write Failed "));
                                remove(next);
                                log.i("SerialRequesQueue send next failed");
                            }else {
                                log.i("SerialRequesQueue send next ok");
                                next.setWriteFuture(writeFuture);
                                next.setSerialListener(this);
                                break;
                            }
                        }else{
                            break;
                        }
                    }
                }
            }
        }
    }

    public IWriteFuture sendResponse(ISCMessage response){
        if(!(response instanceof IResponse)){
            throw new RuntimeException("Please send IResponse message via sendResponse() method.");
        }
        IOSession session = sessionRef.get();
        if(session == null || !session.isReady() || !loginOk){
            log.e("sendResponse failed! session is not available: loginOk = " + loginOk);
            return null;
        }
        return send(response, null);
    }

    private void dealLoginResponse(DeviceLoginRsp resp){
        if(resp == null){
            log.i("dealLoginResponse: timeout");
            closeSession(true);
            scheduleNextLogin();
            return;
        }
        if(resp.getStatus() == 0 && StringUtils.equals(resp.getSmsPort(), "0") && !resp.isNeedSms()){
            log.i("dealLoginResponse: login ok");
            loginOk = true;
            notifyLogin(true);
            timeSynchonize(resp.getHeader().get$time());
            /** 贵州版要求登录后5分钟发第一个心跳，这个需要手动发。之后的心跳就交给KeepAliveFilter处理 */
            worker.scheduleFirstHeatBeat();
        }else if(resp.getStatus() == 0 && resp.isNeedSms()){
            log.i("dealLoginResponse: need sms register");
            String port = resp.getSmsPort().trim();
            closeSession(true);
            clearUserDatas();
            needRegister = true;
            if(smsTryCount < 10){
                String text = LocalDevice.getInstance().getImei() + "@" + LocalDevice.getInstance().getIccid();
                notifySmsRequest("REGISTER", text, port);
                //发短信  IMEI@ICCID
            }else{
                frozen = true;
                savePreference(PREF_KEY_FROZEN_FLAG, frozen);
            }
        }else if(resp.getStatus() == 1){
            log.i("dealLoginResponse: status = 1, failed, try again at heartbeat delay");
            closeSession(true);
            scheduleNextLogin();
        }else if(resp.getStatus() == 2){
            log.i("dealLoginResponse: status = 2, failed");
            closeSession(true);
            frozen = true;
            savePreference(PREF_KEY_FROZEN_FLAG, frozen);
        }
    }

    private void clearUserDatas(){
        FixedNumberDataSource.getInstance().restore();
        ClassModeDataSource.getInstance().restore();
        WhiteListDataSource.getInstance().restore();
        ProfileModeDataSource.getInstance().restore();
        GpsFenceDataSource.getInstance().restore();
    }

    private void receivedResponse(ISCMessage resp){
        log.d("receivedResponse: [" + resp.getHeader().toProtocolHeader() + "  ,  " + resp.toString() + "]");
        String key = resp.getHeader().get$apiName();//不使用流水号  + resp.getHeader().get$sequence();
        WrappedMessage wait = waitRspMap.remove(key);
        log.d("receivedResponse: remove " + key + " from waitRspMap, waitRspMap size=" + waitRspMap.size());
        if(wait != null){
            log.d("receivedResponse: wait=" + wait);
            if(wait.isLoginMessage()){
                dealLoginResponse((DeviceLoginRsp) resp);
            }else{
                if(wait.responseFuture != null){
                    wait.responseFuture.setResponse(resp);
                }
            }
        }else{
            log.w("receivedResponse: wait=" + wait);
        }

        //利用心跳应答做时间同步
//        if((resp instanceof CommonRsp) &&
////            (StringUtils.equals(resp.getHeader().get$apiName(), HeartBeat.NAME))){
////            timeSynchonize(resp.getHeader().get$time());
////        }
    }

    private void timeSynchonize(String yyyyMMddHHmmss){
        Matcher matcher = timePattern.matcher(yyyyMMddHHmmss);
        if (matcher.find()) {
            String syear = matcher.group(1);
            String smon = matcher.group(2);
            String sday = matcher.group(3);
            String shour = matcher.group(4);
            String smin = matcher.group(5);
            int year = Integer.parseInt(syear);
            int month = Integer.parseInt(smon);
            int dayInMonth = Integer.parseInt(sday);
            int hourIn24 = Integer.parseInt(shour);
            int minute = Integer.parseInt(smin);
            notifyLocalTimeCheck(year, month, dayInMonth, hourIn24, minute);
        }
    }

    private void pushServerSet(SetServerInfo msg){
        String addr = msg.getServerAddr();
        int port = msg.getServerPort();
        if(StringUtils.isNotBlank(addr) && addr.length() > 7){
            if(StringUtils.equalsIgnoreCase("http://", addr.substring(0, 7))){
                addr = addr.substring(7);
            }
        }
        boolean setOk = false;
        if(StringUtils.isNotBlank(addr) || port > 0){
            setOk = saveServerAddress(addr + ":" + port);
        }

        CommonRsp rsp = new CommonRsp(msg);
        rsp.setStatus(setOk ? 0 : 1); //应答状态(0=设置成功;1=设置异常;)
        send(rsp, null);
    }

    private boolean saveServerAddress(String addrAndPort){
        FileWriter writer = null;
        try {
            writer = new FileWriter(PERMANENT_FILE);
            writer.write(addrAndPort);
            serverRef.set(addrAndPort);
            log.i("saveServerAddress : " + addrAndPort);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if(writer != null){
                try {
                    writer.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return false;
    }

    private String readServerAddress(){
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(PERMANENT_FILE));
            String server = reader.readLine();
            return server;
        } catch (FileNotFoundException e) {
            log.i("readServerAddress : not found " + PERMANENT_FILE);
            return SERVER_ADDRESS;
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if(reader != null){
                try {
                    reader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return SERVER_ADDRESS;
    }

    private void pushLocationFrequency(SetLocationFrequency msg){
        int min = msg.getLocateFreqMinutes();
        locateRateSeconds = min * 60;
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(PREF_KEY_LOCRATE, locateRateSeconds);
        boolean setOk = editor.commit();
        CommonRsp rsp = new CommonRsp(msg);
        rsp.setStatus(setOk ? 0 : 1);  //应答状态(0=设置成功;1=设置异常;)
        send(rsp, null);
    }

    private void pushHeartBeat(SetHeartBeat msg){
        int min = msg.getHeartBeatMinutes();
        heartBeatSeconds = min * 60;
        keepAliveFilter.setRateMs(heartBeatSeconds * 1000L);
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(PREF_KEY_HEARTBEAT, heartBeatSeconds);
        boolean setOk = editor.commit();
        CommonRsp rsp = new CommonRsp(msg);
        rsp.setStatus(setOk ? 0 : 1); //应答状态(0=设置成功;1=设置异常;)
        send(rsp, null);
    }

    private void dealRspWritten(WrappedMessage wrapped, boolean written){
        //只处理NetCommClient自己发的RSP, 这三个消息应该都不关心应答有没有发送成功吧
        if(StringUtils.equals(wrapped.getMessageName(),SetServerInfo.NAME)){
            log.d("dealRspWritten: Response to " + SetServerInfo.NAME + " sent " + (written?"ok":"failed"));
            if(wrapped.message instanceof CommonRsp){
                CommonRsp rsp = (CommonRsp) wrapped.message;
                if(rsp.getStatus() == 0){
                    //推送服务器地址 应答发送后 断线重连
                    forceRelogin(false);
                }else{
                    log.e("dealRspWritten for " + SetServerInfo.NAME + " set server not ok, do nothing");
                }
            }else{
                log.e("dealRspWritten for " + SetServerInfo.NAME + " message type error!");
            }

        }else if(StringUtils.equals(wrapped.getMessageName(),SetLocationFrequency.NAME)){
            log.d("dealRspWritten: Response to " + SetLocationFrequency.NAME + " sent " + (written?"ok":"failed"));
        }else if(StringUtils.equals(wrapped.getMessageName(),SetHeartBeat.NAME)){
            log.d("dealRspWritten: Response to " + SetHeartBeat.NAME + " sent " + (written?"ok":"failed"));
        }
    }

    private void receivedMessage(ISCMessage in){
        log.d("receivedMessage: [" + in.getHeader().toProtocolHeader() + "  ,  " + in.toString() + "]");
        if(in instanceof SetServerInfo){
            pushServerSet((SetServerInfo) in);
        }else if(in instanceof SetLocationFrequency) {
            pushLocationFrequency((SetLocationFrequency)in);
        }else if(in instanceof SetHeartBeat){
            pushHeartBeat((SetHeartBeat) in);
        }else {
            notifyRemotePush(in); //其他的都要送出去处理
        }
    }

    private class NetReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if(intent.getAction().equals(ConnectivityManager.CONNECTIVITY_ACTION)){
                log.i("NetReceiver: CONNECTIVITY_ACTION");
                if(!isSessionExist() && isNetworkAvailable()) {
                    delayToLogin(1 * 1000L);
                }
            }
        }
    }

    private class ScheduleTaskCallback implements SchedTask.ExpiredCallback{
        @Override
        public void onExpired(SchedTask task) {
            if(task.getName().equals(DeviceLogin.NAME)){
                log.i("on ScheduledTask Expired: task = " + task.getName() + ", loginOk = " + loginOk);
                if(!loginOk){
                    if(!isSessionExist() && isNetworkAvailable()) {
                        startUpSession();
                    }
                }
                loginScheduled.set(false);
            }
        }
    }

    private class WrappedMessage{
        private ISCMessage message;
        private IWriteFuture writeFuture;
        private ResponseFuture responseFuture;
        private String key;
        private long expiredAt;

        WrappedMessage(ISCMessage message) {
            this.message = message;
            this.key = message.getHeader().get$apiName();//不使用流水号  + message.getHeader().get$sequence();
        }

        public void setWriteFuture(IWriteFuture writeFuture) {
            this.writeFuture = writeFuture;
        }

        public void setResponseFuture(ResponseFuture responseFuture) {
            this.responseFuture = responseFuture;
        }

        private String getKey(){
            return key;
        }

        private boolean isRequest(){
            return (message instanceof IRequest);
        }

        private String getMessageName(){
            return message.getHeader().get$apiName();
        }

        private boolean isLoginMessage(){
            return (message instanceof DeviceLogin);
        }

        public void start2waitResponse() {
            this.expiredAt = SystemClock.elapsedRealtime() + RESP_DELAY_MS;
        }

        public boolean isExpired(){
            return (SystemClock.elapsedRealtime() >= expiredAt);
        }
    }

    private class Worker extends Handler{
        private final static int MSG_DO_SOMETHING = 1;
        private final static int MSG_DELAY_LOGIN = 2;
        private final static int MSG_NOTIFY_CLOSE = 3;
        private final static int MSG_FIRST_HEARTBEAT = 4;

        public void delayToLogin(long mills){
            WakeLock.getInstance().acquire();
            sendEmptyMessageDelayed(MSG_DELAY_LOGIN, mills);
        }

        public void scheduleFirstHeatBeat(){
            //这里是不用拿锁的
            sendEmptyMessageDelayed(MSG_FIRST_HEARTBEAT, DELAY_FIRST_HEARTBEAT_MS);
        }

        public void notifyClose(){
            WakeLock.getInstance().acquire();
            removeMessages(MSG_FIRST_HEARTBEAT);
            sendEmptyMessage(MSG_NOTIFY_CLOSE);
        }

        public void forceWakeUp(){
            // MSG_DO_SOMETHING 消息主要处理两个队列（），
            // 在worker外部向这两个队列里添加数据时 需要调用这个接口唤醒
            removeMessages(MSG_DO_SOMETHING);
            sendEmptyMessage(MSG_DO_SOMETHING);
        }

        //只在NetCommClient销毁时调用
        public void quit(){
            removeMessages(MSG_FIRST_HEARTBEAT);
            removeMessages(MSG_DELAY_LOGIN);
            pause();
        }

        public void pause(){
            removeMessages(MSG_DO_SOMETHING);
        }

        private void delayToNext() {
            //延迟100ms？？300ms？？
            sendEmptyMessageDelayed(MSG_DO_SOMETHING, 100L);
        }

        @Override
        public void handleMessage(Message message) {
            switch (message.what){
                case MSG_DELAY_LOGIN:{
                    if(!isSessionExist() && isNetworkAvailable()) {
                        startUpSession();
                    }
                    loginScheduled.set(false);
                    WakeLock.getInstance().release();
                    break;
                }
                case MSG_NOTIFY_CLOSE:{
                    checkWriteFutueQueue();
                    checkWaitRspQueue(true);
                    WakeLock.getInstance().release();
                    break;
                }
                case MSG_FIRST_HEARTBEAT:{
                    IOSession s = sessionRef.get();
                    //让KeepAliveFilter处理，统一处理所有心跳的响应超时
                    keepAliveFilter.sendPing(s);
                    break;
                }
                case MSG_DO_SOMETHING:{
                    checkWriteFutueQueue();
                    checkWaitRspQueue(false);
                    if(writeFutureQueue.isEmpty() && waitRspQueue.isEmpty()){
                        pause();
                    }else{
                        delayToNext();
                    }
                    break;
                }
            }
        }

        private void checkWaitRspQueue(boolean close){
            if(waitRspQueue.isEmpty()) return;
            for(;;){
                WrappedMessage wait = waitRspQueue.peek();
                if(wait == null)
                    break;
                String key = wait.getKey();
                if(waitRspMap.containsKey(key)){
                    log.d("checkWaitRspQueue:  " + key + " found in waitRspMap");
                    if(close || wait.isExpired()){
                        waitRspQueue.poll();
                        waitRspMap.remove(key);
                        log.d("checkWaitRspQueue:  " + key + " expired, remove from waitRspMap");
                        if(wait.isLoginMessage()){
                            dealLoginResponse(null);
                        }
                        if(wait.responseFuture != null){
                            Throwable e = close ? (new IOException("Session Closed")) : (new SocketTimeoutException("Response Time out"));
                            wait.responseFuture.setException(e);
                        }
                    }else{
                        break;
                    }
                }else{
                    //map 中没有了就是已经收到应答处理掉了 直接poll掉就行了
                    log.d("checkWaitRspQueue:  " + key + "   NOT found in waitRspMap");
                    waitRspQueue.poll();
                }
            }
        }

        private void checkWriteFutueQueue(){
            if(writeFutureQueue.isEmpty()) return;

            for (;;) {
                WrappedMessage wrapped = writeFutureQueue.peek();
                if(wrapped == null)
                    break;
                if(wrapped.writeFuture.isDone()){ //已完成
                    wrapped = writeFutureQueue.poll();
                    if(wrapped.writeFuture.isWritten()){ //写成功 加入等待应答的map
                        if(wrapped.isRequest()) {
                            log.d("write done add " + wrapped.getKey() + " to  waitRspQueue");
                            wrapped.start2waitResponse();
                            waitRspQueue.add(wrapped);
                        }else{
                            dealRspWritten(wrapped, true);
                        }
                    }else{
                        log.e("write failed message = {" + wrapped.getKey() + "}");
                        if(wrapped.isLoginMessage()){ //登录失败关闭会话，下一个心跳时间重试
                            closeSession(true);
                            scheduleNextLogin();
                        }else{
                            if(wrapped.isRequest()) {
                                log.d("write failed remove " + wrapped.getKey() + " from waitRspMap");
                                waitRspMap.remove(wrapped.getKey());
                                if (wrapped.responseFuture != null) {
                                    Throwable e = wrapped.writeFuture.getException();
                                    if (e == null) {
                                        e = new WriteException("null");
                                    }
                                    wrapped.responseFuture.setException(e);
                                }
                            }else{
                                dealRspWritten(wrapped, false);
                            }
                        }
                    }
                }else{
                    break; //写请求是按顺序的发的，第一个没做完 后面就不用看了，等下一次循环再处理
                }
            }
        }
    }

    private class CommHandler extends IOHandlerAdapter {
        @Override
        public void exceptionCaught(IOSession session, Throwable throwable) {
            throwable.printStackTrace();
        }
        @Override
        public void messageReceived(IOSession session, Object message) throws Exception {
            if (message instanceof ISCMessage) {
                if(message instanceof IResponse) {
                    receivedResponse((ISCMessage) message);
                }else{
                    receivedMessage((ISCMessage) message);
                }
            } else {
                log.e("messageReceived : unknown message =" + message.getClass().getSimpleName());
            }
        }

        @Override
        public void sessionOpened(IOSession session) {
            log.i("sessionOpened: " + session.getUniqueKey());
        }

        @Override
        public void sessionClosed(IOSession session) {
            if(sessionRef.get() == null){
                //主动断开
                log.i("sessionClosed: active " + session.getUniqueKey());
                worker.notifyClose();
                WakeLock.getInstance().release(); //会话关闭释放锁
            }else if(sessionRef.compareAndSet(session, null)) {
                //被动断开
                log.i("sessionClosed: passive " + session.getUniqueKey());
                worker.notifyClose();
                loginOk = false;
                notifyLogin(false);
                delayToLogin(15 * 1000L);
                WakeLock.getInstance().release(); //会话关闭释放锁
            }else{
                log.e("session not match");
            }
        }
    }
}
