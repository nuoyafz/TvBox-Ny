package com.github.tvbox.osc.util;

import com.orhanobut.hawk.Hawk;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Set;

/**
 * 预置接口源。
 *
 * 数据来源：公开整理的影视仓 / TVBox 配置地址（b.zhuiju.me/p/3）。
 *
 * 写入位置是「设置 → 接口与源 → 接口历史」（{@link HawkConfig#API_HISTORY}），
 * 每条格式为 {@code 名称\t地址}，与 {@link HistoryHelper#buildApiLine} 一致，
 * 所以列表里显示的是中文名，点一下就直接切过去。
 *
 * 只灌一次：靠 {@link HawkConfig#BUILTIN_SOURCE_SEED} 记录批次号，
 * 用户手动删掉的条目不会在下次启动时被塞回来。
 * 以后新增源时把 SEED_VERSION +1 即可，老用户也能收到增量。
 */
public final class BuiltinSourceList {

    /** 预置批次：改下面的列表就 +1 */
    private static final int SEED_VERSION = 2;

    /** 接口历史最多保留多少条（与 HistoryHelper 的 30 条一致） */
    private static final int MAX_HISTORY = 30;

    /**
     * 名称, 地址。
     *
     * 中文域名的站点已转成 punycode —— Android 的 HTTP 栈对 IDN 支持不稳定，
     * 直接写中文域名会解析失败（例：饭太硬 → xn--sss604efuw）。
     * 顺序 = 2026-10-27 实测下载耗时升序（最快在前）：影仓 558 / 小马 574 /
     * 创享影视 579 / 小盒子4K 603 / 挺好分享多仓 619 / 饭太硬 688 / 小盒子多仓 790 /
     * 小盒子单仓 1023 / 肥猫 1129 / 摸鱼儿 1152 / 嗷呜 1504 / 王二小 1751。
     * 已剔除实测失效项：老刘备(超时) / 拾光多仓(超时) / 潇洒(403)。
     */
    private static final String[][] SOURCES = {
            {"影仓", "http://124.71.19.220/c/1"},
            {"小马", "https://szyyds.cn/tv/x.json"},
            {"创享影视", "https://ys.cxkjg.cn/api/provide/tvbox"},
            {"小盒子4K", "http://xhztv.top/4k.json"},
            {"挺好分享·多仓", "http://ztha.top/TVBox/GYCK.json"},
            {"饭太硬", "http://www.xn--sss604efuw.net/tv"},
            {"小盒子·多仓", "http://xhztv.top/dc"},
            {"小盒子·单仓", "http://xhztv.top/xhz"},
            {"肥猫", "http://xn--z7x900a.net/"},
            {"摸鱼儿", "http://www.y456y.com"},
            {"嗷呜", "http://itv666.cc/aowu/config.webp"},
            {"王二小", "http://new.xn--4kq62z5rby2qupq9ub.top"},
    };

    /**
     * 实测失效的预置源（超时 / 403），seed 时直接从历史里剔除，不留死链。
     */
    private static final java.util.Set<String> DEAD_URLS = new java.util.HashSet<>(java.util.Arrays.asList(
            "https://raw.liucn.cc/box/m.json",
            "http://xmbjm.fh4u.org/dc.txt",
            "https://cnb.cool/fish2018/xs/-/git/raw/main/api.json"
    ));

    private BuiltinSourceList() {
    }

    /** 必须在 Hawk 初始化之后调用；重复调用无副作用（靠 SEED_VERSION 控制只重排/清理一次） */
    public static void seedIfNeeded() {
        try {
            if (Hawk.get(HawkConfig.BUILTIN_SOURCE_SEED, 0) >= SEED_VERSION) {
                return;
            }
            ArrayList<String> history = Hawk.get(HawkConfig.API_HISTORY, new ArrayList<String>());

            // 1) 先剔除实测失效的死链（超时 / 403 等），不留历史残留
            ArrayList<String> kept = new ArrayList<>();
            for (String item : history) {
                if (DEAD_URLS.contains(HistoryHelper.getApiLineUrl(item).trim())) {
                    continue;
                }
                kept.add(item);
            }
            history = kept;

            // 2) 按最新实测速度顺序重排：SOURCES 顺序的条目排到最前，其余用户自定义项保持在后
            java.util.LinkedHashMap<String, String> urlToEntry = new java.util.LinkedHashMap<>();
            for (String item : history) {
                urlToEntry.put(HistoryHelper.getApiLineUrl(item).trim(), item);
            }
            ArrayList<String> newHistory = new ArrayList<>();
            for (String[] source : SOURCES) {
                String name = source[0];
                String url = source[1];
                String existing = urlToEntry.remove(url);
                if (existing != null) {
                    newHistory.add(existing);
                } else {
                    newHistory.add(HistoryHelper.buildApiLine(name, url));
                }
            }
            // 用户自己加的、不在内置列表里的源，原样跟在后面
            for (String entry : urlToEntry.values()) {
                newHistory.add(entry);
            }

            while (newHistory.size() > MAX_HISTORY) {
                newHistory.remove(newHistory.size() - 1);
            }
            Hawk.put(HawkConfig.API_HISTORY, newHistory);
            Hawk.put(HawkConfig.BUILTIN_SOURCE_SEED, SEED_VERSION);
            LOG.i("BuiltinSourceList reseeded v" + SEED_VERSION + ", total=" + newHistory.size());
        } catch (Throwable th) {
            th.printStackTrace();
        }
    }
}
