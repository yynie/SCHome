package com.spde.sclauncher.net.message.GZ;

import com.spde.sclauncher.net.message.AbstractISCMessage;
import com.spde.sclauncher.net.message.BodyFormatException;
import com.spde.sclauncher.net.message.IRequest;
import com.spde.sclauncher.net.message.ISCHeader;
import com.spde.sclauncher.net.message.Type;

import java.util.HashMap;
import java.util.Map;

public class SetButtons extends AbstractISCMessage<GZProtocolHeader> implements IRequest {
    public final static String NAME = "SET_NORMAL_BUTTON";
    public final static Type TYPE = Type.TYPE_DOWNSTREAM_REQ;
    private Map<String, String> keyMap = new HashMap<String, String>();  //key="0" 是sos键

    public SetButtons(ISCHeader header) {
        super(header);
    }

    @Override
    protected GZProtocolHeader generateHeader() {
        //TYPE_DOWNSTREAM_REQ 的消息无需实现此方法
        return null;
    }

    public boolean isEmpty(){
        return keyMap.isEmpty();
    }

    //0号是sos键
    public final String getKeyNumber(int keyId) {
        return keyMap.get(String.valueOf(keyId));
    }

    public final Map<String, String> getKeyMap() {
        return keyMap;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Type getType() {
        return TYPE;
    }

    @Override
    public String toProtocolBody() {
        return null;
    }

    @Override
    public void setProtocolBody(String body, int length) throws BodyFormatException {
        String[] keys = body.split("!");
        for(String key: keys){
            if(key.trim().isEmpty())
                continue;

            String[] sets = key.split("=");
            if(sets.length >= 2) {
                keyMap.put(sets[0].trim(), sets[1].trim());
            }else if(sets.length >= 1){
                keyMap.put(sets[0].trim(), "");
            }else{
                //just ignore
                //throw new BodyFormatException("SetButtons", "Error set:" + key);
            }
        }
    }
}
