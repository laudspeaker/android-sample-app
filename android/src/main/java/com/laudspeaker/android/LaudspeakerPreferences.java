package com.laudspeaker.android;


import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class LaudspeakerPreferences {
    private final Object lock = new Object();
    private final Map<String, Object> preferences = new HashMap<>();
    // Constants
    public static final String ANONYMOUS_ID = "anonymousId";
    public static final String DISTINCT_ID = "distinctId";
    public static final String FCM_TOKEN = "fcmToken";

    public static final String VERSION = "version";
    public static final String BUILD = "build";

    public static final Set<String> ALL_INTERNAL_KEYS = Set.of(ANONYMOUS_ID, DISTINCT_ID, VERSION, BUILD);

    public Object getValue(String key, Object defaultValue) {
        synchronized (lock) {
            return preferences.getOrDefault(key, defaultValue);
        }
    }

    public void setValue(String key, Object value) {
        synchronized (lock) {
            preferences.put(key, value);
        }
    }

    public void clear(List<String> except) {
        synchronized (lock) {
            preferences.keySet().retainAll(except);
        }
    }

    public void remove(String key) {
        synchronized (lock) {
            preferences.remove(key);
        }
    }

    public Map<String, Object> getAll() {
        synchronized (lock) {
            return preferences.entrySet().stream().filter(entry -> !ALL_INTERNAL_KEYS.contains(entry.getKey())).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        }
    }
}