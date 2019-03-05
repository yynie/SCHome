package com.spde.sclauncher.schcard;

public interface OnBussinessEventListener {
    void onLoginStatusChanged(boolean loginOk);
    void onUserRegiterRequired();
    void onSeverSmsShow(String sms, boolean emergent, int showtimes, int showType, boolean flash, boolean ring, boolean vibrate);
    void onDialSos();
    void onDoReboot(boolean recovery);
}
