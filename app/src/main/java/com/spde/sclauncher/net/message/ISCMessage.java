package com.spde.sclauncher.net.message;

public interface ISCMessage {
    void generateSequence();

    ISCHeader getHeader();
    String toProtocolBody();
    void setProtocolBody(String body, int length) throws BodyFormatException;
//    String getName();
//    Type getType();
    //    void setHeader(ISCHeader header);
}
