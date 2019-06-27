package com.spde.sclauncher;

import android.content.Context;
import android.content.SharedPreferences;

import static com.spde.sclauncher.SchoolCardPref.DEBUG_PREF_NAME;
import static com.spde.sclauncher.SchoolCardPref.DP_KEY_CLOSE_NETCHAT;
import static com.spde.sclauncher.SchoolCardPref.DP_KEY_DEBUG;
import static com.spde.sclauncher.SchoolCardPref.DP_KEY_DEBUG_NETCHAT;
import static com.spde.sclauncher.SchoolCardPref.DP_KEY_TESTSERVER;

public class DebugDynamic {
    private static DebugDynamic INSTANCE;
    private final Context context;
    private final SharedPreferences prefs;
    private boolean debug;
    private volatile boolean debugNetChat;
    private volatile boolean closeNetChat;
    private volatile boolean useTestServer;
    private volatile boolean disableCipher;

    public DebugDynamic(Context context) {
        this.context = context;
        this.prefs = this.context.getSharedPreferences(DEBUG_PREF_NAME, Context.MODE_PRIVATE);
        init();
        INSTANCE = this;
    }

    private void init(){
        debug = prefs.getBoolean(DP_KEY_DEBUG, SCConfig.DEBUG);
        debugNetChat = prefs.getBoolean(DP_KEY_DEBUG_NETCHAT, SCConfig.DEBUG_NETWORK_MSG);
        closeNetChat = prefs.getBoolean(DP_KEY_CLOSE_NETCHAT, SCConfig.CLOSE_NETWORK_SESSION);
        useTestServer = prefs.getBoolean(DP_KEY_TESTSERVER, SCConfig.USE_TEST_SERVER);
        disableCipher = SCConfig.DISABLE_CIPHER;
    }

    private void save(String key, boolean value){
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(key, value);
        editor.commit();
    }

    public static DebugDynamic getInstance() {
        if (INSTANCE == null) {
            throw new RuntimeException("SCConfigDynamic is not running!");
        }
        return INSTANCE;
    }

    public boolean isDebug() {
        return debug;
    }

    public void setDebug(boolean debug) {
        if(debug == this.debug){
            return;
        }
        this.debug = debug;
        save(DP_KEY_DEBUG, this.debug);
    }

    public boolean isDebugNetChat() {
        return debugNetChat;
    }

    public void setDebugNetChat(boolean debugNetChat) {
        if(debugNetChat == this.debugNetChat){
            return;
        }
        this.debugNetChat = debugNetChat;
        save(DP_KEY_DEBUG_NETCHAT, this.debugNetChat);
    }

    public boolean isCloseNetChat() {
        return closeNetChat;
    }

    public void setCloseNetChat(boolean closeNetChat) {
        if(closeNetChat == this.closeNetChat){
            return;
        }
        this.closeNetChat = closeNetChat;
        save(DP_KEY_CLOSE_NETCHAT, this.closeNetChat);
    }

    public boolean isUseTestServer() {
        return SCConfig.USE_TEST_SERVER;
        //return useTestServer;
    }

    public void setUseTestServer(boolean useTestServer) {
        if(useTestServer == this.useTestServer){
            return;
        }
        this.useTestServer = useTestServer;
        save(DP_KEY_TESTSERVER, this.useTestServer);
    }

    public boolean isDisableCipher() {
        return disableCipher;
    }

    public void setDisableCipher(boolean disableCipher) {
        this.disableCipher = disableCipher;
    }
}
