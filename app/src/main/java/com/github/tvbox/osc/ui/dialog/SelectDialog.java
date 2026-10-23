package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.ui.adapter.SelectDialogAdapter;
import com.owen.tvrecyclerview.widget.GridLayoutManager;
import com.owen.tvrecyclerview.widget.TvRecyclerView;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public class SelectDialog<T> extends BaseDialog {
    public SelectDialog(@NonNull @NotNull Context context) {
        super(context);
        setContentView(R.layout.dialog_select);
    }

    public SelectDialog(@NonNull @NotNull Context context, int resId) {
        super(context);
        setContentView(resId);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 同源玻璃：底座颜色由当前主题的 bgA/bgB + accent 现算，
        // 不再用写死日落紫红的 bg_vault_dialog.xml（那张图是四套主题撞色的根因）。
        View root = findViewById(R.id.cl_root);
        if (root != null) {
            try {
                root.setBackground(com.github.tvbox.osc.util.ThemeManager.vaultGlass(getContext()));
            } catch (Throwable ignored) {
            }
        }
    }

    public void setTip(String tip) {
        TextView t = findViewById(R.id.title);
        if (t != null) t.setText(tip);
    }

    /**
     * 数据源弹窗专用：打开「源检测 / 健康度排序」工具条。
     * 其他复用 dialog_select 的选择弹窗不调它，工具条一直是 GONE，布局完全不受影响。
     */
    public void showSourceTools(boolean show, View.OnClickListener onCheck, View.OnClickListener onSort) {
        View row = findViewById(R.id.sourceTools);
        if (row == null) return;
        row.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            View check = findViewById(R.id.btnSourceCheck);
            View sort = findViewById(R.id.btnSourceSort);
            if (check != null) check.setOnClickListener(onCheck);
            if (sort != null) sort.setOnClickListener(onSort);
        }
    }

    /** 右上角小字：共 N 个源 */
    public void setSourceCountText(CharSequence text) {
        TextView t = findViewById(R.id.tvSourceCount);
        if (t != null) t.setText(text);
    }

    /** 工具条右侧提示：体检进度 / 体检结果 */
    public void setToolHint(CharSequence text) {
        TextView t = findViewById(R.id.tvToolHint);
        if (t != null) t.setText(text);
    }

    /** 排序按钮的"已生效"状态：用当前主题强调色 */
    public void setSortActive(boolean active) {
        TextView t = findViewById(R.id.btnSourceSort);
        if (t == null) return;
        t.setText(active ? "已按健康度排序" : "健康度排序");
        t.setTextColor(active
                ? com.github.tvbox.osc.util.ThemeManager.accent()
                : t.getContext().getResources().getColor(R.color.top_link_text));
    }

    public void setAdapter(SelectDialogAdapter.SelectDialogInterface<T> sourceBeanSelectDialogInterface,
                           DiffUtil.ItemCallback<T> sourceBeanItemCallback,
                           List<T> data, int select) {
        final int selectIdx = select;
        SelectDialogAdapter<T> adapter = new SelectDialogAdapter<>(sourceBeanSelectDialogInterface, sourceBeanItemCallback);
        adapter.setData(data, select);
        TvRecyclerView tvRecyclerView = findViewById(R.id.list);
        tvRecyclerView.setAdapter(adapter);
        tvRecyclerView.setSelectedPosition(select);
        if (select<10){
            tvRecyclerView.setSelection(select);
        }
        tvRecyclerView.post(new Runnable() {
            @Override
            public void run() {
                if (selectIdx >= 10) {
                    tvRecyclerView.smoothScrollToPosition(selectIdx);
                    tvRecyclerView.setSelectionWithSmooth(selectIdx);
                }
            }
        });
    }

}
