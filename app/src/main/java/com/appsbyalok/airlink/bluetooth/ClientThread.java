package com.appsbyalok.airlink.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

import com.appsbyalok.airlink.core.AppConstants;

import java.io.IOException;

/**
 * Background thread that attempts to make an outgoing connection
 * with a device. It runs straight through; the connection either
 * succeeds or fails.
 */
public class ClientThread extends Thread {
    private static final String TAG = "ClientThread";
    private final BluetoothSocket mmSocket;
    private final BluetoothDevice mmDevice;
    private final BluetoothAdapter mAdapter;
    private final BluetoothService mService;

    public ClientThread(BluetoothDevice device, BluetoothAdapter adapter, BluetoothService service) {
        mmDevice = device;
        mAdapter = adapter;
        mService = service;
        BluetoothSocket tmp = null;

        try {
            // Get a BluetoothSocket to connect with the given BluetoothDevice
            tmp = device.createRfcommSocketToServiceRecord(AppConstants.SPP_UUID);
        } catch (IOException e) {
            Log.e(TAG, "Socket's create() method failed", e);
        }
        mmSocket = tmp;
    }

    public void run() {
        // Cancel discovery because it otherwise slows down the connection.
        if (mAdapter.isDiscovering()) {
            mAdapter.cancelDiscovery();
        }

        try {
            // Connect to the remote device through the socket. This call blocks
            // until it succeeds or throws an exception.
            mmSocket.connect();
        } catch (IOException connectException) {
            Log.e(TAG, "Connection failed", connectException);
            // Unable to connect; close the socket and return
            try {
                mmSocket.close();
            } catch (IOException closeException) {
                Log.e(TAG, "Could not close the client socket", closeException);
            }
            mService.connectionFailed();
            return;
        }

        // Reset the ClientThread because we're done
        synchronized (mService) {
            mService.resetClientThread();
        }

        // Perform the connected work
        mService.connected(mmSocket, mmDevice);
    }

    // Closes the client socket and causes the thread to finish.
    public void cancel() {
        try {
            mmSocket.close();
        } catch (IOException e) {
            Log.e(TAG, "Could not close the client socket", e);
        }
    }
}