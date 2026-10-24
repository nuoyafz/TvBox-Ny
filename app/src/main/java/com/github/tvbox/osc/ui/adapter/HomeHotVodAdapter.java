package com.github.tvbox.osc.ui.adapter;

import android.text.TextUtils;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.util.AtvFocusHelper;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.ImgUtil;
import com.orhanobut.hawk.Hawk;

import java.util.ArrayList;

import me.jessyan.autosize.utils.AutoSizeUtils;

public class HomeHotVodAdapter extends BaseQuickAdapter<Movie.Video, BaseViewHolder> {
    private String tvRateValue;
    private boolean immersiveMode;

    public HomeHotVodAdapter(ImgUtil.Style style, String tvRate) {
        super(R.layout.item_user_hot_vod, new ArrayList<>());
        this.tvRateValue = tvRate;
    }

    public void setImmersiveMode(boolean enabled) {
        if (immersiveMode == enabled) return;
        immersiveMode = enabled;
        notifyDataSetChanged();
    }

    @Override
    protected void convert(BaseViewHolder helper, Movie.Video item) {
        FrameLayout tvDel = helper.getView(R.id.delFrameLayout);
        tvDel.setVisibility(HawkConfig.hotVodDelete ? View.VISIBLE : View.GONE);

        FrameLayout card = helper.getView(R.id.mItemFrame);
        FrameLayout.LayoutParams cardParams = (FrameLayout.LayoutParams) card.getLayoutParams();
        int cardWidth = AutoSizeUtils.mm2px(mContext, immersiveMode ? 224 : 188);
        int cardHeight = AutoSizeUtils.mm2px(mContext, immersiveMode ? 132 : 100);
        if (cardParams.width != cardWidth || cardParams.height != cardHeight) {
            cardParams.width = cardWidth;
            cardParams.height = cardHeight;
            card.setLayoutParams(cardParams);
        }
        View root = helper.itemView;
        android.view.ViewGroup.LayoutParams rootParams = root.getLayoutParams();
        if (rootParams != null && rootParams.height != cardHeight) {
            rootParams.height = cardHeight;
            root.setLayoutParams(rootParams);
        }

        TextView tvRate = helper.getView(R.id.tvRate);
        if (Hawk.get(HawkConfig.HOME_REC, HawkConfig.DEFAULT_HOME_REC) == 2) {
            SourceBean bean = ApiConfig.get().getSource(item.sourceKey);
            tvRateValue = bean != null ? bean.getName() : "";
        }
        tvRate.setText(tvRateValue);

        TextView tvNote = helper.getView(R.id.tvNote);
        if (item.note == null || item.note.isEmpty()) {
            tvNote.setVisibility(View.GONE);
        } else {
            tvNote.setText(item.note);
            tvNote.setVisibility(View.VISIBLE);
        }
        helper.setText(R.id.tvName, item.name);
        ImageView ivThumb = helper.getView(R.id.ivThumb);

        // 首页频道条使用宽银幕卡片；尺寸需与 item_user_hot_vod.xml 同步。
        String pic = item.pic == null ? "" : item.pic.trim();
        if (!TextUtils.isEmpty(pic)) {
            if (ImgUtil.isBase64Image(pic)) {
                ivThumb.setImageBitmap(ImgUtil.decodeBase64ToBitmap(pic));
            } else {
                ImgUtil.load(pic, ivThumb, AutoSizeUtils.mm2px(mContext, 8), AutoSizeUtils.mm2px(mContext, 188), AutoSizeUtils.mm2px(mContext, 100), item.name);
            }
        } else {
            ivThumb.setImageDrawable(ImgUtil.createTextDrawable(item.name));
        }
        AtvFocusHelper.bind(helper.itemView);
    }
}
