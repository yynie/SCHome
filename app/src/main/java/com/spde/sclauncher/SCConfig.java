package com.spde.sclauncher;

import android.os.Environment;

public class SCConfig {
    /* INFO and higher log would be output if DEBUG is false */
    public static boolean DEBUG = false;

    /* msg transiferred on network would be output in INFO level*/
    public static boolean DEBUG_NETWORK_MSG = true;

    /* At most 15 server sms will be saved in database  */
    public static int MAX_SERVER_SMS = 15;

    /* Supported GPS fence shape */
    public static String SUPPORTED_FENCE_SHAPE = "'Round','Polygon'";

    /* whether on not to append rfid in DEVICE_LOGIN packet */
    public static boolean APPEND_RFID_IN_LOGIN_PACKET = true; 

    /* local time check gate, in minutes */
    public static int TIME_CHECK_GATE = 3;

    /* sos dialing waiting time in second */
    public static int TIME_SOS_WAITING_IN_MILLIS = 20 * 1000;

    /* sos dialing retry */
    public static int DIAL_SOS_RETRY = 3;

    /* Net watcher for absence of android.net.conn.CONNECTIVITY_CHANGE */
    public static boolean NET_WATCHER_ENABLE = true;
    public static long NET_WATCHER_DELAY_MS = 10 * 60 * 1000L;

    /* Save Server address in PERMANENT_FILE to avoid to be erased in recovery mode */
    //public static String PERMANENT_FILE = "/productinfo/sc_permanent_file";

    /* Flags below are used for debug  */
    /* If u don't want to communicate to cloud server, set CLOSE_NETWORK_SESSION true  */
    public static boolean CLOSE_NETWORK_SESSION = false;

    /* If cloud server is really really poor, set SERIAL_REQ_LIMIT true.
     * No guarantee for this feature!!! Never set this flag true!!!
     *  */
    public static boolean SERIAL_REQ_LIMIT = true;

    /* If u want use wifi, set  this flag true to avoid wifi closed by program  */
    public static boolean USE_WIFI_NETWORK = true;

    /* we use USE_TEST_SERVER to switch to different default server address. */
    public static boolean USE_TEST_SERVER = false;

    /* write testserver:port here */
    public static String TEST_SERVER_FILE = Environment.getExternalStorageDirectory().getAbsolutePath() + "/testserver";

    /**
     * 119.29.52.71:6666
     * 117.187.201.26:9125 4G server 商用服务器
     * 117.187.201.26:9124 2G server
     * 47.100.248.25:12001  ali D-station test server, it's ours and it don't support CIPHER
     * 120.92.91.44:9124  zhongyi test server
     * */
    public static String DEFAULT_SERVER_ADDRESS = "117.187.201.26:9125";

    /* If ur server do not support CIPHER , set this flag true */
    public static boolean DISABLE_CIPHER = false;

    /* If u wanna to see the device's screen lighting-up synchronously when CPU is waked up, set this flag true  */
    public static boolean KEEP_SCREEN_ON = false;

}
