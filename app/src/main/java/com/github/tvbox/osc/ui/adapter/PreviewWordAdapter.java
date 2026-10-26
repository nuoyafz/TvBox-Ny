package com.github.tvbox.osc.ui.adapter;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;

import java.util.ArrayList;

/**
 * 首字母预览卡片适配器。
 *
 * 数据就是联想接口返回的一串片名，不是 Movie.Video —— 因为这些词条没有 sourceKey / id / 封面，
 * 硬套 SearchAdapter 会在 ApiConfig.get().getSource(null) 上直接空指针。
 */
public class PreviewWordAdapter extends BaseQuickAdapter<String, BaseViewHolder> {

    /** 当前输入的首字母，显示在卡片左上角；只做视觉标识，不参与点击逻辑 */
    private String keyword = "";

    public PreviewWordAdapter() {
        super(R.layout.item_search_preview, new ArrayList<>());
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword == null ? "" : keyword.trim();
    }

    @Override
    protected void convert(BaseViewHolder helper, String item) {
        helper.setText(R.id.tvPreviewName, item == null ? "" : item);
        helper.setText(R.id.tvPreviewIni, keyword);
    }
}
