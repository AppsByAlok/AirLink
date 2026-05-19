package com.appsbyalok.airlink.core;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

public class PermissionsHelper {

    public static final int PERMISSION_REQUEST_CODE = 101;

    /**
     * Consolidates Android version checking for permissions
     */
    public static String[] getRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ (API 33+) requires the POST_NOTIFICATIONS permission
            return new String[]{
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.POST_NOTIFICATIONS
            };
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12 (API 31-32)
            return new String[]{
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_ADVERTISE
            };
        } else {
            // Android 11 and lower
            return new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            };
        }
    }

    /**
     * Checks if all required permissions are already granted
     */
    public static boolean hasAllPermissions(Context context) {
        for (String permission : getRequiredPermissions()) {
            if (context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    /**
     * Requests permissions if they are not yet granted
     */
    public static void requestPermissions(Activity activity) {
        if (!hasAllPermissions(activity)) {
            activity.requestPermissions(getRequiredPermissions(), PERMISSION_REQUEST_CODE);
        }
    }
}