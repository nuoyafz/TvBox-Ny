package com.github.tvbox.osc.util;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Build;

import com.github.tvbox.osc.R;
import com.orhanobut.hawk.Hawk;

/**
 * 焦点音效（tvOS 手感）。
 *
 * 设计原则：
 * - 极短（32~115ms）、极轻（音量压到刚能听见）
 * - 只在"确实是用户操作导致焦点变化"时响，进页面/切页引起的批量 focus 变化不响
 * - 全局单例，SoundPool 常驻，避免每次 new
 */
public class SoundFx {

    private static volatile SoundFx instance;

    private SoundPool pool;
    private int focusId = -1;
    private int clickId = -1;
    private int backId = -1;
    private boolean loaded;

    /** 节流：焦点音效最短间隔，防止 ViewPager 切页时连响成一串 */
    private static final long FOCUS_THROTTLE_MS = 55;
    private long lastFocusAt;

    private SoundFx() {
    }

    public static SoundFx get() {
        if (instance == null) {
            synchronized (SoundFx.class) {
                if (instance == null) instance = new SoundFx();
            }
        }
        return instance;
    }

    public void init(Context context) {
        if (pool != null) return;
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        pool = new SoundPool.Builder().setMaxStreams(4).setAudioAttributes(attrs).build();
        pool.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
            @Override
            public void onLoadComplete(SoundPool soundPool, int sampleId, int status) {
                if (sampleId == focusId || sampleId == clickId || sampleId == backId) loaded = true;
            }
        });
        focusId = pool.load(context, R.raw.sfx_focus, 1);
        clickId = pool.load(context, R.raw.sfx_click, 1);
        backId = pool.load(context, R.raw.sfx_back, 1);
    }

    private boolean enabled() {
        // 默认开；用户在设置里关掉后彻底静音
        return Hawk.get(HawkConfig.SOUND_FX, true);
    }

    /** 焦点移动 */
    public void focus() {
        long now = System.currentTimeMillis();
        if (now - lastFocusAt < FOCUS_THROTTLE_MS) return;
        lastFocusAt = now;
        play(focusId, 0.36f);
    }

    /** 确认 / 进入 */
    public void click() {
        play(clickId, 0.42f);
    }

    /** 返回 / 退出 */
    public void back() {
        play(backId, 0.36f);
    }

    private void play(int id, float volume) {
        if (!enabled() || !loaded || pool == null || id < 0) return;
        try {
            pool.play(id, volume, volume, 0, 0, 1.0f);
        } catch (Throwable th) {
            // 音效失败绝不能影响交互
        }
    }

    public void release() {
        if (pool != null) {
            pool.release();
            pool = null;
            loaded = false;
            instance = null;
        }
    }
}
