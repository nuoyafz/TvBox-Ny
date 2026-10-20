package com.github.tvbox.osc.ui.activity;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.receiver.PushReceiver;
import com.github.tvbox.osc.server.ControlManager;
import com.github.tvbox.osc.ui.tv.QRCodeGen;

import me.jessyan.autosize.utils.AutoSizeUtils;

public class PushActivity extends BaseActivity {
    private ImageView ivQRCode;
    private TextView tvAddress;

    /** 扫码打开哪个页面：push（推送）/ remote（遥控）/ api（接口），默认 push */
    private String page = "push";

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_push;
    }

    @Override
    protected void init() {
        // 先读 Intent 再初始化视图：refreshQRCode() 依赖 page，
        // 反过来会导致遥控入口生成的还是 push.html 的二维码
        initData();
        initView();
    }

    private void initView() {
        ivQRCode = findViewById(R.id.ivQRCode);
        tvAddress = findViewById(R.id.tvAddress);
        refreshQRCode();
        // 「推送剪贴板」只对推送页有意义，遥控页里这个按钮是废的，直接藏掉
        boolean isPushPage = "push".equals(page);
        findViewById(R.id.pushLocal).setVisibility(isPushPage ? View.VISIBLE : View.GONE);
        findViewById(R.id.pushLocal).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    ClipboardManager manager = (ClipboardManager) PushActivity.this.getSystemService(Context.CLIPBOARD_SERVICE);
                    if (manager != null) {
                        if (manager.hasPrimaryClip() && manager.getPrimaryClip() != null && manager.getPrimaryClip().getItemCount() > 0) {
                            ClipData.Item addedText = manager.getPrimaryClip().getItemAt(0);
                            CharSequence text = addedText.coerceToText(PushActivity.this);
                            if (text != null) PushReceiver.send(PushActivity.this, text.toString().trim());
                        }
                    }
                } catch (Throwable th) {

                }
            }
        });
    }

    private void refreshQRCode() {
        String address = ControlManager.get().getAddress(false);
        String tip;
        if ("remote".equals(page)) {
            tip = "手机扫码后变成遥控器（摇杆 + 语音搜片），和电视同一个 WiFi 即可\n%s";
        } else if ("api".equals(page)) {
            tip = "手机扫码后可以改接口、传文件\n%s";
        } else {
            tip = "手机/电脑扫描上方二维码或者直接浏览器访问地址\n%s";
        }
        tvAddress.setText(String.format(tip, address));
        ivQRCode.setImageBitmap(QRCodeGen.generateBitmap(address + page + ".html",
                AutoSizeUtils.mm2px(this, 300), AutoSizeUtils.mm2px(this, 300), 4));
    }

    private void initData() {
        Intent intent = getIntent();
        if (intent != null && intent.getExtras() != null) {
            String p = intent.getExtras().getString("page");
            if (p != null && !p.isEmpty()) page = p;
        }
    }
}