package com.github.tvbox.osc.ui.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.util.ThemeManager;

/**
 * 暗房底图：石墨黑基底、极轻主题光晕和细网格，不使用图片壁纸。
 *
 * 背景仅用 Canvas 和 Shader 绘制，网格边缘渐隐；没有海报背景或持续动画。
 */
public class AuroraBackgroundView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private LinearGradient gBase, gSky, gGrid;
    private RadialGradient gSun, gPink, gGround;
    private int W, H;
    /** 网格线合并成一条 Path 一次 drawPath，替代原来每根线一次 drawLine（1080p 下约 25 次） */
    private final Path gridPath = new Path();

    /** Static light field keeps the home screen calm and avoids background animation noise. */

    /** 主题色（由 ThemeManager 注入） */
    private int cSkyTop, cSkyMid, cGround, cSun, cBgA, cBgB;

    public AuroraBackgroundView(Context context, AttributeSet attrs) {
        super(context, attrs);
        // 静态背景：5 个 shader + 网格线只画一次。走硬件层后 invalidate 只是重放显示列表，
        // 不用再全量软绘（换主题时 setPalette 会重建 shader 再 invalidate）。
        setLayerType(LAYER_TYPE_HARDWARE, null);
        cSkyTop = ThemeManager.skyTop();
        cSkyMid = ThemeManager.skyMid();
        cGround = ThemeManager.ground();
        cSun = ThemeManager.sun();
        cBgA = ThemeManager.bgA();
        cBgB = ThemeManager.bgB();
    }

    /** 切换主题：换色 + 立刻重绘（不需要重建 View） */
    public void setPalette(int skyTop, int skyMid, int ground, int sun, int bgA, int bgB) {
        this.cSkyTop = skyTop;
        this.cSkyMid = skyMid;
        this.cGround = ground;
        this.cSun = sun;
        this.cBgA = bgA;
        this.cBgB = bgB;
        if (W > 0 && H > 0) {
            buildShaders();
            invalidate();
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        W = w;
        H = h;
        buildShaders();
    }

    private void buildShaders() {
        if (W <= 0 || H <= 0) return;
        float longSide = Math.max(W, H);

        // ① 底色
        gBase = new LinearGradient(0, 0, 0, H, new int[]{cBgA, cBgB}, null, Shader.TileMode.CLAMP);

        // ② 天空带：主色 → 次色 → 透明，只占上半屏
        gSky = new LinearGradient(0, 0, 0, H * 0.70f,
                new int[]{cSkyTop, cSkyMid & 0x00FFFFFF}, null, Shader.TileMode.CLAMP);
        gGrid = new LinearGradient(0, 0, 0, H,
                new int[]{0x000D1714, 0x0AFFFFFF, 0x0AFFFFFF, 0x000D1714},
                new float[]{0f, 0.18f, 0.84f, 1f}, Shader.TileMode.CLAMP);

        // ③ 太阳点
        gSun = new RadialGradient(W * 0.78f, H * 0.12f, longSide * 0.42f,
                new int[]{cSun, cSun & 0x00FFFFFF}, null, Shader.TileMode.CLAMP);

        // ④ 侧光
        gPink = new RadialGradient(W * 0.10f, H * 0.78f, longSide * 0.45f,
                new int[]{cSkyMid, cSkyMid & 0x00FFFFFF}, null, Shader.TileMode.CLAMP);

        // ⑤ 地光
        gGround = new RadialGradient(W * 0.52f, H * 1.12f, longSide * 0.72f,
                new int[]{cGround, cGround & 0x00FFFFFF}, null, Shader.TileMode.CLAMP);

        // 网格线一次性构建：横竖各 ~16 根合成一条 Path，绘制时一次 drawPath 完成
        gridPath.reset();
        float gap = Math.max(48f, W / 16f);
        for (float x = 0; x <= W; x += gap) {
            gridPath.moveTo(x, 0);
            gridPath.lineTo(x, H);
        }
        for (float y = 0; y <= H; y += gap) {
            gridPath.moveTo(0, y);
            gridPath.lineTo(W, y);
        }
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        if (W <= 0 || H <= 0) return;
        paint.setShader(gBase);
        canvas.drawRect(0, 0, W, H, paint);
        paint.setShader(gSky);
        canvas.drawRect(0, 0, W, H, paint);
        paint.setShader(gSun);
        canvas.drawCircle(W * 0.78f, H * 0.12f, Math.max(W, H) * 0.42f, paint);
        paint.setShader(gPink);
        canvas.drawCircle(W * 0.10f, H * 0.78f, Math.max(W, H) * 0.45f, paint);
        paint.setShader(gGround);
        canvas.drawCircle(W * 0.52f, H * 1.12f, Math.max(W, H) * 0.72f, paint);
        paint.setShader(gGrid);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, getResources().getDisplayMetrics().density * 0.35f));
        canvas.drawPath(gridPath, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(null);
    }

    /** Retained for API compatibility; this visual system intentionally has no ambient motion. */
    public void startFlow() {
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startFlow();
    }
}
