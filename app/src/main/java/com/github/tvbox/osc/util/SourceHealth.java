package com.github.tvbox.osc.util;

import com.github.tvbox.osc.bean.SourceBean;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import com.orhanobut.hawk.Hawk;

import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.StringCallback;
import com.lzy.okgo.model.Response;

/**
 * 源健康度 v2：v1 的基础上补了三件必须做的事。
 *
 * 1. 时间窗：只统计 24 小时内的失败，过期自动作废（一次网络抖动不会把源永久打死）；
 *    任意一次成功立即复活（fail 清零）。
 * 2. 并发安全：所有读改写走同一把锁，首页并发 getSort 不会丢计数。
 * 3. 一键体检：checkAll() 并发探活配置里的源，结果直接写回健康度。
 *
 * 存储："sh_<key>" = ok,fail,lastMs,totalMs,lastFailAt,lastOkAt
 */
public final class SourceHealth {

    private static final String PREFIX = "sh_";
    private static final int DEAD_STREAK = 3;
    /** 失败统计时间窗：24 小时 */
    private static final long FAIL_WINDOW_MS = 24 * 3600 * 1000L;
    private static final Object LOCK = new Object();
    /** 取 Spider 实例要串行：getCSP 会改 loader 里的全局状态（currentPyKey 等） */
    private static final Object SPIDER_INIT_LOCK = new Object();

    private SourceHealth() {
    }

    private static String raw(String key) {
        try {
            return Hawk.get(PREFIX + key, "0,0,0,0,0,0");
        } catch (Throwable t) {
            return "0,0,0,0,0,0";
        }
    }

    private static void put(String key, String value) {
        try {
            Hawk.put(PREFIX + key, value);
        } catch (Throwable ignored) {
        }
    }

    /** 成功一次：costMs 本次耗时；成功即复活（fail 清零） */
    public static void reportSuccess(String key, long costMs) {
        reportSuccess(key, costMs, false);
    }

    /**
     * 成功一次（带来源类型）。
     *
     * cached=true 表示这次耗时是「本地缓存/纯本地拼装」打出来的——典型是蜘蛛源的
     * homeContent() 命中 jar 内缓存，1~2ms 就返回，跟网络快慢无关。label() 会把
     * 这类数据显示成「缓存 · Nms」，不然用户看到「快 · 1ms」会以为源真的快。
     */
    public static void reportSuccess(String key, long costMs, boolean cached) {
        if (key == null) return;
        synchronized (LOCK) {
            String[] p = raw(key).split(",");
            int ok = parseInt(p, 0) + 1;
            long last = Math.max(0, costMs);
            long total = parseLong(p, 3) + Math.max(0, costMs);
            put(key, ok + ",0," + last + "," + total + ",0," + System.currentTimeMillis() + "," + (cached ? 1 : 0));
        }
    }

    /** 失败一次：fail 只累计时间窗内的，超过 24h 的老失败作废 */
    public static void reportFail(String key) {
        if (key == null) return;
        synchronized (LOCK) {
            String[] p = raw(key).split(",");
            int ok = parseInt(p, 0);
            long lastFail = parseLong(p, 4);
            long now = System.currentTimeMillis();
            int fail = (now - lastFail > FAIL_WINDOW_MS) ? 1 : parseInt(p, 1) + 1;
            long lastOk = parseLong(p, 5);
            int kind = p.length > 6 ? parseInt(p, 6) : 0;
            put(key, ok + "," + fail + "," + parseLong(p, 2) + "," + parseLong(p, 3) + "," + now + "," + lastOk + "," + kind);
        }
    }

    public static void reset(String key) {
        if (key != null) put(key, "0,0,0,0,0,0");
    }

    /** 判死：24h 内失败≥3 且多于成功 */
    public static boolean isDead(String key) {
        synchronized (LOCK) {
            String[] p = raw(key).split(",");
            long lastFail = parseLong(p, 4);
            if (lastFail <= 0 || System.currentTimeMillis() - lastFail > FAIL_WINDOW_MS) return false;
            int ok = parseInt(p, 0);
            int fail = parseInt(p, 1);
            return fail >= DEAD_STREAK && fail > ok;
        }
    }

    public static long avgCost(String key) {
        synchronized (LOCK) {
            String[] p = raw(key).split(",");
            int ok = parseInt(p, 0);
            long total = parseLong(p, 3);
            if (ok <= 0) return -1;
            return total / ok;
        }
    }

    /** 评分：越高越健康；无数据 50 分（中性） */
    public static float score(String key) {
        synchronized (LOCK) {
            String[] p = raw(key).split(",");
            int ok = parseInt(p, 0);
            int fail = parseInt(p, 1);
            if (ok == 0 && fail == 0) return 50f;
            float rate = (float) ok / (ok + fail);
            long avg = avgCost(key);
            float latencyPenalty = avg < 0 ? 10f : Math.min(avg / 50f, 25f);
            float s = rate * 100f - fail * 8f - latencyPenalty;
            // 死源直接压到底
            return isDead(key) ? Math.min(s, 5f) : s;
        }
    }

    /** 卡片小注 */
    public static String label(String key) {
        if (isDead(key)) return "源已失效";
        long avg = avgCost(key);
        if (avg < 0) return "";
        // 蜘蛛源命中本地缓存时耗时是函数调用级（<50ms），跟网络无关，别冒充「快」
        if (avg < 50 && isCachedKind(key)) return "缓存 · " + avg + "ms";
        if (avg <= 800) return "快 · " + avg + "ms";
        if (avg <= 2500) return "一般 · " + avg + "ms";
        return "慢 · " + avg + "ms";
    }

    /** 最近一次成功是否来自本地缓存路径（蜘蛛 homeContent 命中缓存） */
    private static boolean isCachedKind(String key) {
        synchronized (LOCK) {
            String[] p = raw(key).split(",");
            return p.length > 6 && parseInt(p, 6) == 1;
        }
    }

    /** 按健康度排序；调用方负责是否启用（默认设置里是关的，避免打乱用户习惯） */
    public static void sortByHealth(List<SourceBean> sites) {
        if (sites == null || sites.size() < 2) return;
        try {
            Collections.sort(sites, (a, b) -> Float.compare(
                    score(b == null ? null : b.getKey()),
                    score(a == null ? null : a.getKey())));
        } catch (Throwable ignored) {
        }
    }

    /** 体检结果：只统计真正探测过的源 */
    public static String summary(List<SourceBean> tested, int skipped, int totalSites) {
        int dead = 0, fast = 0;
        for (SourceBean s : tested) {
            if (s == null) continue;
            if (isDead(s.getKey())) dead++;
            long avg = avgCost(s.getKey());
            if (avg >= 0 && avg <= 800) fast++;
        }
        String base = "共 " + totalSites + " 个源 · 已测 " + tested.size()
                + " · 快 " + fast + " · 失效 " + dead;
        return skipped > 0 ? base + " · 跳过 " + skipped : base;
    }

    public interface ScanCallback {
        /** done 数量 / 总数，用于进度提示 */
        void onProgress(int done, int total);

        /** 全部完成 */
        void onDone(String summary);
    }

    /**
     * 体检进度：两类探活（HTTP 直连 / 蜘蛛）共用一个计数器，最后一个完成时回调 onDone。
     */
    private static final class ScanState {
        final int total;
        final int skipped;
        final int totalSites;
        final List<SourceBean> tested;
        final ScanCallback cb;
        final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
        int done = 0;
        boolean finished = false;

        ScanState(int total, int skipped, int totalSites, List<SourceBean> tested, ScanCallback cb) {
            this.total = total;
            this.skipped = skipped;
            this.totalSites = totalSites;
            this.tested = tested;
            this.cb = cb;
        }

        void step() {
            final int d;
            synchronized (this) {
                if (finished) return;
                done++;
                d = done;
                if (d >= total) finished = true;
            }
            if (cb == null) return;
            if (d < total) {
                main.post(() -> cb.onProgress(d, total));
            } else {
                final String sum = summary(tested, skipped, totalSites);
                main.post(() -> {
                    cb.onProgress(d, total);
                    cb.onDone(sum);
                });
            }
        }
    }

    /**
     * 一键体检。分两类源分别探活，覆盖配置文件里的全部站点：
     *
     * 1. HTTP 直连源（api 是 http/https url，多为 type 0/1）：直接打 ac=list 探活，并发、4 秒看门狗；
     * 2. 蜘蛛源（api 是 csp_xxx / jar / py / js）：api 根本不是 url，只能取 Spider 实例调
     *    homeContent()，走它内部的真实接口。这一步慢，4 路并发 + 单源 8 秒超时。
     *
     * 旧版只筛 http，实测一份 49 源的配置里 46 个是 csp 蜘蛛源、只有 3 个是 url，
     * 用户看到的就是"只测了 3 个"。
     *
     * 不阻塞调用线程，回调在主线程。
     */
    public static void checkAll(final List<SourceBean> sites, final ScanCallback cb) {
        if (sites == null || sites.isEmpty()) {
            if (cb != null) cb.onDone("没有可体检的源");
            return;
        }
        final List<SourceBean> httpTargets = new ArrayList<>();
        final List<SourceBean> spiderTargets = new ArrayList<>();
        for (SourceBean s : sites) {
            if (s == null) continue;
            String api = s.getApi();
            if (api.startsWith("http://") || api.startsWith("https://")) {
                httpTargets.add(s);
            } else if (!api.isEmpty()) {
                spiderTargets.add(s);
            }
        }
        final int total = httpTargets.size() + spiderTargets.size();
        if (total == 0) {
            if (cb != null) cb.onDone("没有可体检的源");
            return;
        }
        final List<SourceBean> tested = new ArrayList<>(httpTargets);
        tested.addAll(spiderTargets);
        final ScanState st = new ScanState(total, sites.size() - total, sites.size(), tested, cb);

        // ---- 第一类：HTTP 直连源，并发探活 ----
        for (final SourceBean s : httpTargets) {
            final long t0 = System.currentTimeMillis();
            final AtomicBoolean settled = new AtomicBoolean(false);
            // 4 秒看门狗：OkGo 3.0.4 没有请求级超时，超时未回调就判定该源失败
            st.main.postDelayed(() -> {
                if (settled.compareAndSet(false, true)) {
                    reportFail(s.getKey());
                    st.step();
                }
            }, 4000);
            try {
                OkGo.<String>get(s.getApi())
                        .tag("source-check")
                        .params("ac", "list")
                        .params("pg", "1")
                        .params("t", System.currentTimeMillis())
                        .execute(new StringCallback() {
                            @Override
                            public void onSuccess(Response<String> response) {
                                String body = response.body();
                                if (body != null && body.length() > 0) {
                                    reportSuccess(s.getKey(), System.currentTimeMillis() - t0);
                                } else {
                                    reportFail(s.getKey());
                                }
                                tick();
                            }

                            @Override
                            public void onError(Response<String> response) {
                                super.onError(response);
                                reportFail(s.getKey());
                                tick();
                            }

                            private void tick() {
                                if (!settled.compareAndSet(false, true)) return;
                                st.step();
                            }
                        });
            } catch (Throwable t) {
                if (settled.compareAndSet(false, true)) {
                    reportFail(s.getKey());
                    st.step();
                }
            }
        }

        // ---- 第二类：蜘蛛源，4 路并发 + 单源 8 秒超时 ----
        if (spiderTargets.isEmpty()) return;
        ExecutorService pool = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "src-probe");
            t.setDaemon(true);
            return t;
        });
        for (final SourceBean s : spiderTargets) {
            pool.execute(() -> probeSpider(s, st));
        }
        pool.shutdown();
    }

    /**
     * 蜘蛛源探活：取其 Spider 实例调 homeContent()，返回非空即算活。
     * 单源 8 秒还没回来按失败记（不直接判死：要累计 3 次才判死，一次超时不冤枉源）。
     */
    private static void probeSpider(final SourceBean s, final ScanState st) {
        final long t0 = System.currentTimeMillis();
        final AtomicBoolean settled = new AtomicBoolean(false);
        Thread worker = new Thread(() -> {
            boolean ok = false;
            try {
                com.github.catvod.crawler.Spider spider;
                synchronized (SPIDER_INIT_LOCK) {
                    spider = com.github.tvbox.osc.api.ApiConfig.get().getCSP(s);
                }
                if (spider != null) {
                    String home = spider.homeContent(false);
                    ok = home != null && home.length() > 2;
                }
            } catch (Throwable ignored) {
            }
            if (!settled.compareAndSet(false, true)) return;
            // cached=true：homeContent 大概率是 jar 内缓存/本地拼装，label 里标「缓存」不冒充网络快
            if (ok) reportSuccess(s.getKey(), System.currentTimeMillis() - t0, true);
            else reportFail(s.getKey());
            st.step();
        }, "src-probe-" + s.getKey());
        worker.setDaemon(true);
        worker.start();
        try {
            worker.join(8000);
        } catch (InterruptedException ignored) {
        }
        if (settled.compareAndSet(false, true)) {
            // 超时：线程还在跑（可能是慢接口），这里只判本次失败，先放行继续测下一个
            reportFail(s.getKey());
            st.step();
        }
    }

    /** 最近一次体检时间，供设置页显示 */
    public static String lastScanText() {
        long ts = Hawk.get("sh_last_scan", 0L);
        if (ts <= 0) return "尚未体检";
        return new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(new Date(ts));
    }

    public static void markScanned() {
        Hawk.put("sh_last_scan", System.currentTimeMillis());
    }

    private static int parseInt(String[] p, int i) {
        try {
            return i < p.length ? Integer.parseInt(p[i].trim()) : 0;
        } catch (Throwable t) {
            return 0;
        }
    }

    private static long parseLong(String[] p, int i) {
        try {
            return i < p.length ? Long.parseLong(p[i].trim()) : 0L;
        } catch (Throwable t) {
            return 0L;
        }
    }
}
