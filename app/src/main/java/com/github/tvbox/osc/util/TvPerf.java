package com.github.tvbox.osc.util;

import androidx.recyclerview.widget.RecyclerView;

/**
 * 全站 RecyclerView 性能基线。
 *
 * 背景：电视端焦点移动远比手机滑动频繁，而项目里所有 RV 都没配缓存 ——
 * 默认 itemViewCacheSize=2 且开着 DefaultItemAnimator，导致焦点每移动一格，
 * 屏幕外 1~2 格的 item 就被完全解绑重绑：重新跑 convert()、重新建 Glide 请求、
 * 重新 setOnFocusChangeListener，快速连按时这就是"发涩"的主因。
 *
 * 两个动作：
 * 1. setItemViewCacheSize(10)：屏幕外 10 个 item 保持已绑定状态，焦点回来直接用；
 * 2. setItemAnimator(null)：关掉默认 change 动画。电视上换源/刷新列表时
 *    DefaultItemAnimator 的 fade 淡入不仅添乱，还会让 RecyclerView 多跑一轮
 *    layout + 动画回调；加载态有 LoadSir 兜着，不需要 animator。
 */
public final class TvPerf {

    /** 屏幕外保持绑定的 item 数。电视一屏 6~12 格，10 覆盖上下各一行 */
    private static final int CACHE_SIZE = 10;

    private TvPerf() {
    }

    public static void apply(RecyclerView rv) {
        if (rv == null) return;
        try {
            rv.setItemViewCacheSize(CACHE_SIZE);
            rv.setItemAnimator(null);
        } catch (Throwable ignored) {
            // 第三方 TvRecyclerView 个别版本签名差异，不影响主流程
        }
    }
}
