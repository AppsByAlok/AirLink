package com.appsbyalok.airlink.core;

import java.util.UUID;

public class AppConstants {
    // Standard Serial Port Profile (SPP) UUID for Chat/File transfers
    public static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    // Service Name for Server Socket
    public static final String SERVICE_NAME = "BTChatSecure";

    // Protocol Headers (Framing)
    public static final String HEADER_MSG = "[MSG]";
    public static final String HEADER_FILE = "[FILE]";
    public static final String HEADER_ACK = "[ACK]";

    // Protocol Delimiters
    public static final String MESSAGE_DELIMITER = "\n";
    public static final String PROTOCOL_SEPARATOR = "|";

    // Broadcast Intent Actions for background-to-UI communication
    public static final String ACTION_STATE_CHANGED = "com.bt.ACTION_STATE_CHANGED";
    public static final String ACTION_MESSAGE_RECEIVED = "com.bt.ACTION_MESSAGE_RECEIVED";
}