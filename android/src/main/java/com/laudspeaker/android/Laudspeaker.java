package com.laudspeaker.android;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.google.gson.Gson;

import java.io.File;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.jakewharton.threetenabp.AndroidThreeTen;

import org.threeten.bp.ZonedDateTime;
import org.threeten.bp.ZoneId;
import org.threeten.bp.format.DateTimeFormatter;
import org.threeten.bp.LocalTime;

import io.sentry.ISpan;
import io.sentry.Sentry;
import io.sentry.SentryEvent;
import io.sentry.android.core.SentryAndroid;
import io.sentry.ITransaction;
import io.sentry.protocol.Message;


public class Laudspeaker extends FirebaseMessagingService {
    private static int notificationIconResId = com.google.android.gms.base.R.drawable.common_google_signin_btn_icon_dark; // Default icon in the library
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


    public static <T extends LaudspeakerConfig> Laudspeaker with(T config, Context context) {
        Laudspeaker instance = new Laudspeaker(); // Assuming there's a default constructor or appropriate constructor available
        AndroidThreeTen.init(context);
        if (!Sentry.isEnabled()) {
            SentryAndroid.init(context, options -> {
                options.setDsn(config.getSentryDSN());
                options.setTracesSampleRate(1.0);
            });
        }
        instance.setup(config);
        instance.getFcmTokenAsync(new FcmTokenCallback() {
            @Override
            public void onTokenReceived(String token) {
                if (token != null && !token.trim().isEmpty() && config != null) {
                    config.getLogger().log("Retrieved FCM token: " + token);
                } else {
                    if (config != null) {
                        config.getLogger().log("getFCMToken called but token was empty.");
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

                if (config.getUpdatedHost()) {
                    this.memoryPreferences.setValue(LaudspeakerPreferences.HOST, config.getHost());
                }
                if (config.getUpdatedKey()) {
                    this.memoryPreferences.setValue(LaudspeakerPreferences.API_KEY, config.getApiKey());
                }
                if (config.getUpdatedClass()) {
                    this.memoryPreferences.setTargetActivityClass(config.getTargetActivityClass());
                }
                if (config.getUpdatedDSN()) {
                    this.memoryPreferences.setValue(LaudspeakerPreferences.SENTRY_DSN, config.getSentryDSN());
                }

                this.enabled = true;

                queue.start();

            } catch (Throwable e) {
                config.getLogger().log("Setup failed: " + e);
            }
        }
    }

    public void setNotificationIcon(int resId) {
        notificationIconResId = resId;
    }

    public int getNotificationIconResId() {
        return notificationIconResId;
    }

    public void handlePushOpened(Intent intent) {
        ITransaction transaction = Sentry.startTransaction("LaudspeakerAndroid.handlePushOpened()", "task");
        try {
            if (intent != null && intent.getExtras() != null) {
                Map<String, Object> openMessage = new HashMap<>();
                openMessage.put("customerID", intent.getStringExtra("customerID"));
                openMessage.put("stepID", intent.getStringExtra("stepID"));
                openMessage.put("templateID", intent.getStringExtra("templateID"));
                openMessage.put("messageID", intent.getStringExtra("messageID"));
                openMessage.put("workspaceID", intent.getStringExtra("workspaceID"));
                this.capture("$opened", openMessage);
            }
        } catch (Exception e) {
            Sentry.captureException(e);
        } finally {
            transaction.finish();
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
//                            callback.onError(e);
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
        ITransaction transaction = Sentry.startTransaction("LaudspeakerAndroid.capture()", "task");
        try {
            if (!isEnabled()) {
                config.getLogger().log("capture call not allowed, Laudspeaker instance not enabled.");
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
            Sentry.captureException(e);
        } finally {
            transaction.finish();
        }
    }

    public void identify(String primaryKey, Map<String, Object> userProperties) {
        ITransaction transaction = Sentry.startTransaction("LaudspeakerAndroid.identify()", "task");

        try {
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
        } catch (Exception e) {
            Sentry.captureException(e);
        } finally {
            transaction.finish();
        }

    }

    public void set(Map<String, Object> userProperties) {
        ITransaction transaction = Sentry.startTransaction("LaudspeakerAndroid.set()", "task");
        try {
            if (!isEnabled()) {
                return;
            }

            capture("$set", userProperties);
        } catch (Exception e) {
            Sentry.captureException(e);
        } finally {
            transaction.finish();
        }
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

        // Check if config and context are not null, then get static context and add to props
        if (config != null && config.getLaudspeakerContext() != null) {
            Map<String, Object> staticContext = config.getLaudspeakerContext().getStaticContext();
            if (staticContext != null) {
                props.putAll(staticContext);
            }

            // Get dynamic context and add to props
            Map<String, Object> dynamicContext = config.getLaudspeakerContext().getDynamicContext();
            if (dynamicContext != null) {
                props.putAll(dynamicContext);
            }
        }


        if (properties != null) {
            props.putAll(properties);
        }
        return props;
    }

    public void close() {
        ITransaction transaction = Sentry.startTransaction("LaudspeakerAndroid.close()", "task");
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
                Sentry.captureException(e);
            } finally {
                transaction.finish();
            }
        }
    }

    public boolean isQuietHours(Map<String, String> data) {
        ITransaction transaction = Sentry.startTransaction("LaudspeakerAndroid.isQuietHours()", "task");
        boolean isQuietHour = false;
        try {

            Gson gson = new Gson();
            QuietHours quietHours = gson.fromJson(data.get("quietHours"), QuietHours.class);

            if (quietHours != null) {
                String utcStartTime = convertTimeToUTC(quietHours.getStartTime(), 0);
                String utcEndTime = convertTimeToUTC(quietHours.getEndTime(), 0);

                ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");
                String utcNowString = now.format(formatter);

                isQuietHour = isWithinInterval(utcStartTime, utcEndTime, utcNowString);
            }
        } catch (Exception e) {
            Sentry.captureException(e);
        } finally {
            transaction.finish();
        }
        return isQuietHour;
    }

    public void reset() {
        ITransaction transaction = Sentry.startTransaction("LaudspeakerAndroid.reset()", "task");
        try {
            if (!isEnabled()) {
                return;
            }

            List<String> except = Arrays.asList(LaudspeakerPreferences.VERSION, LaudspeakerPreferences.BUILD);
            getPreferences().clear(except);
            if (queue != null) {
                queue.clear();
            }
        } catch (Exception e) {
            Sentry.captureException(e);
        } finally {
            transaction.finish();
        }
    }

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        Map<String, String> data = remoteMessage.getData();
        String title = null;
        String body = null;

        // Prioritize notification payload if it exists
        if (remoteMessage.getNotification() != null) {
            title = remoteMessage.getNotification().getTitle();
            body = remoteMessage.getNotification().getBody();
        }

        // If notification payload does not exist, use data payload
        if (title == null || body == null) {
            title = data.get("title");
            body = data.get("body");
        }

        // Pass data and title, body to the handler
        handleDataMessage(data, title, body);
    }

    private void createNotificationChannel() {
        ITransaction transaction = Sentry.startTransaction("LaudspeakerAndroid.createNotificationChannel()", "task");
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                CharSequence name = "My Notification Channel";
                String description = "Channel description";
                int importance = NotificationManager.IMPORTANCE_HIGH;
                NotificationChannel channel = new NotificationChannel("CHANNEL_ID", name, importance);
                channel.setDescription(description);
                NotificationManager notificationManager = getSystemService(NotificationManager.class);
                notificationManager.createNotificationChannel(channel);
            }
        } catch (Exception e) {
            Sentry.captureException(e);
        } finally {
            transaction.finish();
        }

    }

    public void notifyDelivered(Map<String, String> data) {
        ITransaction transaction = Sentry.startTransaction("LaudspeakerAndroid.notifyDelivered()", "task");
        try {
            Map<String, Object> deliveryMessage = new HashMap<>();
            deliveryMessage.put("customerID", data.get("customerID"));
            deliveryMessage.put("stepID", data.get("stepID"));
            deliveryMessage.put("templateID", data.get("templateID"));
            deliveryMessage.put("messageID", data.get("messageID"));
            deliveryMessage.put("workspaceID", data.get("workspaceID"));

            if (!this.isEnabled()) {
                Context context = this.getApplicationContext();
                LaudspeakerAndroidConfig config = new LaudspeakerAndroidConfig(null);
                config.setLogger(new LaudspeakerLogger(config));
                File path = new File(context.getCacheDir(), "laudspeaker-disk-queue");
                config.setStoragePrefix(config.getStoragePrefix() == null ? path.getAbsolutePath() : config.getStoragePrefix());
                LaudspeakerPreferences preferences = config.getCachePreferences() == null ? new LaudspeakerPreferences(context) : config.getCachePreferences();
                config.setCachePreferences(preferences);
                config.setNetworkStatus(config.getNetworkStatus() == null ? new LaudspeakerNetworkStatus(context) : config.getNetworkStatus());
                config.setSdkVersion("1");
                config.setSdkName("laudspeaker-android");
                this.setup(config);
            }

            this.capture("$delivered", deliveryMessage);
        } catch (Exception e) {
            Sentry.captureException(e);
        } finally {
            transaction.finish();
        }

    }

    /*
    WARNING:DO NOT USE ANY DEFAULT-NULL CLASS VARIABLES HERE
     */
    private void handleDataMessage(Map<String, String> data,String title, String body) {
        this.notifyDelivered(data);

        if (this.isQuietHours(data)) return;

        createNotificationChannel();

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "CHANNEL_ID").setSmallIcon(this.getNotificationIconResId()).setContentTitle(title).setContentText(body).setPriority(NotificationCompat.PRIORITY_MAX);

        Intent intent = new Intent(this, this.config.getCachePreferences().getTargetActivityClass());
        intent.putExtra("customerID", data.get("customerID"));
        intent.putExtra("stepID", data.get("stepID"));
        intent.putExtra("templateID", data.get("templateID"));
        intent.putExtra("messageID", data.get("messageID"));
        intent.putExtra("workspaceID", data.get("workspaceID"));
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        builder.setContentIntent(pendingIntent);
        builder.setAutoCancel(true);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);

        int notificationId = (int) System.currentTimeMillis();
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        notificationManager.notify(notificationId, builder.build());
    }

    private static String convertTimeToUTC(String localTime, int utcOffsetMinutes) {
        // This method should convert local time to UTC based on the utcOffsetMinutes.
        // Placeholder implementation. The real implementation will depend on how the times are represented.
        LocalTime time = LocalTime.parse(localTime);
        return time.minusMinutes(utcOffsetMinutes).format(DateTimeFormatter.ofPattern("HH:mm"));
    }

    private static boolean isWithinInterval(String startTime, String endTime, String currentTime) {
        // This method checks if currentTime is within the interval [startTime, endTime].
        // Note: This simplistic implementation may not handle over-midnight spans correctly.
        LocalTime start = LocalTime.parse(startTime);
        LocalTime end = LocalTime.parse(endTime);
        LocalTime current = LocalTime.parse(currentTime);

        if (start.isBefore(end)) {
            return !current.isBefore(start) && !current.isAfter(end);
        } else { // Handles the over-midnight case
            return !current.isBefore(start) || !current.isAfter(end);
        }
    }

    /*
    WARNING:DO NOT USE ANY DEFAULT-NULL CLASS VARIABLES HERE
     */
    private void handleNotification(String messageBody) {
        System.out.println("Got a notification message:" + messageBody.toString());
    }


    // Define a callback interface
    public interface FcmTokenCallback {
        void onTokenReceived(String token);

        void onError(Exception exception);
    }
}