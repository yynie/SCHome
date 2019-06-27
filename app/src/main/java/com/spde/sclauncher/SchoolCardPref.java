package com.spde.sclauncher;

public class SchoolCardPref {
    public static final String PREF_NAME = "com.spde.sclauncher";
    public static final String PREF_KEY_SERVERADDR = "serverAddr";
    public static final String PREF_KEY_HEARTBEAT = "heartBeat";
    public static final String PREF_KEY_LOCRATE = "locRate";
    public static final String PREF_KEY_CONFIG_CHECK_STR = "configCheckStr";
    public static final String PREF_KEY_LAST_CALLLOG = "lastCallLog";

    /** 重启后要复位的 start */
    public static final String PREF_KEY_REGISTER_SMS_COUNT = "registSMSCount";
    public static final String PREF_KEY_FROZEN_FLAG = "frozen";
    /** 重启后要复位的 end */

    /** 用于调试秘窗 start */
    public static final String DEBUG_PREF_NAME = "com.spde.sclauncher_debug";
    public static final String DP_KEY_DEBUG = "debug";
    public static final String DP_KEY_DEBUG_NETCHAT = "debug_netchat";
    public static final String DP_KEY_CLOSE_NETCHAT = "close_netchar";
    public static final String DP_KEY_TESTSERVER = "use_test_server";
}
