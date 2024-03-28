package com.laudspeaker.android;

import com.google.gson.Gson;

public class LaudspeakerConfig {

    public static final String defaultHost = "https://laudspeaker.com";
    private final String apiKey;
    private String host = defaultHost;
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

    public LaudspeakerConfig(String apiKey) {
        this.apiKey = apiKey;
    }

    public LaudspeakerConfig(String apiKey, String host) {
        this.apiKey = apiKey;
        this.host = host;
    }

    // Getters and Setters for all properties

    public String getApiKey() {
        return apiKey;
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
}