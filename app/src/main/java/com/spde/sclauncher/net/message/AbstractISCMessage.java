package com.spde.sclauncher.net.message;


import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class AbstractISCMessage<H extends ISCHeader> implements ISCMessage {
    protected String SPLIT_CH = "@";
    protected H header;
    private SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
    private static final AtomicInteger seqnumGen = new AtomicInteger(0);

    public AbstractISCMessage(ISCHeader header) {
        this.header = (H) header;
        if(this.header == null) {
            initHeader();
        }
    }

    public AbstractISCMessage(){
        this.header = null;
    }

    protected void initHeader(){
        if(this.header == null) {
            this.header = generateHeader();
            this.header.set$apiName(getName());
            this.header.set$type(getType());
            this.header.set$time(genTimsProtocolString());
        }
    }

    public void reGenHeaderTime(){
        this.header.set$time(genTimsProtocolString());
    }

    public String genTimsProtocolString(){
        Calendar calendar = Calendar.getInstance();
        return sdf.format(calendar.getTime());
    }

    @Override
    public void generateSequence(){
        Calendar calendar = Calendar.getInstance();
        String time = sdf.format(calendar.getTime());
        header.set$sequence(time + String.format("%04d", seqnumGen.getAndIncrement()));
        seqnumGen.compareAndSet(10000, 0);
    }

    @Override
    public H getHeader() {
        return header;
    }

    protected abstract H generateHeader();

    protected abstract String getName();

    protected abstract Type getType();
}
