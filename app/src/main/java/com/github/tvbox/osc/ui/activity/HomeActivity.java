package com.github.tvbox.osc.ui.activity;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.IntEvaluator;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.BounceInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.ViewPager;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.base.BaseLazyFragment;
import com.github.tvbox.osc.bean.AbsSortXml;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.bean.MovieSort;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.server.ControlManager;
import com.github.tvbox.osc.ui.adapter.HomePageAdapter;
import com.github.tvbox.osc.ui.adapter.SelectDialogAdapter;
import com.github.tvbox.osc.ui.adapter.SortAdapter;
import com.github.tvbox.osc.ui.dialog.SelectDialog;
import com.github.tvbox.osc.ui.dialog.TipDialog;
import com.github.tvbox.osc.ui.fragment.GridFragment;
import com.github.tvbox.osc.ui.fragment.UserFragment;
import com.github.tvbox.osc.ui.tv.widget.DefaultTransformer;
import com.github.tvbox.osc.ui.tv.widget.FixedSpeedScroller;
import com.github.tvbox.osc.ui.tv.widget.NoScrollViewPager;
import com.github.tvbox.osc.ui.tv.widget.ViewObj;
import com.github.tvbox.osc.util.AppManager;
import com.github.tvbox.osc.util.DefaultConfig;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.util.FileUtils;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.LOG;
import com.github.tvbox.osc.util.MD5;
import com.github.tvbox.osc.util.SourceHealth;
import com.github.tvbox.osc.util.TasteProfile;
import com.github.tvbox.osc.viewmodel.SourceViewModel;
import com.orhanobut.hawk.Hawk;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7GridLayoutManager;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import me.jessyan.autosize.utils.AutoSizeUtils;

public class HomeActivity extends BaseActivity implements View.OnClickListener {
    private LinearLayout topLayout;
    private LinearLayout contentLayout;
    private TextView tvDate;
    private TextView tvName;
    /** 顶栏第二个文字位：当前首页数据源名（品牌位固定是 NOYA，不再被源名覆盖） */
    private TextView tvSource;
    private TvRecyclerView mGridView;
    private NoScrollViewPager mViewPager;
    private SourceViewModel sourceViewModel;
    private SortAdapter sortAdapter;
    private HomePageAdapter pageAdapter;
    private View currentView;
    private final List<BaseLazyFragment> fragments = new ArrayList<>();
    private boolean isDownOrUp = false;
    private boolean sortChange = false;
    private int currentSelected = 0;
    private int sortFocused = 0;
    public View sortFocusView = null;
    private String loadingSourceKey;
    private String previousHomeName;
    private SourceBean previousHomeSource;
    private boolean homeSortLoading = false;
    private boolean refreshHomeRec = false;
    private final Handler mHandler = new Handler();
    private long mExitTime = 0;
    private boolean eventBusRegistered = false;
    /**
     * 顶栏总高度（mm）——必须与 activity_home.xml 里 topLayout 的 layout_height 保持一致。
     * 两行结构：行1 30 + 间距 4 + 行2 46 + 内边距 10 = 90。
     * 内边距已经进了高度里，所以不再有 marginTop（旧版是 14mm 外边距 + 78mm 高）。
     */
    private static final float TOPBAR_HEIGHT_MM = 90f;
    private static final float TOPBAR_MARGIN_MM = 0f;
    private final Runnable mRunnable = new Runnable() {
        @SuppressLint("SetTextI18n")
        @Override
        public void run() {
            Date date = new Date();
            SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy/MM/dd  E  HH:mm", Locale.CHINA);
            tvDate.setText(timeFormat.format(date));
            mHandler.postDelayed(this, 1000);
        }
    };
    private final Runnable refreshTopInfoTextSizeRunnable = new Runnable() {
        @Override
        public void run() {
            refreshTopInfoTextSize();
        }
    };
    private final Runnable refreshTopLayoutRunnable = new Runnable() {
        @Override
        public void run() {
            if (topLayout == null || isActivityUnavailable()) return;
            // 顶栏现在是叠加层：位置和高度都由布局固定，这里绝不能再改
            // height / marginTop —— 每改一次就是一次 relayout，滚动时肉眼可见地卡。
            hideSysBar();
            topLayout.setAlpha(1.0f);
            refreshTopInfoTextSize();
        }
    };

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_home;
    }

    @Override
    protected boolean shouldRefreshAutoSize() {
        return true;
    }

    boolean useCacheConfig = false;

    /**
     * 配置缓存可信时长。缓存文件在这段时间内直接用，不再走网络。
     * 超过就重新拉一次（保证源站换了地址能跟上），但每次启动都拉一遍是没必要的。
     */
    private static final long API_CACHE_TRUST_MS = 6L * 60 * 60 * 1000;

    /**
     * 冷启动是否优先吃磁盘缓存。
     *
     * 原来无论本地有没有缓存都调 loadConfig(false)/loadJar(false)，这两个方法只有在
     * 网络失败时才去翻缓存，于是每次开 App 都要等一轮 config 拉取 + jar 下载才出画面，
     * 就是那个一直转圈的「正在加载」。
     *
     * 注意不能直接复用 useCacheConfig：它同时还决定"要不要开机自动跳直播"，
     * 这里单独开一个标记，只影响配置/jar 的取用方式。
     */
    private boolean preferCacheConfig = false;

    @Override
    protected void init() {
        EventBus.getDefault().register(this);
        eventBusRegistered = true;
        ControlManager.get().startServer();
        initView();
        initViewModel();
        useCacheConfig = false;
        Intent intent = getIntent();
        if (intent != null && intent.getExtras() != null) {
            Bundle bundle = intent.getExtras();
            useCacheConfig = bundle.getBoolean("useCache", false);
        }
        preferCacheConfig = useCacheConfig || hasFreshApiCache();
        LOG.i("echo-home start preferCache=" + preferCacheConfig);
        initData();
        // 首次启动的更新说明弹窗已取消（用户要求：不要一进来就弹通知）
    }

    /** 当前接口的配置文件是否已存在且未过期 */
    private boolean hasFreshApiCache() {
        try {
            String apiUrl = Hawk.get(HawkConfig.API_URL, "");
            if (apiUrl == null || apiUrl.isEmpty()) {
                return false; // 首次安装，尚未确定接口
            }
            File cache = new File(getFilesDir().getAbsolutePath() + "/" + MD5.encode(apiUrl));
            if (!cache.exists() || cache.length() <= 0) {
                return false;
            }
            return System.currentTimeMillis() - cache.lastModified() < API_CACHE_TRUST_MS;
        } catch (Throwable th) {
            return false;
        }
    }

    private void initView() {
        this.topLayout = findViewById(R.id.topLayout);
        this.tvDate = findViewById(R.id.tvDate);
        this.tvName = findViewById(R.id.tvName);
        this.tvSource = findViewById(R.id.tvSource);
        this.contentLayout = findViewById(R.id.contentLayout);
        this.mGridView = findViewById(R.id.mGridView);
        this.mViewPager = findViewById(R.id.mViewPager);
        this.sortAdapter = new SortAdapter();
        // 画报式：分类回到顶栏横向排列，文字 + 焦点下划线，不再有侧栏和胶囊
        this.mGridView.setLayoutManager(new V7LinearLayoutManager(this.mContext, 0, false));
        this.mGridView.setSpacingWithMargins(0, 0);
        this.mGridView.setAdapter(this.sortAdapter);
        // 顶栏行2 的六个快捷入口（原来压在首页最底部一行，被电视 overscan 裁掉过）
        findViewById(R.id.tvLive).setOnClickListener(this);
        findViewById(R.id.tvHistory).setOnClickListener(this);
        findViewById(R.id.tvFavorite).setOnClickListener(this);
        findViewById(R.id.tvPush).setOnClickListener(this);
        findViewById(R.id.tvRemote).setOnClickListener(this);
        findViewById(R.id.tvSearch).setOnClickListener(this);
        findViewById(R.id.tvSetting).setOnClickListener(this);
        // 顶栏源名可点：确认键 = 切换首页数据源（原来只有 MENU 短按能做，太隐蔽）
        this.tvSource.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                showSiteSwitch();
            }
        });
        // 「⟳ 换源」按钮：才是给用户看的入口，tvSource 现在只负责显示源名
        View switchSource = findViewById(R.id.tvSwitchSource);
        if (switchSource != null) {
            switchSource.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    FastClickCheckUtil.check(v);
                    showSiteSwitch();
                }
            });
        }
        applyTheme();
        sortAdapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onChanged() {
                mGridView.post(() -> {
                    View firstChild = Objects.requireNonNull(mGridView.getLayoutManager()).findViewByPosition(0);
                    if (firstChild != null) {
                        mGridView.setSelectedPosition(0);
                        firstChild.requestFocus();
                    }
                });
            }
        });
        this.mGridView.setOnItemListener(new TvRecyclerView.OnItemListener() {
            public void onItemPreSelected(TvRecyclerView tvRecyclerView, View view, int position) {
                if (view != null && !HomeActivity.this.isDownOrUp) {
                    mHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            // 焦点态交给 bg_neon_rail_item + neon_rail_text 两个 selector，
                            // 这里不再做缩放和硬设颜色（原来的 1.1 倍 Bounce 在竖排 rail 里会左右溢出）
                            if (sortFocused != p) {
                                view.findViewById(R.id.tvFilter).setVisibility(View.GONE);
                                view.findViewById(R.id.tvFilterColor).setVisibility(View.GONE);
                            }
                        }

                        public final int p = position;
                    }, 10);
                }
            }

            public void onItemSelected(TvRecyclerView tvRecyclerView, View view, int position) {
                if (view != null) {
                    HomeActivity.this.currentView = view;
                    HomeActivity.this.isDownOrUp = false;
                    HomeActivity.this.sortChange = true;
                    MovieSort.SortData sortData = sortAdapter.getItem(position);
                    if (!sortData.filters.isEmpty()) {
                        showFilterIcon(sortData.filterSelectCount());
                    }
                    HomeActivity.this.sortFocusView = view;
                    HomeActivity.this.sortFocused = position;
                    mHandler.removeCallbacks(mDataRunnable);
                    mHandler.postDelayed(mDataRunnable, 200);
                }
            }

            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {
                if (itemView != null && currentSelected == position) {
                    BaseLazyFragment baseLazyFragment = fragments.get(currentSelected);
                    if ((baseLazyFragment instanceof GridFragment) && !sortAdapter.getItem(position).filters.isEmpty()) {// 弹出筛选
                        ((GridFragment) baseLazyFragment).showFilter();
                    } else if (baseLazyFragment instanceof UserFragment) {
                        showSiteSwitch();
                    }
                }
            }
        });

        this.mGridView.setOnInBorderKeyEventListener(new TvRecyclerView.OnInBorderKeyEventListener() {
            public boolean onInBorderKeyEvent(int direction, View view) {
                if (direction == View.FOCUS_UP) {
                    // 【这是"遥控器上不去顶栏"的真正元凶】
                    // 原版这里做的是：静默刷新当前分类，然后 return true 把这一次方向键
                    // 整个吃掉。后果是纯遥控器用户在主页按上键**一点反应都没有**，
                    // 焦点死死钉在分类 tab 第一项上（实测连按两次上键，
                    // focused 的 bounds 仍然是 [84,66][190,135]，原地不动）。
                    //
                    // 现在把上键改成正经的"出口"：直接把焦点送到顶栏那颗「换源」胶囊。
                    // 顶栏可见时才接管；收起状态（GONE）下返回 false，让按键照常走，
                    // 不会出现"焦点被扔进看不见的顶栏"。
                    if (topLayout != null && topLayout.getVisibility() == View.VISIBLE) {
                        View chip = findViewById(R.id.tvSwitchSource);
                        if (chip != null && chip.requestFocus()) {
                            return true;
                        }
                    }
                    return false;
                }
                if (direction != View.FOCUS_DOWN) {
                    return false;
                }
                BaseLazyFragment baseLazyFragment = fragments.get(sortFocused);
                if (!(baseLazyFragment instanceof GridFragment)) {
                    return false;
                }
                return !((GridFragment) baseLazyFragment).isLoad();
            }
        });
        tvName.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                if(dataInitOk && jarInitOk){
                    String jar=ApiConfig.get().getHomeSourceBean().getJar();
                    String jarUrl=!jar.isEmpty()?jar:ApiConfig.get().getSpider();
                    String jarSource = jarUrl.split(";md5;")[0];
                    File cspCacheDir = new File(FileUtils.getFilePath() + "/csp/" + MD5.string2MD5(jarSource) + ".jar");
                    File jarCacheDir = new File(FileUtils.getCachePath() + "/jar/" + MD5.string2MD5(jarSource) + ".jar");
                    File jarFullCacheDir = new File(FileUtils.getCachePath() + "/jar/" + MD5.string2MD5(jarUrl) + ".jar");
                    Toast.makeText(mContext, "缓存已清除", Toast.LENGTH_LONG).show();
                    new Thread(() -> {
                        try {
                            FileUtils.deleteFile(cspCacheDir);
                            FileUtils.deleteFile(jarCacheDir);
                            FileUtils.deleteFile(jarFullCacheDir);
                            FileUtils.clearSpiderCacheFiles();
                            ApiConfig.get().clearSpiderCache();
                            refreshHome();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }).start();

                }else {
                    jumpActivity(SettingActivity.class);
                }
            }
        });
        tvName.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                jumpActivity(SettingActivity.class);
                return true;
            }
        });
        setLoadSir(this.contentLayout);
        //mHandler.postDelayed(mFindFocus, 500);
    }

    /**
     * 把当前主题刷到首页会看到的每一处：极光层、换源胶囊、七个快捷入口、源名。
     * 其它页面在切主题时会 recreate，跟着一起变。
     */
    private void applyTheme() {
        com.github.tvbox.osc.ui.widget.AuroraBackgroundView aurora =
                findViewById(R.id.auroraBg);
        com.github.tvbox.osc.util.ThemeManager.applyTo(aurora);

        View chip = findViewById(R.id.tvSwitchSource);
        if (chip != null) {
            // 半透明玻璃胶囊：本体无彩色（透出后面极光），强调色只做描边 / 焦点光晕
            chip.setBackground(com.github.tvbox.osc.util.ThemeManager.glassChipSelector(this));
            // 与上键形成回路：换源 → 下键回到分类 tab，不会掉进内容区
            chip.setNextFocusDownId(R.id.mGridView);
            if (chip instanceof TextView) {
                ((TextView) chip).setTextColor(
                        com.github.tvbox.osc.util.ThemeManager.glassChipTextColors());
            }
        }
        int[] links = {R.id.tvLive, R.id.tvHistory, R.id.tvFavorite, R.id.tvPush,
                R.id.tvRemote, R.id.tvSetting};
        android.content.res.ColorStateList csl =
                com.github.tvbox.osc.util.ThemeManager.topLinkColors();
        for (int id : links) {
            View v = findViewById(id);
            if (v instanceof TextView) ((TextView) v).setTextColor(csl);
        }
        // 搜索做成常亮胶囊：它是最高频入口，不能跟着其他入口一起变灰
        View search = findViewById(R.id.tvSearch);
        if (search != null) {
            search.setBackground(com.github.tvbox.osc.util.ThemeManager.searchChipSelector(this));
            if (search instanceof TextView) {
                ((TextView) search).setTextColor(
                        com.github.tvbox.osc.util.ThemeManager.searchTextColors());
            }
        }
        if (tvName != null) tvName.setTextColor(0xF2FDF6F2);
        if (tvSource != null) tvSource.setTextColor(com.github.tvbox.osc.util.ThemeManager.accent());
    }


    private boolean skipNextUpdate = false;

    private void initViewModel() {
        sourceViewModel = new ViewModelProvider(this).get(SourceViewModel.class);
        sourceViewModel.sortResult.observe(this, new Observer<AbsSortXml>() {
            @Override
            public void onChanged(AbsSortXml absXml) {
                if (skipNextUpdate) {
                    skipNextUpdate = false;
                    return;
                }
                if (!homeSortLoading && loadingSourceKey == null) {
                    return;
                }
                if (absXml != null && absXml.sourceKey != null && loadingSourceKey != null && !loadingSourceKey.equals(absXml.sourceKey)) {
                    return;
                }
                SourceBean home = ApiConfig.get().getHomeSourceBean();
                showSuccess();
                clearHomePages();
                List<MovieSort.SortData> newSortData;
                if (absXml != null && absXml.classes != null && absXml.classes.sortList != null) {
                    newSortData = DefaultConfig.adjustSort(ApiConfig.get().getHomeSourceBean().getKey(), absXml.classes.sortList, true);
                } else {
                    newSortData = DefaultConfig.adjustSort(ApiConfig.get().getHomeSourceBean().getKey(), new ArrayList<>(), true);
                }
                updateSortData(newSortData);
                initViewPager(absXml);
                updateHomeRec(absXml);
                if (home != null && home.getName() != null && !home.getName().isEmpty() && tvSource != null) tvSource.setText(home.getName());
                tvName.clearAnimation();
                homeSortLoading = false;
                loadingSourceKey = null;
                previousHomeName = null;
                previousHomeSource = null;
            }
        });
    }

    // ================= 顶栏随滚动收起 / 展开 =================

    /** 已挂过监听、并在监听里的竖直列表（GridFragment 会复用同一个 mGridView 换页重挂，靠它去重） */
    private final java.util.HashSet<RecyclerView> scrollBound = new java.util.HashSet<>();
    private boolean topBarHidden = false;

    private final RecyclerView.OnScrollListener topBarScrollListener = new RecyclerView.OnScrollListener() {
        @Override
        public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
            // 阈值 6px：遥控器一次按键的抖动不算，避免顶栏来回抽。
            // 顶栏是叠加层，显隐只是一次 GPU 位移，不动内容布局，
            // 所以这里可以放心按方向即时切换，不会再把焦点挤掉。
            //
            // 【必须判 canScrollVertically(-1)】列表还在顶部时不许收。
            // 首页数据加载完、RecyclerView 从 0 高变成实际高度、恢复上次浏览位置，
            // 都会在"没有用户操作"的情况下回调一次 dy>6 —— 上一版就是这么在启动瞬间
            // 把顶栏吃掉的（表现为首页顶部空一条、看不到 NOYA 和分类 tab）。
            if (dy > 6) {
                if (rv.canScrollVertically(-1)) setTopBarHidden(true);
            } else if (dy < -6) {
                setTopBarHidden(false);
            }
        }

        @Override
        public void onScrollStateChanged(@NonNull RecyclerView rv, int newState) {
            // 滑回顶部（或一屏内容根本滑不动）时兜底展开，避免顶栏永久消失
            if (newState == RecyclerView.SCROLL_STATE_IDLE && !rv.canScrollVertically(-1)) {
                setTopBarHidden(false);
            }
        }
    };

    /**
     * 下滑收起顶栏、上滑到顶展开。
     *
     * 顶栏是叠加层（FrameLayout 里画在内容之上）。收起时做三件事：
     *   ① 顶栏自身 translationY(-h) + alpha(0)，结束后 GONE（不再参与 focus search）；
     *   ② 内容层 paddingTop 90mm → 0。**这一步是"沉浸"的关键**：
     *      顶栏没了却还留着 90mm 的内边距，那条空白露出来就是用户说的
     *      "框架还在、内容没了"；
     *   ③ padding 变化会让内容瞬间上移 h，所以同时给 mViewPager 一个 +h 的
     *      translationY 抵消，再动画回 0 —— 画面连续，看到的是内容平滑地
     *      长进顶栏让出来的位置。
     *
     * 之前试过把顶栏放在垂直 LinearLayout 里改 height，那会让整棵树 relayout，
     * 正在聚焦的卡片被回收、焦点掉回导航栏，所以彻底弃用。
     */
    private void setTopBarHidden(boolean hide) {
        if (topLayout == null) return;
        if (topBarHidden == hide) return;
        final int h = topLayout.getHeight() > 0
                ? topLayout.getHeight()
                : AutoSizeUtils.mm2px(this, TOPBAR_HEIGHT_MM);
        topBarHidden = hide;
        LOG.i("NYTOP topBar=" + (hide ? "HIDE" : "SHOW"));
        topLayout.animate().cancel();
        topLayout.removeCallbacks(topBarSettleRunnable);
        applyContentInset(hide, h);

        if (hide) {
            // 收起中先把后代从焦点链摘掉，避免方向键挑中正在淡出的分类 tab
            topLayout.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
            topLayout.animate().translationY(-h).alpha(0f)
                    .setDuration(200)
                    .setInterpolator(new DecelerateInterpolator())
                    .withEndAction(topBarSettleRunnable).start();
            // 兜底：连续快滑时 ViewPropertyAnimator 会被下一次调用 cancel，
            // withEndAction 不执行，顶栏就停在半透明状态（看上去只剩一条框架）。
            // 260ms 后无条件收口一次，幂等。
            topLayout.postDelayed(topBarSettleRunnable, 260);
            return;
        }

        topLayout.setVisibility(View.VISIBLE);
        topLayout.setTranslationY(-h);
        topLayout.setAlpha(0f);
        topLayout.animate().translationY(0f).alpha(1f)
                .setDuration(200)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        if (topBarHidden) return;
                        topLayout.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
                    }
                }).start();
    }

    /** 顶栏收起后的收口动作：GONE + 复位。动画结束和超时兜底两条路径都走它，幂等 */
    private final Runnable topBarSettleRunnable = new Runnable() {
        @Override
        public void run() {
            if (!topBarHidden || topLayout == null) return;
            topLayout.setVisibility(View.GONE);
            topLayout.setTranslationY(0f);
            topLayout.setAlpha(1f);
        }
    };

    /**
     * 内容区顶部内边距跟着顶栏一起走：收起 → 0，展开 → h。
     * padding 变化会立刻 relayout，所以先用 translationY 抵消这次位移，再反向动画回 0。
     */
    private void applyContentInset(boolean hide, int h) {
        if (contentLayout == null || mViewPager == null) return;
        int inset = hide ? 0 : h;
        if (contentLayout.getPaddingTop() == inset) return;
        contentLayout.setPadding(contentLayout.getPaddingLeft(), inset,
                contentLayout.getPaddingRight(), contentLayout.getPaddingBottom());
        mViewPager.animate().cancel();
        mViewPager.setTranslationY(hide ? h : -h);
        mViewPager.animate().translationY(0f)
                .setDuration(200)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }


    /** 递归找当前页里的竖直列表挂滚动监听（横向的分类 tab / 海报排会被跳过） */
    private void installScrollHide(View v) {
        if (v == null) return;
        if (v instanceof RecyclerView) {
            RecyclerView rv = (RecyclerView) v;
            if (!scrollBound.contains(rv) && isVerticalList(rv)) {
                scrollBound.add(rv);
                rv.addOnScrollListener(topBarScrollListener);
            }
            return;
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0, n = g.getChildCount(); i < n; i++) {
                installScrollHide(g.getChildAt(i));
            }
        }
    }

    private boolean isVerticalList(RecyclerView rv) {
        RecyclerView.LayoutManager lm = rv.getLayoutManager();
        if (!(lm instanceof androidx.recyclerview.widget.LinearLayoutManager)) return false;
        return ((androidx.recyclerview.widget.LinearLayoutManager) lm).getOrientation()
                == RecyclerView.VERTICAL;
    }

    private boolean pageChangeHooked = false;
    private boolean dataInitOk = false;
    private boolean jarInitOk = false;
    private boolean searchSpiderWarmStarted = false;
    private TipDialog mConfigErrorDialog;

    private void initData() {
        if (dataInitOk && jarInitOk) {
            loadHomeSort(false);
            if (hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                LOG.e("有");
            } else {
                LOG.e("无");
            }
            if (!useCacheConfig && Hawk.get(HawkConfig.DEFAULT_LOAD_LIVE, false)) {
                jumpActivity(LivePlayActivity.class);
            }
            //爬虫预热 仅首次加载
            if(!useCacheConfig)warmSearchSpidersOnce();
            return;
        }
        tvNameAnimation();
        showLoading();
        if (dataInitOk && !jarInitOk) {
            if (!ApiConfig.get().getSpider().isEmpty()) {
                ApiConfig.get().loadJar(preferCacheConfig, ApiConfig.get().getSpider(), new ApiConfig.LoadConfigCallback() {
                    @Override
                    public void success() {
                        jarInitOk = true;
                        mHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
//                                if (!useCacheConfig) Toast.makeText(HomeActivity.this, "自定义jar加载成功", Toast.LENGTH_SHORT).show();
                                initData();
                            }
                        }, 50);
                    }

                    @Override
                    public void notice(String msg) {
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(HomeActivity.this, msg, Toast.LENGTH_SHORT).show();
                            }
                        });
                    }

                    @Override
                    public void error(String msg) {
                        jarInitOk = true;
                        dataInitOk = true;
                        mHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(HomeActivity.this, msg+" jar load err", Toast.LENGTH_SHORT).show();
                                initData();
                            }
                        },50);
                    }
                });
            }
            return;
        }
        ApiConfig.get().loadConfig(preferCacheConfig, new ApiConfig.LoadConfigCallback() {
            @Override
            public void notice(String msg) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(HomeActivity.this, msg, Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void success() {
                dataInitOk = true;
                if (ApiConfig.get().getSpider().isEmpty()) {
                    jarInitOk = true;
                }
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        initData();
                    }
                }, 50);
            }

            @Override
            public void error(String msg) {
                if (msg.equalsIgnoreCase("-1")) {
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            dataInitOk = true;
                            jarInitOk = true;
                            initData();
                        }
                    });
                    return;
                }
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (isActivityUnavailable()) {
                            return;
                        }
                        if (mConfigErrorDialog == null)
                            mConfigErrorDialog = new TipDialog(HomeActivity.this, msg, "重试", "取消", new TipDialog.OnListener() {
                                @Override
                                public void left() {
                                    mHandler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            dismissConfigErrorDialog();
                                            initData();
                                        }
                                    });
                                }

                                @Override
                                public void right() {
                                    dataInitOk = true;
                                    jarInitOk = true;
                                    mHandler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            dismissConfigErrorDialog();
                                            initData();
                                        }
                                    });
                                }

                                @Override
                                public void cancel() {
                                    dataInitOk = true;
                                    jarInitOk = true;
                                    mHandler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            dismissConfigErrorDialog();
                                            initData();
                                        }
                                    });
                                }
                            });
                        if (!mConfigErrorDialog.isShowing())
                            mConfigErrorDialog.show();
                    }
                });
            }
        }, this);
    }

    private void warmSearchSpidersOnce() {
        if (searchSpiderWarmStarted) return;
        searchSpiderWarmStarted = true;
        ApiConfig.get().warmSearchSpiders();
    }

    private void loadHomeSort(boolean keepCurrentContent) {
        SourceBean home = ApiConfig.get().getHomeSourceBean();
        homeSortLoading = keepCurrentContent;
        if (keepCurrentContent && home != null && home.getName() != null && !home.getName().isEmpty()) {
            previousHomeName = tvSource == null || tvSource.getText() == null ? null : tvSource.getText().toString();
            if (tvSource != null) tvSource.setText(home.getName());
        }
        tvNameAnimation();
        if (home == null) {
            loadingSourceKey = null;
            if (!keepCurrentContent) showLoading();
            sourceViewModel.getSort(null);
            return;
        }
        loadingSourceKey = home.getKey();
        if (!keepCurrentContent) {
            showLoading();
        }
        sourceViewModel.getSort(loadingSourceKey);
    }

    private void initViewPager(AbsSortXml absXml) {
        if (sortAdapter.getData().size() > 0) {
            for (MovieSort.SortData data : sortAdapter.getData()) {
                if (data.id.equals("my0")) {
                    if (Hawk.get(HawkConfig.HOME_REC, HawkConfig.DEFAULT_HOME_REC) == 1 && absXml != null && absXml.videoList != null && absXml.videoList.size() > 0) {
                        fragments.add(UserFragment.newInstance(absXml.videoList));
                    } else {
                        fragments.add(UserFragment.newInstance(null));
                    }
                } else {
                    fragments.add(GridFragment.newInstance(data));
                }
            }
            pageAdapter = new HomePageAdapter(getSupportFragmentManager(), fragments);
            try {
                Field field = ViewPager.class.getDeclaredField("mScroller");
                field.setAccessible(true);
                FixedSpeedScroller scroller = new FixedSpeedScroller(mContext, new AccelerateInterpolator());
                field.set(mViewPager, scroller);
                scroller.setmDuration(300);
            } catch (Exception e) {
            }
            mViewPager.setPageTransformer(true, new DefaultTransformer());
            mViewPager.setAdapter(pageAdapter);
            mViewPager.setCurrentItem(currentSelected, false);
            // 顶栏收起：挂到当前页的竖直列表上；换页要重新挂（GridFragment 会把 mGridView 到处搬）
            if (!pageChangeHooked) {
                pageChangeHooked = true;
                mViewPager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
                    @Override
                    public void onPageSelected(int position) {
                        setTopBarHidden(false);
                        mViewPager.postDelayed(() -> installScrollHide(mViewPager), 350);
                    }
                });
            }
            mViewPager.postDelayed(() -> installScrollHide(mViewPager), 800);
            mViewPager.postDelayed(() -> installScrollHide(mViewPager), 2500);
        }
    }

    private void clearHomePages() {
        mHandler.removeCallbacks(mDataRunnable);
        currentSelected = 0;
        sortFocused = 0;
        sortChange = false;
        sortFocusView = null;
        currentView = null;
        if (pageAdapter != null) {
            mViewPager.setAdapter(null);
            pageAdapter.removeAll();
            pageAdapter = null;
        } else if (!fragments.isEmpty()) {
            fragments.clear();
        }
    }

    private void updateSortData(List<MovieSort.SortData> newSortData) {
        if (newSortData == null) {
            newSortData = new ArrayList<>();
        }
        List<MovieSort.SortData> oldSortData = sortAdapter.getData();
        if (oldSortData.isEmpty()
                || newSortData.isEmpty()
                || oldSortData.get(0) == null
                || newSortData.get(0) == null
                || !"my0".equals(oldSortData.get(0).id)
                || !"my0".equals(newSortData.get(0).id)) {
            sortAdapter.setNewData(newSortData);
            return;
        }
        int oldTailCount = oldSortData.size() - 1;
        if (oldTailCount > 0) {
            oldSortData.subList(1, oldSortData.size()).clear();
            sortAdapter.notifyItemRangeRemoved(1, oldTailCount);
        }
        if (newSortData.size() > 1) {
            oldSortData.addAll(newSortData.subList(1, newSortData.size()));
            sortAdapter.notifyItemRangeInserted(1, newSortData.size() - 1);
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    public void onBackPressed() {
        // 打断加载
        if (homeSortLoading) {
            cancelHomeSortLoading();
            return;
        }
        if (isLoading()) {
            refreshEmpty();
            return;
        }
        // 如果处于 VOD 删除模式，则退出该模式并刷新界面
        if (HawkConfig.hotVodDelete) {
            HawkConfig.hotVodDelete = false;
            UserFragment.homeHotVodAdapter.notifyDataSetChanged();
            return;
        }

        // 检查 fragments 状态
        if (this.fragments.size() <= 0 || this.sortFocused >= this.fragments.size() || this.sortFocused < 0) {
            doExit();
            return;
        }

        BaseLazyFragment baseLazyFragment = this.fragments.get(this.sortFocused);
        if (baseLazyFragment instanceof GridFragment) {
            GridFragment grid = (GridFragment) baseLazyFragment;
            // 如果当前 Fragment 能恢复之前保存的 UI 状态，则直接返回
            if (grid.restoreView()) {
                return;
            }
            // 如果 sortFocusView 存在且没有获取焦点，则请求焦点
            if (this.sortFocusView != null && !this.sortFocusView.isFocused()) {
                this.sortFocusView.requestFocus();
            }
            // 如果当前不是第一个界面，则将列表设置到第一项
            else if (this.sortFocused != 0) {
                this.mGridView.setSelection(0);
            } else {
                doExit();
            }
        } else if (baseLazyFragment instanceof UserFragment && UserFragment.tvHotList.canScrollHorizontally(-1)) {
            // 片单改成横向单行，回退就是把这一行滚回最左边
            UserFragment.tvHotList.scrollToPosition(0);
            this.mGridView.setSelection(0);
        } else {
            doExit();
        }
    }

    private void doExit() {
        // 如果两次返回间隔小于 2000 毫秒，则退出应用
        if (System.currentTimeMillis() - mExitTime < 2000) {
            AppManager.getInstance().finishAllActivity();
            unregisterEventBus();
            ControlManager.get().stopServer();
            finish();
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(0);
        } else {
            // 否则仅提示用户，再按一次退出应用
            mExitTime = System.currentTimeMillis();
            Toast.makeText(mContext, "再按一次返回键退出应用", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 从详情/搜索/设置返回时顶栏必须恢复显示；随后由焦点监听决定是否再收起
        setTopBarHidden(false);
        refreshTopInfoTextSize();
        mHandler.removeCallbacks(refreshTopInfoTextSizeRunnable);
        mHandler.postDelayed(refreshTopInfoTextSizeRunnable, 350);
        mHandler.removeCallbacks(refreshTopLayoutRunnable);
        mHandler.postDelayed(refreshTopLayoutRunnable, 450);
        mHandler.post(mRunnable);
    }


    @Override
    protected void onPause() {
        super.onPause();
        mHandler.removeCallbacks(refreshTopInfoTextSizeRunnable);
        mHandler.removeCallbacks(refreshTopLayoutRunnable);
        mHandler.removeCallbacks(mRunnable);
    }

    private void refreshTopInfoTextSize() {
        if (tvName == null || tvDate == null) {
            return;
        }
        tvName.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen.ts_22));
        if (tvSource != null) {
            tvSource.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen.ts_16));
        }
        tvDate.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen.ts_16));
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void refresh(RefreshEvent event) {
        if (event.type == RefreshEvent.TYPE_PUSH_URL) {
            if (ApiConfig.get().getSource("push_agent") != null) {
                Intent newIntent = new Intent(mContext, DetailActivity.class);
                newIntent.putExtra("id", (String) event.obj);
                newIntent.putExtra("sourceKey", "push_agent");
                newIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                HomeActivity.this.startActivity(newIntent);
            }
        } else if (event.type == RefreshEvent.TYPE_FILTER_CHANGE) {
            if (currentView != null) {
                showFilterIcon((int) event.obj);
            }
        } else if (event.type == RefreshEvent.TYPE_HOME_SOURCE_CHANGE) {
            refreshHome(false);
        }
    }

    private void showFilterIcon(int count) {
        boolean visible = count > 0;
        currentView.findViewById(R.id.tvFilterColor).setVisibility(visible ? View.VISIBLE : View.GONE);
        currentView.findViewById(R.id.tvFilter).setVisibility(visible ? View.GONE : View.VISIBLE);
    }

    private final Runnable mDataRunnable = new Runnable() {
        @Override
        public void run() {
            if (sortChange) {
                sortChange = false;
                BaseLazyFragment baseLazyFragment = fragments.get(sortFocused);
                if (sortFocused != currentSelected) {
                    currentSelected = sortFocused;
                    mViewPager.setCurrentItem(sortFocused, false);
                    // 顶栏显隐由滚动位置驱动，切分类时保持现状即可
                    if (baseLazyFragment instanceof GridFragment && ((GridFragment) baseLazyFragment).shouldReloadOnSelect()) {
                        ((GridFragment) baseLazyFragment).forceRefresh();
                    }
                } else if (baseLazyFragment instanceof GridFragment && ((GridFragment) baseLazyFragment).shouldReloadOnSelect()) {
                    ((GridFragment) baseLazyFragment).forceRefresh();
                }
            }
        }
    };

    /** 顶栏行2 的六个快捷入口，从原首页底部一行搬上来 */
    @Override
    public void onClick(View v) {
        HawkConfig.hotVodDelete = false;
        FastClickCheckUtil.check(v);
        int id = v.getId();
        if (id == R.id.tvLive) {
            jumpActivity(LivePlayActivity.class);
        } else if (id == R.id.tvHistory) {
            jumpActivity(HistoryActivity.class);
        } else if (id == R.id.tvFavorite) {
            jumpActivity(CollectActivity.class);
        } else if (id == R.id.tvPush) {
            jumpActivity(PushActivity.class);
        } else if (id == R.id.tvRemote) {
            // 复用推送页那套二维码界面，只是换成遥控页（page=remote 直接落在摇杆面板）
            Bundle bundle = new Bundle();
            bundle.putString("page", "remote");
            jumpActivity(PushActivity.class, bundle);
        } else if (id == R.id.tvSearch) {
            jumpActivity(SearchActivity.class);
        } else if (id == R.id.tvSetting) {
            jumpActivity(SettingActivity.class);
        }
    }

    private long menuKeyDownTime = 0;
    private static final long LONG_PRESS_THRESHOLD = 2000; // 设置长按的阈值，单位是毫秒
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        // 顶栏显隐现在完全由滚动驱动（见 topBarScrollListener），
        // 这里不再拦方向键、也不再抢焦点：以前吞掉 DPAD_UP 再 240ms 后
        // 把焦点硬塞给分类 tab，就是"上滑两下直接回到导航栏"的直接原因。
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                menuKeyDownTime = System.currentTimeMillis();
            } else if (event.getAction() == KeyEvent.ACTION_UP) {
                long pressDuration = System.currentTimeMillis() - menuKeyDownTime;
                if (pressDuration >= LONG_PRESS_THRESHOLD) {
                    jumpActivity(SettingActivity.class);;
                }else {
                    showSiteSwitch();
                }
            }
        }
        return super.dispatchKeyEvent(event);
    }

    /**
     * 兼容入口：GridFragment 网格滚动时回调。
     * 现在 HomeActivity 自己给每个竖直列表挂了 topBarScrollListener，
     * 这里直接转发，保证两条来源不会各弹一套逻辑（以前就是两套在打架）。
     */
    public void onGridScrollHide(boolean hide) {
        if (topLayout == null || isActivityUnavailable()) return;
        setTopBarHidden(hide);
    }

    @Override
    protected void onDestroy() {
        dismissHomeDialogs();
        mHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
        unregisterEventBus();
        if (isFinishing()) {
            ControlManager.get().stopServer();
        }
    }

    private void unregisterEventBus() {
        if (eventBusRegistered) {
            EventBus.getDefault().unregister(this);
            eventBusRegistered = false;
        }
    }

    private SelectDialog<SourceBean> mSiteSwitchDialog;
    /** 源检测进行中（防止连点重复扫） */
    private boolean siteScanning = false;

    void showSiteSwitch() {
        if (isActivityUnavailable()) return;
        List<SourceBean> sites = ApiConfig.get().getSwitchSourceBeanList();
        if (sites.isEmpty()) return;
        if (mSiteSwitchDialog == null) {
            mSiteSwitchDialog = new SelectDialog<>(HomeActivity.this);
            TvRecyclerView tvRecyclerView = mSiteSwitchDialog.findViewById(R.id.list);
            // 霓虹片仓：3~4 列招牌墙，宽度随列数走
            int spanCount = sites.size() > 12 ? 4 : 3;
            tvRecyclerView.setLayoutManager(new V7GridLayoutManager(mSiteSwitchDialog.getContext(), spanCount));
            // 设置对话框宽度
            ConstraintLayout cl_root = mSiteSwitchDialog.findViewById(R.id.cl_root);
            ViewGroup.LayoutParams clp = cl_root.getLayoutParams();
            clp.width = AutoSizeUtils.mm2px(mSiteSwitchDialog.getContext(), 360 + 130 * spanCount);
            mSiteSwitchDialog.setTip("请选择首页数据源");
            // 数据源弹窗才有的工具条：源检测 + 健康度排序
            mSiteSwitchDialog.showSourceTools(true,
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            startSiteScan();
                        }
                    },
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            boolean now = !Hawk.get("smart_source_sort", false);
                            Hawk.put("smart_source_sort", now);
                            if (mSiteSwitchDialog != null) {
                                mSiteSwitchDialog.setSortActive(now);
                                mSiteSwitchDialog.setToolHint(now
                                        ? "已按健康度重排 · 快源在前"
                                        : "已恢复源列表原始顺序");
                            }
                            bindSiteList();
                        }
                    });
        }
        bindSiteList();
        mSiteSwitchDialog.setSourceCountText("共 " + sites.size() + " 个源");
        mSiteSwitchDialog.setSortActive(Hawk.get("smart_source_sort", false));
        String last = SourceHealth.lastScanText();
        mSiteSwitchDialog.setToolHint(last == null || last.isEmpty() ? "点「源检测」给源打分" : last);
        if (!mSiteSwitchDialog.isShowing())
            mSiteSwitchDialog.show();
    }

    /** 重新灌一次源列表（切源、体检完成、切换排序都要重来一遍） */
    private void bindSiteList() {
        if (mSiteSwitchDialog == null) return;
        List<SourceBean> sites = ApiConfig.get().getSwitchSourceBeanList();
        if (sites.isEmpty()) return;
        // 智能选源：仅当开关打开时才重排（默认关，避免打乱肌肉记忆）
        if (Hawk.get("smart_source_sort", false)) {
            SourceHealth.sortByHealth(sites);
        }
        int select = sites.indexOf(ApiConfig.get().getHomeSourceBean());
        if (select < 0 || select >= sites.size()) select = 0;
        mSiteSwitchDialog.setAdapter(new SelectDialogAdapter.SelectDialogInterface<SourceBean>() {
            @Override
            public void click(SourceBean value, int pos) {
                dismissSiteSwitchDialog();
                previousHomeSource = ApiConfig.get().getHomeSourceBean();
                ApiConfig.get().setSourceBean(value);
                refreshHome(false);
            }

            @Override
            public String getDisplay(SourceBean val) {
                // 「名字｜健康度」：SelectDialogAdapter 会把 ｜ 后面的拆成卡下小注
                String health = SourceHealth.label(val.getKey());
                return health.isEmpty() ? val.getName() : val.getName() + "｜" + health;
            }
        }, new DiffUtil.ItemCallback<SourceBean>() {
            @Override
            public boolean areItemsTheSame(@NonNull SourceBean oldItem, @NonNull SourceBean newItem) {
                return oldItem == newItem;
            }

            @Override
            public boolean areContentsTheSame(@NonNull SourceBean oldItem, @NonNull SourceBean newItem) {
                return oldItem.getKey().equals(newItem.getKey());
            }
        }, sites, select);
    }

    /** 一键体检：并发探活，进度直接写在弹窗工具条上 */
    private void startSiteScan() {
        if (siteScanning || mSiteSwitchDialog == null) return;
        final List<SourceBean> sites = ApiConfig.get().getSwitchSourceBeanList();
        if (sites.isEmpty()) return;
        siteScanning = true;
        mSiteSwitchDialog.setToolHint("检测中…");
        SourceHealth.checkAll(sites, new SourceHealth.ScanCallback() {
            @Override
            public void onProgress(int done, int total) {
                if (mSiteSwitchDialog != null && mSiteSwitchDialog.isShowing()) {
                    mSiteSwitchDialog.setToolHint("检测中 " + done + "/" + total);
                }
            }

            @Override
            public void onDone(String summary) {
                siteScanning = false;
                SourceHealth.markScanned();
                if (mSiteSwitchDialog != null && mSiteSwitchDialog.isShowing()) {
                    mSiteSwitchDialog.setToolHint(summary);
                    // 体检完如果开着智能排序，直接重排，用户立刻看到"快源在前"
                    if (Hawk.get("smart_source_sort", false)) {
                        bindSiteList();
                    }
                }
            }
        });
    }

    private void refreshHome()
    {
        refreshHome(true);
    }

    private void refreshHomeSort() {
        refreshHomeRec = true;
        if (UserFragment.homeHotVodAdapter != null) {
            UserFragment.homeHotVodAdapter.setNewData(new ArrayList<Movie.Video>());
        }
        SourceBean home = ApiConfig.get().getHomeSourceBean();
        if (home != null) {
            SourceViewModel.clearSortCache(home.getKey());
        }
        refreshHome(false);
    }

    private void updateHomeRec(AbsSortXml absXml) {
        if (!refreshHomeRec) return;
        refreshHomeRec = false;
        if (Hawk.get(HawkConfig.HOME_REC, HawkConfig.DEFAULT_HOME_REC) != 1) return;
        if (absXml == null || absXml.videoList == null || UserFragment.homeHotVodAdapter == null) return;
        // 口味画像排序：看过的类型优先（纯本地统计，分数=1+3×观看次数）
        try {
            java.util.Collections.sort(absXml.videoList, (a, b) ->
                    Float.compare(TasteProfile.scoreOf(b == null ? null : b.type),
                            TasteProfile.scoreOf(a == null ? null : a.type)));
        } catch (Throwable ignored) {
        }
        UserFragment.homeHotVodAdapter.setNewData(absXml.videoList);
    }

    private void refreshHome(final boolean restart)
    {
        if (Thread.currentThread() != android.os.Looper.getMainLooper().getThread()) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    refreshHome(restart);
                }
            });
            return;
        }
        if (isActivityUnavailable()) {
            return;
        }
        dismissHomeDialogs();
        if (!restart) {
            loadHomeSort(true);
            return;
        }
        Intent intent = new Intent(getApplicationContext(), HomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        Bundle bundle = new Bundle();
        bundle.putBoolean("useCache", true);
        intent.putExtras(bundle);
        HomeActivity.this.startActivity(intent);
    }

    private boolean isActivityUnavailable() {
        return isFinishing() || (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1 && isDestroyed());
    }

    private void dismissHomeDialogs() {
        dismissConfigErrorDialog();
        dismissSiteSwitchDialog();
    }

    private void dismissConfigErrorDialog() {
        if (mConfigErrorDialog != null) {
            if (mConfigErrorDialog.isShowing()) {
                mConfigErrorDialog.dismiss();
            }
            mConfigErrorDialog = null;
        }
    }

    private void dismissSiteSwitchDialog() {
        if (mSiteSwitchDialog != null) {
            if (mSiteSwitchDialog.isShowing()) {
                mSiteSwitchDialog.dismiss();
            }
            mSiteSwitchDialog = null;
        }
    }

    private void refreshEmpty()
    {
        skipNextUpdate=true;
        showSuccess();
        cancelHomeSortLoading();
        clearHomePages();
        sortAdapter.setNewData(DefaultConfig.adjustSort(ApiConfig.get().getHomeSourceBean().getKey(), new ArrayList<>(), true));
        initViewPager(null);
        tvName.clearAnimation();
    }

    private void cancelHomeSortLoading() {
        homeSortLoading = false;
        loadingSourceKey = null;
        tvName.clearAnimation();
        if (previousHomeSource != null) {
            ApiConfig.get().setSourceBean(previousHomeSource);
        }
        if (previousHomeName != null && !previousHomeName.isEmpty() && tvSource != null) {
            tvSource.setText(previousHomeName);
        }
        previousHomeSource = null;
        previousHomeName = null;
    }

    private void tvNameAnimation()
    {
        // 侧栏顶部是品牌位。原来用无限呼吸闪烁提示「点我」，放进 rail 里太吵，改为静态
        tvName.clearAnimation();
        tvName.setAlpha(1.0f);
    }
}
