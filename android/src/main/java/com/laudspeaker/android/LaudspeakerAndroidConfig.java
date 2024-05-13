package com.laudspeaker.android;

public class LaudspeakerAndroidConfig extends LaudspeakerConfig {


    public LaudspeakerAndroidConfig(Class<?> targetActivityClass) {
        this(defaultKey, defaultHost, targetActivityClass, defaultDSN, false, false, false, false);
    }

    public LaudspeakerAndroidConfig(String apiKey, Class<?> targetActivityClass) {
        this(apiKey, defaultHost, targetActivityClass, defaultDSN, true, true, true, true);
    }


    public LaudspeakerAndroidConfig(String apiKey, String host, Class<?> targetActivityClass) {
        this(apiKey, host, targetActivityClass, defaultDSN, true, true, true, true);
    }

    public LaudspeakerAndroidConfig(String apiKey, String host, Class<?> targetActivityClass, String dsn) {
        this(apiKey, host, targetActivityClass, dsn, true, true, true, true);
    }

    public LaudspeakerAndroidConfig(String apiKey, String host, Class<?> targetActivityClass, String dsn, boolean updatedKey, boolean updatedHost, boolean updatedClass, boolean updatedDSN) {
        super(apiKey, host, targetActivityClass, dsn, updatedKey, updatedHost, updatedClass, updatedDSN);
    }


}