package com.appsbyalok.airlink.ui.canvas;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;
import android.widget.OverScroller;

import androidx.annotation.NonNull;

import com.appsbyalok.airlink.data.ChatMessage;

import java.util.concurrent.CopyOnWriteArrayList;

public class ChatView extends View {

    public interface ChatScreenListener {
        void onDisconnectClicked();
        void onMessageLongClicked(String text);
    }

    private ChatScreenListener listener;
    private String deviceName = "Connected Device";

    private float scrollY = 0f;
    private float maxScrollY = 0f;
    private float lastTouchY = 0f;
    private boolean isDragging = false;
    private boolean canScroll = false;

    // Insets provided by the native input bar overlay
    private float bottomInset = 0f;

    private OverScroller scroller;
    private VelocityTracker velocityTracker;
    private GestureDetector gestureDetector;
    private int minimumVelocity;
    private int maximumVelocity;

    private final CopyOnWriteArrayList<ChatMessage> messages = new CopyOnWriteArrayList<>();

    private Paint bgPaint, topBarPaint, bubbleMinePaint, bubbleOtherPaint, iconPaint;
    private TextPaint textMinePaint, textOtherPaint, namePaint;

    private float topBarHeight;
    private final Path backPath = new Path();

    public ChatView(Context context) { super(context); init(); }
    public ChatView(Context context, AttributeSet attrs) { super(context, attrs); init(); }

    private void init() {
        scroller = new OverScroller(getContext());
        final ViewConfiguration configuration = ViewConfiguration.get(getContext());
        minimumVelocity = configuration.getScaledMinimumFlingVelocity();
        maximumVelocity = configuration.getScaledMaximumFlingVelocity();

        // Native GestureDetector for checking Long Presses
        gestureDetector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {
            @Override
            public void onLongPress(@NonNull MotionEvent e) {
                float touchX = e.getX();
                // Translate visual touch coordinates to absolute canvas drawing coordinates
                float touchY = e.getY() - scrollY;

                for (ChatMessage msg : messages) {
                    if (msg.bounds.contains(touchX, touchY)) {
                        if (listener != null && msg.textLayout != null) {
                            // Extract text directly from layout if private, otherwise fallback to your logic
                            listener.onMessageLongClicked(msg.textLayout.getText().toString());
                        }
                        break;
                    }
                }
            }
        });

        topBarHeight = dpToPx(80);

        bgPaint = new Paint();
        bgPaint.setColor(Color.parseColor("#09090B"));

        topBarPaint = new Paint();
        topBarPaint.setColor(Color.parseColor("#18181B"));
        topBarPaint.setShadowLayer(dpToPx(4), 0, dpToPx(2), Color.argb(40, 0, 0, 0));

        bubbleMinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bubbleMinePaint.setColor(Color.parseColor("#3B82F6"));

        bubbleOtherPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bubbleOtherPaint.setColor(Color.parseColor("#27272A"));

        iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        iconPaint.setColor(Color.WHITE);
        iconPaint.setStyle(Paint.Style.STROKE);
        iconPaint.setStrokeWidth(dpToPx(3));
        iconPaint.setStrokeCap(Paint.Cap.ROUND);
        iconPaint.setStrokeJoin(Paint.Join.ROUND);

        Typeface font = Typeface.create("sans-serif", Typeface.NORMAL);
        Typeface boldFont = Typeface.create("sans-serif-medium", Typeface.NORMAL);

        textMinePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        textMinePaint.setColor(Color.WHITE);
        textMinePaint.setTextSize(spToPx(15));
        textMinePaint.setTypeface(font);

        textOtherPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        textOtherPaint.setColor(Color.parseColor("#F4F4F5"));
        textOtherPaint.setTextSize(spToPx(15));
        textOtherPaint.setTypeface(font);

        namePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        namePaint.setColor(Color.WHITE);
        namePaint.setTextSize(spToPx(18));
        namePaint.setTypeface(boldFont);
        namePaint.setTextAlign(Paint.Align.CENTER);
    }

    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }

    private float spToPx(float sp) {
        return sp * getResources().getDisplayMetrics().scaledDensity;
    }

    public void setListener(ChatScreenListener listener) {
        this.listener = listener;
    }

    public void setDeviceName(String name) {
        this.deviceName = (name != null) ? name : "Unknown Device";
        invalidate();
    }

    public void setBottomInset(float inset) {
        this.bottomInset = inset;
        recalculateScroll(true);
        invalidate();
    }

    public void addMessage(String text, boolean isMine) {
        ChatMessage msg = new ChatMessage(text, isMine);
        if (getWidth() > 0) {
            msg.buildLayout(getWidth() * 0.75f, isMine ? textMinePaint : textOtherPaint);
        }
        messages.add(msg);

        float oldScrollY = scrollY;
        recalculateScroll(false);

        ValueAnimator animator = ValueAnimator.ofFloat(oldScrollY, maxScrollY);
        animator.setDuration(350);
        animator.setInterpolator(new DecelerateInterpolator(1.5f));
        animator.addUpdateListener(a -> {
            scrollY = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        // Responsive Back Arrow path
        float backCx = dpToPx(30);
        float backCy = topBarHeight / 2f + dpToPx(10);
        float backS = dpToPx(8);
        backPath.reset();
        backPath.moveTo(backCx + backS, backCy - backS);
        backPath.lineTo(backCx - backS, backCy);
        backPath.lineTo(backCx + backS, backCy + backS);

        for (ChatMessage msg : messages) {
            msg.buildLayout(w * 0.75f, msg.isMine ? textMinePaint : textOtherPaint);
        }
        recalculateScroll(true);
    }

    private void recalculateScroll(boolean isResizing) {
        if (getHeight() == 0) return;

        float totalHeight = topBarHeight + dpToPx(20);
        for (ChatMessage msg : messages) {
            totalHeight += msg.height + dpToPx(16);
        }

        // Use the native input bar height as the padding so text doesn't hide behind it
        float visibleHeight = getHeight() - bottomInset;
        float newMaxScrollY = totalHeight > visibleHeight ? -(totalHeight - visibleHeight) : 0;

        // Keep scroll positioned correctly if we were previously at the bottom
        boolean wasAtBottom = (maxScrollY == 0) || Math.abs(scrollY - maxScrollY) < dpToPx(10);

        maxScrollY = newMaxScrollY;

        if (isResizing && wasAtBottom) {
            scrollY = maxScrollY;
        } else if (scrollY < maxScrollY) {
            scrollY = maxScrollY;
        }
    }

    @Override
    public void computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollY = scroller.getCurrY();
            postInvalidateOnAnimation();
        }
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();

        canvas.drawRect(0, 0, w, h, bgPaint);

        // --- Draw Messages ---
        canvas.save();
        // Clip visually up to the bottom inset to prevent overlapping
        canvas.clipRect(0, topBarHeight, w, h - bottomInset);
        canvas.translate(0, scrollY);

        float currentY = topBarHeight + dpToPx(20);
        float radius = dpToPx(16);

        for (ChatMessage msg : messages) {
            if (msg.textLayout != null) {
                float bubbleWidth = msg.textLayout.getWidth() + dpToPx(30);
                float left = msg.isMine ? w - bubbleWidth - dpToPx(16) : dpToPx(16);
                float right = left + bubbleWidth;
                float bottom = currentY + msg.height + dpToPx(20);

                // Track the visual boundaries into the object for the Long-Press Detector
                msg.bounds.set(left, currentY, right, bottom);

                canvas.drawRoundRect(msg.bounds, radius, radius, msg.isMine ? bubbleMinePaint : bubbleOtherPaint);

                canvas.save();
                canvas.translate(left + dpToPx(15), currentY + dpToPx(10));
                msg.textLayout.draw(canvas);
                canvas.restore();

                currentY += msg.height + dpToPx(28);
            }
        }
        canvas.restore();

        // --- Draw Top Bar ---
        canvas.drawRect(0, 0, w, topBarHeight, topBarPaint);
        canvas.drawPath(backPath, iconPaint);
        canvas.drawText(deviceName, w / 2f, topBarHeight / 2f + dpToPx(15), namePaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Let Android's Native Gesture Detector evaluate long presses before scrolling
        if (gestureDetector.onTouchEvent(event)) {
            return true;
        }

        if (velocityTracker == null) velocityTracker = VelocityTracker.obtain();
        velocityTracker.addMovement(event);

        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (!scroller.isFinished()) scroller.abortAnimation();
                lastTouchY = y;
                isDragging = false;
                // Determine if they clicked inside the scrollable open area
                canScroll = (y > topBarHeight && y < getHeight() - bottomInset);
                invalidate();
                return true;

            case MotionEvent.ACTION_MOVE:
                float dy = y - lastTouchY;
                if (Math.abs(dy) > dpToPx(8)) {
                    isDragging = true;
                }

                // If they started dragging in open space, let them pull as far as they want!
                if (canScroll) {
                    scrollY += dy;
                    if (scrollY > 0) scrollY = 0;
                    if (scrollY < maxScrollY) scrollY = maxScrollY;
                    invalidate();
                }
                lastTouchY = y;
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:

                // Handle Back Button Click (Top Left)
                if (!isDragging && x < dpToPx(60) && y < topBarHeight) {
                    if (listener != null) listener.onDisconnectClicked();
                }

                if (isDragging && canScroll) {
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

                canScroll = false; // Reset scrolling flag

                invalidate();
                performClick();
                return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}