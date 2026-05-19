package com.appsbyalok.airlink.data;

import android.graphics.RectF;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;

public class ChatMessage {
    public String text;
    public boolean isMine;

    // Cached layout data for Canvas rendering
    public StaticLayout textLayout;
    public float height;
    public final RectF bounds = new RectF();

    public ChatMessage(String text, boolean isMine) {
        this.text = text;
        this.isMine = isMine;
    }

    /**
     * Generates the StaticLayout so the Canvas doesn't have to recalculate wrapping every frame.
     * Requires the TextPaint to be passed in from the View.
     */
    public void buildLayout(float maxWidth, TextPaint paint) {
        textLayout = StaticLayout.Builder.obtain(text, 0, text.length(), paint, (int) maxWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.2f)
                .setIncludePad(false)
                .build();
        height = textLayout.getHeight() + 40f; // Add internal bubble padding
    }
}