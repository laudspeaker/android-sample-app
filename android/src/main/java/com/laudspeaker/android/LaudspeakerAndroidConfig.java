package com.laudspeaker.android;

public class LaudspeakerAndroidConfig extends LaudspeakerConfig {

    public LaudspeakerAndroidConfig(String apiKey) {
        this(apiKey, defaultHost);
    }

    public LaudspeakerAndroidConfig(String apiKey, String host) {
        super(apiKey, host);
    }
}