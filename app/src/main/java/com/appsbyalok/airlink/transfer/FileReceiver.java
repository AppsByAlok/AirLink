package com.appsbyalok.airlink.transfer;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Handles receiving exact byte counts from the InputStream and writing them directly
 * to the Android Downloads folder.
 */
public class FileReceiver {
    private static final String TAG = "FileReceiver";
    private static final int BUFFER_SIZE = 8192; // 8KB chunks

    private final Context context;

    public FileReceiver(Context context) {
        this.context = context;
    }

    public boolean receiveFile(InputStream inStream, String fileName, long expectedSize) {
        // Save to the app's external files directory under Downloads (Requires no extra permissions)
        File downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (downloadDir != null && !downloadDir.exists()) {
            downloadDir.mkdirs();
        }

        File targetFile = new File(downloadDir, sanitizeFileName(fileName));

        try (FileOutputStream fos = new FileOutputStream(targetFile)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            long totalBytesRead = 0;

            // Loop strictly bounds the reads to expectedSize so we don't accidentally
            // consume the next text message in the stream.
            while (totalBytesRead < expectedSize) {
                int bytesToRead = (int) Math.min(buffer.length, expectedSize - totalBytesRead);
                int bytesRead = inStream.read(buffer, 0, bytesToRead);

                if (bytesRead == -1) {
                    Log.e(TAG, "Stream ended prematurely before expected file size was reached.");
                    return false;
                }

                fos.write(buffer, 0, bytesRead);
                totalBytesRead += bytesRead;
            }

            fos.flush();
            Log.i(TAG, "File received completely: " + targetFile.getAbsolutePath());
            return true;

        } catch (IOException e) {
            Log.e(TAG, "Error receiving file chunks", e);
            // Clean up the partial/corrupted file
            if (targetFile.exists()) targetFile.delete();
            return false;
        }
    }

    private String sanitizeFileName(String name) {
        return name.replaceAll("[^a-zA-Z0-9.-]", "_");
    }
}