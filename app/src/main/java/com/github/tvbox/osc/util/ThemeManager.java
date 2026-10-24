package com.github.tvbox.osc.util;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.ui.widget.AuroraBackgroundView;
import com.orhanobut.hawk.Hawk;

/**
 * 全局主题（沉浸光感配色）。
 *
 * 为什么不用 Android 的 ThemeOverlay：项目 minSdk 是 19，
 * 而 drawable / color state list 里引用 ?attr 需要 API 21 才稳定解析，
 * 4.4 盒子上会把强调色解析成透明，属于"老设备直接白屏"级别的坑。
 *
 * 所以这里走「代码着色」：极光层由自己的 View 画（颜色完全可控），
 * 顶栏按钮 / 分类 tab 在绑定数据时按主题生成 ColorStateList，
 * 切换主题后 recreate() 一次即可全局生效，且没有任何 API 版本风险。
 */
public class ThemeManager {

    /** 主题存储键 */
    public static final String KEY = "ny_theme";

    public static final int DUSK = 0;   // 暗房：荧光青柠
    public static final int PINE = 1;   // 暗房：冰川青
    public static final int LUCKY = 2;  // 暗房：夜幕紫
    public static final int MIST = 3;   // 暗房：月雾银

    public static final String[] NAMES = {"暗房青柠", "暗房冰川", "暗房夜紫", "暗房月雾"};
    private static final String[] SUBS = {
            "荧光青柠 · 石墨黑",
            "冰川青 · 石墨黑",
            "夜幕紫 · 石墨黑",
            "月雾银 · 石墨黑",
    };

    // 每套 8 个色：accent, accent2, skyTop, skyMid, ground, sun, bgA, bgB
    private static final int[][] P = {
            {0xFFB8C5B8, 0xFF8F9F96, 0x14B8C5B8, 0x0A8F9F96, 0x100F1412, 0x10EEF2EA, 0xFF0A0D0F, 0xFF080B0D},
            {0xFFA8BDC0, 0xFF879FA2, 0x14A8BDC0, 0x0A879FA2, 0x100E1415, 0x10EAF3F2, 0xFF0A0D0F, 0xFF080B0D},
            {0xFFB8B4C8, 0xFF9691A9, 0x14B8B4C8, 0x0A9691A9, 0x10121117, 0x10F0EDF5, 0xFF0A0D0F, 0xFF080B0D},
            {0xFFC4C9C4, 0xFFAAB0AA, 0x14C4C9C4, 0x0AAAB0AA, 0x10121513, 0x10F4F5F1, 0xFF0A0D0F, 0xFF080B0D},
    };

    public static int get() {
        int t = Hawk.get(KEY, DUSK);
        if (t < 0 || t >= P.length) t = DUSK;
        return t;
    }

    public static void set(int theme) {
        Hawk.put(KEY, theme);
    }

    public static String name() {
        return NAMES[get()];
    }

    public static String sub(int i) {
        return SUBS[i];
    }

    public static int accent() {
        return P[get()][0];
    }

    public static int accent2() {
        return P[get()][1];
    }

    public static int skyTop() {
        return P[get()][2];
    }

    public static int skyMid() {
        return P[get()][3];
    }

    public static int ground() {
        return P[get()][4];
    }

    public static int sun() {
        return P[get()][5];
    }

    public static int bgA() {
        return P[get()][6];
    }

    public static int bgB() {
        return P[get()][7];
    }

    /** 主题预览色（给设置页的小色块用） */
    public static int[] swatches(int theme) {
        if (theme < 0 || theme >= P.length) theme = DUSK;
        return new int[]{P[theme][0], P[theme][1], P[theme][4] | 0xFF000000};
    }

    /** 把当前主题刷到极光层 */
    public static void applyTo(AuroraBackgroundView v) {
        if (v == null) return;
        v.setPalette(skyTop(), skyMid(), ground(), sun(), bgA(), bgB());
    }

    /**
     * 强调色胶囊（顶栏「换源」这类按钮）。
     * 常态 = 18% 强调色底 + 55% 强调色边；焦点 = 实心强调色。
     */
    public static GradientDrawable chipBackground(Context ctx, boolean focused) {
        int accent = accent();
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(mm(ctx, 17));
        if (focused) {
            d.setColor(accent);
            d.setStroke(mm(ctx, 1), 0x80FFFFFF);
        } else {
            d.setColor((accent & 0x00FFFFFF) | 0x2E000000);
            d.setStroke(mm(ctx, 1), (accent & 0x00FFFFFF) | 0x73000000);
        }
        return d;
    }

    /** 分类 tab 的文字/图标：选中 = 强调色，未选中 = 近白 */
    public static ColorStateList tabTextColors() {
        return new ColorStateList(
                new int[][]{
                        new int[]{android.R.attr.state_selected},
                        new int[]{}
                },
                new int[]{accent(), 0xF2FDF6F2});
    }

    /** 顶栏快捷入口：焦点 = 强调色，平时 = 近白（暗底上唯一看得清的层级关系） */
    public static ColorStateList topLinkColors() {
        return new ColorStateList(
                new int[][]{
                        new int[]{android.R.attr.state_focused},
                        new int[]{}
                },
                new int[]{accent(), 0xF2FDF6F2});
    }

    /** 换源胶囊：焦点实心强调色 + 深色字，平时半透强调色 + 强调色字 */
    public static android.graphics.drawable.StateListDrawable chipSelector(Context ctx) {
        android.graphics.drawable.StateListDrawable sd = new android.graphics.drawable.StateListDrawable();
        sd.addState(new int[]{android.R.attr.state_focused}, chipBackground(ctx, true));
        sd.addState(new int[]{}, chipBackground(ctx, false));
        return sd;
    }

    public static ColorStateList chipTextColors() {
        return new ColorStateList(
                new int[][]{
                        new int[]{android.R.attr.state_focused},
                        new int[]{}
                },
                new int[]{0xFF141014, accent()});
    }

    /**
     * 顶栏「换源」玻璃胶囊 —— 沉浸光感材质。
     *
     * 两个版本都试过，说清为什么最后选"深色烟玻璃"：
     *
     * ① 白色玻璃（第一版）：本体 33%→13% 白。在**极光绿松**这种亮壁纸上直接糊掉 ——
     *    实测胶囊底 rgb(134,192,172) vs 顶栏底 rgb(121,184,163)，只差 13 个色阶，
     *    非焦点态的文字更惨：rgb(118,195,169) 对 rgb(130,190,170)，几乎完全看不见。
     *    白玻璃只适合暗壁纸，四套主题里有两套是亮的，不能这么干。
     *
     * ② 深色烟玻璃：本体用「bgB 压暗 55% 再掺 10% 强调色」，
     *    常态透明度约 20%~30%，清楚透出顶栏底色，
     *    同时因为只有 60% 不透明，背后的极光还能透上来 —— 这才是玻璃。
     *
     * 受光与焦点：顶部一道白/主题色衰减光交代厚度；焦点态本体加厚到约 45%、
     * 换成 2mm 强调色描边 + 强调色顶光 —— 整条顶栏里唯一"通电"的东西。
     * 本体是深色无彩，强调色只出现在描边和光晕上，四套主题都不会撞色。
     */
    public static Drawable glassChip(Context ctx, boolean focused, float cornerMm) {
        int a = accent();
        // 主题色压暗成"墨"，再掺一点点 accent，保证四套主题的玻璃颜色有细微差别
        int ink = blend(blend(bgB(), 0xFF000000, 0.55f), a, 0.10f);

        GradientDrawable body = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                focused
                        ? new int[]{withAlpha(ink, 0x78), withAlpha(ink, 0x58)}
                        : new int[]{withAlpha(ink, 0x48), withAlpha(ink, 0x30)});
        body.setCornerRadius(mm(ctx, cornerMm));
        if (focused) {
            body.setStroke(mm(ctx, 2), withAlpha(a, 0xD8));
        } else {
            body.setStroke(mm(ctx, 1), 0x3DFFFFFF);
        }

        // 顶部受光：平时是纯白顶光，焦点时掺进主题色（按钮"通电"了）
        GradientDrawable sheen = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                focused
                        ? new int[]{withAlpha(a, 0x40), withAlpha(a, 0x10), 0x00000000}
                        : new int[]{0x33FFFFFF, 0x0DFFFFFF, 0x00000000});
        sheen.setCornerRadius(mm(ctx, cornerMm));

        return new LayerDrawable(new Drawable[]{body, sheen});
    }

    public static android.graphics.drawable.StateListDrawable glassChipSelector(Context ctx) {
        android.graphics.drawable.StateListDrawable sd = new android.graphics.drawable.StateListDrawable();
        sd.addState(new int[]{android.R.attr.state_focused}, glassChip(ctx, true, 17));
        sd.addState(new int[]{}, glassChip(ctx, false, 17));
        return sd;
    }

    /** 玻璃胶囊上的字：焦点纯白，平时近白（深色烟玻璃上，白字是唯一可靠的对比） */
    public static ColorStateList glassChipTextColors() {
        return new ColorStateList(
                new int[][]{
                        new int[]{android.R.attr.state_focused},
                        new int[]{}
                },
                new int[]{0xFFFFFFFF, 0xF2FDF6F2});
    }

    /**
     * 搜索胶囊：全站最高频入口，必须比 7 个灰字链接亮一档。
     * 常态 = 65% 实心强调色 + 深色字（任何主题下都是顶栏最亮的一块）；
     * 焦点 = 近白实底 + 强调色字（反白，焦点位置一眼可见）。
     * 和"换源"胶囊的区别：换源是中性件（平时几乎透明），搜索是常亮件。
     */
    public static GradientDrawable searchChipBackground(Context ctx, boolean focused) {
        int accent = accent();
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(mm(ctx, 18));
        if (focused) {
            d.setColor(0xF2FDF6F2);
            d.setStroke(mm(ctx, 1.4f), accent);
        } else {
            // 未聚焦时保留玻璃透感，主题色只作为细边提示；聚焦后再明确提亮。
            d.setColor((accent & 0x00FFFFFF) | 0x4A000000);
            d.setStroke(mm(ctx, 1.2f), (accent & 0x00FFFFFF) | 0x88000000);
        }
        return d;
    }

    public static android.graphics.drawable.StateListDrawable searchChipSelector(Context ctx) {
        android.graphics.drawable.StateListDrawable sd = new android.graphics.drawable.StateListDrawable();
        sd.addState(new int[]{android.R.attr.state_focused}, searchChipBackground(ctx, true));
        sd.addState(new int[]{}, searchChipBackground(ctx, false));
        return sd;
    }

    /** 常态是亮底 → 字必须深；焦点反白 → 字用强调色 */
    public static ColorStateList searchTextColors() {
        return new ColorStateList(
                new int[][]{
                        new int[]{android.R.attr.state_focused},
                        new int[]{}
                },
                new int[]{accent(), 0xF2FDF6F2});
    }

    public static void tintTab(TextView title, ImageView filter, ImageView filterColor) {
        ColorStateList csl = tabTextColors();
        if (title != null) title.setTextColor(csl);
        tintIcon(filter, csl);
        tintIcon(filterColor, csl);
    }

    /**
     * ImageView.setImageTintList 是 API 21 才有的方法。
     * 这个项目 minSdk 是 19（Android 4.4 的盒子和老电视大量在用），
     * 直接在 4.4 上调用会抛 NoSuchMethodError —— 一进首页分类 tab 就崩。
     * 4.4 退化成单色 setColorFilter（丢选中态变色，但不会崩）。
     */
    private static void tintIcon(ImageView v, ColorStateList csl) {
        if (v == null) return;
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            v.setImageTintList(csl);
        } else {
            v.setColorFilter(csl.getDefaultColor());
        }
    }

    /** 给一片文本上主题色（设置页被选中行的标题等） */
    public static void tintText(TextView tv) {
        if (tv != null) tv.setTextColor(accent());
    }

    public static void tintBackground(View v, int cornerMm) {
        if (v == null) return;
        GradientDrawable d = new GradientDrawable();
        d.setColor(accent());
        d.setCornerRadius(mm(v.getContext(), cornerMm));
        v.setBackground(d);
    }

    private static int mm(Context ctx, float mm) {
        return (int) me.jessyan.autosize.utils.AutoSizeUtils.mm2px(ctx, mm);
    }

    /* ==================== 同源玻璃：弹窗/面板底座 ==================== */

    /** 布局里写死的日落霞橙（neon_accent / atv_accent 同为 #FF9A5A），供 applyAccentFix 纠偏 */
    public static final int LEGACY_ACCENT = 0xFFFF9A5A;
    public static final int LEGACY_ACCENT2 = 0xFFFF4E78;

    /**
     * 弹窗底座（替代 res/drawable/bg_vault_dialog.xml）。
     *
     * 为什么必须用代码画：xml 里的颜色是**编译期常量**，换主题不会变；
     * 而 minSdk 19 的 drawable 又不能用 ?attr（要 21+）。
     * 那张底座写死了 #DE1C0C18（日落大道的暗紫红），所以切到极光绿松时
     * 就变成"紫红底 + 青绿高亮"的撞色 —— 这正是用户截图里看到的问题。
     *
     * 现在：底色 = 当前主题 bgA→bgB 的垂直渐变，顶部掺 14% accent 当受光，
     * 再叠一道 accent 18%→0 的顶部光线，1mm 白边、10mm 圆角，
     * 与 dialog_select 里 cl_root 的尺寸完全对齐，换主题时整块弹窗跟着换。
     */
    public static Drawable vaultGlass(Context ctx) {
        int a = accent();
        int top = blend(bgA(), a, 0.14f);

        GradientDrawable body = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{withAlpha(top, 0xA2), withAlpha(bgA(), 0x8F), withAlpha(bgB(), 0x82)});
        body.setCornerRadius(mm(ctx, 10));
        body.setStroke(mm(ctx, 1), 0x55FFFFFF);

        GradientDrawable sheen = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x20FFFFFF, withAlpha(a, 0x08), withAlpha(a, 0x00)});
        sheen.setCornerRadius(mm(ctx, 10));

        return new LayerDrawable(new Drawable[]{body, sheen});
    }

    /** 面板/浮板用的浅一档玻璃（设置页左栏、搜索页输入区这类） */
    public static GradientDrawable panelGlass(Context ctx, float cornerMm) {
        int a = accent();
        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{withAlpha(blend(bgA(), a, 0.08f), 0xB8), withAlpha(bgA(), 0xA6)});
        d.setCornerRadius(mm(ctx, cornerMm));
        d.setStroke(mm(ctx, 1), 0x4DFFFFFF);
        return d;
    }

    /**
     * 把布局里写死的日落霞橙纠偏成当前主题色。
     *
     * 布局 xml 里 @color/neon_accent（=#FF9A5A）出现在几十处：设置页的 SETTINGS 标签、
     * 搜索页三栏的小标题、收藏/历史页、详情页分割线…资源是编译期常量，换主题纹丝不动，
     * 于是"首页绿松、设置页还是霞橙"。minSdk 19 不能走 ThemeOverlay，
     * 只能运行时把"恰好等于这个旧值"的文字色 / 纯色背景换成当前 accent。
     * 已经是 ColorStateList 的控件（顶栏、分类 tab）不受影响，它们自己会跟着主题变。
     */
    public static void applyAccentFix(View root) {
        if (root == null) return;
        try {
            if (root instanceof TextView) {
                int c = ((TextView) root).getCurrentTextColor();
                if (c == LEGACY_ACCENT) ((TextView) root).setTextColor(accent());
                else if (c == LEGACY_ACCENT2) ((TextView) root).setTextColor(accent2());
            }
            Drawable bg = root.getBackground();
            if (bg instanceof ColorDrawable) {
                int c = ((ColorDrawable) bg).getColor();
                if (c == LEGACY_ACCENT) root.setBackgroundColor(accent());
                else if (c == LEGACY_ACCENT2) root.setBackgroundColor(accent2());
            }
            if (root instanceof RecyclerView) {
                hooksOnce((RecyclerView) root);
            }
            if (root instanceof ViewGroup) {
                ViewGroup g = (ViewGroup) root;
                for (int i = 0, n = g.getChildCount(); i < n; i++) {
                    applyAccentFix(g.getChildAt(i));
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /** RecyclerView 的 item 是复用的，只扫一遍不够 —— 挂个附加监听，之后每个新 item 都补一次 */
    private static void hooksOnce(final RecyclerView rv) {
        if (rv.getTag(android.R.id.content) != null) return;
        rv.setTag(android.R.id.content, Boolean.TRUE);
        rv.addOnChildAttachStateChangeListener(new RecyclerView.OnChildAttachStateChangeListener() {
            @Override
            public void onChildViewAttachedToWindow(View v) {
                applyAccentFix(v);
            }

            @Override
            public void onChildViewDetachedFromWindow(View v) {
            }
        });
    }

    /** 焦点指示条（设置页左栏分组头）：只有焦点那一项亮 accent，平时透明 */
    public static android.graphics.drawable.StateListDrawable accentUnderline(Context ctx) {
        android.graphics.drawable.StateListDrawable sd =
                new android.graphics.drawable.StateListDrawable();
        GradientDrawable on = new GradientDrawable();
        on.setColor(accent());
        on.setCornerRadius(mm(ctx, 1.5f));
        sd.addState(new int[]{android.R.attr.state_focused}, on);
        sd.addState(new int[]{}, new ColorDrawable(0x00000000));
        return sd;
    }

    /** 主题色磨砂主按钮：半透强调色底、受光高光与醒目的遥控焦点描边。 */
    public static android.graphics.drawable.StateListDrawable solidAccentButton(Context ctx, float cornerMm) {
        android.graphics.drawable.StateListDrawable sd =
                new android.graphics.drawable.StateListDrawable();
        GradientDrawable focused = new GradientDrawable();
        focused.setCornerRadius(mm(ctx, cornerMm));
        focused.setColor(withAlpha(blend(accent(), 0xFFFFFFFF, 0.24f), 0xF0));
        focused.setStroke(mm(ctx, 2), 0xFFFFFFFF);
        GradientDrawable normal = new GradientDrawable();
        normal.setCornerRadius(mm(ctx, cornerMm));
        normal.setColor(withAlpha(accent(), 0xD8));
        normal.setStroke(mm(ctx, 1), 0x66FFFFFF);
        GradientDrawable sheenFocused = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x66FFFFFF, 0x1AFFFFFF, 0x00FFFFFF});
        sheenFocused.setCornerRadius(mm(ctx, cornerMm));
        GradientDrawable sheenNormal = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x50FFFFFF, 0x12FFFFFF, 0x00FFFFFF});
        sheenNormal.setCornerRadius(mm(ctx, cornerMm));
        sd.addState(new int[]{android.R.attr.state_focused},
                new LayerDrawable(new Drawable[]{focused, sheenFocused}));
        sd.addState(new int[]{}, new LayerDrawable(new Drawable[]{normal, sheenNormal}));
        return sd;
    }

    private static int blend(int base, int over, float ratio) {
        float ir = 1f - ratio;
        int r = (int) (android.graphics.Color.red(base) * ir + android.graphics.Color.red(over) * ratio);
        int g = (int) (android.graphics.Color.green(base) * ir + android.graphics.Color.green(over) * ratio);
        int b = (int) (android.graphics.Color.blue(base) * ir + android.graphics.Color.blue(over) * ratio);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    /** 只换 alpha 保留 RGB（BaseActivity 拼主题光带用） */
    public static int withA(int color, int alpha) {
        return withAlpha(color, alpha);
    }
}
