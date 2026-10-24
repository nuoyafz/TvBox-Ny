package com.github.tvbox.osc.base;

import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewTreeObserver;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.PermissionChecker;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.callback.EmptyCallback;
import com.github.tvbox.osc.callback.LoadingCallback;
import com.github.tvbox.osc.util.AppManager;
import com.github.tvbox.osc.util.SoundFx;
import com.kingja.loadsir.callback.Callback;
import com.kingja.loadsir.core.LoadService;
import com.kingja.loadsir.core.LoadSir;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import me.jessyan.autosize.AutoSizeConfig;
import me.jessyan.autosize.AutoSizeCompat;
import me.jessyan.autosize.internal.CustomAdapt;
import xyz.doikki.videoplayer.util.CutoutUtil;

/**
 * @author pj567
 * @date :2020/12/17
 * @description:
 */
public abstract class BaseActivity extends AppCompatActivity implements CustomAdapt {
    protected Context mContext;
    private LoadService mLoadService;

    private static float screenRatio = -100.0f;
    private final Runnable refreshAutoSizeRunnable = new Runnable() {
        @Override
        public void run() {
            if (shouldRefreshAutoSize()) {
                refreshAutoSize();
            }
        }
    };
    private final Runnable hideSysBarRunnable = new Runnable() {
        @Override
        public void run() {
            hideSysBar();
        }
    };

    /**
     * 全站底色跟随主题（设置页 / 搜索页 / 详情页 / 收藏 / 历史…都用这一张）。
     *
     * 三层，跟首页 AuroraBackgroundView 同族，只是不跑动画：
     *   底   bgA→bgB 垂直渐变（主题底色）
     *   上   accent 从顶部衰减（天空光）
     *   下   ground 从底部上升（地面光）
     *
     * 注意必须用 Window.setBackgroundDrawable，不能只改 DecorView：
     * 后面 updateWallpaper() 里 else 分支会再设一次资源背景，
     * 用 window 这条通道才能保证"主题底"是最终状态。
     */
    private android.graphics.drawable.Drawable themeBackdrop() {
        int bgA = com.github.tvbox.osc.util.ThemeManager.bgA();
        int bgB = com.github.tvbox.osc.util.ThemeManager.bgB();
        android.graphics.drawable.GradientDrawable base =
                new android.graphics.drawable.GradientDrawable(
                        android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                        new int[]{bgA, bgB});
        android.graphics.drawable.GradientDrawable sky =
                new android.graphics.drawable.GradientDrawable(
                        android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                        new int[]{com.github.tvbox.osc.util.ThemeManager.withA(
                                com.github.tvbox.osc.util.ThemeManager.accent(), 0x0E),
                                com.github.tvbox.osc.util.ThemeManager.withA(
                                        com.github.tvbox.osc.util.ThemeManager.accent(), 0x00)});
        android.graphics.drawable.GradientDrawable ground =
                new android.graphics.drawable.GradientDrawable(
                        android.graphics.drawable.GradientDrawable.Orientation.BOTTOM_TOP,
                        new int[]{com.github.tvbox.osc.util.ThemeManager.withA(
                                com.github.tvbox.osc.util.ThemeManager.ground(), 0x10),
                                com.github.tvbox.osc.util.ThemeManager.withA(
                                        com.github.tvbox.osc.util.ThemeManager.ground(), 0x00)});
        return new android.graphics.drawable.LayerDrawable(
                new android.graphics.drawable.Drawable[]{base, sky, ground});
    }

    private void applyThemeBackdrop() {
        try {
            getWindow().setBackgroundDrawable(themeBackdrop());
        } catch (Throwable ignored) {
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        try {
            if (screenRatio < 0) {
                DisplayMetrics dm = new DisplayMetrics();
                getWindowManager().getDefaultDisplay().getMetrics(dm);
                updateScreenRatio(dm);
            }
        } catch (Throwable th) {
            th.printStackTrace();
        }
        super.onCreate(savedInstanceState);
        setContentView(getLayoutResID());
        applyThemeBackdrop();
        mContext = this;
        initSystemUiListener();
        CutoutUtil.adaptCutoutAboveAndroidP(mContext, true);//设置刘海
        AppManager.getInstance().addActivity(this);
        init();
        initFocusSound();
        installThemeTintFix();
    }

    /**
     * 布局里写死的 @color/neon_accent（=#FF9A5A）是编译期常量，换主题纹丝不动，
     * 于是"首页绿松、设置页还是霞橙"。minSdk 19 用不了 ThemeOverlay，
     * 只能运行时扫一遍视图树把旧值换成当前 accent；RecyclerView 的复用 item
     * 由 ThemeManager 里挂的附加监听兜住，所以后面加载出来的也能跟上。
     */
    private void installThemeTintFix() {
        final View decor = getWindow().getDecorView();
        final Runnable fix = new Runnable() {
            @Override
            public void run() {
                com.github.tvbox.osc.util.ThemeManager.applyAccentFix(decor);
            }
        };
        // 两次就够：decor.post 跑在首次 layout 后（此时静态视图已 attach，但 RecyclerView
        // item 还没建，等于白扫一半）；700ms 那次兜住异步加载出来的 item。
        // 原来还有第三次 2000ms —— 页面早已稳定，且之后 attach 的 item 由 ThemeManager 里
        // hooksOnce 挂的 OnChildAttachStateChangeListener 兜住，纯属每个 Activity 多一次
        // 全视图树遍历，已删。
        decor.post(fix);
        decor.postDelayed(fix, 700);
    }

    /**
     * 全局焦点音效：挂在 decorView 的 ViewTreeObserver 上，
     * 所有 Activity / Fragment 里的焦点变化都会走到这里，无需逐个页面埋点。
     */
    private ViewTreeObserver.OnGlobalFocusChangeListener focusSoundListener;

    private void initFocusSound() {
        try {
            SoundFx.get().init(getApplicationContext());
            final View decor = getWindow().getDecorView();
            focusSoundListener = new ViewTreeObserver.OnGlobalFocusChangeListener() {
                @Override
                public void onGlobalFocusChanged(View oldFocus, View newFocus) {
                    // oldFocus == null 说明是页面首次建立焦点（进页面/换页），此时不响
                    if (oldFocus == null || newFocus == null) return;
                    if (oldFocus == newFocus) return;
                    SoundFx.get().focus();
                }
            };
            decor.getViewTreeObserver().addOnGlobalFocusChangeListener(focusSoundListener);
        } catch (Throwable th) {
            th.printStackTrace();
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            int code = event.getKeyCode();
            if (code == KeyEvent.KEYCODE_DPAD_CENTER
                    || code == KeyEvent.KEYCODE_ENTER
                    || code == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                SoundFx.get().click();
            } else if (code == KeyEvent.KEYCODE_BACK) {
                SoundFx.get().back();
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onDestroy() {
        try {
            if (focusSoundListener != null) {
                getWindow().getDecorView().getViewTreeObserver()
                        .removeOnGlobalFocusChangeListener(focusSoundListener);
                focusSoundListener = null;
            }
        } catch (Throwable th) {
            th.printStackTrace();
        }
        super.onDestroy();
        AppManager.getInstance().finishActivity(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSysBar();
        changeWallpaper(false);
        if (shouldRefreshAutoSize()) {
            refreshAutoSize();
            scheduleRefreshAutoSize();
        }
    }

    public void hideSysBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            int uiOptions = getWindow().getDecorView().getSystemUiVisibility();
            uiOptions |= View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
            uiOptions |= View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
            uiOptions |= View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
            uiOptions |= View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
            uiOptions |= View.SYSTEM_UI_FLAG_FULLSCREEN;
            uiOptions |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            getWindow().getDecorView().setSystemUiVisibility(uiOptions);
        }
    }

    private void initSystemUiListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            final View decorView = getWindow().getDecorView();
            decorView.setOnSystemUiVisibilityChangeListener(new View.OnSystemUiVisibilityChangeListener() {
                @Override
                public void onSystemUiVisibilityChange(int visibility) {
                    int hiddenBars = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN;
                    if ((visibility & hiddenBars) != hiddenBars) {
                        decorView.removeCallbacks(hideSysBarRunnable);
                        decorView.postDelayed(hideSysBarRunnable, 300);
                    }
                }
            });
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        getWindow().getDecorView().removeCallbacks(refreshAutoSizeRunnable);
        getWindow().getDecorView().removeCallbacks(hideSysBarRunnable);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSysBar();
            if (shouldRefreshAutoSize()) {
                scheduleRefreshAutoSize();
            }
        }
    }

    protected boolean shouldRefreshAutoSize() {
        return false;
    }

    private void scheduleRefreshAutoSize() {
        View decorView = getWindow().getDecorView();
        decorView.removeCallbacks(refreshAutoSizeRunnable);
        decorView.postDelayed(refreshAutoSizeRunnable, 300);
    }

    private void refreshAutoSize() {
        try {
            DisplayMetrics dm = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(dm);
            if (dm.widthPixels <= 0 || dm.heightPixels <= 0) {
                return;
            }
            updateScreenRatio(dm);
            AutoSizeConfig.getInstance()
                    .setScreenWidth(dm.widthPixels)
                    .setScreenHeight(dm.heightPixels);
            AutoSizeCompat.autoConvertDensityOfCustomAdapt(super.getResources(), this);
            getWindow().getDecorView().requestLayout();
        } catch (Throwable th) {
            th.printStackTrace();
        }
    }

    private void updateScreenRatio(DisplayMetrics dm) {
        int screenWidth = dm.widthPixels;
        int screenHeight = dm.heightPixels;
        int min = Math.min(screenWidth, screenHeight);
        if (min > 0) {
            screenRatio = (float) Math.max(screenWidth, screenHeight) / (float) min;
        }
    }

    @Override
    public Resources getResources() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            AutoSizeCompat.autoConvertDensityOfCustomAdapt(super.getResources(), this);
        }
        return super.getResources();
    }

    public boolean hasPermission(String permission) {
        boolean has = true;
        try {
            has = PermissionChecker.checkSelfPermission(this, permission) == PermissionChecker.PERMISSION_GRANTED;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return has;
    }

    protected abstract int getLayoutResID();

    protected abstract void init();

    protected void setLoadSir(View view) {
        if (mLoadService == null) {
            mLoadService = LoadSir.getDefault().register(view, new Callback.OnReloadListener() {
                @Override
                public void onReload(View v) {
                }
            });
        }
    }

    protected void showLoading() {
        if (mLoadService != null) {
            mLoadService.showCallback(LoadingCallback.class);
        }
    }

    protected boolean isLoading() {
        if (mLoadService != null && mLoadService.getCurrentCallback() != null) {
            return mLoadService.getCurrentCallback().equals(LoadingCallback.class);
        }
        return false;
    }

    protected void showEmpty() {
        if (null != mLoadService) {
            mLoadService.showCallback(EmptyCallback.class);
        }
    }

    protected void showSuccess() {
        if (null != mLoadService) {
            mLoadService.showSuccess();
        }
    }

    public void jumpActivity(Class<? extends BaseActivity> clazz) {
        Intent intent = new Intent(mContext, clazz);
        startActivity(intent);
    }

    public void jumpActivity(Class<? extends BaseActivity> clazz, Bundle bundle) {
        Intent intent = new Intent(mContext, clazz);
        intent.putExtras(bundle);
        startActivity(intent);
    }

    protected String getAssetText(String fileName) {
        StringBuilder stringBuilder = new StringBuilder();
        try {
            AssetManager assets = getAssets();
            BufferedReader bf = new BufferedReader(new InputStreamReader(assets.open(fileName)));
            String line;
            while ((line = bf.readLine()) != null) {
                stringBuilder.append(line);
            }
            return stringBuilder.toString();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "";
    }

    @Override
    public float getSizeInDp() {
        return isBaseOnWidth() ? 1280 : 720;
    }

    @Override
    public boolean isBaseOnWidth() {
        // 16:9(1.78) / 16:10(1.6) 这类接近设计稿比例的屏，按宽度适配，
        // 逻辑画布正好是 1280×720，电视上表现不变。
        //
        // 19.5:9(2.17)、21:9(2.33) 这类超宽屏（手机横屏、带鱼屏）如果仍按宽度算，
        // 逻辑画布高度会被压到 1080/1.83≈590mm，比设计高 720mm 少 130mm，
        // 首页片单会直接被挤出屏幕。所以超宽屏切成按高度适配，
        // 保证纵向布局与设计稿一致，多出来的横向空间让横滑容器自然铺开。
        return !(screenRatio >= 1.9f);
    }

    public void changeWallpaper(boolean force) {
        // Keep one image-free darkroom surface across activities; posters belong in content cards.
        getWindow().setBackgroundDrawable(themeBackdrop());
    }
}
