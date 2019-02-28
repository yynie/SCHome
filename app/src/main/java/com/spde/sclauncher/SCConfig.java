package com.spde.sclauncher;

public class SCConfig {
    /* Only Warning and Error would be output if DEBUG is false */
    public static boolean DEBUG = true;

    /* At most 15 server sms will be saved in database  */
    public static int MAX_SERVER_SMS = 15;

    /* Save Server address in PERMANENT_FILE to avoid to be erased in recovery mode */
    public static String PERMANENT_FILE = "/productinfo/sc_permanent_file";

    /* Flags below are used for debug  */
    /* If u don't want to communicate to cloud server, set CLOSE_NETWORK_SESSION true  */
    public static boolean CLOSE_NETWORK_SESSION = false;

    /* If cloud server is really really poor, set SERIAL_REQ_LIMIT true.
     * No guarantee for this feature!!! Never set this flag true!!!
     *  */
    public static boolean SERIAL_REQ_LIMIT = false;

    /* If u want use wifi, set  this flag true to avoid wifi closed by program  */
    public static boolean USE_WIFI_NETWORK = false; 

    /* we use TO_TEST_SERVER to switch to different default server address. */
    public static boolean TO_TEST_SERVER = false;
    /**
     * 119.29.52.71:6666
     * sdr.gzixy.cn:9124
     * 120.92.91.44:9124  zhongyi test server
     * */
    public static String SERVER_ADDRESS = (TO_TEST_SERVER?"192.168.20.130:12001":"120.92.91.44:9124");

    /* If ur test server do not support CIPHER , set this flag true */
    public static boolean DISABLE_CIPHER = false;

    /* If u wanna to see the device's screen lighting-up synchronously when CPU is waked up, set this flag true  */
    public static boolean KEEP_SCREEN_ON = true;
}
