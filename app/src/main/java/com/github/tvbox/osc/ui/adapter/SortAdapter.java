package com.github.tvbox.osc.ui.adapter;

import android.widget.ImageView;
import android.widget.TextView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.MovieSort;
import com.github.tvbox.osc.util.ThemeManager;

import java.util.ArrayList;

/**
 * @author pj567
 * @date :2020/12/21
 * @description:
 */
public class SortAdapter extends BaseQuickAdapter<MovieSort.SortData, BaseViewHolder> {
    public SortAdapter() {
        super(R.layout.item_home_sort, new ArrayList<>());
    }

    @Override
    protected void convert(BaseViewHolder helper, MovieSort.SortData item) {
        helper.setText(R.id.tvTitle, item.name);
        // 主题：选中项用当前主题的强调色（以前写死在 nav_item_text 里的霞橙，
        // 换主题时分类 tab 不跟着变，一眼就露馅）
        ThemeManager.tintTab(
                helper.getView(R.id.tvTitle),
                (ImageView) helper.getView(R.id.tvFilter),
                (ImageView) helper.getView(R.id.tvFilterColor));

        // 上键出口：分类 tab → 顶栏「换源」。
        //
        // 为什么必须显式指定，不能靠几何搜索：
        // 分类 tab 第一项在 x 84..190，而顶栏行1 里位置最靠左的可聚焦元件是
        // NOYA（x 84..202），往下才是 源名(x229) 和 换源(x408)。
        // FocusFinder 做绝对方向搜索时要求横向 beam 重叠，能命中的只有 NOYA，
        // 用户按上键会落到品牌名上 —— 而他想去的是换源。这里直接把出口钉死。
        helper.itemView.setNextFocusUpId(R.id.tvSwitchSource);
    }
}