package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.ui.tv.QRCodeGen;
import com.github.tvbox.osc.util.FastClickCheckUtil;

/**
 * 「手机接着看」二维码弹窗 —— 双模式。
 *
 * 中转（默认）：二维码指向电视的 /m?u= 播放页，由电视带正确 UA/Referer 去源站取流。
 *   优点：防盗链源也能播；缺点：手机要和电视同一 WiFi。
 *
 * 直链：二维码就是播放地址本身，手机直接拉源站。
 *   优点：不需要局域网；缺点：源站带防盗链（校验 UA/Referer）时手机浏览器会 403，打不开。
 *
 * 按 OK 在两种模式间切换，二维码和地址文本同步刷新。
 */
public class MobilePlayDialog extends BaseDialog {

    private final String relayUrl;
    private final String directUrl;
    private boolean directMode = false;

    private ImageView ivQRCode;
    private TextView tvMobileUrl;
    private TextView tvMobileMode;

    public MobilePlayDialog(@NonNull Context context, String relayUrl, String directUrl) {
        super(context);
        setCanceledOnTouchOutside(true);
        setCancelable(true);
        setContentView(R.layout.dialog_mobile_play);
        this.relayUrl = relayUrl;
        this.directUrl = directUrl;

        ivQRCode = findViewById(R.id.ivQRCode);
        tvMobileUrl = findViewById(R.id.tvMobileUrl);
        tvMobileMode = findViewById(R.id.tvMobileMode);

        render();

        findViewById(R.id.tvMobileMode).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                directMode = !directMode;
                render();
            }
        });

        findViewById(R.id.tvMobileClose).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                dismiss();
            }
        });
    }

    /** 按当前模式重画二维码、地址文本和切换按钮的提示语 */
    private void render() {
        String content = directMode ? directUrl : relayUrl;
        int size = getContext().getResources().getDimensionPixelSize(R.dimen.vs_300);
        ivQRCode.setImageBitmap(QRCodeGen.generateBitmap(content, size, size, 2));
        tvMobileUrl.setText(content);
        tvMobileMode.setText(directMode ? "模式：直链 · 按 OK 切中转" : "模式：中转 · 按 OK 切直链");
    }

    /** 当前二维码内容（留给上层做日志） */
    public String getPlayUrl() {
        return directMode ? directUrl : relayUrl;
    }
}
