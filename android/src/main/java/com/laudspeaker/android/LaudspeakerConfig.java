package com.laudspeaker.android;

import android.content.Context;

import com.google.gson.Gson;

public class LaudspeakerConfig {
    private Class<?> targetActivityClass; // Target Activity class reference
    private boolean updatedClass = false;
    private boolean updatedDSN = false;
    private boolean updatedHost = false;
    private boolean updatedKey = false;
    public static final String defaultHost = "https://laudspeaker.com";
    public static final String defaultDSN = "https://627a7b79a7f54f3e893c807ad8314d31@o4506038702964736.ingest.us.sentry.io/4507223408508928";
    public static final String defaultKey = "";
    private String apiKey = defaultKey;
    private String host = defaultHost;
    private String sentryDSN = defaultDSN;
    private boolean debug = false;
    private int flushAt = 1;
    private int maxQueueSize = 1000;
    private int maxBatchSize = 50;
    private int flushIntervalSeconds = 1;
    // Internal usage
    private LaudspeakerLogger logger = new LaudspeakerLogger(this);
    private Gson serializer = new Gson();
    private String sdkName = "laudspeaker-android";
    private String sdkVersion = "1"; // Adjust this according to your build system
    private String userAgent = sdkName + "/" + sdkVersion;
    private String storagePrefix = null;
    private LaudspeakerPreferences cachePreferences = null;
    private LaudspeakerNetworkStatus networkStatus = null;
    private LaudspeakerDateProvider dateProvider = new LaudspeakerDateProvider();
    private LaudspeakerPropertiesSanitizer sanitizer;
    private LaudspeakerContext laudspeakerContext;


    public LaudspeakerConfig(String apiKey, Context context) {
        this.apiKey = apiKey;
        this.laudspeakerContext = new LaudspeakerContext(context, this);
    }

    public LaudspeakerConfig(String apiKey, String host, Class<?> targetActivityClass, String dsn, boolean updatedKey, boolean updatedHost, boolean updatedClass, boolean updatedDSN, Context context) {
        this.apiKey = apiKey;
        this.host = host;
        this.targetActivityClass = targetActivityClass;
        this.sentryDSN = dsn;
        this.updatedHost = updatedHost;
        this.updatedClass = updatedClass;
        this.updatedKey = updatedKey;
        this.updatedDSN = updatedDSN;
        this.laudspeakerContext = new LaudspeakerContext(context, this);
    }

    // Getters and Setters for all properties

    public LaudspeakerContext getLaudspeakerContext() {
        return this.laudspeakerContext;
    }

    public boolean getUpdatedHost() {
        return this.updatedHost;
    }

    public boolean getUpdatedKey() {
        return this.updatedKey;
    }

    public boolean getUpdatedClass() {
        return this.updatedClass;
    }
    public boolean getUpdatedDSN() {
        return this.updatedDSN;
    }

    public String getSentryDSN() {
        return sentryDSN;
    }

    public void setSentryDSN(String sentryDSN) {
        this.sentryDSN = sentryDSN;
    }

    public String getApiKey() {
        return apiKey;
    }

    public Class<?> getTargetActivityClass() {
        return targetActivityClass;
    }

    public LaudspeakerDateProvider getDateProvider() {
        return dateProvider;
    }

    public int getFlushIntervalSeconds() {
        return flushIntervalSeconds;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public int getFlushAt() {
        return flushAt;
    }

    public boolean isDebug() {
        return debug;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    // Include getters and setters for all other fields

    public LaudspeakerLogger getLogger() {
        return logger;
    }

    public void setLogger(LaudspeakerLogger logger) {
        this.logger = logger;
    }

    public int getMaxQueueSize() {
        return maxQueueSize;
    }

    public int getMaxBatchSize() {
        return maxBatchSize;
    }

    public LaudspeakerNetworkStatus getNetworkStatus() {
        return networkStatus;
    }

    public void setNetworkStatus(LaudspeakerNetworkStatus networkStatus) {
        this.networkStatus = networkStatus;
    }

    public String getStoragePrefix() {
        return storagePrefix;
    }

    public void setStoragePrefix(String storagePrefix) {
        this.storagePrefix = storagePrefix;
    }

    public Gson getSerializer() {
        return serializer;
    }

    public LaudspeakerPropertiesSanitizer getPropertiesSanitizer() {
        return sanitizer;
    }

    public void setSdkVersion(String sdkVersion) {
        this.sdkVersion = sdkVersion;
    }

    public void setSdkName(String sdkName) {
        this.sdkName = sdkName;
    }

    public LaudspeakerPreferences getCachePreferences() {
        return cachePreferences;
    }

    public void setCachePreferences(LaudspeakerPreferences cachePreferences) {
        this.cachePreferences = cachePreferences;
    }

    public String getSdkName(){
        return this.sdkName;
    }

    public String getSdkVersion() {
        return this.sdkVersion;
    }

}