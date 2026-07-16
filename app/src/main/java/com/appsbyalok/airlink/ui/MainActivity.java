package com.appsbyalok.airlink.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.appsbyalok.airlink.bluetooth.BluetoothService;
import com.appsbyalok.airlink.bluetooth.ConnectionManager;
import com.appsbyalok.airlink.core.AppConstants;
import com.appsbyalok.airlink.core.PermissionsHelper;
import com.appsbyalok.airlink.ui.canvas.ChatView;
import com.appsbyalok.airlink.ui.canvas.DeviceListView;


public class MainActivity extends Activity implements DeviceListView.AppViewListener, ChatView.ChatScreenListener {

    private static final int PICK_FILE_REQUEST_CODE = 1001;

    private FrameLayout rootLayout;
    private LinearLayout nativeInputBar;
    private DeviceListView deviceListView;
    private ChatView chatView;

    private BluetoothService btService;
    private boolean isBound = false;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            BluetoothService.LocalBinder binder = (BluetoothService.LocalBinder) service;
            btService = binder.getService();
            isBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            isBound = false;
            btService = null;
        }
    };

    private final BroadcastReceiver btStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (AppConstants.ACTION_STATE_CHANGED.equals(action)) {
                if (intent.hasExtra("state")) {
                    int state = intent.getIntExtra("state", ConnectionManager.STATE_NONE);

                    if (state == ConnectionManager.STATE_CONNECTED) {
                        showChatScreen();
                        if (deviceListView != null) deviceListView.onConnectionSuccess();
                    } else if (state == ConnectionManager.STATE_LISTEN || state == ConnectionManager.STATE_NONE) {
                        showAppView();
                        if (deviceListView != null) deviceListView.onConnectionLost();
                    }
                }

                if (intent.hasExtra("device_name")) {
                    String deviceName = intent.getStringExtra("device_name");
                    if (chatView != null) chatView.setDeviceName(deviceName);
                }

                if (intent.hasExtra("toast")) {
                    Toast.makeText(MainActivity.this, intent.getStringExtra("toast"), Toast.LENGTH_SHORT).show();
                }

            } else if (AppConstants.ACTION_MESSAGE_RECEIVED.equals(action)) {
                String text = intent.getStringExtra("text");
                boolean isMine = intent.getBooleanExtra("isMine", false);
                if (chatView != null) {
                    chatView.addMessage(text, isMine);
                }
            }
        }
    };

    @SuppressLint({"UnspecifiedRegisterReceiverFlag", "WrongConstant"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        Window window = getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);

        // Ensure UI resizes correctly when keyboard opens, preventing the screen from lifting up (adjustPan)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false);
        } else {
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            );
        }

        if (PermissionsHelper.hasAllPermissions(this)) {
            startBluetoothService();
        }

        IntentFilter filter = new IntentFilter();
        filter.addAction(AppConstants.ACTION_STATE_CHANGED);
        filter.addAction(AppConstants.ACTION_MESSAGE_RECEIVED);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(btStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(btStateReceiver, filter);
        }

        rootLayout = new FrameLayout(this);
        rootLayout.setBackgroundColor(Color.parseColor("#09090B"));

        rootLayout.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @NonNull
            @Override
            public WindowInsets onApplyWindowInsets(@NonNull View v, @NonNull WindowInsets insets) {
                int top;
                int bottom;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    // Includes IME (keyboard) so the layout auto-pushes up when typing!
                    android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime());
                    top = bars.top;
                    bottom = bars.bottom;
                } else {
                    top = insets.getSystemWindowInsetTop();
                    bottom = insets.getSystemWindowInsetBottom();
                }

                v.setPadding(0, top, 0, bottom);
                return insets;
            }
        });

        setContentView(rootLayout);

        deviceListView = new DeviceListView(this);
        deviceListView.setListener(this);
        showAppView();

        PermissionsHelper.requestPermissions(this);
    }

    private void startBluetoothService() {
        Intent serviceIntent = new Intent(this, BluetoothService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        if (!isBound) {
            bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
        }
    }

    private void showAppView() {
        if (deviceListView != null && deviceListView.getParent() == null) {
            rootLayout.removeAllViews();
            rootLayout.addView(deviceListView);
        }
    }

    private void showChatScreen() {
        if (chatView == null) {
            chatView = new ChatView(this);
            chatView.setListener(this);
        }

        if (chatView.getParent() == null) {
            rootLayout.removeAllViews();
            rootLayout.addView(chatView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            setupNativeInputOverlay();
        }
    }

    private void setupNativeInputOverlay() {
        float density = getResources().getDisplayMetrics().density;

        nativeInputBar = new LinearLayout(this);
        nativeInputBar.setOrientation(LinearLayout.HORIZONTAL);
        nativeInputBar.setBackgroundColor(Color.parseColor("#18181B"));
        nativeInputBar.setGravity(Gravity.CENTER_VERTICAL);
        int pad = (int) (12 * density);
        nativeInputBar.setPadding(pad, pad, pad, pad);

        // Attach Button
        TextView attachBtn = new TextView(this);
        attachBtn.setText("📎");
        attachBtn.setTextSize(22);
        attachBtn.setPadding(pad, pad, pad, pad);
        attachBtn.setOnClickListener(v -> onAttachClicked());

        // Native EditText
        EditText messageInput = new EditText(this);
        messageInput.setHint("Message...");
        messageInput.setHintTextColor(Color.parseColor("#A1A1AA"));
        messageInput.setTextColor(Color.WHITE);
        messageInput.setMaxLines(4);

        GradientDrawable gd = new GradientDrawable();
        gd.setColor(Color.parseColor("#27272A"));
        gd.setCornerRadius(20 * density);
        messageInput.setBackground(gd);

        int padH = (int) (16 * density);
        int padV = (int) (10 * density);
        messageInput.setPadding(padH, padV, padH, padV);

        LinearLayout.LayoutParams etParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        etParams.setMargins(pad/2, 0, pad/2, 0);
        messageInput.setLayoutParams(etParams);

        // Send Button
        TextView sendBtn = new TextView(this);
        sendBtn.setText("⬆️");
        sendBtn.setTextSize(22);
        sendBtn.setPadding(pad, pad, pad, pad);
        sendBtn.setOnClickListener(v -> {
            String txt = messageInput.getText().toString().trim();
            if (!txt.isEmpty()) {
                onSendMessage(txt);
                messageInput.setText("");
            }
        });

        nativeInputBar.addView(attachBtn);
        nativeInputBar.addView(messageInput);
        nativeInputBar.addView(sendBtn);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.gravity = Gravity.BOTTOM;
        rootLayout.addView(nativeInputBar, lp);

        // Pass height to ChatScreen so it pads the scrolling canvas above the input bar
        nativeInputBar.post(() -> chatView.setBottomInset(nativeInputBar.getHeight()));
    }

    @Override
    public void onConnectClicked(String address) {
        if (btService != null) {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter != null) {
                btService.connect(adapter.getRemoteDevice(address));
            }
        }
    }

    private void onSendMessage(String text) {
        if (btService != null) {
            btService.writeMessage(text);
        }
    }

    private void onAttachClicked() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, PICK_FILE_REQUEST_CODE);
    }

    @Override
    public void onDisconnectClicked() {
        if (btService != null) {
            btService.startServer();
        }
    }

    @Override
    public void onMessageLongClicked(String text) {
        // Handle Long-Press to Copy
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Chat Message", text);
        if (clipboard != null) {
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "Message copied to clipboard", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_FILE_REQUEST_CODE && resultCode == Activity.RESULT_OK && data != null) {
            Uri fileUri = data.getData();
            if (fileUri != null && btService != null) {
                if (chatView != null) chatView.addMessage("Sending file...", true);
                btService.writeFile(fileUri);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (PermissionsHelper.hasAllPermissions(this)) {
            startBluetoothService();
            if (deviceListView != null) deviceListView.refreshState();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // 1. Unbind the background Bluetooth service safely
        if (isBound) {
            unbindService(serviceConnection);
            isBound = false;
        }

        // 2. Unregister the broadcast receiver safely to prevent
        // "IllegalArgumentException: Receiver not registered" crashes.
        try {
            unregisterReceiver(btStateReceiver);
        } catch (IllegalArgumentException e) {
            // This happens if the activity is destroyed before onCreate finishes,
            // or if the OS already killed the receiver. We can safely ignore it.
            Log.d("Main", "onDestroy: ",e);
        }

        // This tells the Android OS to shut down the service safely.
        Intent serviceIntent = new Intent(this, BluetoothService.class);
        stopService(serviceIntent);
    }
}