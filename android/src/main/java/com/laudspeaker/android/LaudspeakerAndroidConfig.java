package com.laudspeaker.android;
import android.content.Context;


public class LaudspeakerAndroidConfig extends LaudspeakerConfig {

    public LaudspeakerAndroidConfig(Context context, Class<?> targetActivityClass) {
        this(context, defaultKey, defaultHost, targetActivityClass, defaultDSN, false, false, false, false);
    }

    public LaudspeakerAndroidConfig(Context context,String apiKey, Class<?> targetActivityClass) {
        this(context, apiKey, defaultHost, targetActivityClass, defaultDSN, true, true, true, true);
    }


    public LaudspeakerAndroidConfig(Context context, String apiKey, String host, Class<?> targetActivityClass) {
        this(context, apiKey, host, targetActivityClass, defaultDSN, true, true, true, true);
    }

    public LaudspeakerAndroidConfig(Context context, String apiKey, String host, Class<?> targetActivityClass, String dsn) {
        this(context, apiKey, host, targetActivityClass, dsn, true, true, true, true);
    }


    public LaudspeakerAndroidConfig(Context context, String apiKey, String host, Class<?> targetActivityClass, String dsn, boolean updatedKey, boolean updatedHost, boolean updatedClass, boolean updatedDSN) {
        super(context, apiKey, host, targetActivityClass, dsn, updatedKey, updatedHost, updatedClass, updatedDSN);
    }



}