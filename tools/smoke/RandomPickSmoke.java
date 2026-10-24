import com.github.tvbox.osc.util.RandomPickRules;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/** 随机看纯逻辑冒烟：分类归类 + 抽页范围。不依赖 Android，javac 直接跑。 */
public class RandomPickSmoke {
    private static int pass = 0;
    private static int fail = 0;

    public static void main(String[] args) {
        // 常见源分类名 → 期望归类
        expect("电影", true, false);
        expect("片库", true, false);
        expect("影院热播", true, false);
        expect("高清电影", true, false);
        expect("剧场版", true, false);
        expect("动作片", true, false);
        expect("movie", true, false);

        expect("电视剧", false, true);
        expect("连续剧", false, true);
        expect("短剧", false, true);
        expect("美剧", false, true);
        expect("动漫", false, true);
        expect("番剧", false, true);
        expect("综艺", false, true);
        expect("纪录片", false, false);
        expect("series", false, true);

        // 未识别分类：两类都不命中，应落进「全部」池（调用方兜底加入 allCategories）
        expect("最近更新", false, false);
        expect("我的收藏", false, false);

        // 抽页范围：必须落在 1..3，且三页都能抽到
        Random random = new Random(20261024L);
        Set<Integer> pages = new HashSet<>();
        boolean inRange = true;
        for (int i = 0; i < 3000; i++) {
            int page = RandomPickRules.randomPage(random);
            pages.add(page);
            if (page < 1 || page > 3) inRange = false;
        }
        check("randomPage 始终在 1..3", inRange);
        check("randomPage 三页全覆盖 (实际=" + pages + ")", pages.size() == 3);

        System.out.println("----");
        System.out.println("PASS=" + pass + " FAIL=" + fail);
        if (fail > 0) System.exit(1);
    }

    private static void expect(String name, boolean movie, boolean series) {
        int kind = RandomPickRules.classify(name);
        check("归类「" + name + "」= " + (kind == 1 ? "电影" : kind == 2 ? "剧集" : "未识别"),
                kind == (movie ? RandomPickRules.KIND_MOVIE : series ? RandomPickRules.KIND_SERIES : RandomPickRules.KIND_NONE));
    }

    private static void check(String what, boolean ok) {
        if (ok) {
            pass++;
            System.out.println("  ok   " + what);
        } else {
            fail++;
            System.out.println("  FAIL " + what);
        }
    }
}
