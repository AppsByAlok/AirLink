package com.appsbyalok.airlink.bluetooth;

import android.content.Context;
import android.content.Intent;

import com.appsbyalok.airlink.core.AppConstants;

/**
 * A Thread-Safe State Machine that holds the current status of the Bluetooth connection.
 * It broadcasts any state changes so your Canvas UI can update reactively.
 */
public class ConnectionManager {
    public static final int STATE_NONE = 0;       // Doing nothing
    public static final int STATE_LISTEN = 1;     // Listening for incoming connections (Server)
    public static final int STATE_CONNECTING = 2; // Initiating an outgoing connection (Client)
    public static final int STATE_CONNECTED = 3;  // Successfully connected to a remote device

    private int mState;
    private final Context mContext;

    public ConnectionManager(Context context) {
        mContext = context;
        mState = STATE_NONE;
    }

    public synchronized void setState(int state) {
        mState = state;

        // Broadcast the state change so the UI can update automatically
        Intent intent = new Intent(AppConstants.ACTION_STATE_CHANGED);
        intent.putExtra("state", state);
        intent.setPackage(mContext.getPackageName()); // REQUIRED FOR ANDROID 14+
        mContext.sendBroadcast(intent);
    }

    public synchronized int getState() {
        return mState;
    }
}