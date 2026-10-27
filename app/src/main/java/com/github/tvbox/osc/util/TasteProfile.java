package com.github.tvbox.osc.util;

import com.github.tvbox.osc.cache.RoomDataManger;
import com.orhanobut.hawk.Hawk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 口味画像：从观看历史统计类型偏好，纯本地，不联网。
 *
 * 用法：首页推荐列表按 scoreOf(video.type) 降序排，
 * 分数 = 1(基础) + 3×该类型观看次数，看过的类型永远排在没看过的前面。
 * 结果缓存 10 分钟，避免每次刷新首页都全表扫历史。
 */
public final class TasteProfile {

    private static final long CACHE_MS = 10 * 60 * 1000L;
    private static final String KEY = "taste_profile_cache";
    private static final String KEY_TS = "taste_profile_ts";

    private TasteProfile() {
    }

    /** 某类型的权重：没看过=1，看过=1+3×次数（封顶 30，防止一部剧刷几十集把类型刷爆） */
    public static float scoreOf(String type) {
        Map<String, Integer> counts = counts();
        if (type == null) return 1f;
        Integer c = counts.get(type.trim());
        if (c == null || c <= 0) return 1f;
        return 1f + 3f * Math.min(c, 10);
    }

    /**
     * 排序时用：先把计数表取一次，再逐条算分。
     * 原来比较器里每个元素都调 scoreOf → 每次都 Hawk.get + map 查，列表一大就是白烧 CPU。
     */
    public static float scoreOf(Map<String, Integer> snapshot, String type) {
        if (type == null || snapshot == null) return 1f;
        Integer c = snapshot.get(type.trim());
        if (c == null || c <= 0) return 1f;
        return 1f + 3f * Math.min(c, 10);
    }

    /** 当前计数快照（不会阻塞主线程） */
    public static Map<String, Integer> snapshot() {
        return counts();
    }

    /** 前 N 个最爱类型（按次数），没有历史返回空列表 */
    public static List<String> topGenres(int n) {
        Map<String, Integer> counts = counts();
        List<Map.Entry<String, Integer>> list = new ArrayList<>(counts.entrySet());
        list.sort((a, b) -> b.getValue() - a.getValue());
        List<String> out = new ArrayList<>();
        for (int i = 0; i < list.size() && i < n; i++) out.add(list.get(i).getKey());
        return out;
    }

    /** 一句话画像，给 AI/日志用："科幻×12 动作×7 动画×3" */
    public static String summary() {
        StringBuilder sb = new StringBuilder();
        for (String g : topGenres(5)) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(g).append("×").append(counts().get(g));
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Integer> counts() {
        long ts = Hawk.get(KEY_TS, 0L);
        Map<String, Integer> cached = Hawk.get(KEY, (Map<String, Integer>) null);
        if (cached != null && System.currentTimeMillis() - ts < CACHE_MS) {
            return cached;
        }
        // 缓存过期：主线程绝不能在这里同步扫 80 条历史（首页排序就在主线程）。
        // 先把手里的旧值返回，另起 IO 线程重建，下次刷新自然用上新的。
        requestRebuild();
        return cached != null ? cached : java.util.Collections.<String, Integer>emptyMap();
    }

    private static final java.util.concurrent.atomic.AtomicBoolean REBUILDING =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    private static void requestRebuild() {
        if (!REBUILDING.compareAndSet(false, true)) return;
        Threads.io(() -> {
            try {
                rebuild();
            } finally {
                REBUILDING.set(false);
            }
        });
    }

    private static void rebuild() {
        Map<String, Integer> fresh = new HashMap<>();
        try {
            List<com.github.tvbox.osc.bean.VodInfo> history = RoomDataManger.getAllVodRecord(80);
            if (history != null) {
                for (com.github.tvbox.osc.bean.VodInfo info : history) {
                    String t = info.type;
                    if (t == null || t.trim().isEmpty()) continue;
                    t = t.trim().split("[ ,，/、]")[0];
                    Integer c = fresh.get(t);
                    fresh.put(t, c == null ? 1 : c + 1);
                }
            }
        } catch (Throwable th) {
            LOG.longE("taste-profile", "rebuild failed: " + th.getClass().getSimpleName());
            return;
        }
        Hawk.put(KEY, fresh);
        Hawk.put(KEY_TS, System.currentTimeMillis());
    }
}
