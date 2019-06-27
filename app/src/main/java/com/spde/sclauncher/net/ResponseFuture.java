package com.spde.sclauncher.net;

import com.sonf.core.future.DefaultIOFuture;
import com.sonf.core.future.IWriteFuture;
import com.sonf.core.session.IOSession;
import com.spde.sclauncher.net.message.ISCMessage;

public class ResponseFuture extends DefaultIOFuture {
    private IWriteFuture writeFuture;
    private ISCMessage request;
    private boolean enableRetry;
    private final int DEFAULT_MAX_RETRY = 3;
    private int maxRetry = DEFAULT_MAX_RETRY;
    private int retryCount; //3次

    public ResponseFuture(IOSession session, ISCMessage request) {
        super(session);
        this.request = request;
    }

    public void setWriteFuture(IWriteFuture writeFuture) {
        this.writeFuture = writeFuture;
    }

    public final IWriteFuture getWriteFuture() {
        return writeFuture;
    }

    public boolean retryCountIncrement(){
        if(enableRetry) {
            retryCount++;
            return (retryCount < maxRetry);
        }
        return false;
    }

    public boolean isEnableRetry() {
        return enableRetry;
    }

    public void setEnableRetry(boolean enableRetry, int maxRetry) {
        this.enableRetry = enableRetry;
        this.maxRetry = maxRetry;
    }

    public int getMaxRetry() {
        return maxRetry;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public void setResponse(ISCMessage response) {
        setValue(response);
    }

    public ISCMessage getResponse(){
        Object value = getValue();
        if(value instanceof ISCMessage){
            ISCMessage rsp = (ISCMessage) value;
            return rsp;
        }
        return null;
    }

    public ISCMessage getRequest() {
        return request;
    }
}
