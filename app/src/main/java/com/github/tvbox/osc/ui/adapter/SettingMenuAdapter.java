package com.github.tvbox.osc.ui.adapter;

import android.view.View;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.util.ThemeManager;

import java.util.ArrayList;

/**
 * @author pj567
 * @date :2020/12/23
 * @description:
 */
public class SettingMenuAdapter extends BaseQuickAdapter<String, BaseViewHolder> {
    public SettingMenuAdapter() {
        super(R.layout.item_setting_menu, new ArrayList<>());
    }

    @Override
    protected void convert(BaseViewHolder helper, String item) {
        helper.setText(R.id.tvName, item);
        helper.addOnClickListener(R.id.tvName);
        // 焦点竖条原来是写死的霞橙（bg_nav_underline 里 @color/neon_accent），
        // 换主题后设置页会留一道橙线，和绿松/紫蓝主题打架 —— 改成按主题现算。
        View bar = helper.getView(R.id.vFocusBar);
        if (bar != null) {
            bar.setBackground(ThemeManager.accentUnderline(bar.getContext()));
        }
    }
}