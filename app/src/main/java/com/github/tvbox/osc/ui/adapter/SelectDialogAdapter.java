package com.github.tvbox.osc.ui.adapter;

import android.annotation.SuppressLint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.github.tvbox.osc.R;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import me.jessyan.autosize.utils.AutoSizeUtils;

/**
 * Netflix 资料选择器风 · 选择弹窗通用 Adapter
 * 每个条目 = 一块品牌色大磁贴（色相按位置固定，靠色认源不用读字），
 * 名字大字居中，类型小注放磁贴下方；当前选中项 = 白色粗描边。
 */
public class SelectDialogAdapter<T> extends ListAdapter<T, SelectDialogAdapter.SelectViewHolder> {

    /** 品牌色板：按条目位置循环分配 */
    private static final int[] BRAND = {
            0xFFE50914, // 红
            0xFF1F6FEB, // 蓝
            0xFF2EA043, // 绿
            0xFF8E5CD9, // 紫
            0xFFE58A3C, // 橙
            0xFF17A398, // 青
            0xFFD9A521, // 黄
            0xFFD65DB1, // 粉
    };

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

    /** 品牌色加深，做磁贴的对角渐变底 */
    private static int darken(int color, float factor) {
        int r = (int) (((color >> 16) & 0xFF) * factor);
        int g = (int) (((color >> 8) & 0xFF) * factor);
        int b = (int) ((color & 0xFF) * factor);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int lighten(int color, float factor) {
        int r = Math.min(255, (int) (((color >> 16) & 0xFF) * factor));
        int g = Math.min(255, (int) (((color >> 8) & 0xFF) * factor));
        int b = Math.min(255, (int) ((color & 0xFF) * factor));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
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

        // "文采｜秒播" 竖线拆成 磁贴名 + 卡下小注
        String nm = display;
        String cap = "";
        int sp = display.indexOf('｜');
        if (sp < 0) sp = display.indexOf('|');
        if (sp >= 0) {
            nm = display.substring(0, sp).trim();
            cap = display.substring(sp + 1).trim();
        }

        int brand = BRAND[position % BRAND.length];
        boolean sel = (position == select);

        View root = holder.itemView;
        FrameLayout face = root.findViewById(R.id.signPanel);
        TextView tvName = root.findViewById(R.id.tvName);
        TextView tvCap = root.findViewById(R.id.tvSignCap);

        tvName.setText(nm);
        tvCap.setText(cap);
        tvCap.setVisibility(cap.length() == 0 ? View.INVISIBLE : View.VISIBLE);

        // 磁贴底：品牌色对角渐变（Netflix 头像色块的立体感）
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{lighten(brand, 1.12f), darken(brand, 0.45f)});
        bg.setCornerRadius(AutoSizeUtils.mm2px(root.getContext(), 6));
        if (sel) {
            // 选中 = 白色粗环（Netflix 的经典选中态）
            bg.setStroke(AutoSizeUtils.mm2px(root.getContext(), 3), 0xFFFFFFFF);
        } else {
            bg.setStroke(0, 0);
        }
        face.setBackground(bg);

        if (sel) {
            tvName.setTextColor(0xFFFFFFFF);
            tvName.setShadowLayer(AutoSizeUtils.mm2px(root.getContext(), 6), 0, 2, 0x66000000);
            tvCap.setTextColor(0xFFFFFFFF);
        } else {
            tvName.setTextColor(0xF2FFFFFF);
            tvName.setShadowLayer(0, 0, 0, 0);
            tvCap.setTextColor(root.getContext().getResources().getColor(R.color.neon_text_secondary));
        }

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
