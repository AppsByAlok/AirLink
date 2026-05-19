package com.appsbyalok.airlink.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

import com.appsbyalok.airlink.core.AppConstants;

import java.io.IOException;

/**
 * Background thread that listens for incoming connection requests.
 * It acts like a server and runs until a connection is accepted or canceled.
 */
public class ServerThread extends Thread {
    private static final String TAG = "ServerThread";
    private final BluetoothServerSocket mmServerSocket;
    private final BluetoothService mService;

    public ServerThread(BluetoothAdapter adapter, BluetoothService service) {
        mService = service;
        BluetoothServerSocket tmp = null;

        try {
            // MY_UUID is the app's UUID string, also used by the client code
            tmp = adapter.listenUsingRfcommWithServiceRecord(AppConstants.SERVICE_NAME, AppConstants.SPP_UUID);
        } catch (IOException e) {
            Log.e(TAG, "Socket's listen() method failed", e);
        }
        mmServerSocket = tmp;
    }

    public void run() {
        BluetoothSocket socket;

        // Keep listening until an exception occurs or a socket is returned
        while (mService.getState() != ConnectionManager.STATE_CONNECTED) {
            try {
                if (mmServerSocket != null) {
                    socket = mmServerSocket.accept();
                } else {
                    break;
                }
            } catch (IOException e) {
                Log.e(TAG, "Socket's accept() method failed", e);
                break;
            }

            // If a connection was accepted
            if (socket != null) {
                synchronized (mService) {
                    switch (mService.getState()) {
                        case ConnectionManager.STATE_LISTEN:
                        case ConnectionManager.STATE_CONNECTING:
                            // Situation normal. Start the connected thread.
                            mService.connected(socket, socket.getRemoteDevice());
                            break;
                        case ConnectionManager.STATE_NONE:
                        case ConnectionManager.STATE_CONNECTED:
                            // Either not ready or already connected. Terminate new socket.
                            try {
                                socket.close();
                            } catch (IOException e) {
                                Log.e(TAG, "Could not close unwanted socket", e);
                            }
                            break;
                    }
                }
            }
        }
    }

    // Closes the connect socket and causes the thread to finish.
    public void cancel() {
        try {
            if (mmServerSocket != null) mmServerSocket.close();
        } catch (IOException e) {
            Log.e(TAG, "Could not close the connect socket", e);
        }
    }
}