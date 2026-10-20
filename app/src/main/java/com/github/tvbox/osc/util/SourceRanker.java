package com.github.tvbox.osc.util;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import com.orhanobut.hawk.Hawk;

import org.json.JSONObject;

/**
 * 源健康度评分。
 *
 * 解决的问题：TVBox 里一个片源有几十个站点，用户只能一个个点进去试，
 * 不知道哪个能播、哪个快。这里在后台默默记录每个源的真实表现，
 * 下次打开就能按"能播 + 快"排序，而不是按 json 里的原始顺序。
 *
 * 记录三件事：
 *   ok   —— 成功起播次数
 *   fail —— 解析失败 / 超时次数
 *   avg  —— 起播平均耗时（毫秒，从开始拉流到首帧就绪）
 *
 * 评分（0~100）：成功率占 70 分，速度占 30 分。
 * 没记录过的源给 50 分（中性），避免冷启动时把新源全部排到最后。
 */
public class SourceRanker {

    /** 超过这个耗时按满分算，即"慢到没区别" */
    private static final int MAX_MS = 8000;
    /** 无历史记录时的中性分 */
    private static final int NEUTRAL_SCORE = 50;
    /** 表里最多保留多少个源，防止无限增长 */
    private static final int MAX_ENTRIES = 200;

    private static JSONObject table;

    private static synchronized JSONObject table() {
        if (table == null) {
            String raw = Hawk.get(HawkConfig.SOURCE_RANK, "");
            try {
                table = TextUtils.isEmpty(raw) ? new JSONObject() : new JSONObject(raw);
            } catch (Throwable th) {
                table = new JSONObject();
            }
        }
        return table;
    }

    private static JSONObject entry(String key) {
        JSONObject t = table();
        JSONObject e = t.optJSONObject(key);
        if (e == null) {
            e = new JSONObject();
            try {
                e.put("ok", 0);
                e.put("fail", 0);
                e.put("avg", 0);
            } catch (Throwable ignored) {
            }
        }
        return e;
    }

    /** 成功起播。elapsedMs 为从开始拉流到首帧的时间 */
    public static synchronized void recordOk(String sourceKey, long elapsedMs) {
        if (TextUtils.isEmpty(sourceKey) || elapsedMs <= 0 || elapsedMs > 120000) return;
        try {
            JSONObject e = entry(sourceKey);
            int ok = e.optInt("ok", 0) + 1;
            long avg = e.optLong("avg", 0);
            // 指数滑动平均，新样本权重 1/3，避免单次抖动把平均值带偏
            long newAvg = avg <= 0 ? elapsedMs : (avg * 2 + elapsedMs) / 3;
            e.put("ok", ok);
            e.put("avg", newAvg);
            e.put("at", System.currentTimeMillis());
            table().put(sourceKey, e);
            flush();
            LOG.i("SourceRank ok " + sourceKey + " " + elapsedMs + "ms avg=" + newAvg);
        } catch (Throwable th) {
            th.printStackTrace();
        }
    }

    /** 解析失败 / 超时 / 播放报错 */
    public static synchronized void recordFail(String sourceKey) {
        if (TextUtils.isEmpty(sourceKey)) return;
        try {
            JSONObject e = entry(sourceKey);
            e.put("fail", e.optInt("fail", 0) + 1);
            e.put("at", System.currentTimeMillis());
            table().put(sourceKey, e);
            flush();
            LOG.i("SourceRank fail " + sourceKey);
        } catch (Throwable th) {
            th.printStackTrace();
        }
    }

    /** 0~100，越高越值得优先用 */
    public static synchronized int score(String sourceKey) {
        if (TextUtils.isEmpty(sourceKey)) return NEUTRAL_SCORE;
        JSONObject e = table().optJSONObject(sourceKey);
        if (e == null) return NEUTRAL_SCORE;
        int ok = e.optInt("ok", 0);
        int fail = e.optInt("fail", 0);
        int total = ok + fail;
        if (total <= 0) return NEUTRAL_SCORE;
        double succ = (double) ok / (double) total;
        long avg = e.optLong("avg", 0);
        double speed = avg <= 0 ? 0.5 : 1.0 - Math.min(avg, MAX_MS) / (double) MAX_MS;
        // 有真实耗时记录（avg>0）的源，速度项权重 30 → 40，让"真快"的源更靠前；
        // 没记录过的源保持中性 0.5、权重 30，不会强行靠前（避免冷启动把新源顶到最前）。
        int speedW = avg <= 0 ? 30 : 40;
        int s = (int) Math.round(succ * (100 - speedW) + speed * speedW);
        return Math.max(0, Math.min(100, s));
    }

    /** 给 UI 用的短标签，例如 "1.8s · 稳"；没记录返回空串 */
    public static synchronized String label(String sourceKey) {
        if (TextUtils.isEmpty(sourceKey)) return "";
        JSONObject e = table().optJSONObject(sourceKey);
        if (e == null) return "";
        int ok = e.optInt("ok", 0);
        int fail = e.optInt("fail", 0);
        if (ok + fail <= 0) return "";
        long avg = e.optLong("avg", 0);
        String speed = avg > 0 ? String.format(java.util.Locale.US, "%.1fs", avg / 1000.0) : "--";
        double succ = (double) ok / (double) (ok + fail);
        String word = succ >= 0.8 ? "稳" : (succ >= 0.5 ? "一般" : "常失败");
        return speed + " · " + word;
    }

    /** 按评分降序排（稳定排序，同分保持原顺序） */
    public static <T> void sortByScore(java.util.List<T> list, ScoreProvider<T> provider) {
        if (list == null || provider == null || list.size() < 2) return;
        try {
            java.util.Collections.sort(list, new java.util.Comparator<T>() {
                @Override
                public int compare(T a, T b) {
                    return Integer.compare(score(provider.keyOf(b)), score(provider.keyOf(a)));
                }
            });
        } catch (Throwable th) {
            th.printStackTrace();
        }
    }

    public interface ScoreProvider<T> {
        String keyOf(T item);
    }

    /**
     * 写盘去抖间隔。
     *
     * Hawk 每次 put 都会把整个存储 map 序列化成一个 JSON 再落盘，SOURCE_RANK 单条就有
     * 200 条记录（约 20KB）。搜索是几十个源并发的，原来每个源成功都 flush 一次，
     * 一次搜索能触发几十次 20KB 序列化 + SP 写。这里合并成 800ms 内最多写一次，
     * 进程被杀的极端情况最多丢最后一次统计，统计本来就是近似值，可以接受。
     */
    private static final long FLUSH_DELAY_MS = 800L;
    private static final Handler FLUSH_HANDLER = new Handler(Looper.getMainLooper());
    private static final Runnable FLUSH_TASK = new Runnable() {
        @Override
        public void run() {
            doFlush();
        }
    };

    private static void flush() {
        FLUSH_HANDLER.removeCallbacks(FLUSH_TASK);
        FLUSH_HANDLER.postDelayed(FLUSH_TASK, FLUSH_DELAY_MS);
    }

    private static synchronized void doFlush() {
        try {
            JSONObject t = table();
            // 超过上限时不暴力清空（那会让所有源健康度一夜归零、排序失效），
            // 而是按 at 时间戳淘汰最旧的若干条。
            while (t.length() > MAX_ENTRIES) {
                String oldestKey = null;
                long oldestAt = Long.MAX_VALUE;
                java.util.Iterator<String> it = t.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    long at = t.optJSONObject(k).optLong("at", 0);
                    if (at < oldestAt) {
                        oldestAt = at;
                        oldestKey = k;
                    }
                }
                if (oldestKey == null) break;
                t.remove(oldestKey);
            }
            Hawk.put(HawkConfig.SOURCE_RANK, t.toString());
        } catch (Throwable th) {
            th.printStackTrace();
        }
    }

    /** 清空全部统计（设置页"重置源评分"用） */
    public static synchronized void reset() {
        FLUSH_HANDLER.removeCallbacks(FLUSH_TASK);
        table = new JSONObject();
        Hawk.put(HawkConfig.SOURCE_RANK, "{}");
    }
}
