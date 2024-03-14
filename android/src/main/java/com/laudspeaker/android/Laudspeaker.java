package com.laudspeaker.android;

import com.google.firebase.messaging.FirebaseMessaging;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class Laudspeaker {
    private final ExecutorService queueExecutor = Executors.newSingleThreadScheduledExecutor(new LaudspeakerThreadFactory("LaudspeakerQueueThread"));
    private final Object setupLock = new Object();
    private final Object customerIdLock = new Object();
    private final Object primaryKeyLock = new Object();
    private final Object fcmTokenLock = new Object();
    public volatile boolean enabled = false;
    private LaudspeakerConfig config;
    private LaudspeakerQueue queue;
    private LaudspeakerPreferences memoryPreferences;
    private String customerIdCache;
    private String primaryKeyCache;
    private String fcmTokenCache;
    private String apiKey;


    public static <T extends LaudspeakerConfig> Laudspeaker with(T config) {
        Laudspeaker instance = new Laudspeaker(); // Assuming there's a default constructor or appropriate constructor available
        instance.setup(config);
        instance.sendFcmTokenAsync();
        return instance;
    }

    public <T extends LaudspeakerConfig> void setup(T config) {
        synchronized (setupLock) {
            try {
                if (enabled) {
                    config.getLogger().log("Setup called despite already being setup!");
                    return;
                }

                if (apiKey == config.getApiKey()) {
                    config.getLogger().log("API Key: " + config.getApiKey() + " already has a Laudspeaker instance.");
                }

                this.memoryPreferences = config.getCachePreferences();
                LaudspeakerApi api = new LaudspeakerApi(config);
                this.queue = new LaudspeakerQueue(config, api, LaudspeakerApiEndpoint.EVENT, config.getStoragePrefix(), queueExecutor);

                this.config = config;

                this.enabled = true;

                queue.start();

            } catch (Throwable e) {
                config.getLogger().log("Setup failed: " + e);
            }
        }
    }

    public String getCustomerId() {
        synchronized (customerIdLock) {
            if (customerIdCache == null || customerIdCache.isEmpty()) {
                Object value = getPreferences().getValue(LaudspeakerPreferences.CUSTOMER_ID, null);
                if (value instanceof String && !((String) value).isEmpty()) {
                    customerIdCache = (String) value;
                } else {
                    customerIdCache = UUID.randomUUID().toString();
                    setCustomerId(customerIdCache);
                }
            }
            return customerIdCache;
        }
    }

    public void setCustomerId(String value) {
        synchronized (customerIdLock) {
            getPreferences().setValue(LaudspeakerPreferences.CUSTOMER_ID, value);
            this.customerIdCache = value; // Cache the value to avoid fetching it repeatedly
        }
    }


    public String getPrimaryKey() {
        synchronized (primaryKeyLock) {
            if (primaryKeyCache == null || primaryKeyCache.isEmpty() || primaryKeyCache == "") {

                Object value = getPreferences().getValue(LaudspeakerPreferences.PRIMARY_KEY, getCustomerId());

                if (value instanceof String) {
                    primaryKeyCache = (String) value;
                } else {
                    primaryKeyCache = ""; // Default to empty string if value is not a string
                }
            }
        }
        return primaryKeyCache;
    }

    public void setPrimaryKey(String value) {
        synchronized (primaryKeyLock) {
            getPreferences().setValue(LaudspeakerPreferences.PRIMARY_KEY, value);
            this.primaryKeyCache = value; // Update the cache
        }
    }

    // Modify getFcmToken to use the callback
    public void getFcmTokenAsync(FcmTokenCallback callback) {
        synchronized (fcmTokenLock) {
            if (fcmTokenCache == null || fcmTokenCache.isEmpty()) {
                Object value = getPreferences().getValue(LaudspeakerPreferences.FCM_TOKEN, null);
                if (value instanceof String && !((String) value).isEmpty()) {
                    fcmTokenCache = (String) value;
                    callback.onTokenReceived(fcmTokenCache);
                } else {
                    FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
                        if (!task.isSuccessful()) {
                            Exception e = task.getException();
                            config.getLogger().log("Fetching FCM registration token failed: " + e);
                            callback.onError(e);
                        } else {
                            // Assuming the token is successfully retrieved and not null
                            fcmTokenCache = task.getResult();
                            config.getLogger().log("Retrieved FCM Token: " + fcmTokenCache);
                            setFcmToken(fcmTokenCache); // Make sure to implement this method to save the token
                            callback.onTokenReceived(fcmTokenCache);
                        }
                    });
                }
            } else {
                // If the token is already cached, return it immediately
                callback.onTokenReceived(fcmTokenCache);
            }
        }
    }

    public void setFcmToken(String value) {
        synchronized (fcmTokenLock) {
            getPreferences().setValue(LaudspeakerPreferences.FCM_TOKEN, value);
            this.fcmTokenCache = value; // Update the cache
        }
    }

    public LaudspeakerPreferences getPreferences() {
        return memoryPreferences;
    }

    private boolean isEnabled() {
        if (!enabled) {
            if (config != null) config.getLogger().log("Setup isn't called.");
        }
        return enabled;
    }

    public void capture(String event, Map<String, Object> properties) {

        try {
            if (!isEnabled()) {
                return;
            }

            String customerId = getCustomerId();

            if (customerId == null || customerId.trim().isEmpty()) {
                if (config != null) {
                    config.getLogger().log("capture call not allowed, customer ID is invalid: " + customerId);
                }
                return;
            }

            Map<String, Object> mergedProperties = buildProperties(properties);
            Map<String, Object> sanitizedProperties = config != null && config.getPropertiesSanitizer() != null ? config.getPropertiesSanitizer().sanitize(mergedProperties) : mergedProperties;

            LaudspeakerEvent laudspeakerEvent = new LaudspeakerEvent(event, customerId, sanitizedProperties);


            if (queue != null) {
                queue.add(laudspeakerEvent);
            }

        } catch (Throwable e) {
            if (config != null) {
                config.getLogger().log("Capture failed: " + e);
            }
        }
    }

    public void identify(String primaryKey, Map<String, Object> userProperties) {

        if (!isEnabled()) {
            return;
        }

        Map<String, Object> props = userProperties == null ? new HashMap<>() : userProperties;


        if (primaryKey == null || primaryKey.trim().isEmpty()) {
            if (config != null) {
                config.getLogger().log("identify call not allowed, primary key is invalid: " + primaryKey);
            }
            return;
        } else {
            props.put("distinct_id", primaryKey);
        }

        String previousPrimaryKey = getPrimaryKey();

        capture("$identify", props);

        // Check if primary key being set is the same as previously set
        if (!previousPrimaryKey.equals(primaryKey)) {
            setPrimaryKey(primaryKey);
        }

    }

    public void set(Map<String, Object> userProperties) {

        if (!isEnabled()) {
            return;
        }

        capture("$set", userProperties);
    }

    public void sendFcmTokenAsync() {
        if (!isEnabled()) {
            return;
        }

        getFcmTokenAsync(new FcmTokenCallback() {
            @Override
            public void onTokenReceived(String token) {
                if (token != null && !token.trim().isEmpty()) {
                    Map<String, Object> props = new HashMap<>();
                    props.put("androidDeviceToken", token);
                    capture("$fcm", props);
                } else {
                    if (config != null) {
                        config.getLogger().log("sendFcmToken called but token was empty.");
                    }
                }
            }

            @Override
            public void onError(Exception exception) {
                if (config != null) {
                    config.getLogger().log("Failed to fetch FCM token: " + exception.toString());
                }
            }
        });
    }

    private Map<String, Object> buildProperties(Map<String, Object> properties) {

        Map<String, Object> props = new HashMap<>();

        if (properties != null) {
            props.putAll(properties);
        }
        return props;
    }

    public void close() {
        synchronized (setupLock) {
            try {
                enabled = false;

                if (config != null) {
                    apiKey = null;
                }

                if (queue != null) {
                    queue.stop();
                }

            } catch (Throwable e) {
                if (config != null) {
                    config.getLogger().log("Close failed: " + e);
                }
            }
        }
    }

    public void reset() {
        if (!isEnabled()) {
            return;
        }

        List<String> except = Arrays.asList(LaudspeakerPreferences.VERSION, LaudspeakerPreferences.BUILD);
        getPreferences().clear(except);
        if (queue != null) {
            queue.clear();
        }
    }

    // Define a callback interface
    public interface FcmTokenCallback {
        void onTokenReceived(String token);

        void onError(Exception exception);
    }
}