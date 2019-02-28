package com.spde.sclauncher.provider;

public class SCDB {
    public static final String AUTHORITY = "com.spde.sclauncher.provider";
    public static final String DATABASE_NAME = "schoolcard.db";
    public static final int DATABASE_VERSION = 1;

    public static final String TABLE_CONTACTS = "t_contacts";
    public static final class Contacts {
        public static final String NAME = "name";
        public static final String PHONE = "phone";
    }
    public static final String TABLE_CLASSMODE = "t_classmode";
    public static final class ClassMode {
        public static final String STARTMIN = "startmin";
        public static final String ENDMIN = "endmin";
        public static final String DAY = "day";
        public static final String ONOFF = "onoff";
        public static final String SOS_IN = "sos_in";
        public static final String SOS_OUT = "sos_out";
    }
    public static final String TABLE_SERVER_SMS = "t_server_sms";
    public static final class ServerSms {
        public static final String MESSAGE = "message";
        public static final String EMERGENT = "emergent";
        public static final String SHOWTIMES = "showtimes";
        public static final String SHOWTYPE = "showtype";
        public static final String FLASH = "flash";
        public static final String RING = "ring";
        public static final String VIBRATE = "vibrate";
        public static final String UPDATETIME = "updatetime";
    }
    public static final String TABLE_WHTTELIST = "t_whitelist";
    public static final class WhiteList {
        public static final String PHONE = "phone";
        public static final String STARTMIN = "startmin";
        public static final String ENDMIN = "endmin";
        public static final String CALL_IN = "call_in";
        public static final String DAY = "day";
    }
    public static final String TABLE_PROFILE = "t_profile";
    public static final class Profile {
        public static final String RING = "ring";
        public static final String CALL_IN_FORBID = "forbid_in";
        public static final String CALL_OUT_FORBID = "forbid_out";
    }
}
