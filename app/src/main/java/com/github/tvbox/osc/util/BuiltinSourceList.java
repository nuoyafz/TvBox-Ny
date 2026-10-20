package com.github.tvbox.osc.util;

import com.orhanobut.hawk.Hawk;

import java.util.ArrayList;
import java.util.HashSet;
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
    private static final int SEED_VERSION = 1;

    /** 接口历史最多保留多少条（与 HistoryHelper 的 30 条一致） */
    private static final int MAX_HISTORY = 30;

    /**
     * 名称, 地址。
     *
     * 中文域名的站点已转成 punycode —— Android 的 HTTP 栈对 IDN 支持不稳定，
     * 直接写中文域名会解析失败（例：饭太硬 → xn--sss604efuw）。
     * 顺序按实测可用性排，单仓在前、多仓在后。
     */
    private static final String[][] SOURCES = {
            {"摸鱼儿", "http://www.y456y.com"},
            {"王二小", "http://new.xn--4kq62z5rby2qupq9ub.top"},
            {"肥猫", "http://xn--z7x900a.net/"},
            {"小盒子4K", "http://xhztv.top/4k.json"},
            {"创享影视", "https://ys.cxkjg.cn/api/provide/tvbox"},
            {"影仓", "http://124.71.19.220/c/1"},
            {"老刘备", "https://raw.liucn.cc/box/m.json"},
            {"小马", "https://szyyds.cn/tv/x.json"},
            {"小盒子·单仓", "http://xhztv.top/xhz"},
            {"嗷呜", "http://itv666.cc/aowu/config.webp"},
            {"小盒子·多仓", "http://xhztv.top/dc"},
            {"拾光·多仓", "http://xmbjm.fh4u.org/dc.txt"},
            {"挺好分享·多仓", "http://ztha.top/TVBox/GYCK.json"},
            {"潇洒", "https://cnb.cool/fish2018/xs/-/git/raw/main/api.json"},
            {"饭太硬", "http://www.xn--sss604efuw.net/tv"},
    };

    private BuiltinSourceList() {
    }

    /** 必须在 Hawk 初始化之后调用；重复调用无副作用 */
    public static void seedIfNeeded() {
        try {
            if (Hawk.get(HawkConfig.BUILTIN_SOURCE_SEED, 0) >= SEED_VERSION) {
                return;
            }
            ArrayList<String> history = Hawk.get(HawkConfig.API_HISTORY, new ArrayList<String>());

            // 按"地址"去重：用户可能已经手动加过同一条纯 URL
            Set<String> existUrls = new HashSet<>();
            for (String item : history) {
                existUrls.add(HistoryHelper.getApiLineUrl(item));
            }

            ArrayList<String> toAdd = new ArrayList<>();
            for (String[] source : SOURCES) {
                String name = source[0];
                String url = source[1];
                if (existUrls.contains(url)) {
                    continue;
                }
                toAdd.add(HistoryHelper.buildApiLine(name, url));
                existUrls.add(url);
            }

            if (!toAdd.isEmpty()) {
                // 预置项放最前面，用户原有记录保持在后面
                history.addAll(0, toAdd);
            }
            while (history.size() > MAX_HISTORY) {
                history.remove(history.size() - 1);
            }
            Hawk.put(HawkConfig.API_HISTORY, history);
            Hawk.put(HawkConfig.BUILTIN_SOURCE_SEED, SEED_VERSION);
            LOG.i("BuiltinSourceList seeded " + toAdd.size() + " sources, total=" + history.size());
        } catch (Throwable th) {
            th.printStackTrace();
        }
    }
}
