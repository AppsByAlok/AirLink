package com.appsbyalok.airlink.transfer;

import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.net.Uri;
import android.util.Base64;
import android.util.Log;


import com.appsbyalok.airlink.bluetooth.BluetoothService;
import com.appsbyalok.airlink.core.AppConstants;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Handles the actual reading and writing of data over the active Bluetooth Socket.
 */
public class StreamWorker extends Thread {
    private static final String TAG = "StreamWorker";

    private final BluetoothSocket mmSocket;
    private final InputStream mmInStream;
    private final OutputStream mmOutStream;
    private final BluetoothService mService;

    // A single thread executor guarantees that text and files are sent sequentially,
    // preventing interleaving of bytes in the OutputStream.
    private final ExecutorService writeExecutor = Executors.newSingleThreadExecutor();

    private final FileReceiver fileReceiver;
    private final FileSender fileSender;

    public StreamWorker(BluetoothSocket socket, BluetoothService service) {
        Log.d(TAG, "Creating StreamWorker");
        mmSocket = socket;
        mService = service;

        InputStream tmpIn = null;
        OutputStream tmpOut = null;

        try {
            tmpIn = socket.getInputStream();
            tmpOut = socket.getOutputStream();
        } catch (IOException e) {
            Log.e(TAG, "Error creating streams", e);
        }

        mmInStream = tmpIn;
        mmOutStream = tmpOut;

        fileReceiver = new FileReceiver(service);
        fileSender = new FileSender(service, mmOutStream);
    }

    public void run() {
        Log.i(TAG, "BEGIN StreamWorker");
        // Use ByteArrayOutputStream instead of StringBuilder to safely handle multibyte UTF-8 characters
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        // Keep listening to the InputStream until an exception occurs
        while (true) {
            try {
                // Read byte-by-byte for headers to prevent over-buffering binary file data
                int b = mmInStream.read();
                if (b == -1) break; // End of stream

                if (b == '\n') {
                    // Frame complete, parse the bytes into a UTF-8 string safely
                    String command = new String(buffer.toByteArray(), StandardCharsets.UTF_8);
                    processCommand(command);
                    buffer.reset(); // Reset for the next message
                } else {
                    buffer.write(b);
                }
            } catch (IOException e) {
                Log.e(TAG, "Connection lost during read", e);
                mService.connectionLost();
                break;
            }
        }
    }

    private void processCommand(String command) throws IOException {
        String[] parts = command.split("\\" + AppConstants.PROTOCOL_SEPARATOR);
        if (parts.length == 0) return;

        String header = parts[0];

        if (AppConstants.HEADER_MSG.equals(header) && parts.length > 1) {
            String payload = command.substring(AppConstants.HEADER_MSG.length() + AppConstants.PROTOCOL_SEPARATOR.length());

            try {
                // Decode Base64 to safely restore multiline and international text
                byte[] decodedBytes = Base64.decode(payload, Base64.NO_WRAP);
                String text = new String(decodedBytes, StandardCharsets.UTF_8);
                broadcastMessage(text, false);
            } catch (IllegalArgumentException e) {
                // Fallback just in case we receive plain text from an older un-updated version of the app
                broadcastMessage(payload, false);
            }

        } else if (AppConstants.HEADER_FILE.equals(header) && parts.length >= 3) {
            String fileName = parts[1];
            long fileSize = Long.parseLong(parts[2]);

            broadcastMessage("Receiving file: " + fileName + "...", false);

            // Delegate the InputStream to the FileReceiver to parse exact binary chunk
            boolean success = fileReceiver.receiveFile(mmInStream, fileName, fileSize);

            if (success) {
                broadcastMessage("File saved to Downloads: " + fileName, false);
            } else {
                broadcastMessage("File transfer failed.", false);
            }
        }
    }

    /**
     * Write text asynchronously
     */
    public void writeMessage(String text) {
        writeExecutor.execute(() -> {
            try {
                // Base64 encode the text so that multi-line '\n' and UTF-8 characters
                // transmit safely without breaking the protocol frame delimiter.
                String base64Text = Base64.encodeToString(text.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);

                String framedMsg = AppConstants.HEADER_MSG + AppConstants.PROTOCOL_SEPARATOR + base64Text + "\n";
                mmOutStream.write(framedMsg.getBytes(StandardCharsets.UTF_8));
                mmOutStream.flush();
                broadcastMessage(text, true); // Update local UI with original text
            } catch (IOException e) {
                Log.e(TAG, "Exception during text write", e);
            }
        });
    }

    /**
     * Write file asynchronously
     */
    public void writeFile(Uri fileUri) {
        writeExecutor.execute(() -> {
            boolean success = fileSender.sendFile(fileUri);
            if (success) {
                broadcastMessage("File sent successfully.", true);
            } else {
                broadcastMessage("Failed to send file.", true);
            }
        });
    }

    private void broadcastMessage(String text, boolean isMine) {
        Intent intent = new Intent(AppConstants.ACTION_MESSAGE_RECEIVED);
        intent.putExtra("text", text);
        intent.putExtra("isMine", isMine);
        intent.setPackage(mService.getPackageName()); // REQUIRED FOR ANDROID 14+
        mService.sendBroadcast(intent);
    }

    public void cancel() {
        try {
            writeExecutor.shutdownNow();
            if (mmSocket != null) {
                mmSocket.close();
            }
        } catch (IOException e) {
            Log.e(TAG, "close() of connect socket failed", e);
        }
    }
}