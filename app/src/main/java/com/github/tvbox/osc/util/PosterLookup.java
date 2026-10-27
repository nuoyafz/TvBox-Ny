package com.github.tvbox.osc.util;

import android.text.TextUtils;
import android.util.LruCache;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.AbsCallback;
import com.lzy.okgo.model.Response;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 片名 → 海报查询（搜索页右侧预览用）。
 *
 * 图源实测（2026-10-27）：
 * - 豆瓣 subject_suggest：有图但**连发约 5 次后限流**，HTTP 200 却返回空数组 → 只做兜底；
 * - 爱奇艺 html5 搜索：12 连发不限流、出图 ~11/12，字段 albumVImage/channel/releaseDate 齐全 → 主源。
 *
 * 另外爱奇艺首条不一定是正片（搜「斩神」首条返回「精神病面前，鬼东西算个球」），
 * 所以按「完全相等 &gt; 前缀 &gt; 包含」打分挑最像的，避免张冠李戴。
 *
 * 缓存用 LruCache（200 条）防无限增长；PENDING 防同一词重复请求，也用于给调用方做并发闸门。
 */
public final class PosterLookup {

    public interface Callback {
        /** pic 一定非空；meta 形如「电影 · 2015」，可能为空串 */
        void onResult(String word, String pic, String meta);
    }

    private static final int CACHE_SIZE = 200;
    private static final int MAX_CANDIDATES = 6;
    /** 同一时刻最多同时在跑的查询数，避免快速输入时请求堆叠 */
    public static final int MAX_CONCURRENT = 6;

    private static final String TAG = "preview-poster";
    private static final String IQIYI_SEARCH = "https://search.video.iqiyi.com/o";
    private static final String DOUBAN_SUGGEST = "https://movie.douban.com/j/subject_suggest";
    private static final String UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36";

    private static final LruCache<String, String[]> CACHE = new LruCache<String, String[]>(CACHE_SIZE) {
        @Override
        protected int sizeOf(String key, String[] value) {
            return 1;
        }
    };
    private static final Set<String> PENDING = Collections.synchronizedSet(new HashSet<String>());

    private PosterLookup() {
    }

    /** 命中缓存返回 {pic, meta}，否则 null */
    public static String[] getCached(String word) {
        if (TextUtils.isEmpty(word)) return null;
        return CACHE.get(word);
    }

    public static int pendingCount() {
        return PENDING.size();
    }

    /** Activity 销毁时调：取消所有在途请求并清 pending（缓存保留，跨页面复用） */
    public static void cancelAll() {
        OkGo.getInstance().cancelTag(TAG);
        PENDING.clear();
    }

    /**
     * 查一个词的海报。命中缓存直接回调；已有同词在跑则忽略（由原请求回调）。
     * 回调在主线程。
     */
    public static void fetch(final String word, final Callback cb) {
        if (TextUtils.isEmpty(word) || cb == null) return;
        String[] cached = CACHE.get(word);
        if (cached != null) {
            cb.onResult(word, cached[0], cached[1]);
            return;
        }
        if (!PENDING.add(word)) return;
        OkGo.<String>get(IQIYI_SEARCH)
                .tag(TAG)
                .params("if", "html5")
                .params("key", word)
                .execute(new AbsCallback<String>() {
                    @Override
                    public void onSuccess(Response<String> response) {
                        String[] hit = parseIqiyi(response.body(), word);
                        if (hit != null) {
                            done(word, hit[0], hit[1], cb);
                        } else {
                            // 爱奇艺没图再兜底豆瓣（豆瓣有限流，失败直接放弃不重试）
                            fetchFromDouban(word, cb);
                        }
                    }

                    @Override
                    public String convertResponse(okhttp3.Response response) throws Throwable {
                        return response.body() == null ? "" : response.body().string();
                    }

                    @Override
                    public void onError(Response<String> response) {
                        super.onError(response);
                        fetchFromDouban(word, cb);
                    }
                });
    }

    private static void fetchFromDouban(final String word, final Callback cb) {
        OkGo.<String>get(DOUBAN_SUGGEST)
                .tag(TAG)
                .params("q", word)
                .headers("User-Agent", UA)
                .headers("Referer", "https://www.douban.com/")
                .execute(new AbsCallback<String>() {
                    @Override
                    public void onSuccess(Response<String> response) {
                        String body = response.body();
                        if (TextUtils.isEmpty(body)) {
                            PENDING.remove(word);
                            return;
                        }
                        try {
                            JsonArray arr = new Gson().fromJson(body, JsonArray.class);
                            if (arr == null || arr.size() == 0) {
                                PENDING.remove(word);
                                return;
                            }
                            JsonObject o = arr.get(0).getAsJsonObject();
                            String pic = optStr(o, "img");
                            String type = optStr(o, "type");
                            String year = optStr(o, "year");
                            if (TextUtils.isEmpty(pic)) {
                                PENDING.remove(word);
                                return;
                            }
                            String meta = "movie".equals(type) ? "电影" : "tv".equals(type) ? "剧集" : type;
                            if (!TextUtils.isEmpty(year)) {
                                meta = TextUtils.isEmpty(meta) ? year : meta + " · " + year;
                            }
                            done(word, pic, meta, cb);
                        } catch (Throwable t) {
                            PENDING.remove(word);
                            LOG.i("PosterLookup douban parse fail: " + word);
                        }
                    }

                    @Override
                    public String convertResponse(okhttp3.Response response) throws Throwable {
                        return response.body() == null ? "" : response.body().string();
                    }

                    @Override
                    public void onError(Response<String> response) {
                        super.onError(response);
                        PENDING.remove(word);
                        LOG.i("PosterLookup douban error: " + word);
                    }
                });
    }

    private static void done(String word, String pic, String meta, Callback cb) {
        PENDING.remove(word);
        String m = meta == null ? "" : meta;
        CACHE.put(word, new String[]{pic, m});
        cb.onResult(word, pic, m);
    }

    /**
     * 解析爱奇艺搜索结果，返回 {海报URL, 类型·年份}；没有可信匹配返回 null。
     * 打分：完全相等 100 / 前缀 80 / 包含 60，再减候选次序；0 分视为没匹配上。
     */
    private static String[] parseIqiyi(String body, String word) {
        if (TextUtils.isEmpty(body)) return null;
        try {
            JsonObject root = new Gson().fromJson(body, JsonObject.class);
            if (root == null || !root.has("data")) return null;
            JsonObject data = root.getAsJsonObject("data");
            if (data == null || !data.has("docinfos")) return null;
            JsonArray docs = data.getAsJsonArray("docinfos");
            String bestImg = null;
            String bestMeta = "";
            int bestScore = 0;
            for (int i = 0; i < docs.size() && i < MAX_CANDIDATES; i++) {
                JsonElement el = docs.get(i);
                if (el == null || !el.isJsonObject()) continue;
                JsonObject wrap = el.getAsJsonObject();
                if (!wrap.has("albumDocInfo") || !wrap.get("albumDocInfo").isJsonObject()) continue;
                JsonObject a = wrap.getAsJsonObject("albumDocInfo");
                String title = optStr(a, "albumTitle");
                String img = optStr(a, "albumVImage");
                if (TextUtils.isEmpty(img)) img = optStr(a, "albumImg");
                if (TextUtils.isEmpty(img) || TextUtils.isEmpty(title)) continue;
                int score;
                if (title.equals(word)) score = 100;
                else if (title.startsWith(word)) score = 80;
                else if (title.contains(word)) score = 60;
                else score = 0;
                score -= i;
                if (score > bestScore) {
                    bestScore = score;
                    bestImg = img;
                    String ch = optStr(a, "channel");
                    int comma = ch.indexOf(',');
                    String type = comma > 0 ? ch.substring(0, comma) : ch;
                    String date = optStr(a, "releaseDate");
                    String year = date.length() >= 4 ? date.substring(0, 4) : "";
                    bestMeta = TextUtils.isEmpty(type) ? year
                            : (TextUtils.isEmpty(year) ? type : type + " · " + year);
                }
            }
            if (TextUtils.isEmpty(bestImg)) return null;
            return new String[]{bestImg, bestMeta};
        } catch (Throwable t) {
            return null;
        }
    }

    private static String optStr(JsonObject o, String key) {
        try {
            return o != null && o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString().trim() : "";
        } catch (Throwable t) {
            return "";
        }
    }
}
