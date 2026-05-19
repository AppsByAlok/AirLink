package com.appsbyalok.airlink.ui.widget;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.bluetooth.BluetoothAdapter;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;

import com.appsbyalok.airlink.R;
import com.appsbyalok.airlink.ui.MainActivity;

public class BluetoothWidgetProvider extends AppWidgetProvider {
    private static final String TAG = "Wd";

    private static final String ACTION_TOGGLE_BT = "com.bt.ACTION_TOGGLE_BT";
    private static final int COLOR_ACCENT_ON = Color.parseColor("#3B82F6");
    private static final int COLOR_ACCENT_OFF = Color.parseColor("#27272A");

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int widgetId : appWidgetIds) {
            updateWidget(context, appWidgetManager, widgetId);
        }
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager appWidgetManager, int appWidgetId, Bundle newOptions) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions);
        updateWidget(context, appWidgetManager, appWidgetId);
    }

    private void updateWidget(Context context, AppWidgetManager appWidgetManager, int widgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_layout);

        boolean isEnabled = false;
        try {
            BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
            isEnabled = (btAdapter != null && btAdapter.isEnabled());
        } catch (SecurityException e) {
            Log.d(TAG, "updateWidget: ",e);
        }

        // 1. Draw Icon
        views.setImageViewBitmap(R.id.widget_image, generateIcon(isEnabled));

        // 2. Read live connection state saved by BluetoothService
        SharedPreferences prefs = context.getSharedPreferences("BT_PREFS", Context.MODE_PRIVATE);
        boolean isConnectedToChat = prefs.getBoolean("IS_CONNECTED", false);
        String deviceName = prefs.getString("CONNECTED_DEVICE_NAME", "Unknown Device");

        // 3. Layout Resizing Logic
        Bundle options = appWidgetManager.getAppWidgetOptions(widgetId);
        int minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH);

        if (minWidth > 130) { // Widget is expanded to 2x1 or larger
            views.setViewVisibility(R.id.expanded_ui, View.VISIBLE);

            if (isEnabled) {
                views.setTextViewText(R.id.widget_status_text, "Bluetooth is ON");

                if (isConnectedToChat) {
                    views.setTextViewText(R.id.widget_device_text, "Connected: " + deviceName);
                    views.setViewVisibility(R.id.widget_btn_chat, View.VISIBLE);
                } else {
                    views.setTextViewText(R.id.widget_device_text, "Ready to pair or scan");
                    views.setViewVisibility(R.id.widget_btn_chat, View.GONE);
                }
            } else {
                views.setTextViewText(R.id.widget_status_text, "Bluetooth is OFF");
                views.setTextViewText(R.id.widget_device_text, "Tap icon to enable");
                views.setViewVisibility(R.id.widget_btn_chat, View.GONE);
            }
        } else {
            // Widget is 1x1, hide text to let ImageView take 100% space
            views.setViewVisibility(R.id.expanded_ui, View.GONE);
        }

        // 4. Click Listeners for Buttons

        // Setup "Open App" / "Chat" buttons
        Intent openAppIntent = new Intent(context, MainActivity.class);
        PendingIntent pendingApp = PendingIntent.getActivity(context, widgetId, openAppIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_btn_chat, pendingApp);
        views.setOnClickPendingIntent(R.id.widget_btn_scan, pendingApp);

        // Setup "Android Settings" button
        Intent settingsIntent = new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS);
        PendingIntent pendingSettings = PendingIntent.getActivity(context, widgetId, settingsIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_btn_settings, pendingSettings);

        // Setup Icon Toggle Logic
        PendingIntent pendingToggle;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!isEnabled) {
                Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                enableIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                pendingToggle = PendingIntent.getActivity(context, widgetId, enableIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            } else {
                pendingToggle = pendingSettings; // Android 13+ forces you to open settings to turn off
            }
        } else {
            Intent toggleIntent = new Intent(context, BluetoothWidgetProvider.class);
            toggleIntent.setAction(ACTION_TOGGLE_BT);
            pendingToggle = PendingIntent.getBroadcast(context, widgetId, toggleIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        }
        views.setOnClickPendingIntent(R.id.widget_image, pendingToggle);

        appWidgetManager.updateAppWidget(widgetId, views);
    }

    private Bitmap generateIcon(boolean isEnabled) {
        Bitmap bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        float cx = 100f; float cy = 100f; float radius = 90f; // Slightly smaller to prevent canvas clipping

        Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(isEnabled ? COLOR_ACCENT_ON : COLOR_ACCENT_OFF);
        bgPaint.setShadowLayer(10f, 0f, 5f, Color.argb(80, 0, 0, 0));
        canvas.drawCircle(cx, cy, radius, bgPaint);

        Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        iconPaint.setColor(Color.WHITE);
        iconPaint.setStyle(Paint.Style.STROKE);
        iconPaint.setStrokeWidth(12f);
        iconPaint.setStrokeCap(Paint.Cap.ROUND);
        iconPaint.setStrokeJoin(Paint.Join.ROUND);

        float s = radius * 0.35f;
        Path path = new Path();
        path.moveTo(cx - s * 0.6f, cy - s);
        path.lineTo(cx + s * 0.6f, cy + s);
        path.lineTo(cx, cy + 1.8f * s);
        path.lineTo(cx, cy - 1.8f * s);
        path.lineTo(cx + s * 0.6f, cy - s);
        path.lineTo(cx - s * 0.6f, cy + s);
        canvas.drawPath(path, iconPaint);

        return bitmap;
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);

        if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction()) ||
                "com.bt.ACTION_WIDGET_STATE_UPDATE".equals(intent.getAction())) { // Custom action for live UI sync
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
            ComponentName thisWidget = new ComponentName(context, BluetoothWidgetProvider.class);
            int[] appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget);
            onUpdate(context, appWidgetManager, appWidgetIds);
        }

        if (ACTION_TOGGLE_BT.equals(intent.getAction())) {
            BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
            if (btAdapter != null) {
                try {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                        if (btAdapter.isEnabled()) btAdapter.disable();
                        else btAdapter.enable();
                    }
                    // Trigger an immediate visual refresh
                    AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
                    ComponentName thisWidget = new ComponentName(context, BluetoothWidgetProvider.class);
                    int[] appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget);
                    onUpdate(context, appWidgetManager, appWidgetIds);
                } catch (SecurityException e) {
                    Log.d(TAG, "onReceive: ",e);
                }
            }
        }
    }
}