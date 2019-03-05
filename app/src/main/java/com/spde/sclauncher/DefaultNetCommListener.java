package com.spde.sclauncher;

import com.spde.sclauncher.net.INetCommListener;
import com.spde.sclauncher.net.message.ISCMessage;

public class DefaultNetCommListener implements INetCommListener {
    @Override
    public void onLoginStatusChanged(boolean loginOk) {

    }

    @Override
    public void onUserRegiterRequired() {

    }

    @Override
    public void onSmsSendRequest(String tag, String text, String toPort) {

    }

    @Override
    public void onLocalTimeCheck(int year, int month, int dayInMonth, int hourIn24, int minutes) {

    }

    @Override
    public void onRemotePush(ISCMessage push) {

    }
}
