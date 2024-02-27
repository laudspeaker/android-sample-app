package com.laudspeaker.android;

import static android.content.ContentValues.TAG;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.messaging.FirebaseMessaging;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import io.socket.client.IO;
import io.socket.client.Socket;

public class LaudspeakerAndroid {
    private final SharedPreferences preferences; //Used for setting customerID for socket auth
    protected String host = "https://api.laudspeaker.com/"; // Default URL for connecting to server
    protected Socket socket; // Socket instance
    protected String apiKey; // API key for connecting to server
    protected boolean development = false; // Development mode switch
    protected boolean isAutomatedPush = false; // Indicates whether or not we automatically send over the FCM token
    private Context context; // Add a Context field
    private int retryCount = 0; // Used to keep track of how many times we've tried reconnecting to the server
    private long retryDelay = 1000; // Initial retry delay in milliseconds, starting at 1 second
    private long maxRetryDelay = 32000; // Max retry delay in milliseconds
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(); // Used for scheduling the connection retry
    private ScheduledFuture<?> retryFuture = null; // The connection retry task
    private final String MESSAGE_STORAGE_FILE = "message_queue.txt"; // Define the file path for storage
    private File messageFileWithPath; // File where we store messages until we successfully reconnect
    private List<ConnectListener> onConnectListenersList = new ArrayList<>(); // Any external objects that are listening for socket events
    private IO.Options ioOptions = new IO.Options(); // Global options for socket connection
    private Map<String, String> authMap = new HashMap<>(); // Authentication params for socket connection


    private static String trimmedURL(String urlString) {
        try {
            URL url = new URL(urlString);
            URI uri = new URI(url.getProtocol(), url.getUserInfo(), url.getHost(), url.getPort(), "/", null, null);
            return uri.toString();
        } catch (MalformedURLException | URISyntaxException e) {
            e.printStackTrace();
            return null;
        }
    }

    public LaudspeakerAndroid(Context context, SharedPreferences preferences, @NonNull String apiKey) {
        this(context, preferences, apiKey, null);
    }

    public LaudspeakerAndroid(Context context, SharedPreferences preferences, @NonNull String apiKey, String apiHost) {
        this(context, preferences, apiKey, apiHost, false);
    }

    public LaudspeakerAndroid(Context context, SharedPreferences preferences, @NonNull String apiKey, String apiHost, boolean isAutomatedPush) {
        this(context, preferences, apiKey, apiHost, isAutomatedPush, false);
    }

    public LaudspeakerAndroid(Context context, SharedPreferences preferences, @NonNull String apiKey, String apiHost, boolean isAutomatedPush, boolean development) {
        this.context = context;
        this.preferences = preferences;
        this.isAutomatedPush = isAutomatedPush;
        this.messageFileWithPath = new File(context.getFilesDir(), MESSAGE_STORAGE_FILE);

        // Ensure the file exists
        if (!messageFileWithPath.exists()) {
            try {
                boolean created = messageFileWithPath.createNewFile();
                if (!created) {
                    throw new RuntimeException("Socket message queue could not be created");
                }
            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
                // Handle possible IOException (e.g., no permission or disk space issues)
            }
        }

        try {
            this.connect(apiKey, apiHost, development);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public void connect(@NonNull String apiKey, String apiHost, boolean development) throws URISyntaxException {
        this.apiKey = apiKey;
        if (apiHost != null) this.host = apiHost;
        this.development = development;

        if (this.socket != null && this.socket.connected()) this.socket.disconnect();


        this.authMap.put("apiKey", this.apiKey);
        this.authMap.put("customerId", this.preferences.getString("customerId", ""));
        this.authMap.put("development", this.development ? "true" : "");

        this.ioOptions.auth = authMap;

        this.socket = IO.socket(trimmedURL(this.host), ioOptions);
        this.setupSocketListeners();
        this.socket.connect();
    }

    private void setupSocketListeners() {
        this.socket.on(Socket.EVENT_CONNECT, args -> onConnect(args)).on(Socket.EVENT_DISCONNECT, args -> onDisconnect(args)).on(Socket.EVENT_CONNECT_ERROR, args -> onConnectError(args)).on("error", args -> onError(args)).on("log", args -> onLog(args)).on("customerId", args -> onCustomerId(args)).on("flush", args -> onFlush(args))
        // Add other event listeners as needed
        ;
    }

    private void onConnect(Object[] args) {
        System.out.println("[laudspeaker API]: Connected to laudspeaker API websocket gateway");
        // Reset retry logic on successful connection
        this.retryCount = 0;
        this.retryDelay = 1000; // Reset the retry delay to its initial value
        for (ConnectListener connectListener : this.onConnectListenersList) {
            connectListener.onConnected();
        }
    }

    private void onDisconnect(Object[] args) {
        System.out.println("[laudspeaker API]: Disconnected from laudspeaker API websocket gateway");
        for (ConnectListener connectListener : this.onConnectListenersList) {
            connectListener.onDisconnected();
        }
        this.startRetry(); // Initiate reconnection attempt
    }

    private void onConnectError(Object[] args) {
        System.out.println("[laudspeaker API]: Error connecting to laudspeaker API websocket gateway");
        this.startRetry(); // Initiate reconnection attempt on connection error as well
    }

    private void onError(Object[] args) {
        System.out.println("[laudspeaker API]: Error:" + args[0]);
        for (ConnectListener connectListener : this.onConnectListenersList) {
            connectListener.onError();
        }
    }

    private void onLog(Object[] args) {
        System.out.println("[laudspeaker API]:" + args[0]);
        for (ConnectListener connectListener : this.onConnectListenersList) {
            connectListener.onError();
        }
    }

    private void onCustomerId(Object[] args) {
        this.preferences.edit().putString("customerId", (String) args[0]).apply();
        this.authMap.put("customerId", this.preferences.getString("customerId", ""));
        this.socket.connect();
        if (this.isAutomatedPush) this.sendFCMToken();
    }

    private void onFlush(Object[] args) {
        System.out.println("[laudspeaker API]: Flushing messages");
        this.flushMessages();
    }

    public void startRetry() {
        this.retryCount = 0; // Reset retry count
        this.retryDelay = 1000; // Reset retry delay
        this.scheduleRetryConnection();
    }

    private void scheduleRetryConnection() {
        if (this.retryFuture != null && !this.retryFuture.isDone()) {
            this.retryFuture.cancel(false); // Cancel previous task if still running
        }
        this.retryFuture = this.scheduler.scheduleWithFixedDelay(this::retryConnection, 0, this.retryDelay, TimeUnit.MILLISECONDS);
    }

    private void retryConnection() {
        if (this.socket.connected()) { // Assuming `socket.isConnected()` checks if the connection is established
            System.out.println("[laudspeaker API]: Connection established. Stopping retries.");
            this.retryFuture.cancel(true); // Stop retrying
            return;
        }

        try {
            System.out.println("[laudspeaker API]: Attempting to reconnect, attempt #" + (retryCount + 1));
            this.socket.connect(); // Attempt to connect
            retryCount++;
            retryDelay = Math.min(retryDelay * 2, maxRetryDelay); // Exponential backoff
        } catch (Exception e) {
            System.err.println("[laudspeaker API]: Retry failed: " + e.getMessage());
            // Schedule next retry with updated delay
            scheduleRetryConnection();
        }
    }

    public void identify(@NonNull String primary_key) {
        this.identify(primary_key, null);
    }

    public void identify(@NonNull String primary_key, @Nullable Map<String, Object> optionalProperties) {
        JSONObject msgObject = new JSONObject();
        try {
            msgObject.put("__PrimaryKey", primary_key);

            if (optionalProperties != null) {
                JSONObject optionalPropertiesObject = new JSONObject(optionalProperties);
                msgObject.put("optionalProperties", optionalPropertiesObject);
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        this.emit("identify", msgObject);
    }

    public void set(@NonNull Map<String, Object> properties) {
        JSONObject msgObject = new JSONObject();
        try {
            JSONObject optionalPropertiesObject = new JSONObject(properties);
            msgObject.put("optionalProperties", optionalPropertiesObject);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        this.emit("set", msgObject);
    }

    public void fire(@NonNull String event) {
        this.fire(event, null);
    }

    public void fire(@NonNull String event, @Nullable Map<String, Object> payload) {
        JSONObject msgObject = new JSONObject();
        String payloadString = "{}";
        try {
            if (payload != null) {
                JSONObject payloadJson = new JSONObject(payload);
                payloadString = payloadJson.toString();
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Unable to parse payload into string");
            // Handle exception
        }

        try {
            msgObject.put("eventName", event);
            msgObject.put("customerId", this.preferences.getString("customerId", ""));
            msgObject.put("payload", payloadString);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        this.emit("fire", msgObject);
    }

    public CompletableFuture<String> fireAPI(@NonNull String event) {
        return this.fireAPI(event, null);
    }

    public CompletableFuture<String> fireAPI(@NonNull String event, @Nullable Map<String, Object> payload) {
        return CompletableFuture.supplyAsync(() -> {
            String payloadString = "{}";
            try {
                if (payload != null) {
                    JSONObject payloadJson = new JSONObject(payload);
                    payloadString = payloadJson.toString();
                }
            } catch (Exception e) {
                e.printStackTrace();
                System.err.println("Unable to parse payload into string");
                // Handle exception
            }

            try {
                String customerId = this.preferences.getString("customerId", "");
                JSONObject parameters = new JSONObject();
                parameters.put("correlationKey", "_id");
                parameters.put("correlationValue", customerId);
                parameters.put("source", "mobile");
                parameters.put("event", event);
                parameters.put("payload", new JSONObject(payloadString)); // Since payload is a JSON string, we convert it back to JSONObject

                URL url = new URL(this.host + "events");
                HttpURLConnection http = (HttpURLConnection) url.openConnection();
                http.setRequestMethod("POST");
                http.setDoOutput(true);
                http.setRequestProperty("Authorization", "Api-Key " + this.apiKey);
                http.setRequestProperty("Content-Type", "application/json");
                byte[] input = parameters.toString().getBytes(StandardCharsets.UTF_8);


                try (OutputStream stream = http.getOutputStream()) {
                    stream.write(input);
                }
                int responseCode = http.getResponseCode();
                http.disconnect();

                return "Response Code: " + responseCode;
            } catch (Exception e) {
                e.printStackTrace();
                return "Error: " + e.getMessage();
            }
        });

    }

    public void sendFCMToken() {
        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                Log.w(TAG, "Fetching FCM registration token failed", task.getException());
                return;
            }

            String fcmToken = task.getResult();
            this.sendFCMToken(fcmToken);
        });
    }

    public void sendFCMToken(String fcmToken) {
        JSONObject object = new JSONObject();
        try {
            object.put("type", "Android");
            object.put("token", fcmToken);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        this.emit("fcm_token", object);
    }

    public void disconnect() {
        if (this.socket != null && this.socket.connected()) this.socket.disconnect();
    }

    public void ping() {
        this.emit("ping");
    }

    public void onConnected(ConnectListener connectLister) {
        this.onConnectListenersList.add(connectLister);
    }

    public void emit(String channel) {
        this.emit(channel, null);
    }

    public void emit(String channel, @Nullable JSONObject messageObject) {
        if (this.socket.connected()) {
            System.out.println("[laudspeaker API]: Emitting message:" + channel + ":" + messageObject.toString());
            this.socket.emit(channel, messageObject);
            this.retryCount = 0; // Reset retry count upon successful emit
            this.retryDelay = 1000; // Reset retry count upon successful emit
        } else {
            this.persistMessage(channel, messageObject); // Persist message if disconnected
        }
    }

    private void persistMessage(String channel, @Nullable JSONObject messageObject) {
        // Append the message to a file or in-memory structure
        System.out.println("[laudspeaker API]: Persisting message:" + channel + ":" + messageObject.toString());
        try (FileWriter fileWriter = new FileWriter(this.messageFileWithPath, true)) {
            fileWriter.append(channel + ":" + messageObject.toString() + "\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void flushMessages() {
        if (this.socket.connected()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(this.messageFileWithPath))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    // Assuming the line format is "channel:message"
                    int separatorIndex = line.indexOf(':');
                    if (separatorIndex != -1) {
                        String channel = line.substring(0, separatorIndex);
                        String message = line.substring(separatorIndex + 1);
                        System.out.println("[laudspeaker API]: Flushing message:" + channel + ":" + message);
                        this.socket.emit(channel, new JSONObject(message)); // Resend each persisted message
                    }
                }

                // Clear the file after flushing
                new FileWriter(this.messageFileWithPath, false).close();
            } catch (IOException | JSONException e) {
                e.printStackTrace();
            }
        }
    }
}
