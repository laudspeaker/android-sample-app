package com.laudspeaker.android;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.UiModeManager;
import android.content.Context;
import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.util.DisplayMetrics;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

public class LaudspeakerContext {

    private final Context context;
    private final LaudspeakerConfig config;
    private Map<String, Object> cacheStaticContext;

    public LaudspeakerContext(Context context, LaudspeakerConfig config) {
        this.context = context;
        this.config = config;
    }

    public static int densityValue(int value, float density) {
        return (int) (value / density);
    }

    public Map<String, Object> getStaticContext() {
        if (cacheStaticContext == null) {
            cacheStaticContext = new HashMap<>();

            DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();
            cacheStaticContext.put("$screen_density", displayMetrics.density);
            cacheStaticContext.put("$screen_height", densityValue(displayMetrics.heightPixels, displayMetrics.density));
            cacheStaticContext.put("$screen_width", densityValue(displayMetrics.widthPixels, displayMetrics.density));

            // Add other context information
            // The method getPackageInfo() should be implemented to fetch package info

            cacheStaticContext.put("$app_name", context.getApplicationInfo().loadLabel(context.getPackageManager()));
            cacheStaticContext.put("$device_manufacturer", Build.MANUFACTURER);
            cacheStaticContext.put("$device_model", Build.MODEL);
            cacheStaticContext.put("$device_name", Build.DEVICE);
            cacheStaticContext.put("$device_type", getDeviceType(context, displayMetrics));
            cacheStaticContext.put("$os_name", "Android");
            cacheStaticContext.put("$os_version", Build.VERSION.RELEASE);
            cacheStaticContext.put("$lib", config.getSdkName());
            cacheStaticContext.put("$lib_version", config.getSdkVersion());
            //cacheStaticContext.put("$is_emulator", isEmulator());

            // Return the cache
        }
        return cacheStaticContext;
    }

    private String getDeviceType(Context context, DisplayMetrics displayMetrics) {
        if (context.getPackageManager().hasSystemFeature("amazon.hardware.fire_tv")) {
            return "TV";
        }

        UiModeManager uiManager = (UiModeManager) context.getSystemService(Context.UI_MODE_SERVICE);
        if (uiManager != null && uiManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION) {
            return "TV";
        }

        String deviceTypeFromResourceConfiguration = getDeviceTypeFromResourceConfiguration(context);
        return deviceTypeFromResourceConfiguration != null ? deviceTypeFromResourceConfiguration : getDeviceTypeFromPhysicalSize(context, displayMetrics);
    }

    private String getDeviceTypeFromPhysicalSize(Context context, DisplayMetrics displayMetrics) {
        double widthInches;
        double heightInches;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            int densityDpi = context.getResources().getConfiguration().densityDpi;
            widthInches = displayMetrics.widthPixels / (double) densityDpi;
            heightInches = displayMetrics.heightPixels / (double) densityDpi;
        } else {
            widthInches = displayMetrics.widthPixels / displayMetrics.xdpi;
            heightInches = displayMetrics.heightPixels / displayMetrics.ydpi;
        }

        double diagonalSizeInches = Math.sqrt(Math.pow(widthInches, 2.0) + Math.pow(heightInches, 2.0));

        if (diagonalSizeInches >= 3.0 && diagonalSizeInches <= 6.9) {
            return "Mobile";
        } else if (diagonalSizeInches > 6.9 && diagonalSizeInches <= 18.0) {
            return "Tablet";
        } else {
            return null;
        }
    }

    private String getDeviceTypeFromResourceConfiguration(Context context) {
        int smallestScreenWidthDp = context.getResources().getConfiguration().smallestScreenWidthDp;

        if (smallestScreenWidthDp == Configuration.SMALLEST_SCREEN_WIDTH_DP_UNDEFINED) {
            return null;
        } else if (smallestScreenWidthDp >= 600) {
            return "Tablet";
        } else {
            return "Mobile";
        }
    }

    @SuppressLint("MissingPermission")
    public Map<String, Object> getDynamicContext() {
        Map<String, Object> dynamicContext = new HashMap<>();
        dynamicContext.put("$locale", Locale.getDefault().getLanguage() + "-" + Locale.getDefault().getCountry());
        dynamicContext.put("$user_agent", System.getProperty("http.agent"));
        dynamicContext.put("$timezone", TimeZone.getDefault().getID());

        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                NetworkCapabilities networkCapabilities = connectivityManager.getNetworkCapabilities(connectivityManager.getActiveNetwork());

                if (networkCapabilities != null) {
                    dynamicContext.put("$network_wifi", networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI));
                    dynamicContext.put("$network_bluetooth", networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH));
                    dynamicContext.put("$network_cellular", networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR));
                }
            }
        }

        return dynamicContext;
    }

    /*
    private boolean isEmulator() {
        return (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.HARDWARE.contains("goldfish")
                || Build.HARDWARE.contains("ranchu")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || Build.PRODUCT.contains("sdk")
                || Build.PRODUCT.contains("vbox86p")
                || Build.PRODUCT.contains("emulator")
                || Build.PRODUCT.contains("simulator");
    }
     */

    /*
    @Override
    public Map<String, Object> getStaticContext() {
        return getStaticContext();
    }
    */

}
