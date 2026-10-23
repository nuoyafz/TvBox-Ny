package com.github.tvbox.osc.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 壁纸源（多源兜底链）。
 *
 * 主源：uapis.cn 随机图接口（直接返回图片二进制，OkGo 自动跟 302）。
 * 备源：樱花随机图 t.alcy.cc（/pc 电脑风景 /ycy 二次元）、paul 巡游 api.paugram.com。
 *
 * 关键：任何一个源都可能抽风（返回 HTML / 超时 / DNS 失败），
 * 所以 endpointChain()/nextFallback() 提供按类别排序的候选链，
 * 下载失败时逐个换源重试，而不是只重试一次同一源。
 */
public final class WallpaperHelper {

    /** 可选类别：[key, 中文名]。bq(表情包)/furry 默认不进轮换池，不宜做背景。 */
    public static final String[][] CATEGORIES = {
            {"landscape", "风景"},
            {"pc_wallpaper", "电脑壁纸"},
            {"mobile_wallpaper", "手机壁纸"},
            {"acg", "二次元"},
            {"anime", "混合动漫"},
            {"general_anime", "动漫"},
            {"ai_drawing", "AI绘画"},
            {"furry", "福瑞"},
            {"bq", "表情包"},
    };

    /** 默认轮换池：挑适合做背景的，避开表情包/福瑞 */
    private static final String[] CALM_POOL = {
            "landscape", "pc_wallpaper", "mobile_wallpaper", "acg", "anime", "general_anime", "ai_drawing",
    };

    private static final String ENDPOINT = "https://uapis.cn/api/v1/random/image";
    /** 二次元向备源（樱花 ycy），返回真图片 */
    private static final String ACG_FALLBACK = "https://t.alcy.cc/ycy";
    /** 风景/电脑向备源（樱花 pc） */
    private static final String PC_FALLBACK = "https://t.alcy.cc/pc";
    /** 通用兜底（paul 巡游随机壁纸） */
    private static final String GENERIC_FALLBACK = "https://api.paugram.com/wallpaper/";

    /** 轮换游标，避免连续两次拿到同一批 */
    private static int cursor = 0;

    private WallpaperHelper() {
    }

    /** 类别 key → 中文名（用于选择对话框展示） */
    public static String labelOf(String key) {
        if (key == null) return "";
        for (String[] c : CATEGORIES) {
            if (c[0].equals(key)) return c[1];
        }
        return key;
    }

    /** 主源地址；拼时间戳绕过 CDN 缓存，保证真能换 */
    public static String endpointFor(String category) {
        String cat = (category == null) ? "" : category.trim();
        String url = ENDPOINT + (cat.isEmpty() ? "" : ("?category=" + cat));
        return url + (url.contains("?") ? "&" : "?") + "t=" + System.currentTimeMillis();
    }

    /** 默认轮换：按时段感知选池——早上动漫、白天风景、傍晚霞光感电脑壁纸、深夜 calming 二次元 */
    public static String nextUrl() {
        String cat = categoryByHour();
        if (cat == null) cat = CALM_POOL[Math.abs(cursor++) % CALM_POOL.length];
        return endpointFor(cat);
    }

    /**
     * 时段感知类别：夜里少刺激（二次元/动漫），傍晚配"日落大道"氛围用风景，
     * 白天正常轮换。null = 跟随原轮换池。
     */
    public static String categoryByHour() {
        int h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        if (h >= 23 || h < 6) return "acg";            // 深夜：安静
        if (h >= 17 && h < 23) return "landscape";     // 傍晚：霞光感
        if (h >= 6 && h < 11) return "anime";          // 早上：元气
        return null;                                    // 白天：正常轮换
    }

    /**
     * 多源兜底：给定类别和已失败的 URL 集合，返回下一个还没试过的候选源；
     * 全部试过则返回 null（上层提示放弃）。
     *
     * 顺序：主源(uapis) → 类别匹配备源(樱花 ycy/pc) → 通用兜底(paugram)。
     */
    public static String nextFallback(String category, Collection<String> tried) {
        List<String> chain = new ArrayList<>();
        chain.add(endpointFor(category));
        String cat = (category == null) ? "" : category.trim();
        boolean animeish = cat.equals("acg") || cat.equals("anime") || cat.equals("general_anime")
                || cat.equals("ai_drawing") || cat.equals("bq") || cat.equals("furry");
        chain.add(animeish ? ACG_FALLBACK : PC_FALLBACK);
        chain.add(PC_FALLBACK);
        chain.add(GENERIC_FALLBACK);
        for (String url : chain) {
            String stamped = url + (url.contains("?") ? "&" : "?") + "t=" + System.currentTimeMillis();
            if (tried == null || !containsPrefix(tried, url)) {
                return stamped;
            }
        }
        return null;
    }

    /** 失败记录带时间戳，按裸地址前缀匹配 */
    private static boolean containsPrefix(Collection<String> tried, String bare) {
        for (String t : tried) {
            if (t != null && t.startsWith(bare)) return true;
        }
        return false;
    }
}
