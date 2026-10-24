package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import com.github.tvbox.osc.util.ThemeManager;

/** Quiet lime-and-graphite roulette wheel, sized for viewing from the sofa. */
public class RandomWheelView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();
    private final int[] colors = {0xFFB8C5B8, 0xFF28302D, 0xFF46504B, 0xFF18201D,
            0xFF89968E, 0xFF28302D, 0xFF5A655F, 0xFF18201D};

    public RandomWheelView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float size = Math.min(getWidth(), getHeight());
        if (size <= 0) return;
        float inset = size * 0.07f;
        bounds.set(inset, inset, size - inset, size - inset);
        float sweep = 360f / colors.length;
        for (int i = 0; i < colors.length; i++) {
            paint.setColor(colors[i]);
            canvas.drawArc(bounds, i * sweep, sweep, true, paint);
        }
        paint.setColor(0xFF0D1210);
        canvas.drawCircle(size / 2f, size / 2f, size * 0.22f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(size * 0.018f);
        int accent = ThemeManager.accent();
        paint.setColor((accent & 0x00FFFFFF) | 0x66000000);
        canvas.drawCircle(size / 2f, size / 2f, size * 0.47f, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(accent);
        Path pointer = new Path();
        float x = size / 2f;
        float y = inset * 0.45f;
        float r = size * 0.065f;
        pointer.moveTo(x, y + r * 1.55f);
        pointer.lineTo(x - r, y - r);
        pointer.lineTo(x + r, y - r);
        pointer.close();
        canvas.drawPath(pointer, paint);
    }
}
