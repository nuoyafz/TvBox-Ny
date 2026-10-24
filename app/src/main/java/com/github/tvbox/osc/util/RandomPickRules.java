package com.github.tvbox.osc.util;

import java.util.Random;

/**
 * 随机看的纯逻辑规则（不依赖 Android，可单独跑 JVM 测试）。
 *
 * 分类识别原来写在对话框里、词库只有「电影/电视剧/动漫」几种写法，
 * 源里叫「片库」「影院」「短剧」「综艺」的分类全被丢掉，按钮上只剩 2~3 类。
 */
public final class RandomPickRules {

    private RandomPickRules() {
    }

    /** 电影类关键词（命中即算电影，优先于剧集判断）。 */
    private static final String[] MOVIE_KEYS = {
            "剧场版", "电影", "影片", "影院", "片库", "movie", "film", "片", "蓝光"
    };

    /** 剧集类关键词。 */
    private static final String[] SERIES_KEYS = {
            "电视剧", "连续剧", "剧集", "短剧", "系列剧", "series", "tv",
            "动漫", "番剧", "动画", "综艺", "纪录", "剧", "番", "集"
    };

    public static final int KIND_NONE = 0;
    public static final int KIND_MOVIE = 1;
    public static final int KIND_SERIES = 2;

    public static boolean isMovie(String value) {
        return containsAny(value, MOVIE_KEYS);
    }

    public static boolean isSeries(String value) {
        return containsAny(value, SERIES_KEYS);
    }

    /**
     * 互斥归类：电影优先。
     * 「剧场版」同时含「剧」、「纪录片」同时含「片」和「纪录」，
     * 两个独立判断会双双命中，必须由这一层定唯一归属。
     */
    public static int classify(String value) {
        if (value == null) return KIND_NONE;
        String lower = value.toLowerCase();
        // 纪录片/专题片：二分归不下去，含裸「片」会被电影吞掉，先摘出来丢给「全部」池
        if (lower.contains("纪录") || lower.contains("记录") || lower.contains("专题片")) {
            return KIND_NONE;
        }
        if (isMovie(lower)) return KIND_MOVIE;
        if (isSeries(lower)) return KIND_SERIES;
        return KIND_NONE;
    }

    /** 抽页：大分类有几百上千部，只在第 1 页抽等于永远在前几十部里打转。 */
    public static int randomPage(Random random) {
        return 1 + (random == null ? new Random().nextInt(3) : random.nextInt(3));
    }

    private static boolean containsAny(String value, String[] keys) {
        if (value == null) return false;
        String lower = value.toLowerCase();
        for (String key : keys) {
            if (lower.contains(key)) return true;
        }
        return false;
    }
}
