package com.appsbyalok.airlink.ui.canvas;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Build;
import android.text.InputType;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.OvershootInterpolator;
import android.widget.EditText;
import android.widget.OverScroller;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.appsbyalok.airlink.data.DeviceItem;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

public class DeviceListView extends View {

    private static final String TAG = "AppView";

    public interface AppViewListener {
        void onConnectClicked(String address);
        void onDisconnectClicked();
    }

    private AppViewListener listener;

    // --- Core State ---
    private boolean isBtEnabled = false;
    private boolean previousBtState = false;
    private boolean isScanning = false;
    private BluetoothAdapter btAdapter;

    // --- Active Connection State ---
    private String connectedAddress = null;
    private String connectingAddress = null;
    private AlertDialog macDialog;

    // --- Paints & Styling ---
    private Paint cardPaint, iconPaint, glowPaint, indicatorPaint, ringPaint;
    private TextPaint titleTextPaint, subTextPaint, headerTextPaint, scanBtnTextPaint, actionBtnTextPaint;
    private Paint scanBtnPaint, actionBtnPaint;

    // Modern Dark Theme Colors
    private final int COLOR_BG = Color.parseColor("#09090B");
    private final int COLOR_CARD = Color.parseColor("#18181B");
    private final int COLOR_ACCENT_ON = Color.parseColor("#3B82F6"); // Modern Blue
    private final int COLOR_ACCENT_OFF = Color.parseColor("#27272A");
    private final int COLOR_CONNECTING = Color.parseColor("#F59E0B");
    private final int COLOR_INDICATOR = Color.parseColor("#10B981");

    private int currentBgColor;
    private float currentScale = 1.0f;

    private boolean isScanBtnPressed = false;
    private boolean isMacBtnPressed = false;
    private boolean isDiscoverBtnPressed = false;

    private final CopyOnWriteArrayList<DeviceItem> pairedDevices = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<DeviceItem> availableDevices = new CopyOnWriteArrayList<>();

    // --- Layout Coordinates & Scrolling ---
    private float scrollY = 0f;
    private float lastTouchY = 0f;
    private float maxScrollY = 0f;
    private boolean isDragging = false;

    // Smooth scrolling tools
    private OverScroller scroller;
    private VelocityTracker velocityTracker;
    private int minimumVelocity;
    private int maximumVelocity;

    private float btnCx, btnCy, btnRadius;
    private float scanBtnTop, scanBtnBottom, scanBtnLeft, scanBtnRight;
    private float macBtnTop, macBtnBottom;
    private float discoverBtnTop, discoverBtnBottom;
    private float availableCardTextWidth = 0f;

    private final RectF scanBtnRect = new RectF();
    private final RectF macBtnRect = new RectF();
    private final RectF discoverBtnRect = new RectF();
    private final RectF tempCardRect = new RectF();
    private final Path btIconPath = new Path();

    private final BroadcastReceiver btReceiver = new BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action) ||
                    BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
                refreshState();
            } else if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device != null) {
                    try {
                        if (device.getBondState() == BluetoothDevice.BOND_BONDED) return;
                        DeviceItem newItem = new DeviceItem(device.getName(), device.getAddress());
                        if (!availableDevices.contains(newItem)) {
                            if (availableCardTextWidth > 0) newItem.buildLayouts(availableCardTextWidth, false, titleTextPaint, subTextPaint);
                            availableDevices.add(newItem);
                            invalidate();
                        }
                    } catch (SecurityException e) {
                        Log.e(TAG, "Permission denied retrieving device info", e);
                    }
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)) {
                isScanning = true;
                invalidate();
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                isScanning = false;
                invalidate();
            }
        }
    };

    public DeviceListView(Context context) { super(context); init(); }
    public DeviceListView(Context context, AttributeSet attrs) { super(context, attrs); init(); }

    public void setListener(AppViewListener listener) {
        this.listener = listener;
    }

    private void init() {
        btAdapter = BluetoothAdapter.getDefaultAdapter();
        scroller = new OverScroller(getContext());
        final ViewConfiguration configuration = ViewConfiguration.get(getContext());
        minimumVelocity = configuration.getScaledMinimumFlingVelocity();
        maximumVelocity = configuration.getScaledMaximumFlingVelocity();

        cardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        cardPaint.setColor(COLOR_CARD);
        cardPaint.setShadowLayer(dpToPx(8), 0f, dpToPx(4), Color.argb(30, 0, 0, 0));
        setLayerType(LAYER_TYPE_SOFTWARE, null);

        glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        glowPaint.setStyle(Paint.Style.FILL);

        ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(dpToPx(2));

        indicatorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        indicatorPaint.setColor(COLOR_INDICATOR);
        indicatorPaint.setStyle(Paint.Style.FILL);

        iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        iconPaint.setColor(Color.WHITE);
        iconPaint.setStyle(Paint.Style.STROKE);
        iconPaint.setStrokeWidth(dpToPx(4));
        iconPaint.setStrokeCap(Paint.Cap.ROUND);
        iconPaint.setStrokeJoin(Paint.Join.ROUND);

        Typeface modernFont = Typeface.create("sans-serif-medium", Typeface.NORMAL);
        Typeface boldFont = Typeface.create("sans-serif-bold", Typeface.NORMAL);

        titleTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        titleTextPaint.setColor(Color.WHITE);
        titleTextPaint.setTextSize(spToPx(16));
        titleTextPaint.setTypeface(modernFont);

        subTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        subTextPaint.setColor(Color.parseColor("#A1A1AA"));
        subTextPaint.setTextSize(spToPx(13));
        subTextPaint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));

        headerTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        headerTextPaint.setColor(Color.parseColor("#F4F4F5"));
        headerTextPaint.setTextSize(spToPx(18));
        headerTextPaint.setTypeface(boldFont);
        headerTextPaint.setLetterSpacing(0.02f);

        scanBtnPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        scanBtnPaint.setColor(COLOR_ACCENT_ON);

        scanBtnTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        scanBtnTextPaint.setColor(Color.WHITE);
        scanBtnTextPaint.setTextSize(spToPx(15));
        scanBtnTextPaint.setTextAlign(Paint.Align.CENTER);
        scanBtnTextPaint.setTypeface(boldFont);

        actionBtnPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        actionBtnTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        actionBtnTextPaint.setColor(Color.WHITE);
        actionBtnTextPaint.setTextSize(spToPx(13));
        actionBtnTextPaint.setTextAlign(Paint.Align.CENTER);
        actionBtnTextPaint.setTypeface(boldFont);

        checkBtState();
        currentBgColor = isBtEnabled ? COLOR_ACCENT_ON : COLOR_ACCENT_OFF;
    }

    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }

    private float spToPx(float sp) {
        return sp * getResources().getDisplayMetrics().scaledDensity;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float cardMargin = w * 0.05f;
        availableCardTextWidth = w - (2 * cardMargin) - dpToPx(120); // Leave space for button

        btnCx = w / 2f;
        btnRadius = w * 0.15f;
        btnCy = btnRadius + dpToPx(50);

        float s = btnRadius * 0.35f;
        btIconPath.reset();
        btIconPath.moveTo(btnCx - s*0.6f, btnCy - s);
        btIconPath.lineTo(btnCx + s*0.6f, btnCy + s);
        btIconPath.lineTo(btnCx, btnCy + 1.8f * s);
        btIconPath.lineTo(btnCx, btnCy - 1.8f * s);
        btIconPath.lineTo(btnCx + s*0.6f, btnCy - s);
        btIconPath.lineTo(btnCx - s*0.6f, btnCy + s);

        scanBtnTop = btnCy + btnRadius + dpToPx(30);
        scanBtnBottom = scanBtnTop + dpToPx(50);
        scanBtnLeft = w * 0.2f;
        scanBtnRight = w * 0.8f;
        scanBtnRect.set(scanBtnLeft, scanBtnTop, scanBtnRight, scanBtnBottom);

        macBtnTop = scanBtnBottom + dpToPx(12);
        macBtnBottom = macBtnTop + dpToPx(40);
        macBtnRect.set(scanBtnLeft, macBtnTop, scanBtnRight, macBtnBottom);

        discoverBtnTop = macBtnBottom + dpToPx(12);
        discoverBtnBottom = discoverBtnTop + dpToPx(40);
        discoverBtnRect.set(scanBtnLeft, discoverBtnTop, scanBtnRight, discoverBtnBottom);

        for (DeviceItem item : pairedDevices) item.buildLayouts(availableCardTextWidth, true, titleTextPaint, subTextPaint);
        for (DeviceItem item : availableDevices) item.buildLayouts(availableCardTextWidth, false, titleTextPaint, subTextPaint);
    }

    private void checkBtState() {
        try {
            isBtEnabled = (btAdapter != null && btAdapter.isEnabled());
            if (!isBtEnabled && previousBtState && listener != null) {
                listener.onDisconnectClicked();
            }
            previousBtState = isBtEnabled;
        } catch (SecurityException e) {
            isBtEnabled = false;
            previousBtState = false;
        }
    }

    public void refreshState() {
        checkBtState();
        animateColorChange(isBtEnabled ? COLOR_ACCENT_ON : COLOR_ACCENT_OFF);

        if (isBtEnabled) {
            loadPairedDevices();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                availableDevices.removeIf(pairedDevices::contains);
            } else {
                availableDevices.removeAll(pairedDevices);
            }
        } else {
            pairedDevices.clear();
            availableDevices.clear();
            isScanning = false;
        }
        invalidate();
    }

    private void loadPairedDevices() {
        pairedDevices.clear();
        try {
            if (btAdapter != null && btAdapter.isEnabled()) {
                Set<BluetoothDevice> paired = btAdapter.getBondedDevices();
                if (paired != null) {
                    for (BluetoothDevice device : paired) {
                        DeviceItem item = new DeviceItem(device.getName(), device.getAddress());
                        if (availableCardTextWidth > 0) item.buildLayouts(availableCardTextWidth, true, titleTextPaint, subTextPaint);
                        pairedDevices.add(item);
                    }
                }
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Failed to load paired devices", e);
        }
    }

    public void onConnectionSuccess() {
        connectedAddress = connectingAddress;
        connectingAddress = null;
        invalidate();
    }

    public void onConnectionLost() {
        connectedAddress = null;
        connectingAddress = null;
        invalidate();
    }

    private void animateColorChange(int targetColor) {
        ValueAnimator colorAnimator = ValueAnimator.ofObject(new ArgbEvaluator(), currentBgColor, targetColor);
        colorAnimator.setDuration(400);
        colorAnimator.addUpdateListener(animator -> {
            currentBgColor = (int) animator.getAnimatedValue();
            invalidate();
        });
        colorAnimator.start();
    }

    private void animateClickBounce() {
        ValueAnimator scaleAnimator = ValueAnimator.ofFloat(1.0f, 0.90f, 1.0f);
        scaleAnimator.setDuration(350);
        scaleAnimator.setInterpolator(new OvershootInterpolator(2.0f));
        scaleAnimator.addUpdateListener(animator -> {
            currentScale = (float) animator.getAnimatedValue();
            invalidate();
        });
        scaleAnimator.start();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        getContext().registerReceiver(btReceiver, filter);
        refreshState();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        getContext().unregisterReceiver(btReceiver);
    }

    @Override
    public void computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollY = scroller.getCurrY();
            postInvalidateOnAnimation();
        }
    }

    @SuppressLint("DrawAllocation")
    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        canvas.drawColor(COLOR_BG);

        canvas.save();
        canvas.translate(0, scrollY);

        // Header Toggle
        canvas.save();
        canvas.scale(currentScale, currentScale, btnCx, btnCy);

        if (isBtEnabled) {
            // Inner Glow
            glowPaint.setColor(currentBgColor);
            glowPaint.setAlpha(40);
            canvas.drawCircle(btnCx, btnCy, btnRadius + dpToPx(15), glowPaint);

            // Modern gradient ring
            ringPaint.setShader(new LinearGradient(btnCx - btnRadius, btnCy - btnRadius, btnCx + btnRadius, btnCy + btnRadius,
                    currentBgColor, Color.parseColor("#60A5FA"), Shader.TileMode.CLAMP));
            canvas.drawCircle(btnCx, btnCy, btnRadius + dpToPx(4), ringPaint);
        } // Avoid object allocations during draw/layout operations (preallocate and reuse instead)

        glowPaint.setShader(null);
        glowPaint.setColor(currentBgColor);
        glowPaint.setAlpha(255);
        canvas.drawCircle(btnCx, btnCy, btnRadius, glowPaint);
        canvas.drawPath(btIconPath, iconPaint);
        canvas.restore();

        // Scan Button (Pill shaped)
        scanBtnPaint.setColor(isScanBtnPressed ? Color.parseColor("#2563EB") : COLOR_ACCENT_ON);
        scanBtnPaint.setAlpha(isBtEnabled ? 255 : 80);
        float pillRadius = (scanBtnBottom - scanBtnTop) / 2f;
        canvas.drawRoundRect(scanBtnRect, pillRadius, pillRadius, scanBtnPaint);

        String btnText = isScanning ? "Scanning..." : "Scan for Devices";
        float textY = scanBtnTop + ((scanBtnBottom - scanBtnTop) / 2f) - ((scanBtnTextPaint.descent() + scanBtnTextPaint.ascent()) / 2f);
        canvas.drawText(btnText, width / 2f, textY, scanBtnTextPaint);

        // Custom MAC Button
        actionBtnPaint.setColor(isMacBtnPressed ? Color.parseColor("#3F3F46") : COLOR_CARD);
        actionBtnPaint.setAlpha(isBtEnabled ? 255 : 100);
        canvas.drawRoundRect(macBtnRect, dpToPx(12), dpToPx(12), actionBtnPaint);

        float macTextY = macBtnTop + ((macBtnBottom - macBtnTop) / 2f) - ((actionBtnTextPaint.descent() + actionBtnTextPaint.ascent()) / 2f);
        canvas.drawText("Connect via MAC", width / 2f, macTextY, actionBtnTextPaint);

        // Make Discoverable Button
        actionBtnPaint.setColor(isDiscoverBtnPressed ? Color.parseColor("#3F3F46") : COLOR_CARD);
        actionBtnPaint.setAlpha(isBtEnabled ? 255 : 100);
        canvas.drawRoundRect(discoverBtnRect, dpToPx(12), dpToPx(12), actionBtnPaint);

        float discoverTextY = discoverBtnTop + ((discoverBtnBottom - discoverBtnTop) / 2f) - ((actionBtnTextPaint.descent() + actionBtnTextPaint.ascent()) / 2f);
        canvas.drawText("Make Device Discoverable", width / 2f, discoverTextY, actionBtnTextPaint);

        float currentY = discoverBtnBottom + dpToPx(40);
        float cardMargin = width * 0.05f;
        float padding = dpToPx(16);

        // Sections
        canvas.drawText("Paired Devices", cardMargin + dpToPx(4), currentY, headerTextPaint);
        currentY += dpToPx(30);

        if (!isBtEnabled) {
            currentY = drawEmptyState(canvas, "Turn on Bluetooth to see devices.", cardMargin, currentY);
        } else if (pairedDevices.isEmpty()) {
            currentY = drawEmptyState(canvas, "No paired devices.", cardMargin, currentY);
        } else {
            for (DeviceItem item : pairedDevices) {
                currentY = drawDeviceCard(canvas, item, cardMargin, currentY, width - cardMargin, padding, true);
            }
        }

        currentY += dpToPx(24);
        canvas.drawText("Available Devices", cardMargin + dpToPx(4), currentY, headerTextPaint);
        currentY += dpToPx(30);

        if (!isBtEnabled) {
            currentY = drawEmptyState(canvas, "Turn on Bluetooth to scan.", cardMargin, currentY);
        } else if (availableDevices.isEmpty()) {
            currentY = drawEmptyState(canvas, isScanning ? "Looking for devices..." : "Tap 'Scan' to find nearby devices.", cardMargin, currentY);
        } else {
            for (DeviceItem item : availableDevices) {
                currentY = drawDeviceCard(canvas, item, cardMargin, currentY, width - cardMargin, padding, false);
            }
        }

        float contentHeight = currentY + dpToPx(50);
        maxScrollY = contentHeight > getHeight() ? -(contentHeight - getHeight()) : 0;
        canvas.restore();
    }

    private float drawEmptyState(Canvas canvas, String message, float x, float y) {
        canvas.drawText(message, x + dpToPx(4), y + dpToPx(10), subTextPaint);
        return y + dpToPx(40);
    }

    private float drawDeviceCard(Canvas canvas, DeviceItem item, float left, float top, float right, float padding, boolean isPaired) {
        if (item.nameLayout == null || item.addressLayout == null) return top;

        float nameHeight = item.nameLayout.getHeight();
        float addressHeight = item.addressLayout.getHeight();
        float spacingBetweenText = dpToPx(4);
        float totalCardHeight = padding + nameHeight + spacingBetweenText + addressHeight + padding;
        if (totalCardHeight < dpToPx(70)) totalCardHeight = dpToPx(70);

        tempCardRect.set(left, top, right, top + totalCardHeight);
        canvas.drawRoundRect(tempCardRect, dpToPx(16), dpToPx(16), cardPaint);

        boolean isConnected = isPaired && item.address.equals(connectedAddress);
        boolean isConnectingThis = isPaired && item.address.equals(connectingAddress);

        float textXOffset = left + padding;

        if (isPaired) {
            if (isConnected) {
                indicatorPaint.setAlpha(40);
                canvas.drawCircle(left + padding + dpToPx(6), top + padding + dpToPx(8), dpToPx(8), indicatorPaint);
                indicatorPaint.setAlpha(255);
                canvas.drawCircle(left + padding + dpToPx(6), top + padding + dpToPx(8), dpToPx(4), indicatorPaint);
            }
            textXOffset += dpToPx(20);
        }

        canvas.save();
        canvas.translate(textXOffset, top + padding);
        item.nameLayout.draw(canvas);
        canvas.restore();

        canvas.save();
        canvas.translate(textXOffset, top + padding + nameHeight + spacingBetweenText);
        if (isConnected) {
            subTextPaint.setColor(COLOR_INDICATOR);
            canvas.drawText("Active Connection", 0, subTextPaint.getTextSize() - dpToPx(2), subTextPaint);
            subTextPaint.setColor(Color.parseColor("#A1A1AA")); // Revert
        } else {
            item.addressLayout.draw(canvas);
        }
        canvas.restore();

        float connectBtnWidth = dpToPx(80);
        float connectBtnHeight = dpToPx(32);
        float btnLeft = right - connectBtnWidth - padding;
        float btnTop = top + (totalCardHeight - connectBtnHeight) / 2f;

        item.actionBounds.set(btnLeft, btnTop, btnLeft + connectBtnWidth, btnTop + connectBtnHeight);

        String actionText;
        if (isPaired) {
            if (isConnected) {
                actionBtnPaint.setColor(Color.parseColor("#3F3F46")); // Subtle gray for disconnect
                actionText = "Disconnect";
            } else if (isConnectingThis) {
                actionBtnPaint.setColor(COLOR_CONNECTING);
                actionText = "Wait...";
            } else {
                actionBtnPaint.setColor(COLOR_ACCENT_ON);
                actionText = "Connect";
            }
        } else {
            actionBtnPaint.setColor(COLOR_ACCENT_ON);
            actionText = "Pair";
        }

        actionBtnPaint.setAlpha(item.isActionPressed ? 180 : 255);
        canvas.drawRoundRect(item.actionBounds, dpToPx(16), dpToPx(16), actionBtnPaint);

        float textY = btnTop + (connectBtnHeight / 2f) - ((actionBtnTextPaint.descent() + actionBtnTextPaint.ascent()) / 2f);
        canvas.drawText(actionText, item.actionBounds.centerX(), textY, actionBtnTextPaint);

        return top + totalCardHeight + dpToPx(12);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (velocityTracker == null) velocityTracker = VelocityTracker.obtain();
        velocityTracker.addMovement(event);

        float x = event.getX();
        float y = event.getY();
        float adjustedY = y - scrollY;

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (!scroller.isFinished()) scroller.abortAnimation();
                lastTouchY = y;
                isDragging = false;
                if (x >= scanBtnLeft && x <= scanBtnRight && adjustedY >= scanBtnTop && adjustedY <= scanBtnBottom) isScanBtnPressed = true;
                if (x >= scanBtnLeft && x <= scanBtnRight && adjustedY >= macBtnTop && adjustedY <= macBtnBottom) isMacBtnPressed = true;
                if (x >= scanBtnLeft && x <= scanBtnRight && adjustedY >= discoverBtnTop && adjustedY <= discoverBtnBottom) isDiscoverBtnPressed = true;

                for (DeviceItem item : pairedDevices) if (item.actionBounds.contains(x, adjustedY)) item.isActionPressed = true;
                for (DeviceItem item : availableDevices) if (item.actionBounds.contains(x, adjustedY)) item.isActionPressed = true;
                invalidate();
                return true;

            case MotionEvent.ACTION_MOVE:
                float dy = y - lastTouchY;
                if (Math.abs(dy) > dpToPx(8)) {
                    isDragging = true;
                    isScanBtnPressed = false;
                    isMacBtnPressed = false;
                    isDiscoverBtnPressed = false;
                    for (DeviceItem item : pairedDevices) item.isActionPressed = false;
                    for (DeviceItem item : availableDevices) item.isActionPressed = false;
                }
                scrollY += dy;
                if (scrollY > 0) scrollY = 0;
                if (scrollY < maxScrollY) scrollY = maxScrollY;
                lastTouchY = y;
                invalidate();
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                isScanBtnPressed = false;
                isMacBtnPressed = false;
                isDiscoverBtnPressed = false;

                if (!isDragging && event.getAction() == MotionEvent.ACTION_UP) {
                    if (Math.hypot(x - btnCx, adjustedY - btnCy) <= btnRadius + dpToPx(15)) {
                        animateClickBounce();
                        toggleBluetooth();
                    } else if (x >= scanBtnLeft && x <= scanBtnRight && adjustedY >= scanBtnTop && adjustedY <= scanBtnBottom) {
                        handleScanClick();
                    } else if (x >= scanBtnLeft && x <= scanBtnRight && adjustedY >= macBtnTop && adjustedY <= macBtnBottom) {
                        if (isBtEnabled) showMacInputDialog();
                    } else if (x >= scanBtnLeft && x <= scanBtnRight && adjustedY >= discoverBtnTop && adjustedY <= discoverBtnBottom) {
                        if (isBtEnabled) requestDiscoverable();
                    } else {
                        for (DeviceItem item : pairedDevices) {
                            if (item.isActionPressed && item.actionBounds.contains(x, adjustedY)) {
                                if (item.address.equals(connectedAddress)) {
                                    if (listener != null) listener.onDisconnectClicked();
                                } else if (!item.address.equals(connectingAddress)) {
                                    connectingAddress = item.address;
                                    invalidate();
                                    if (listener != null) listener.onConnectClicked(item.address);
                                }
                            }
                            item.isActionPressed = false;
                        }
                        for (DeviceItem item : availableDevices) {
                            if (item.isActionPressed && item.actionBounds.contains(x, adjustedY)) pairToDevice(item);
                            item.isActionPressed = false;
                        }
                    }
                } else if (isDragging) {
                    velocityTracker.computeCurrentVelocity(1000, maximumVelocity);
                    float velocityY = velocityTracker.getYVelocity();
                    if (Math.abs(velocityY) > minimumVelocity) {
                        scroller.fling(0, (int) scrollY, 0, (int) velocityY, 0, 0, (int) maxScrollY, 0);
                        postInvalidateOnAnimation();
                    }
                }

                if (velocityTracker != null) {
                    velocityTracker.recycle();
                    velocityTracker = null;
                }
                invalidate();
                performClick();
                return true;
        }
        return super.onTouchEvent(event);
    }

    @SuppressLint("MissingPermission")
    private void handleScanClick() {
        if (isBtEnabled && btAdapter != null) {
            try {
                if (btAdapter.isDiscovering()) btAdapter.cancelDiscovery();
                else {
                    availableDevices.clear();
                    btAdapter.startDiscovery();
                }
            } catch (SecurityException e) {
                Toast.makeText(getContext(), "Missing scan permission", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @SuppressLint("MissingPermission")
    private void requestDiscoverable() {
        if (!isBtEnabled) return;
        try {
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            getContext().startActivity(discoverableIntent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to request discoverable", e);
        }
    }

    private void showMacInputDialog() {
        if (macDialog != null && macDialog.isShowing()) return;

        int theme = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1
                ? android.R.style.Theme_DeviceDefault_Dialog_Alert
                : android.R.style.Theme_Material_Dialog_Alert;
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), theme);
        builder.setTitle("Connect manually");
        builder.setMessage("Enter the exact Bluetooth MAC address (e.g. AA:11:BB:22:CC:33)");

        final EditText input = new EditText(builder.getContext());
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint("00:00:00:00:00:00");
        input.setHintTextColor(Color.GRAY);
        int pad = (int) dpToPx(20);
        input.setPadding(pad, pad, pad, pad);
        builder.setView(input);

        builder.setPositiveButton("Connect", (dialog, which) -> {
            String mac = input.getText().toString().trim().toUpperCase();
            if (BluetoothAdapter.checkBluetoothAddress(mac)) {
                connectingAddress = mac;
                invalidate();
                if (listener != null) listener.onConnectClicked(mac);
            } else {
                Toast.makeText(getContext(), "Invalid MAC address format", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        macDialog = builder.show();
    }

    @SuppressLint("MissingPermission")
    private void pairToDevice(DeviceItem item) {
        try {
            if (btAdapter != null) {
                BluetoothDevice device = btAdapter.getRemoteDevice(item.address);
                device.createBond();
                Toast.makeText(getContext(), "Pairing with " + item.name + "...", Toast.LENGTH_SHORT).show();
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Failed to pair due to missing permissions", e);
        }
    }

    private void toggleBluetooth() {
        if (btAdapter != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                try {
                    if (!btAdapter.isEnabled()) {
                        getContext().startActivity(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
                    } else {
                        Toast.makeText(getContext(), "Android 13+ requires you to turn off Bluetooth in Settings", Toast.LENGTH_LONG).show();
                        getContext().startActivity(new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS));
                    }
                } catch (SecurityException e) { Log.e(TAG, "Toggle failed (13+)", e); }
            } else {
                try {
                    if (btAdapter.isEnabled()) btAdapter.disable();
                    else btAdapter.enable();
                } catch (SecurityException e) { Log.e(TAG, "Toggle failed", e); }
            }
        }
    }

    @Override
    public boolean performClick() { return super.performClick(); }
}