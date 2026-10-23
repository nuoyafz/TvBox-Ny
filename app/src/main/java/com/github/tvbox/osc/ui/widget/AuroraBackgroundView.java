package com.github.tvbox.osc.ui.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.util.ThemeManager;

/**
 * 沉浸光感 · 极光底图（主题可切）。
 *
 * 五层，全部用 Shader 直接画，尺寸变化时才重建，之后每帧都是 GPU 缓存合成：
 *
 *   ① 底色      深夜色（保证白字对比）
 *   ② 天空带    顶部主色 → 中段次色 → 透明，形成"地平线"
 *   ③ 太阳点    左上偏白暖光，给材质一点通透感（华为那种"光从屏幕里透出来"）
 *   ④ 侧光      右上次色，跟顶栏玻璃互相咬合
 *   ⑤ 地光      底部深色，托住海报排
 *
 * 性能：onDraw 只有 1 次 drawRect + 3 次 drawCircle，比贴一张 PNG 底图还便宜；
 * 流光动画只跑 8fps（慢漂移不需要 60fps），低配设备直接静态。
 */
public class AuroraBackgroundView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private LinearGradient gBase, gSky;
    private RadialGradient gSun, gPink, gGround;
    private int W, H;

    /** 流光相位：0~1，随时间缓慢移动光斑圆心（低配传 false 关掉） */
    private final boolean flow;
    private long startMs;

    /** 天空带占屏高的比例 */
    private static final float SKY_RATIO = 0.66f;

    /** 主题色（由 ThemeManager 注入） */
    private int cSkyTop, cSkyMid, cGround, cSun, cBgA, cBgB;

    public AuroraBackgroundView(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.flow = !App.getInstance().isLowEndDevice();
        startMs = System.currentTimeMillis();
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
            buildShaders(0f);
            invalidate();
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        W = w;
        H = h;
        buildShaders(0f);
    }

    private void buildShaders(float phase) {
        if (W <= 0 || H <= 0) return;
        float drift = phase * W * 0.045f;
        float longSide = Math.max(W, H);

        // ① 底色
        gBase = new LinearGradient(0, 0, 0, H, new int[]{cBgA, cBgB}, null, Shader.TileMode.CLAMP);

        // ② 天空带：主色 → 次色 → 透明，只占上半屏
        gSky = new LinearGradient(0, 0, 0, H * SKY_RATIO,
                new int[]{cSkyTop, cSkyMid, cSkyMid & 0x00FFFFFF},
                new float[]{0f, 0.46f, 1f}, Shader.TileMode.CLAMP);

        // ③ 太阳点
        gSun = new RadialGradient(W * 0.15f + drift, H * 0.07f, longSide * 0.26f,
                new int[]{cSun, cSun & 0x00FFFFFF}, null, Shader.TileMode.CLAMP);

        // ④ 侧光
        gPink = new RadialGradient(W * 0.92f - drift, H * 0.30f, longSide * 0.40f,
                new int[]{cSkyMid, cSkyMid & 0x00FFFFFF}, null, Shader.TileMode.CLAMP);

        // ⑤ 地光
        gGround = new RadialGradient(W * 0.48f, H * 1.06f, longSide * 0.60f,
                new int[]{cGround, cGround & 0x00FFFFFF}, null, Shader.TileMode.CLAMP);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        if (W <= 0 || H <= 0) return;
        paint.setShader(gBase);
        canvas.drawRect(0, 0, W, H, paint);
        paint.setShader(gSky);
        canvas.drawRect(0, 0, W, H, paint);
        paint.setShader(gSun);
        canvas.drawCircle(W * 0.15f, H * 0.07f, Math.max(W, H) * 0.26f, paint);
        paint.setShader(gPink);
        canvas.drawCircle(W * 0.92f, H * 0.30f, Math.max(W, H) * 0.40f, paint);
        paint.setShader(gGround);
        canvas.drawCircle(W * 0.48f, H * 1.06f, Math.max(W, H) * 0.60f, paint);
    }

    /** 流光：光斑圆心缓慢漂移；低配设备不跑动画，静态光斑 */
    public void startFlow() {
        if (!flow) return;
        postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isAttachedToWindow()) return;
                float t = (System.currentTimeMillis() - startMs) % 14000f / 14000f;
                float phase = 0.5f - 0.5f * (float) Math.cos(t * Math.PI * 2);
                buildShaders(phase);
                invalidate();
                // 8fps：慢漂移看不出来帧率，但 shader 重建次数少了 7 倍，滚动时更稳
                postDelayed(this, 125);
            }
        }, 125);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startFlow();
    }
}
