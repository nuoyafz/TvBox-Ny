package com.github.tvbox.osc.ui.adapter;

import android.text.TextUtils;
import android.widget.ImageView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.util.ImgUtil;

import java.util.ArrayList;

/**
 * 首字母预览卡片适配器。
 *
 * 数据不是 Movie.Video —— 联想接口返回的词条没有 sourceKey / id，硬套 SearchAdapter
 * 会在 ApiConfig.get().getSource(null) 上直接空指针。
 *
 * 海报：联想接口本身不返图，由 SearchActivity 拿片名去豆瓣 subject_suggest 逐条补齐，
 * pic 为空时先用居中大字顶住，补到图后 notifyItemChanged 切成海报。
 */
public class PreviewWordAdapter extends BaseQuickAdapter<PreviewWordAdapter.Item, BaseViewHolder> {

    /** 预览条目：片名 + 海报 + 类型年份 */
    public static class Item {
        public final String word;
        public String pic = "";
        public String meta = "";

        public Item(String word) {
            this.word = word;
        }
    }

    /** 当前输入的首字母，显示在卡片左上角；只做视觉标识，不参与点击逻辑 */
    private String keyword = "";

    public PreviewWordAdapter() {
        super(R.layout.item_search_preview, new ArrayList<>());
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword == null ? "" : keyword.trim();
    }

    @Override
    protected void convert(BaseViewHolder helper, Item item) {
        String word = item == null || item.word == null ? "" : item.word;
        String pic = item == null ? "" : item.pic;
        helper.setText(R.id.tvPreviewIni, keyword);
        helper.setText(R.id.tvPreviewName, word);
        helper.setText(R.id.tvPreviewBig, word);
        helper.setText(R.id.tvPreviewMeta, item == null ? "" : item.meta);

        ImageView iv = helper.getView(R.id.ivPreviewPoster);
        boolean hasPoster = !TextUtils.isEmpty(pic);
        helper.setVisible(R.id.tvPreviewBig, !hasPoster);
        helper.setVisible(R.id.ivPreviewPoster, hasPoster);
        if (hasPoster) {
            int w = (int) android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_DIP, 120,
                    iv.getResources().getDisplayMetrics());
            int h = (int) (w * 1.5f);
            ImgUtil.load(pic, iv, 8, w, h, word);
        } else {
            // 复用视图时必须清掉上一张海报，否则会串图
            iv.setImageDrawable(null);
        }
    }
}
