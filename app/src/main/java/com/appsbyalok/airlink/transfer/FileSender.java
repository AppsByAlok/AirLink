package com.appsbyalok.airlink.transfer;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Log;

import com.appsbyalok.airlink.core.AppConstants;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Handles chunking large files into the OutputStream to prevent OutOfMemory crashes.
 */
public class FileSender {
    private static final String TAG = "FileSender";
    private static final int BUFFER_SIZE = 8192; // 8KB chunks

    private final Context context;
    private final OutputStream outStream;

    public FileSender(Context context, OutputStream outStream) {
        this.context = context;
        this.outStream = outStream;
    }

    public boolean sendFile(Uri fileUri) {
        String fileName = getFileName(fileUri);
        long fileSize = getFileSize(fileUri);

        if (fileName == null || fileSize <= 0) {
            Log.e(TAG, "Invalid file selected");
            return false;
        }

        try (InputStream fileInputStream = context.getContentResolver().openInputStream(fileUri)) {
            if (fileInputStream == null) return false;

            // 1. Send the Protocol Header: [FILE]|name.jpg|1024\n
            String header = AppConstants.HEADER_FILE + AppConstants.PROTOCOL_SEPARATOR +
                    fileName + AppConstants.PROTOCOL_SEPARATOR + fileSize + "\n";
            outStream.write(header.getBytes(StandardCharsets.UTF_8));
            outStream.flush();

            // 2. Stream the raw bytes in chunks
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            long totalBytesSent = 0;

            while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                outStream.write(buffer, 0, bytesRead);
                totalBytesSent += bytesRead;

                // Optional: You could broadcast totalBytesSent here to update a UI progress bar
            }

            outStream.flush();
            Log.i(TAG, "File sent completely: " + totalBytesSent + " bytes");
            return true;

        } catch (IOException e) {
            Log.e(TAG, "Error sending file chunks", e);
            return false;
        }
    }

    private String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme() != null && uri.getScheme().equals("content")) {
            try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (idx != -1) result = cursor.getString(idx);
                }
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result != null ? result.lastIndexOf('/') : -1;
            if (cut != -1) result = result.substring(cut + 1);
        }
        return result != null ? result : "unknown_file";
    }

    private long getFileSize(Uri uri) {
        long result = 0;
        if (uri.getScheme() != null && uri.getScheme().equals("content")) {
            try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int idx = cursor.getColumnIndex(OpenableColumns.SIZE);
                    if (idx != -1) result = cursor.getLong(idx);
                }
            }
        }
        return result;
    }
}