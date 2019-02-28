package com.spde.sclauncher.net.message;

public class BodyFormatException extends Exception {
    
    public BodyFormatException(String clazz, String detailMessage) {
        super("@" + clazz + " => " + detailMessage);
    }
}
