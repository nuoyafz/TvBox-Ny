package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.R;

import org.jetbrains.annotations.NotNull;

/**
 * 首次启动的更新说明弹窗。
 * 只在版本号提升后的第一次进入首页时出现一次，点「开始使用」后写标记。
 */
public class ChangelogDialog extends BaseDialog {

    public interface OnConfirmListener {
        void onConfirm();
    }

    public ChangelogDialog(@NonNull @NotNull Context context, final OnConfirmListener listener) {
        super(context);
        setContentView(R.layout.dialog_changelog);
        findViewById(R.id.btnOk).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) {
                    listener.onConfirm();
                }
                dismiss();
            }
        });
    }
}
