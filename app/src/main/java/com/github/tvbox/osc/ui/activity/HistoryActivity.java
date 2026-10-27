package com.github.tvbox.osc.ui.activity;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.bean.VodInfo;
import com.github.tvbox.osc.cache.RoomDataManger;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.ui.adapter.HistoryAdapter;
import com.github.tvbox.osc.ui.dialog.ConfirmClearDialog;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.util.AtvFocusHelper;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.TvPerf;
import com.github.tvbox.osc.util.Threads;
import com.orhanobut.hawk.Hawk;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7GridLayoutManager;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.List;

/**
 * @author pj567
 * @date :2021/1/7
 * @description:
 */
public class HistoryActivity extends BaseActivity {
    private ImageView tvDelete;
    private ImageView tvClear;
    private TextView tvDelTip;
    private TvRecyclerView mGridView;
    private HistoryAdapter historyAdapter;
    /** 清理对话框需要清空列表，但不能用静态强引用持有 Activity 的 adapter（会泄漏），改用弱引用 */
    private static java.lang.ref.WeakReference<HistoryAdapter> sAdapterRef;
    private boolean delMode = false;

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_history;
    }

    @Override
    protected boolean shouldRefreshAutoSize() {
        return true;
    }

    @Override
    protected void init() {
        initView();
        initData();
    }

    private void toggleDelMode() {
    	HawkConfig.hotVodDelete = !HawkConfig.hotVodDelete;
        historyAdapter.notifyDataSetChanged();
        delMode = !delMode;
        tvDelTip.setVisibility(delMode ? View.VISIBLE : View.GONE);        
    }

    private void initView() {
        EventBus.getDefault().register(this);
        tvDelete = findViewById(R.id.tvDelete);
        tvClear = findViewById(R.id.tvClear);
        tvDelTip = findViewById(R.id.tvDelTip);
        mGridView = findViewById(R.id.mGridView);
        mGridView.setHasFixedSize(true);
        mGridView.setLayoutManager(new V7GridLayoutManager(this.mContext, isBaseOnWidth() ? 5 : 6));
        historyAdapter = new HistoryAdapter();
        sAdapterRef = new java.lang.ref.WeakReference<>(historyAdapter);
        mGridView.setAdapter(historyAdapter);
        TvPerf.apply(mGridView);
        tvDelete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleDelMode();
            }
        });
        tvClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ConfirmClearDialog dialog = new ConfirmClearDialog(mContext, "History");
                dialog.show();
            }
        });
        mGridView.setOnInBorderKeyEventListener(new TvRecyclerView.OnInBorderKeyEventListener() {
            @Override
            public boolean onInBorderKeyEvent(int direction, View focused) {
                if (direction == View.FOCUS_UP) {
                    tvDelete.setFocusable(true);
                    tvClear.setFocusable(true);
                    tvDelete.requestFocus();
                }
                return false;
            }
        });
        mGridView.setOnItemListener(new TvRecyclerView.OnItemListener() {
            @Override
            public void onItemPreSelected(TvRecyclerView parent, View itemView, int position) {
                AtvFocusHelper.applyScale(itemView, false);
            }

            @Override
            public void onItemSelected(TvRecyclerView parent, View itemView, int position) {
                AtvFocusHelper.applyScale(itemView, true);
            }

            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {

            }
        });
        historyAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                FastClickCheckUtil.check(view);
                if (position == -1) return;
                VodInfo vodInfo = historyAdapter.getData().get(position);

                if (vodInfo != null) {
                    if (delMode) {
                        historyAdapter.remove(position);
                        RoomDataManger.deleteVodRecord(vodInfo.sourceKey, vodInfo);
                    } else {
                        Bundle bundle = new Bundle();
                        bundle.putString("id", vodInfo.id);
                        bundle.putString("sourceKey", vodInfo.sourceKey);
                        SourceBean sourceBean = ApiConfig.get().getSource(vodInfo.sourceKey);
                        if(sourceBean!=null){
                            bundle.putString("title", vodInfo.name);
                            bundle.putString("picture", vodInfo.pic);
                            jumpActivity(DetailActivity.class, bundle);
                        }else {
                            bundle.putString("title", vodInfo.name);
                            if(Hawk.get(HawkConfig.FAST_SEARCH_MODE, true)){
                                jumpActivity(FastSearchActivity.class, bundle);
                            }else {
                                jumpActivity(SearchActivity.class, bundle);
                            }
                        }
                    }
                }
            }
        });
        historyAdapter.setOnItemLongClickListener(new BaseQuickAdapter.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(BaseQuickAdapter adapter, View view, int position) {
                VodInfo vodInfo = historyAdapter.getData().get(position);
                if (vodInfo != null) {
                    Bundle bundle = new Bundle();
                    bundle.putString("title", vodInfo.name);
                    if (Hawk.get(HawkConfig.FAST_SEARCH_MODE, true)) {
                        jumpActivity(FastSearchActivity.class, bundle);
                    } else {
                        jumpActivity(SearchActivity.class, bundle);
                    }
                }
                return true;
            }
        });
    }

    private void initData() {
        // 主线程同步查 100 条历史在低端盒子上会明显卡；查询移到 IO，回主线程刷列表
        Threads.io(() -> {
            List<VodInfo> allVodRecord;
            try {
                allVodRecord = RoomDataManger.getAllVodRecord(100);
            } catch (Throwable th) {
                allVodRecord = null;
            }
            final List<VodInfo> vodInfoList = new ArrayList<>();
            if (allVodRecord != null) {
                for (VodInfo vodInfo : allVodRecord) {
                    if (vodInfo.playNote != null && !vodInfo.playNote.isEmpty())
                        vodInfo.note = "上次看到" + vodInfo.playNote;
                    vodInfoList.add(vodInfo);
                }
            }
            Threads.main(() -> {
                if (isFinishing() || historyAdapter == null) return;
                historyAdapter.setNewData(vodInfoList);
            });
        });
    }


    @Subscribe(threadMode = ThreadMode.MAIN)
    public void refresh(RefreshEvent event) {
        if (event.type == RefreshEvent.TYPE_HISTORY_REFRESH) {
            initData();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
    }

    @Override
    public void onBackPressed() {
        if (delMode) {
            toggleDelMode();
            return;
        }
        super.onBackPressed();
    }

    /** 历史全清后刷新列表（供 ConfirmClearDialog 调用，避免静态持有 adapter） */
    public static void refreshAfterClear(java.util.List<com.github.tvbox.osc.bean.VodInfo> list) {
        HistoryAdapter a = sAdapterRef == null ? null : sAdapterRef.get();
        if (a == null) return;
        a.setNewData(list);
        a.notifyDataSetChanged();
    }
}
