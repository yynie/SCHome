package com.spde.sclauncher.net.codec;

import com.sonf.core.buffer.IoBuffer;
import com.sonf.core.session.IOSession;
import com.sonf.filter.IProtocolDecoder;
import com.sonf.filter.IProtocolEncoder;
import com.sonf.filter.IProtocolOutput;
import com.sonf.filter.ProtocolFilter;
import com.spde.sclauncher.net.message.BodyFormatException;
import com.spde.sclauncher.net.message.GZ.CommonRsp;
import com.spde.sclauncher.net.message.IRequest;
import com.spde.sclauncher.net.message.ISCHeader;
import com.spde.sclauncher.net.message.ISCMessage;
import com.spde.sclauncher.net.message.Type;
import com.spde.sclauncher.net.message.UnknownMessage;
import com.spde.sclauncher.net.message.UnknownRsp;
import com.yynie.myutils.Logger;
import com.yynie.myutils.StringUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SCProtocolCodecFilter extends ProtocolFilter {
    private final Logger log = Logger.get(SCProtocolCodecFilter.class, Logger.Level.INFO);
    private final String START_SYM = "[";
    private final String END_SYM = "]";
    private final String SPLIT_CH = ",";
    private Class<? extends ISCHeader> headerType;
    private int headerFieldNumber = 0;
    private final Map<String, Class<? extends ISCMessage>> messageTypeMap = new ConcurrentHashMap<String, Class<? extends ISCMessage>>();


    public SCProtocolCodecFilter() {
        setEncoder(new SCEncoder());
        setDecoder(new SCDecoder());
    }

    public void setHeaderType(Class<? extends ISCHeader> headerType){
        this.headerType = headerType;
        this.headerFieldNumber = 0;
        Field[] fields = headerType.getDeclaredFields();
        for (Field f : fields) {
            if(f.isSynthetic()){  //Android studio 调试环境在高版本api上会向类中添加成员变量
                continue;
            }
            if(f.getName().startsWith("$")){
                this.headerFieldNumber ++;
            }
        }
    }

    public boolean registerMessageType(String apiName, Type type, Class<? extends ISCMessage> messageType){
        String key = apiName + "#" + type.value();
        if(messageTypeMap.containsKey(key)) {
            return false;
        }
        messageTypeMap.put(key, messageType);
        return true;
    }

    public void clearMessageMap(){
        messageTypeMap.clear();
    }

    private Class<? extends ISCMessage> getMessageType(String apiName, Type type){
        String key = apiName + "#" + type.value();
        return messageTypeMap.get(key);
    }

    private class SCEncoder implements IProtocolEncoder{

        @Override
        public void encode(IOSession session, Object message, IProtocolOutput out) throws Exception {
            if(message instanceof ISCMessage){
                ISCMessage iscMessage = (ISCMessage) message;
                if(message instanceof IRequest) {
                    iscMessage.generateSequence(); //要发送了 生成流水号
                }
                String body = iscMessage.toProtocolBody();
                ISCHeader ischeader = iscMessage.getHeader();
                ischeader.set$contentLength(body.length());
                String header = ischeader.toProtocolHeader();
                String send = new String(START_SYM + header + SPLIT_CH + body + END_SYM);
                log.i("==>" + send);
                out.write(send);
            }else{
                throw new Exception("Unknown message class:" + message.getClass().getSimpleName());
            }
        }

        @Override
        public void dispose(IOSession session) throws Exception {

        }
    }

    private class SCDecoder implements IProtocolDecoder{

        @Override
        public void decode(IOSession session, IoBuffer in, IProtocolOutput out) throws Exception {
            String raw = in.getString(Charset.forName("UTF-8").newDecoder());
            log.i("<==" + raw);

            if(StringUtils.isBlank(raw)) return;

            String inString = raw.trim();

            if(!inString.startsWith(START_SYM)) throw new Exception("Unknown message. Can't match the start symbol( " +  START_SYM + ").");
            if(!inString.endsWith(END_SYM)) throw new Exception("Unknown message. Can't match the end symbol( " +  END_SYM + ").");

            int len = inString.length();
            inString = inString.substring(START_SYM.length(), len - END_SYM.length());
            String[] fields = new String[headerFieldNumber];

            for(int i = 0; i< headerFieldNumber; i ++){
                int pos = inString.indexOf(SPLIT_CH);
                if(pos < 0){
                    if(i == (headerFieldNumber - 1)){
                        //不带报文体的消息
                        log.i("Message without body");
                        fields[i] = inString;
                        inString = "";
                    }else {
                        log.e("Message header field number is not match to class " + headerType.getSimpleName());
                        throw new Exception("Message header field number is not match to class " + headerType.getSimpleName());
                    }
                }else {
                    fields[i] = inString.substring(0, pos).trim();
                    inString = inString.substring(pos + 1);
                }
            }

            try {
                Constructor<? extends ISCHeader> constructor = headerType.getConstructor();
                ISCHeader header = constructor.newInstance();
                header.fillFields(fields);
                String api = header.get$apiName();
                Type type = header.get$type();
                int contentLen = header.get$contentLength();
                Class<? extends ISCMessage> msgType = getMessageType(api, type);
                if(msgType != null){
                    Constructor<? extends  ISCMessage> msgCons = msgType.getConstructor(ISCHeader.class);
                    ISCMessage message = msgCons.newInstance(header);
                    message.setProtocolBody(inString, contentLen);
                    out.write(message);
                    return;
                }
                if(type == Type.TYPE_UPSTREAM_RSP) {
                    log.i("try to match to CommonRsp");
                    msgType = getMessageType(CommonRsp.class.getSimpleName(), type);
                    if(msgType != null) {
                        Constructor<? extends ISCMessage> msgCons = msgType.getConstructor(ISCHeader.class);
                        ISCMessage message = msgCons.newInstance(header);
                        try {
                            message.setProtocolBody(inString, contentLen);
                            out.write(message);
                            return;
                        }catch (BodyFormatException e){
                            log.e("match to CommonRsp failed");
                        }
                    }
                    log.e("Unknown response received!");
                    msgType = getMessageType(UnknownRsp.class.getSimpleName(), type);
                    if(msgType != null) {
                        Constructor<? extends ISCMessage> msgCons = msgType.getConstructor(ISCHeader.class);
                        ISCMessage message = msgCons.newInstance(header);
                        message.setProtocolBody(inString, contentLen);
                        out.write(message);
                        return;
                    }
                }else{
                    log.e("Unknown message received!");
                    msgType = getMessageType(UnknownMessage.class.getSimpleName(), type);
                    if(msgType != null) {
                        Constructor<? extends ISCMessage> msgCons = msgType.getConstructor(ISCHeader.class);
                        ISCMessage message = msgCons.newInstance(header);
                        message.setProtocolBody(inString, contentLen);
                        out.write(message);
                        return;
                    }
                }
                throw new Exception("Message can not be recognized :" + api + " " + type);
            }catch (Exception e){
                throw e;
            }
        }

        @Override
        public void finishDecode(IOSession session, IProtocolOutput out) throws Exception {

        }

        @Override
        public void dispose(IOSession session) throws Exception {

        }
    }
}
