package com.github.tvbox.osc.util;

import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * 全站统一的焦点动效。
 *
 * 画报版规则（别再改回 1.08 弹跳）：
 * 1. 焦点反馈的主载体是卡片外圈的白色 2mm 环（见 selector_poster_card），
 *    缩放只是极轻的呼吸，1.03 倍，放大太多会显得廉价；
 * 2. 进 160ms / 出 120ms，Decelerate 收尾，不要 Bounce；
 * 3. 只改 scale，不动布局参数，避免 RecyclerView 重排。
 *
 * 宿主 RecyclerView 必须 clipChildren=false 且 clipToPadding=false，否则放大部分会被裁掉。
 */
public final class AtvFocusHelper {

    private static final float FOCUS_SCALE = 1.03f;
    private static final long FOCUS_DURATION = 160L;
    private static final long UNFOCUS_DURATION = 120L;

    /**
     * 插值器复用。
     * 原来每次焦点变化都 new 一个 OvershootInterpolator：快速上下连按时
     * 每秒要新建几十个对象并起几十个动画，一起抢 Choreographer 的帧 —— 这就是"滑动不顺畅"。
     */
    private static final DecelerateInterpolator DEC = new DecelerateInterpolator();

    /** 焦点落点回调，用于记录位置，返回时还原 */
    public interface OnFocusListener {
        void onItemFocused(View itemView);
    }

    private AtvFocusHelper() {
    }

    public static void bind(final View itemView) {
        bind(itemView, null);
    }

    public static void bind(final View itemView, final OnFocusListener listener) {
        bind(itemView, listener, null, null);
    }

    /**
     * 带压暗的焦点绑定：非焦点时给 dimView 套 dimFilter（去色+压暗），
     * 焦点时清除恢复全彩 —— "彩色只留焦点卡"的沉静版核心。
     */
    public static void bind(final View itemView, final OnFocusListener listener,
                            final android.widget.ImageView dimView, final android.graphics.ColorFilter dimFilter) {
        if (itemView == null) return;
        itemView.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                applyScale(v, hasFocus);
                if (dimView != null) {
                    dimView.setColorFilter(hasFocus ? null : dimFilter);
                }
                if (hasFocus && listener != null) {
                    try {
                        listener.onItemFocused(v);
                    } catch (Throwable ignored) {
                    }
                }
            }
        });
    }

    public static void applyScale(View v, boolean hasFocus) {
        if (v == null) return;
        float target = hasFocus ? FOCUS_SCALE : 1.0f;
        // 已经在目标值上就别再起动画：RecyclerView 复用卡片时，
        // 同一个 view 会被反复 bind + requestFocus，这一刀能省掉大量空转动画。
        if (Math.abs(v.getScaleX() - target) < 0.001f && Math.abs(v.getScaleY() - target) < 0.001f) {
            return;
        }
        v.animate().cancel();
        v.animate()
                .scaleX(target)
                .scaleY(target)
                .setDuration(hasFocus ? FOCUS_DURATION : UNFOCUS_DURATION)
                .setInterpolator(DEC)
                .start();
    }
}
