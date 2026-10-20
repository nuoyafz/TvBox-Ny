package com.github.tvbox.osc.ui.fragment;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.BaseLazyFragment;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.bean.VodInfo;
import com.github.tvbox.osc.cache.RoomDataManger;
import com.github.tvbox.osc.event.ServerEvent;
import com.github.tvbox.osc.ui.activity.CollectActivity;
import com.github.tvbox.osc.ui.activity.DetailActivity;
import com.github.tvbox.osc.ui.activity.FastSearchActivity;
import com.github.tvbox.osc.ui.activity.HistoryActivity;
import com.github.tvbox.osc.ui.activity.LivePlayActivity;
import com.github.tvbox.osc.ui.activity.PushActivity;
import com.github.tvbox.osc.ui.activity.SearchActivity;
import com.github.tvbox.osc.ui.activity.SettingActivity;
import com.github.tvbox.osc.ui.adapter.HomeHotVodAdapter;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.ImgUtil;
import com.github.tvbox.osc.util.UA;
import com.github.tvbox.osc.viewmodel.SourceViewModel;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.AbsCallback;
import com.lzy.okgo.model.Response;
import com.orhanobut.hawk.Hawk;
import me.jessyan.autosize.utils.AutoSizeUtils;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * 首页第一屏 · 画报式
 *
 * 版面只保留三块：主视觉（正在看的那一部）→ 一条横向片单 → 一行文字入口。
 * 原来的 6 个大图标方块已经压成底部一行小字，不再抢主视觉的权重。
 */
public class UserFragment extends BaseLazyFragment {
    // 直播/历史/收藏/推送/搜索/设置 六个入口已上移到 activity_home 的顶栏第二行，
    // 由 HomeActivity 统一处理点击，这里不再持有引用。

    private View tvHeroBlock;
    private TextView tvHeroTitle;
    private TextView tvHeroMeta;
    private TextView tvHeroPlay;
    private TextView tvRowLabel;
    private ImageView ivHeroBg;
    private ImageView ivHeroPoster;
    private TextView tvHeroEp;
    private View heroPosterBox;

    public static HomeHotVodAdapter homeHotVodAdapter;
    private List<Movie.Video> homeSourceRec;
    public static TvRecyclerView tvHotList;
    private SourceViewModel sourceViewModel;

    public static UserFragment newInstance() {
        return new UserFragment();
    }

    public static UserFragment newInstance(List<Movie.Video> recVod) {
        return new UserFragment().setArguments(recVod);
    }

    public UserFragment setArguments(List<Movie.Video> recVod) {
        this.homeSourceRec = recVod;
        return this;
    }

    @Override
    protected void onFragmentResume() {
        super.onFragmentResume();
        // 画报版只用单行横向片单，不再按 HOME_REC_STYLE 切成网格
        tvHotList.setVisibility(View.VISIBLE);
        tvHotList.setHasFixedSize(true);
        tvHotList.setLayoutManager(new V7LinearLayoutManager(this.mContext, V7LinearLayoutManager.HORIZONTAL, false));
        int half = tvHotList.mHorizontalSpacingWithMargins / 2;
        tvHotList.setPadding(-half, 0, -half, 0);
        if (Hawk.get(HawkConfig.HOME_REC, HawkConfig.DEFAULT_HOME_REC) == 2) {
            List<VodInfo> allVodRecord = RoomDataManger.getAllVodRecord(20);
            List<Movie.Video> vodList = new ArrayList<>();
            for (VodInfo vodInfo : allVodRecord) {
                Movie.Video vod = new Movie.Video();
                vod.id = vodInfo.id;
                vod.sourceKey = vodInfo.sourceKey;
                vod.name = vodInfo.name;
                vod.pic = vodInfo.pic;
                if (vodInfo.playNote != null && !vodInfo.playNote.isEmpty())
                    vod.note = "上次看到" + vodInfo.playNote;
                vodList.add(vod);
            }
            homeHotVodAdapter.setNewData(vodList);
        }
        // 每次回到首页都刷新主视觉：刚看完一集回来，这里要立刻变成新的那一部
        loadHero();
    }

    @Override
    protected int getLayoutResID() {
        return R.layout.fragment_user;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        TvRecyclerView hotList = view.findViewById(R.id.tvHotList);
        if (hotList != null && hotList.getLayoutManager() == null) {
            hotList.setLayoutManager(new V7LinearLayoutManager(mContext, V7LinearLayoutManager.HORIZONTAL, false));
        }
    }

    private void jumpSearch(Movie.Video vod) {
        Intent newIntent;
        if (Hawk.get(HawkConfig.FAST_SEARCH_MODE, true)) {
            newIntent = new Intent(mContext, FastSearchActivity.class);
        } else {
            newIntent = new Intent(mContext, SearchActivity.class);
        }
        newIntent.putExtra("title", vod.name);
        newIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        mActivity.startActivity(newIntent);
    }

    private ImgUtil.Style style;

    @Override
    protected void init() {
        EventBus.getDefault().register(this);
        sourceViewModel = new ViewModelProvider(this).get(SourceViewModel.class);

        tvHeroBlock = findViewById(R.id.tvHeroBlock);
        tvHeroTitle = findViewById(R.id.tvHeroTitle);
        tvHeroMeta = findViewById(R.id.tvHeroMeta);
        tvHeroPlay = findViewById(R.id.tvHeroPlay);
        tvRowLabel = findViewById(R.id.tvRowLabel);
        ivHeroBg = findViewById(R.id.ivHeroBg);
        ivHeroPoster = findViewById(R.id.ivHeroPoster);
        tvHeroEp = findViewById(R.id.tvHeroEp);
        heroPosterBox = findViewById(R.id.heroPosterBox);

        tvHotList = findViewById(R.id.tvHotList);
        if (Hawk.get(HawkConfig.HOME_REC, HawkConfig.DEFAULT_HOME_REC) == 1 && homeSourceRec != null) {
            style = ImgUtil.initStyle();
        }
        String tvRate = "";
        if (Hawk.get(HawkConfig.HOME_REC, HawkConfig.DEFAULT_HOME_REC) == 0) {
            tvRate = "豆瓣热播";
        } else if (Hawk.get(HawkConfig.HOME_REC, HawkConfig.DEFAULT_HOME_REC) == 1) {
            tvRate = homeSourceRec != null ? "站点推荐" : "豆瓣热播";
        }
        if (tvRowLabel != null) {
            tvRowLabel.setText(tvRate.isEmpty() ? "接着看" : tvRate);
        }
        homeHotVodAdapter = new HomeHotVodAdapter(style, tvRate);
        homeHotVodAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                if (ApiConfig.get().getSourceBeanList().isEmpty())
                    return;
                Movie.Video vod = ((Movie.Video) adapter.getItem(position));

                if (Hawk.get(HawkConfig.HOME_REC, HawkConfig.DEFAULT_HOME_REC) == 1 && homeSourceRec != null && vod.action != null) {
                    sourceViewModel.action(vod.sourceKey, vod.action);
                    return;
                }

                if ((vod.id != null && !vod.id.isEmpty()) && (Hawk.get(HawkConfig.HOME_REC, HawkConfig.DEFAULT_HOME_REC) == 2) && HawkConfig.hotVodDelete) {
                    homeHotVodAdapter.remove(position);
                    VodInfo vodInfo = RoomDataManger.getVodInfo(vod.sourceKey, vod.id);
                    assert vodInfo != null;
                    RoomDataManger.deleteVodRecord(vod.sourceKey, vodInfo);
                    Toast.makeText(mContext, "已删除当前记录", Toast.LENGTH_SHORT).show();
                } else if (vod.id != null && vod.id.startsWith("msearch:")) {
                    jumpSearch(vod);
                } else {
                    Bundle bundle = new Bundle();
                    bundle.putString("id", vod.id);
                    bundle.putString("sourceKey", vod.sourceKey);
                    bundle.putString("title", vod.name);
                    bundle.putString("picture", vod.pic);
                    jumpActivity(DetailActivity.class, bundle);
                }
            }
        });

        homeHotVodAdapter.setOnItemLongClickListener(new BaseQuickAdapter.OnItemLongClickListener() {
            @SuppressLint("NotifyDataSetChanged")
            @Override
            public boolean onItemLongClick(BaseQuickAdapter adapter, View view, int position) {
                if (ApiConfig.get().getSourceBeanList().isEmpty()) return false;
                Movie.Video vod = ((Movie.Video) adapter.getItem(position));
                // Additional Check if : Home Rec 0=豆瓣, 1=推荐, 2=历史
                assert vod != null;
                if (vod.id != null && vod.id.startsWith("msearch:")) {
                    Bundle bundle = new Bundle();
                    bundle.putString("id", vod.id);
                    bundle.putString("sourceKey", vod.sourceKey);
                    bundle.putString("title", vod.name);
                    bundle.putString("picture", vod.pic);
                    jumpActivity(DetailActivity.class, bundle);
                } else if ((vod.id != null && !vod.id.isEmpty()) && (Hawk.get(HawkConfig.HOME_REC, HawkConfig.DEFAULT_HOME_REC) == 2)) {
                    HawkConfig.hotVodDelete = !HawkConfig.hotVodDelete;
                    homeHotVodAdapter.notifyDataSetChanged();
                } else {
                    Bundle bundle = new Bundle();
                    bundle.putString("title", vod.name);
                    jumpActivity(FastSearchActivity.class, bundle);
                }
                return true;
            }
        });

        // 焦点反馈交给 selector_poster_card（白色 2mm 环），这里不再做缩放
        tvHotList.setAdapter(homeHotVodAdapter);

        initHomeHotVod(homeHotVodAdapter);
        loadHero();
        sourceViewModel.actionResult.observe(this, new Observer<JSONObject>() {
            @Override
            public void onChanged(JSONObject jsonObject) {
                if (jsonObject == null) return;
                String msg = jsonObject.optString("msg");
                if (!msg.isEmpty()) Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * 主视觉 = 最近一条观看记录。
     * 没有记录就把整块藏掉（不留空壳），片单自动上移。
     */
    private void loadHero() {
        if (tvHeroBlock == null || tvHeroTitle == null || tvHeroMeta == null) return;
        try {
            List<VodInfo> records = RoomDataManger.getAllVodRecord(1);
            if (records == null || records.isEmpty()) {
                tvHeroBlock.setVisibility(View.INVISIBLE);
            if (heroPosterBox != null) heroPosterBox.setVisibility(View.INVISIBLE);
                return;
            }
            final VodInfo vod = records.get(0);
            if (vod == null || vod.id == null || vod.id.isEmpty()) {
                tvHeroBlock.setVisibility(View.INVISIBLE);
            if (heroPosterBox != null) heroPosterBox.setVisibility(View.INVISIBLE);
                return;
            }
            SourceBean bean = ApiConfig.get().getSource(vod.sourceKey);
            StringBuilder meta = new StringBuilder();
            if (bean != null && bean.getName() != null && !bean.getName().isEmpty()) {
                meta.append(bean.getName());
            }
            if (vod.playNote != null && !vod.playNote.isEmpty()) {
                if (meta.length() > 0) meta.append("   ·   ");
                meta.append("上次看到 ").append(vod.playNote);
            }
            tvHeroTitle.setText(vod.name == null ? "" : vod.name);
            tvHeroMeta.setText(meta.length() == 0 ? "继续上次的进度" : meta.toString());

            // 主视觉跨页：背景 + 实体海报 + 集数角标
            String heroName = vod.name == null ? "" : vod.name;
            String heroPic = vod.pic == null ? "" : vod.pic.trim();
            if (!TextUtils.isEmpty(heroPic) && !ImgUtil.isBase64Image(heroPic)) {
                int bgW = AutoSizeUtils.mm2px(mContext, 420);
                int bgH = AutoSizeUtils.mm2px(mContext, 300);
                int pw = AutoSizeUtils.mm2px(mContext, 200);
                int ph = AutoSizeUtils.mm2px(mContext, 280);
                int r = AutoSizeUtils.mm2px(mContext, 8);
                ImgUtil.load(heroPic, ivHeroPoster, r, pw, ph, heroName);
                ImgUtil.load(heroPic, ivHeroBg, r, bgW, bgH, heroName);
            } else {
                ivHeroPoster.setImageDrawable(ImgUtil.createTextDrawable(heroName));
                ivHeroBg.setImageDrawable(null);
            }
            if (tvHeroEp != null) {
                if (vod.playNote != null && !vod.playNote.isEmpty()) {
                    tvHeroEp.setText(vod.playNote);
                    tvHeroEp.setVisibility(View.VISIBLE);
                } else {
                    tvHeroEp.setVisibility(View.GONE);
                }
            }
            if (heroPosterBox != null) heroPosterBox.setVisibility(View.VISIBLE);

            if (tvHeroPlay != null) {
                tvHeroPlay.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Bundle bundle = new Bundle();
                        bundle.putString("id", vod.id);
                        bundle.putString("sourceKey", vod.sourceKey);
                        bundle.putString("title", vod.name);
                        bundle.putString("picture", vod.pic);
                        jumpActivity(DetailActivity.class, bundle);
                    }
                });
            }
            tvHeroBlock.setVisibility(View.VISIBLE);
        } catch (Throwable th) {
            tvHeroBlock.setVisibility(View.INVISIBLE);
            if (heroPosterBox != null) heroPosterBox.setVisibility(View.INVISIBLE);
        }
    }

    private void initHomeHotVod(HomeHotVodAdapter adapter) {
        if (Hawk.get(HawkConfig.HOME_REC, HawkConfig.DEFAULT_HOME_REC) == 1) {
            if (homeSourceRec != null) {
                adapter.setNewData(homeSourceRec);
                return;
            }
        } else if (Hawk.get(HawkConfig.HOME_REC, HawkConfig.DEFAULT_HOME_REC) == 2) {
            return;
        }
        setDouBanData(adapter);
    }

    private void setDouBanData(HomeHotVodAdapter adapter) {
        try {
            Calendar cal = Calendar.getInstance();
            int year = cal.get(Calendar.YEAR);
            int month = cal.get(Calendar.MONTH) + 1;
            int day = cal.get(Calendar.DATE);
            String today = String.format("%d%d%d", year, month, day);
            String requestDay = Hawk.get("home_hot_day", "");
            if (requestDay.equals(today)) {
                String json = Hawk.get("home_hot", "");
                if (!json.isEmpty()) {
                    ArrayList<Movie.Video> hotMovies = loadHots(json);
                    if (hotMovies != null && hotMovies.size() > 0) {
                        adapter.setNewData(hotMovies);
                        return;
                    }
                }
            }
            String doubanUrl = "https://movie.douban.com/j/new_search_subjects?sort=U&range=0,10&tags=&playable=1&start=0&year_range=" + year + "," + year;
            OkGo.<String>get(doubanUrl)
                    .headers("User-Agent", UA.randomOne())
                    .execute(new AbsCallback<String>() {
                        @Override
                        public void onSuccess(Response<String> response) {
                            String netJson = response.body();
                            Hawk.put("home_hot_day", today);
                            Hawk.put("home_hot", netJson);
                            mActivity.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    adapter.setNewData(loadHots(netJson));
                                }
                            });
                        }

                        @Override
                        public String convertResponse(okhttp3.Response response) throws Throwable {
                            return response.body().string();
                        }
                    });
        } catch (Throwable th) {
            th.printStackTrace();
        }
    }

    private ArrayList<Movie.Video> loadHots(String json) {
        ArrayList<Movie.Video> result = new ArrayList<>();
        try {
            JsonObject infoJson = new Gson().fromJson(json, JsonObject.class);
            JsonArray array = infoJson.getAsJsonArray("data");
            int limit = Math.min(array.size(), 25);
            for (int i = 0; i < limit; i++) {
                JsonElement ele = array.get(i);
                JsonObject obj = ele.getAsJsonObject();
                Movie.Video vod = new Movie.Video();
                vod.name = obj.get("title").getAsString();
                vod.note = obj.get("rate").getAsString();
                if (!vod.note.isEmpty()) vod.note += " 分";
                vod.pic = obj.get("cover").getAsString()
                        + "@User-Agent=" + UA.randomOne()
                        + "@Referer=https://www.douban.com/";

                result.add(vod);
            }
        } catch (Throwable th) {

        }
        return result;
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void server(ServerEvent event) {
        if (event.type == ServerEvent.SERVER_CONNECTION) {
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
    }
}
