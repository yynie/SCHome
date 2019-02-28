package com.spde.sclauncher.net;

import android.os.SystemClock;

import com.sonf.core.filter.IFilterAdapter;
import com.sonf.core.filter.IFilterChain;
import com.sonf.core.session.AttributeKey;
import com.sonf.core.session.IOSession;
import com.sonf.core.session.IdleStatus;
import com.sonf.core.write.IWritePacket;
import com.sonf.core.write.WritePacket;
import com.sonf.future.WriteFuture;
import com.spde.sclauncher.DataSource.BatteryDataSource;
import com.spde.sclauncher.net.message.GZ.HeartBeat;
import com.spde.sclauncher.net.message.ISCMessage;
import com.yynie.myutils.Logger;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static com.spde.sclauncher.SCConfig.SERIAL_REQ_LIMIT;


public class KeepAliveFilter extends IFilterAdapter {
    private final Logger log = Logger.get(KeepAliveFilter.class, Logger.Level.INFO);
    
    private final AttributeKey LAST_READ_TIME = new AttributeKey(KeepAliveFilter.class, "lastRead");
    private final AttributeKey LAST_WRITE_TIME = new AttributeKey(KeepAliveFilter.class, "lastWrite");
    private final AttributeKey WAITING_QUEUE = new AttributeKey(KeepAliveFilter.class, "waitRspQ");
    private final AttributeKey FAIL_COUNT = new AttributeKey(KeepAliveFilter.class, "failCount");
    private final long DEFAULT_DELAY_MS = NetCommClient.DEFAULT_HEARTBEAT_DURATION_SEC * 1000L;
    private final long RESP_DELAY_MS = NetCommClient.RESP_DELAY_MS;
    private AtomicLong regularDelayMs = new AtomicLong(DEFAULT_DELAY_MS);
    private AtomicLong rspDelayMs = new AtomicLong(RESP_DELAY_MS);

    public void setRateMs(long regularDelayMs){
        if(regularDelayMs > 0) {
            this.regularDelayMs.set(regularDelayMs);
        }
    }

    public void setResponseTimeOutMs(long rspDelayMs){
        if(rspDelayMs > 0) {
            this.rspDelayMs.set(rspDelayMs);
        }
    }

    public void sendPing(IOSession session){
        if(session != null && session.isReady()) {
            log.i("sendPing by explicit call");
            ISCMessage message = generatePing();
            session.write(message);
            session.setAttribute(LAST_READ_TIME, SystemClock.elapsedRealtime()); //发完心跳更新时间
            String key = message.getHeader().get$apiName() + message.getHeader().get$sequence();
            addToWaitRsp(session, key);
        }
    }

    @Override
    public void onPreAdd(IFilterChain parent, String name) throws Exception {
        if (parent.contains(this)) {
            throw new IllegalArgumentException("You can't add the same filter instance more than once. "
                    + "Create another instance and add it.");
        }
    }

    @Override
    public void onPostAdd(IFilterChain parent, String name) throws Exception {
        parent.getSession().setAttribute(LAST_READ_TIME, SystemClock.elapsedRealtime());
        parent.getSession().setAttribute(LAST_WRITE_TIME, SystemClock.elapsedRealtime());
        parent.getSession().setAttribute(WAITING_QUEUE, new ConcurrentLinkedQueue<String>());
        parent.getSession().setAttribute(FAIL_COUNT, new AtomicInteger(0));
    }

    @Override
    public void onPostRemove(IFilterChain parent, String name) throws Exception {
        parent.getSession().removeAttribute(LAST_READ_TIME);
        parent.getSession().removeAttribute(LAST_WRITE_TIME);
        parent.getSession().removeAttribute(FAIL_COUNT);
        Queue q = (Queue) parent.getSession().removeAttribute(WAITING_QUEUE);
        q.clear();
    }

    @Override
    public void messageSent(IFilterChain.Entry next, IOSession session, IWritePacket packet) {
        session.setAttribute(LAST_WRITE_TIME, SystemClock.elapsedRealtime());
        next.getFilter().messageSent(next.getNextEntry(), session, packet);
    }

    @Override
    public void messageReceived(IFilterChain.Entry next, IOSession session, Object message) throws Exception {
       // log.i("receive something");
        //收到任何数据 都算链路通的
        Queue q = (Queue) session.getAttribute(WAITING_QUEUE);
        if(!q.isEmpty()){
            q.clear();
        }
        session.setAttribute(LAST_READ_TIME, SystemClock.elapsedRealtime()); //记录读数据时间

        ((AtomicInteger)session.getAttribute(FAIL_COUNT)).set(0);
        next.getFilter().messageReceived(next.getNextEntry(), session, message);
    }

    @Override
    public void sessionIdle(IFilterChain.Entry next, IOSession session, IdleStatus status) throws Exception {
        if(status == IdleStatus.READER_IDLE){
            boolean ok = checkResponseTimeOut(session);
            if(!ok) return;
            long lastReadAt = (Long) session.getAttribute(LAST_READ_TIME);
            long lastWriteAt = (Long) session.getAttribute(LAST_WRITE_TIME);
            long cur = SystemClock.elapsedRealtime();
            boolean canSend = ((cur - lastWriteAt) > rspDelayMs.get());
            if(!SERIAL_REQ_LIMIT){ //如果服务器要求串行，就不能连续发
                canSend = true;
            }

            if ((cur - lastReadAt) >= regularDelayMs.get() && canSend){
                log.i("send Ping for sessionIdle READER_IDLE");
                ISCMessage message = generatePing();
                WritePacket packet = new WritePacket(message, new WriteFuture(session));

                //这里是sonf内部回调出来的，利用filter链向channel直接提交写请求
                //入参next是本节点的下一个节点，应该是tail，tail的prev是本节点, 因为filterWrite事件是往前传递的，不知道的看api doc
                IFilterChain.Entry me = next.getPrevEntry();
                me.getFilter().filterWrite(next.getPrevEntry().getPrevEntry(), session, packet);  //提交写请求

                session.setAttribute(LAST_READ_TIME, SystemClock.elapsedRealtime()); //发完心跳更新时间

                String key = message.getHeader().get$apiName() + message.getHeader().get$sequence();
                addToWaitRsp(session, key);
            }
        }
        next.getFilter().sessionIdle(next.getNextEntry(), session, status);
    }

    private void addToWaitRsp(IOSession session, String key){
        log.i(key + " send");
        Queue q = (Queue) session.getAttribute(WAITING_QUEUE);
        assert (q != null);
        q.offer(key + "#" + SystemClock.elapsedRealtime());
    }

    private boolean checkResponseTimeOut(IOSession session){
        Queue q = (Queue) session.getAttribute(WAITING_QUEUE);
        String first = (String) q.peek();
        if(first == null){
            return true;
        }
        long cur = SystemClock.elapsedRealtime();
        long sendAt = Long.parseLong(first.substring(first.indexOf("#") + 1));
        if((cur - sendAt) >= rspDelayMs.get()){
            q.poll();
            int failed = ((AtomicInteger)session.getAttribute(FAIL_COUNT)).incrementAndGet();
            log.w(first + " expired!!, failed count = " + failed);
            if(failed >= 3){
                log.e("too many Ping message time out, close session now");
                session.closeNow();
                return false;
            }
        }
        return true;
    }

    private ISCMessage generatePing(){
        int value = BatteryDataSource.getInstance().getBatteryPercent();
        HeartBeat ping = new HeartBeat(null);
        ping.setBatteryPercent(value);
        return ping;
    }

}
