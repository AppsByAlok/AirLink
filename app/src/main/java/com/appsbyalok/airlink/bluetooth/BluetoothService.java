package com.appsbyalok.airlink.bluetooth;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import com.appsbyalok.airlink.core.AppConstants;
import com.appsbyalok.airlink.transfer.StreamWorker;
import com.appsbyalok.airlink.ui.MainActivity;


/**
 * A Foreground Service that handles all Bluetooth connections.
 * By running as a foreground service, the Android OS will not kill the
 * active chat socket when the user minimizes the app to look at something else.
 */
public class BluetoothService extends Service {
    private static final String TAG = "BluetoothService";
    private static final String CHANNEL_ID = "BTChatChannel";
    private static final int NOTIFICATION_ID = 101;

    private final IBinder mBinder = new LocalBinder();
    private BluetoothAdapter mAdapter;
    private ConnectionManager mConnectionManager;

    private ServerThread mServerThread;
    private ClientThread mClientThread;
    private StreamWorker mStreamWorker;

    // Restarts the server automatically when OS Bluetooth comes back online
    // and shuts down the service entirely if Bluetooth is turned off.
    private final BroadcastReceiver btStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())) {
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                if (state == BluetoothAdapter.STATE_ON) {
                    Log.i(TAG, "Bluetooth turned ON, restarting server thread");
                    startServer();
                } else if (state == BluetoothAdapter.STATE_OFF || state == BluetoothAdapter.STATE_TURNING_OFF) {
                    Log.i(TAG, "Bluetooth turned OFF, stopping threads and service");
                    if (mServerThread != null) { mServerThread.cancel(); mServerThread = null; }
                    if (mClientThread != null) { mClientThread.cancel(); mClientThread = null; }
                    if (mStreamWorker != null) { mStreamWorker.cancel(); mStreamWorker = null; }
                    setState(ConnectionManager.STATE_NONE);

                    // The user turned off Bluetooth, kill the persistent notification and service
                    stopServiceCleanly();
                }
            }
        }
    };

    public class LocalBinder extends Binder {
        public BluetoothService getService() {
            return BluetoothService.this;
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    public void onCreate() {
        super.onCreate();
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mConnectionManager = new ConnectionManager(this);
        createNotificationChannel();

        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(btStateReceiver, filter, Context.RECEIVER_VISIBLE_TO_INSTANT_APPS);
        } else {
            registerReceiver(btStateReceiver, filter);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, buildNotification("Listening for connections..."), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
        } else {
            startForeground(NOTIFICATION_ID, buildNotification("Listening for connections..."));
        }

        if (mAdapter != null && mAdapter.isEnabled()) {
            startServer();
        }

        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public synchronized int getState() {
        return mConnectionManager.getState();
    }

    private void setState(int state) {
        mConnectionManager.setState(state);
        updateNotification(state);
    }

    public synchronized void startServer() {
        Log.d(TAG, "startServer");

        if (mClientThread != null) { mClientThread.cancel(); mClientThread = null; }
        if (mStreamWorker != null) { mStreamWorker.cancel(); mStreamWorker = null; }

        if (mServerThread == null) {
            mServerThread = new ServerThread(mAdapter, this);
            mServerThread.start();
        }
        setState(ConnectionManager.STATE_LISTEN);

        // Tell the Widget that we are no longer actively chatting
        syncWidgetState(false, null);
    }

    public synchronized void connect(BluetoothDevice device) {
        Log.d(TAG, "connect to: " + device);

        if (mConnectionManager.getState() == ConnectionManager.STATE_CONNECTING) {
            if (mClientThread != null) { mClientThread.cancel(); mClientThread = null; }
        }

        if (mStreamWorker != null) { mStreamWorker.cancel(); mStreamWorker = null; }

        mClientThread = new ClientThread(device, mAdapter, this);
        mClientThread.start();
        setState(ConnectionManager.STATE_CONNECTING);
    }

    @SuppressLint("MissingPermission")
    public synchronized void connected(BluetoothSocket socket, BluetoothDevice device) {
        Log.d(TAG, "connected");

        if (mClientThread != null) { mClientThread.cancel(); mClientThread = null; }
        if (mStreamWorker != null) { mStreamWorker.cancel(); mStreamWorker = null; }
        if (mServerThread != null) { mServerThread.cancel(); mServerThread = null; }

        mStreamWorker = new StreamWorker(socket, this);
        mStreamWorker.start();

        Intent intent = new Intent(AppConstants.ACTION_STATE_CHANGED);
        intent.putExtra("device_name", device.getName());
        intent.putExtra("device_address", device.getAddress());
        intent.setPackage(getPackageName()); // REQUIRED FOR ANDROID 14+
        sendBroadcast(intent);

        setState(ConnectionManager.STATE_CONNECTED);

        // Tell the Widget we successfully connected to a device!
        syncWidgetState(true, device.getName());
    }

    public void writeMessage(String text) {
        if (mStreamWorker != null) {
            mStreamWorker.writeMessage(text);
        }
    }

    public void writeFile(Uri fileUri) {
        if (mStreamWorker != null) {
            mStreamWorker.writeFile(fileUri);
        }
    }

    public void connectionFailed() {
        Intent intent = new Intent(AppConstants.ACTION_STATE_CHANGED);
        intent.putExtra("toast", "Unable to connect device");
        intent.setPackage(getPackageName()); // REQUIRED FOR ANDROID 14+
        sendBroadcast(intent);

        syncWidgetState(false, null);

        if (mAdapter != null && mAdapter.isEnabled()) {
            BluetoothService.this.startServer();
        }
    }

    public void connectionLost() {
        Intent intent = new Intent(AppConstants.ACTION_STATE_CHANGED);
        intent.putExtra("toast", "Device connection was lost");
        intent.setPackage(getPackageName()); // REQUIRED FOR ANDROID 14+
        sendBroadcast(intent);

        syncWidgetState(false, null);

        if (mAdapter != null && mAdapter.isEnabled()) {
            BluetoothService.this.startServer();
        }
    }

    public synchronized void resetClientThread() {
        mClientThread = null;
    }

    // --- Foreground Notification Logic ---
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Bluetooth Chat Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Keeps the Bluetooth connection active");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(String text) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        );

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        return builder.setContentTitle("Bluetooth Chat Active")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setContentIntent(pendingIntent)
                .build();
    }

    private void updateNotification(int state) {
        String text = "Listening for connections...";
        if (state == ConnectionManager.STATE_CONNECTING) text = "Connecting...";
        else if (state == ConnectionManager.STATE_CONNECTED) text = "Connected to device";

        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, buildNotification(text));
        }
    }

    // --- Lifecycle and Cleanup Logic ---

    /**
     * Updates the SharedPreferences and pushes a Broadcast to force the Home Screen
     * Widget to update its layout immediately.
     */
    private void syncWidgetState(boolean isConnected, String deviceName) {
        SharedPreferences.Editor editor = getSharedPreferences("BT_PREFS", Context.MODE_PRIVATE).edit();
        editor.putBoolean("IS_CONNECTED", isConnected);
        if (deviceName != null) {
            editor.putString("CONNECTED_DEVICE_NAME", deviceName);
        }
        editor.apply();
        sendBroadcast(new Intent("com.bt.ACTION_WIDGET_STATE_UPDATE").setPackage(getPackageName()));
    }

    /**
     * Triggered by the Android OS when the user swipes the app away from the "Recent Apps" menu.
     */
    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        Log.i(TAG, "App swiped away, terminating Service completely.");

        syncWidgetState(false, null);
        stopServiceCleanly();
    }

    private void stopServiceCleanly() {
        // Force the OS Notification Manager to manually delete the notification
        // This bypasses the bug on Android 6 where bound services keep notifications alive
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.cancel(NOTIFICATION_ID);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            // Deprecated in API 33, but required for older devices
            stopForeground(true);
        }
        stopSelf();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(btStateReceiver);
        if (mServerThread != null) { mServerThread.cancel(); }
        if (mClientThread != null) { mClientThread.cancel(); }
        if (mStreamWorker != null) { mStreamWorker.cancel(); }

        // Failsafe cleanup
        syncWidgetState(false, null);
        stopServiceCleanly();
    }
}