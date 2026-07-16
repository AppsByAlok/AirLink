package com.appsbyalok.airlink.data;

import android.graphics.RectF;
import android.os.Build;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;

public class DeviceItem {
    public String name;
    public String address;

    // Indicates if the device was found during the active scan (nearby)
    public boolean isNearby = false;

    // Cached Canvas data
    public StaticLayout nameLayout;
    public StaticLayout addressLayout;
    public final RectF actionBounds = new RectF();
    public boolean isActionPressed = false;

    public DeviceItem(String name, String address) {
        this.name = (name != null && !name.isEmpty()) ? name : "Unknown Device";
        this.address = address;
    }

    /**
     * Calculates the text wrapping based on available screen space and current paints.
     */
    public void buildLayouts(float totalWidth, boolean isPaired, TextPaint titlePaint, TextPaint subPaint) {
        // Reserve 220px for the action button on the right
        // Reserve an extra 50px on the left for the Status Indicator dot
        float reserveLeft = 50f;
        float textAvailableWidth = totalWidth - 220f - reserveLeft;
        if (textAvailableWidth < 100f) textAvailableWidth = 100f;

        nameLayout = createStaticLayout(name, titlePaint, textAvailableWidth);
        addressLayout = createStaticLayout(address, subPaint, textAvailableWidth);
    }

    @SuppressWarnings("deprecation")
    private StaticLayout createStaticLayout(String text, TextPaint paint, float width) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return StaticLayout.Builder.obtain(text, 0, text.length(), paint, (int) width)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(0f, 1f)
                    .setIncludePad(false)
                    .build();
        } else {
            // Fallback for API 21 & 22
            return new StaticLayout(text, paint, (int) width, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        DeviceItem that = (DeviceItem) obj;
        return address.equals(that.address);
    }

    @Override
    public int hashCode() {
        return address.hashCode();
    }
}