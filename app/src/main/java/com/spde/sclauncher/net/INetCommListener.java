package com.spde.sclauncher.net;

import com.spde.sclauncher.net.message.ISCMessage;

public interface INetCommListener {
    void onLoginStatusChanged(boolean loginOk);
    void onSmsSendRequest(String tag, String text, String toPort);
    void onLocalTimeCheck(int year, int month, int dayInMonth, int hourIn24, int minutes);
    void onRemotePush(ISCMessage push);
}
