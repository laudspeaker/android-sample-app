package com.laudspeaker.android;

import com.google.firebase.messaging.FirebaseMessaging;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Date;
import java.util.List;


public class Laudspeaker {
    private final ExecutorService queueExecutor;
    public volatile boolean enabled = false;
    private final Object setupLock = new Object();
    private final Object sessionLock = new Object();
    private final Object anonymousLock = new Object();
    private final Object distinctIdLock = new Object();
    private final Object fcmTokenLock = new Object();
    private LaudspeakerConfig config;
    private LaudspeakerQueue queue;
    private LaudspeakerPreferences memoryPreferences = new LaudspeakerPreferences();
    private UUID sessionIdNone = new UUID(0, 0);
    private UUID sessionId = sessionIdNone;
    private String anonymousIdCache;
    private String distinctIdCache;
    private String fcmTokenCache;
    private String apiKey;


    private Laudspeaker(ExecutorService queueExecutor) {
        this.queueExecutor = queueExecutor != null ? queueExecutor : Executors.newSingleThreadScheduledExecutor(new LaudspeakerThreadFactory("LaudspeakerQueueThread"));
    }

    public <T extends LaudspeakerConfig> void setup(T config) {
        synchronized (setupLock) {
            try {
                if (enabled) {
                    config.getLogger().log("Setup called despite already being setup!");
                    return;
                }

                if (apiKey == config.getApiKey()) {
                    config.getLogger().log("API Key: " + config.getApiKey() + " already has a PostHog instance.");
                }

                LaudspeakerPreferences cachePreferences = memoryPreferences;
                config.setCachePreferences(cachePreferences);
                LaudspeakerApi api = new LaudspeakerApi(config);
                queue = new LaudspeakerQueue(config, api, LaudspeakerApiEndpoint.EVENT, config.getStoragePrefix(), queueExecutor);

                Date startDate = config.getDateProvider().currentDate();

                this.config = config;
                this.queue = queue;

                this.enabled = true;

                queue.start();

                startSession();
            } catch (Throwable e) {
                config.getLogger().log("Setup failed: " + e.toString());
            }
        }
    }

    public static <T extends LaudspeakerConfig> Laudspeaker with(T config) {
        Laudspeaker instance = new Laudspeaker(null); // Assuming there's a default constructor or appropriate constructor available
        instance.setup(config);
        instance.sendFcmTokenAsync();
        return instance;
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

                endSession();
            } catch (Throwable e) {
                if (config != null) {
                    config.getLogger().log("Close failed: " + e.toString());
                }
            }
        }
    }

    public String getAnonymousId() {
        synchronized (anonymousLock) {
            if (anonymousIdCache == null || anonymousIdCache.isEmpty()) {
                Object value = getPreferences().getValue(LaudspeakerPreferences.ANONYMOUS_ID, null);
                if (value instanceof String && !((String) value).isEmpty()) {
                    anonymousIdCache = (String) value;
                } else {
                    anonymousIdCache = UUID.randomUUID().toString();
                    setAnonymousId(anonymousIdCache);
                }
            }
            return anonymousIdCache;
        }
    }

    public void setAnonymousId(String value) {
        synchronized (anonymousLock) {
            getPreferences().setValue(LaudspeakerPreferences.ANONYMOUS_ID, value);
            this.anonymousIdCache = value; // Cache the value to avoid fetching it repeatedly
        }
    }


    public String getDistinctId() {
        synchronized (distinctIdLock) {
            if (distinctIdCache == null || distinctIdCache.isEmpty() || distinctIdCache == "") {

                Object value = getPreferences().getValue(LaudspeakerPreferences.DISTINCT_ID, getAnonymousId());

                if (value instanceof String) {
                    distinctIdCache = (String) value;
                } else {
                    distinctIdCache = ""; // Default to empty string if value is not a string
                }
            }
        }
        return distinctIdCache;
    }

    public String getFcmToken() {
        synchronized (fcmTokenLock) {
            if (fcmTokenCache == null || fcmTokenCache.isEmpty() || fcmTokenCache == "") {

                Object value = getPreferences().getValue(LaudspeakerPreferences.FCM_TOKEN, null);

                if (value instanceof String) {
                    fcmTokenCache = (String) value;
                } else {
                    FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
                        if (!task.isSuccessful()) {
                            config.getLogger().log("Fetching FCM registration token failed: " + task.getException());
                            fcmTokenCache = "";
                        } else {
                            fcmTokenCache = task.getResult();
                            config.getLogger().log("Retrieved FCM Token: " + fcmTokenCache);
                            setFcmToken(fcmTokenCache);
                        }
                    });
                }
            }
        }
        return fcmTokenCache;
    }

    // Define a callback interface
    public interface FcmTokenCallback {
        void onTokenReceived(String token);

        void onError(Exception exception);
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

    public void setDistinctId(String value) {
        synchronized (distinctIdLock) {
            getPreferences().setValue(LaudspeakerPreferences.DISTINCT_ID, value);
            this.distinctIdCache = value; // Update the cache
        }
    }

    public void startSession() {
        synchronized (sessionLock) {
            if (sessionId == sessionIdNone) {
                sessionId = UUID.randomUUID();
            }
        }
    }

    public void endSession() {
        synchronized (sessionLock) {
            sessionId = sessionIdNone;
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

            String distinctId = getDistinctId();

            if (distinctId == null || distinctId.trim().isEmpty()) {
                if (config != null) {
                    config.getLogger().log("capture call not allowed, distinctId is invalid: " + distinctId);
                }
                return;
            }

            Map<String, Object> mergedProperties = buildProperties(properties);

            // Assuming there's a method to sanitize properties, similar to Kotlin's
            Map<String, Object> sanitizedProperties = config != null && config.getPropertiesSanitizer() != null ? config.getPropertiesSanitizer().sanitize(mergedProperties) : mergedProperties;

            LaudspeakerEvent postHogEvent = new LaudspeakerEvent(event, distinctId, sanitizedProperties);


            if (queue != null) {
                queue.add(postHogEvent);
            }

        } catch (Throwable e) {
            if (config != null) {
                config.getLogger().log("Capture failed: " + e.toString());
            }
        }
    }

    public void identify(String distinctId, Map<String, Object> userProperties) {

        if (!isEnabled()) {
            return;
        }

        Map<String, Object> props = userProperties == null ? new HashMap<>() : userProperties;


        if (distinctId == null || distinctId.trim().isEmpty()) {
            if (config != null) {
                config.getLogger().log("identify call not allowed, distinctId is invalid: " + distinctId);
            }
            return;
        } else {
            props.put("distinct_id", distinctId);
        }

        String previousDistinctId = getDistinctId();


        String anonymousId = getAnonymousId();
        if (anonymousId != null && !anonymousId.trim().isEmpty()) {
            props.put("$anon_distinct_id", anonymousId);
        } else {
            if (config != null) {
                config.getLogger().log("identify called with invalid anonymousId: " + anonymousId);
            }
        }

        capture("$identify", props);

        if (!previousDistinctId.equals(distinctId)) {
            if (previousDistinctId != null && !previousDistinctId.trim().isEmpty()) {
                setAnonymousId(previousDistinctId);
            } else {
                if (config != null) {
                    config.getLogger().log("identify called with invalid former distinctId: " + previousDistinctId);
                }
            }
            setDistinctId(distinctId);
        }
    }

    public void set(Map<String, Object> userProperties) {

        if (!isEnabled()) {
            return;
        }

        Map<String, Object> props = new HashMap<>();
        String anonymousId = getAnonymousId();
        if (anonymousId != null && !anonymousId.trim().isEmpty()) {
            props.put("$anon_distinct_id", anonymousId);
        } else {
            if (config != null) {
                config.getLogger().log("identify called with invalid anonymousId: " + anonymousId);
            }
        }

        capture("$set", userProperties);
    }

    public void sendFcmToken() {

        if (!isEnabled()) {
            return;
        }

        String distinctId = getDistinctId();

        if (distinctId == null || distinctId.trim().isEmpty()) {
            if (config != null) {
                config.getLogger().log("capture call not allowed, distinctId is invalid: " + distinctId);
            }
            return;
        }

        Map<String, Object> props = new HashMap<>();
        String anonymousId = getAnonymousId();
        if (anonymousId != null && !anonymousId.trim().isEmpty()) {
            props.put("$anon_distinct_id", anonymousId);
        } else {
            if (config != null) {
                config.getLogger().log("identify called with invalid anonymousId: " + anonymousId);
            }
        }
        String fcmToken = getFcmToken();
        if (fcmToken != null && !fcmToken.trim().isEmpty()) {
            props.put("$anon_distinct_id", fcmToken);
        } else {
            if (config != null) {
                config.getLogger().log("sendFcmToken called with invalid fcmToken: " + fcmToken);
            }
        }

        capture("$fcm", props);
    }

    public void sendFcmTokenAsync() {
        if (!isEnabled()) {
            return;
        }

        String distinctId = getDistinctId();
        if (distinctId == null || distinctId.trim().isEmpty()) {
            if (config != null) {
                config.getLogger().log("capture call not allowed, distinctId is invalid: " + distinctId);
            }
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

    public void reset() {
        if (!isEnabled()) {
            return;
        }

        List<String> except = Arrays.asList(LaudspeakerPreferences.VERSION, LaudspeakerPreferences.BUILD);
        getPreferences().clear(except);
        if (queue != null) {
            queue.clear();
        }

        endSession();
    }
}