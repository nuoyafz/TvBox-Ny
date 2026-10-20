package com.github.tvbox.osc.ui.adapter;

import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.util.AtvFocusHelper;
import com.github.tvbox.osc.util.ImgUtil;

import java.util.ArrayList;
import java.util.List;

import me.jessyan.autosize.utils.AutoSizeUtils;

public class GridAdapter extends BaseQuickAdapter<Movie.Video, BaseViewHolder> {
    /** 卡片圆角（mm，与 shape_neon_media 保持一致）：画报版统一 6mm 小圆角 */
    private static final float CARD_RADIUS_MM = 8f;

    private boolean mShowList;
    private int defaultWidth;
    public ImgUtil.Style style;

    public GridAdapter(boolean showList, ImgUtil.Style style) {
        super(showList ? R.layout.item_list : R.layout.item_grid, new ArrayList<>());
        this.mShowList = showList;
        if (style != null) {
            if (style.type.equals("list")) this.mShowList = true;
            this.defaultWidth = ImgUtil.getStyleDefaultWidth(style);
        }
        this.style = style;
    }

    /** 预取前面几张卡片的海报，滚入视野时直接命中缓存 */
    private static final int PRELOAD_AHEAD = 6;

    @Override
    protected void convert(BaseViewHolder helper, Movie.Video item) {
        if (this.mShowList) {
            helper.setText(R.id.tvNote, item.note);
            helper.setText(R.id.tvName, item.name);
            ImageView ivThumb = helper.getView(R.id.ivThumb);
            String pic = item.pic == null ? "" : item.pic.trim();
            if (!TextUtils.isEmpty(pic)) {
                if (ImgUtil.isBase64Image(pic)) {
                    ivThumb.setImageBitmap(ImgUtil.decodeBase64ToBitmap(pic));
                } else {
                    ImgUtil.load(pic, ivThumb, AutoSizeUtils.mm2px(mContext, CARD_RADIUS_MM), AutoSizeUtils.mm2px(mContext, 240), AutoSizeUtils.mm2px(mContext, 336), item.name);
                    preloadAhead(helper, 240, 336);
                }
            } else {
                ivThumb.setImageDrawable(ImgUtil.createTextDrawable(item.name));
            }
            return;
        }

        TextView tvYear = helper.getView(R.id.tvYear);
        if (item.year <= 0) {
            tvYear.setVisibility(View.GONE);
        } else {
            tvYear.setText(String.valueOf(item.year));
            tvYear.setVisibility(View.VISIBLE);
        }
        TextView tvLang = helper.getView(R.id.tvLang);
        tvLang.setVisibility(View.GONE);
        TextView tvArea = helper.getView(R.id.tvArea);
        tvArea.setVisibility(View.GONE);
        // 杂志编号：001…（列表级序号，翻页累计）
        int posNo = helper.getAdapterPosition();
        helper.setText(R.id.tvIndex, posNo >= 0 ? String.format("%03d", posNo + 1) : "");
        // 副行：类型 · 年份（列表无简介字段，用类型+年份顶替，缺失时回退 note）
        StringBuilder sub = new StringBuilder();
        if (!TextUtils.isEmpty(item.type)) {
            sub.append(item.type);
        }
        if (item.year > 0) {
            if (sub.length() > 0) sub.append(" · ");
            sub.append(item.year);
        }
        if (sub.length() == 0 && !TextUtils.isEmpty(item.note)) {
            sub.append(item.note);
        }
        if (sub.length() == 0) {
            helper.setVisible(R.id.tvNote, false);
        } else {
            helper.setVisible(R.id.tvNote, true);
            helper.setText(R.id.tvNote, sub.toString());
        }
        helper.setText(R.id.tvName, item.name);
        helper.setText(R.id.tvActor, item.actor);
        ImageView ivThumb = helper.getView(R.id.ivThumb);
        int newWidth = ImgUtil.defaultWidth;
        int newHeight = ImgUtil.defaultHeight;
        if (style != null) {
            newWidth = defaultWidth;
            newHeight = (int) (newWidth / style.ratio);
        }
        String pic = item.pic == null ? "" : item.pic.trim();
        if (!TextUtils.isEmpty(pic)) {
            if (ImgUtil.isBase64Image(pic)) {
                ivThumb.setImageBitmap(ImgUtil.decodeBase64ToBitmap(pic));
            } else {
                ImgUtil.load(pic, ivThumb, AutoSizeUtils.mm2px(mContext, CARD_RADIUS_MM), AutoSizeUtils.mm2px(mContext, newWidth), AutoSizeUtils.mm2px(mContext, newHeight), item.name);
                preloadAhead(helper, newWidth, newHeight);
            }
        } else {
            ivThumb.setImageDrawable(ImgUtil.createTextDrawable(item.name));
        }
        applyStyleToImage(ivThumb);
        AtvFocusHelper.bind(helper.itemView, mFocusListener);
    }

    private void preloadAhead(BaseViewHolder helper, int widthMm, int heightMm) {
        int pos = helper.getAdapterPosition();
        if (pos < 0) {
            return;
        }
        List<Movie.Video> data = getData();
        int radiusPx = AutoSizeUtils.mm2px(mContext, CARD_RADIUS_MM);
        int w = AutoSizeUtils.mm2px(mContext, widthMm);
        int h = AutoSizeUtils.mm2px(mContext, heightMm);
        for (int i = 1; i <= PRELOAD_AHEAD; i++) {
            int j = pos + i;
            if (j < 0 || j >= data.size()) {
                break;
            }
            String p = data.get(j).pic;
            if (!TextUtils.isEmpty(p) && !ImgUtil.isBase64Image(p)) {
                ImgUtil.preload(p, radiusPx, w, h);
            }
        }
    }

    private AtvFocusHelper.OnFocusListener mFocusListener;

    /** 焦点落点回调：宿主用它记录位置，返回时还原焦点 */
    public void setOnItemFocusListener(AtvFocusHelper.OnFocusListener listener) {
        this.mFocusListener = listener;
    }

    private void applyStyleToImage(final ImageView ivThumb) {
        if (style != null) {
            ViewGroup container = (ViewGroup) ivThumb.getParent();
            int width = defaultWidth;
            int height = (int) (width / style.ratio);
            ViewGroup.LayoutParams containerParams = container.getLayoutParams();
            containerParams.height = AutoSizeUtils.mm2px(mContext, height);
            containerParams.width = AutoSizeUtils.mm2px(mContext, width);
            container.setLayoutParams(containerParams);
        }
    }
}
