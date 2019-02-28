package com.spde.sclauncher.net.message;

public interface ISCHeader {
    void fillFields(String[] fields) throws Exception;
    void set$apiName(String $apiName);
    String get$apiName();
    void set$type(Type type);
    Type get$type();
    String get$time();
    void set$time(String $time);
    void set$sequence(String $sequence);
    String get$sequence();
    void set$contentLength(int $contentLength);
    int get$contentLength();
    String toProtocolHeader();
}
