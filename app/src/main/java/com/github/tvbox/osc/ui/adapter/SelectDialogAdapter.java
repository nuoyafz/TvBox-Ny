package com.github.tvbox.osc.ui.adapter;

import android.annotation.SuppressLint;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.util.ThemeManager;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import me.jessyan.autosize.utils.AutoSizeUtils;

/**
 * 选择弹窗通用 Adapter（沉浸光感 · 玻璃卡版）
 *
 * 两版改版原因：
 *   1. 最初是 Netflix 那套高饱和品牌色磁贴（红/蓝/绿/黄）；
 *   2. 改过一版"日落色域八色循环"（砖橙/晚霞粉/暮紫…），但**颜色是按位置循环给的**，
 *      不表达任何信息，整墙看过去还是八种颜色互相打架。
 *
 * 现在：磁贴统一玻璃卡（半透明白 + 1mm 受光边），颜色只留给底部 2mm 状态线，
 * 唯一语义 = 健康度（快/一般/慢/失效）。选中靠主题强调色粗环 + 底色提亮，
 * 不再整块换色 —— 这样弹窗和首页/顶栏是同一套材质语言。
 */
public class SelectDialogAdapter<T> extends ListAdapter<T, SelectDialogAdapter.SelectViewHolder> {

    /* 健康度状态线配色：只表达"这个源能不能用、快不快"，不做品牌色 */
    private static final int HEALTH_FAST = 0xFF8DB99A; // 快
    private static final int HEALTH_MID = 0xFFD6B97F;  // 一般
    private static final int HEALTH_SLOW = 0xFFD49A78; // 慢
    private static final int HEALTH_DEAD = 0xFFD27F7F;  // 失效
    private static final int HEALTH_NONE = 0x33FFFFFF;  // 未测 / 无数据

    /* 玻璃卡三档白：常态、选中 */
    private static final int GLASS_TOP = 0x1FFFFFFF;
    private static final int GLASS_BOTTOM = 0x0AFFFFFF;
    private static final int GLASS_TOP_SEL = 0x38FFFFFF;
    private static final int GLASS_BOTTOM_SEL = 0x1FFFFFFF;

    class SelectViewHolder extends RecyclerView.ViewHolder {

        public SelectViewHolder(@NonNull @NotNull View itemView) {
            super(itemView);
        }
    }

    public interface SelectDialogInterface<T> {
        void click(T value, int pos);

        String getDisplay(T val);
    }


    public static DiffUtil.ItemCallback<String> stringDiff = new DiffUtil.ItemCallback<String>() {

        @Override
        public boolean areItemsTheSame(@NonNull @NotNull String oldItem, @NonNull @NotNull String newItem) {
            return oldItem.equals(newItem);
        }

        @Override
        public boolean areContentsTheSame(@NonNull @NotNull String oldItem, @NonNull @NotNull String newItem) {
            return oldItem.equals(newItem);
        }
    };


    private ArrayList<T> data = new ArrayList<>();

    private int select = 0;

    private SelectDialogInterface dialogInterface;

    public SelectDialogAdapter(SelectDialogInterface dialogInterface, DiffUtil.ItemCallback diffCallback) {
        super(diffCallback);
        this.dialogInterface = dialogInterface;
    }

    public void setData(List<T> newData, int defaultSelect) {
        data.clear();
        data.addAll(newData);
        select = defaultSelect;
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    /** 卡下小注里的健康度 → 状态线颜色。认不出就先当"未测"。 */
    private static int healthColor(String cap) {
        if (cap == null || cap.isEmpty()) return HEALTH_NONE;
        if (cap.contains("失效")) return HEALTH_DEAD;
        if (cap.startsWith("快")) return HEALTH_FAST;
        if (cap.startsWith("一般")) return HEALTH_MID;
        if (cap.startsWith("慢")) return HEALTH_SLOW;
        return HEALTH_NONE;
    }

    @Override
    public SelectDialogAdapter.SelectViewHolder onCreateViewHolder(@NonNull @NotNull ViewGroup parent, int viewType) {
        return new SelectDialogAdapter.SelectViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_dialog_select, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull @NotNull SelectDialogAdapter.SelectViewHolder holder, @SuppressLint("RecyclerView") int position) {
        T value = data.get(position);
        String display = dialogInterface.getDisplay(value);
        if (display == null) display = "";

        // 「源名｜健康度」拆成 磁贴名 + 卡下小注。
        // 必须用 lastIndexOf：源名本身可能带 ｜（例："豆豆｜片单"），
        // 用 indexOf 会把名字从中间劈开，磁贴显示成"豆豆｜片单 <..."。
        String nm = display;
        String cap = "";
        int sp = display.lastIndexOf('｜');
        if (sp < 0) sp = display.lastIndexOf('|');
        if (sp >= 0) {
            String tail = display.substring(sp + 1).trim();
            // 只有尾巴确实是健康度才拆，否则（比如名字里自带竖线）整串当名字
            if (tail.contains("·") || tail.contains("失效") || tail.startsWith("快")
                    || tail.startsWith("一般") || tail.startsWith("慢") || tail.equals("待检测")) {
                nm = display.substring(0, sp).trim();
                cap = tail;
            }
        }

        boolean sel = (position == select);
        int accent = ThemeManager.accent();

        View root = holder.itemView;
        FrameLayout face = root.findViewById(R.id.signPanel);
        TextView tvName = root.findViewById(R.id.tvName);
        TextView tvCap = root.findViewById(R.id.tvSignCap);
        TextView tvCurrent = root.findViewById(R.id.tvCurrentBadge);
        View vDot = root.findViewById(R.id.vHealthDot);

        tvName.setText(nm);
        boolean sourceCard = value instanceof SourceBean;
        tvName.setGravity(sourceCard ? Gravity.CENTER_VERTICAL | Gravity.START : Gravity.CENTER);
        int nameInset = sourceCard ? AutoSizeUtils.mm2px(root.getContext(), 14) : AutoSizeUtils.mm2px(root.getContext(), 8);
        tvName.setPadding(nameInset, 0, nameInset, 0);
        tvCap.setText(cap);
        tvCap.setVisibility(cap.length() == 0 ? View.INVISIBLE : View.VISIBLE);
        if (tvCurrent != null) {
            tvCurrent.setVisibility(sel ? View.VISIBLE : View.GONE);
            tvCurrent.setTextColor(accent);
            GradientDrawable badge = new GradientDrawable();
            badge.setColor((accent & 0x00FFFFFF) | 0x26000000);
            badge.setCornerRadius(AutoSizeUtils.mm2px(root.getContext(), 8));
            badge.setStroke(AutoSizeUtils.mm2px(root.getContext(), 1), (accent & 0x00FFFFFF) | 0x77000000);
            tvCurrent.setBackground(badge);
        }

        // 玻璃底：垂直微渐变（上亮下暗），跟弹窗底座 bg_vault_dialog 同一套材质
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                sel ? new int[]{GLASS_TOP_SEL, GLASS_BOTTOM_SEL}
                        : new int[]{GLASS_TOP, GLASS_BOTTOM});
        bg.setCornerRadius(AutoSizeUtils.mm2px(root.getContext(), 8));
        bg.setStroke(AutoSizeUtils.mm2px(root.getContext(), 1), sel ? 0x4DFFFFFF : 0x26FFFFFF);
        if (sel) bg.setColor((accent & 0x00FFFFFF) | 0x24000000);
        face.setBackground(bg);
        com.github.tvbox.osc.util.AtvFocusHelper.bind(root);

        // 唯一带颜色的地方：卡下那颗 7mm 健康度圆点
        if (vDot != null) {
            if (cap.length() == 0) {
                vDot.setVisibility(View.INVISIBLE);
            } else {
                vDot.setVisibility(View.VISIBLE);
                GradientDrawable dot = new GradientDrawable();
                dot.setShape(GradientDrawable.OVAL);
                dot.setColor(healthColor(cap));
                vDot.setBackground(dot);
            }
        }

        tvName.setTextColor(sel ? 0xFFFFFFFF : 0xF2FDF6F2);
        tvName.setShadowLayer(0, 0, 0, 0);
        tvCap.setTextColor(sel ? 0xCCFFFFFF : 0xA6FFFFFF);

        root.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (position == select)
                    return;
                int old = select;
                select = position;
                notifyItemChanged(old);
                notifyItemChanged(select);
                dialogInterface.click(value, position);
            }
        });
    }
}
