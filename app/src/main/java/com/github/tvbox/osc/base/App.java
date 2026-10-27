package com.github.tvbox.osc.base;

import android.app.Activity;
import androidx.multidex.MultiDexApplication;

import com.github.tvbox.osc.bean.VodInfo;
import com.github.tvbox.osc.callback.EmptyCallback;
import com.github.tvbox.osc.callback.LoadingCallback;
import com.github.tvbox.osc.data.AppDataManager;
import com.github.tvbox.osc.server.ControlManager;
import com.github.tvbox.osc.util.AppManager;
import com.github.tvbox.osc.util.BuiltinSourceList;
import com.github.tvbox.osc.util.EpgUtil;
import com.github.tvbox.osc.util.FileUtils;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.LOG;
import com.github.tvbox.osc.util.OkGoHelper;
import com.github.tvbox.osc.util.PlayerHelper;
import com.kingja.loadsir.core.LoadSir;
import com.orhanobut.hawk.Hawk;
import com.whl.quickjs.android.QuickJSLoader;
import com.github.catvod.crawler.JsLoader;

import me.jessyan.autosize.AutoSizeConfig;
import me.jessyan.autosize.unit.Subunits;

/**
 * @author pj567
 * @date :2020/12/17
 * @description:
 */
public class App extends MultiDexApplication {
    private static App instance;

    private static String dashData;

    /** 低配设备：核数<=4 或 总内存<=2GB。沉浸光感的流光动画在这类机器上自动关掉 */
    private Boolean lowEnd;

    public boolean isLowEndDevice() {
        if (lowEnd == null) {
            boolean low;
            try {
                android.app.ActivityManager am = (android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE);
                android.app.ActivityManager.MemoryInfo mi = new android.app.ActivityManager.MemoryInfo();
                if (am != null) am.getMemoryInfo(mi);
                int cores = Runtime.getRuntime().availableProcessors();
                long totalMb = mi.totalMem / (1024 * 1024);
                low = cores <= 4 || totalMb <= 2048;
            } catch (Throwable t) {
                low = false;
            }
            lowEnd = low;
        }
        return lowEnd;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        initParams();
        // OKGo
        OkGoHelper.init(); //台标获取
        EpgUtil.init();
        // 初始化Web服务器
        ControlManager.init(this);
        //初始化数据库
        AppDataManager.init();
        LoadSir.beginBuilder()
                .addCallback(new EmptyCallback())
                .addCallback(new LoadingCallback())
                .commit();
        AutoSizeConfig.getInstance().setCustomFragment(true).getUnitsManager()
                .setSupportDP(false)
                .setSupportSP(false)
                .setSupportSubunits(Subunits.MM);
        PlayerHelper.init();
        QuickJSLoader.init();
        // 递归删除 ijk / thunder 缓存目录是纯磁盘 IO，原来放在 onCreate 主线程上，
        // 缓存越大冷启动越慢（几百个 ts 分片就是几百次 stat+unlink）。挪到后台线程，
        // 播放开始前基本已经删完，不影响起播。
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    FileUtils.cleanPlayerCache();
                } catch (Throwable ignored) {
                }
            }
        }, "tvbox-cache-clean").start();
    }

    private void initParams() {
        // Hawk
        Hawk.init(this).build();
        // Hawk 是单文件全量序列化存储：每次 put 都要把整个 map 序列化成 JSON 再写盘，
        // 所以启动路径上不能无脑写。原来这两行无条件 put，等于每次冷启动白白多两次全量 IO，
        // 而且会把用户手动打开的调试开关覆盖回 false。
        if (!Hawk.contains(HawkConfig.DEBUG_OPEN)) {
            Hawk.put(HawkConfig.DEBUG_OPEN, false);
        }
        if (!Hawk.contains(HawkConfig.PLAYER_IS_LIVE)) {
            Hawk.put(HawkConfig.PLAYER_IS_LIVE, false);
        }
        if (!Hawk.contains(HawkConfig.PLAY_TYPE)) {
            Hawk.put(HawkConfig.PLAY_TYPE, 2);
        } else if (Hawk.get(HawkConfig.PLAY_TYPE, 2) == 0) {
            Hawk.put(HawkConfig.PLAY_TYPE, 2);
        }
        // 首次启动灌入预置接口源（只灌一次，用户删掉的不会回来）
        BuiltinSourceList.seedIfNeeded();
    }

    public static App getInstance() {
        return instance;
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        JsLoader.destroy();
    }


    private VodInfo vodInfo;
    public void setVodInfo(VodInfo vodinfo){
        this.vodInfo = vodinfo;
    }
    public VodInfo getVodInfo(){
        return this.vodInfo;
    }

    public Activity getCurrentActivity() {
        return AppManager.getInstance().currentActivity();
    }

    /**
     * 最近一次真正起播的解析后地址（goPlayUrl 落点）。
     * key = sourceKey|playFlag|playIndex，手机播放二维码用它取真实 m3u8，
     * 而不是详情页里那条原始集 URL（很多源是网页/解析页）。
     */
    private String resolvedPlayKey = null;
    private String resolvedPlayUrl = null;

    public void putResolvedPlay(String key, String url) {
        if (key == null || url == null || url.length() == 0) return;
        resolvedPlayKey = key;
        resolvedPlayUrl = url;
    }

    public String getResolvedPlay(String key) {
        if (key == null || resolvedPlayKey == null || !resolvedPlayKey.equals(key)) return null;
        return resolvedPlayUrl;
    }

    public void setDashData(String data) {
        dashData = data;
    }
    public String getDashData() {
        return dashData;
    }
}