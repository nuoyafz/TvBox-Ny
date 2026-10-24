package com.github.tvbox.osc.cache;

import android.text.TextUtils;

import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.bean.VodInfo;
import com.github.tvbox.osc.data.AppDataManager;
import com.google.gson.ExclusionStrategy;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.HistoryHelper;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import com.orhanobut.hawk.Hawk;

import android.os.Handler;
import android.os.Looper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author pj567
 * @date :2021/1/7
 * @description:
 */
public class RoomDataManger {
    /** 异步读结果回调，onResult 保证回主线程 */
    public interface Callback<T> {
        void onResult(T result);
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static void post(Runnable r) {
        MAIN.post(r);
    }

    static ExclusionStrategy vodInfoStrategy = new ExclusionStrategy() {
        @Override
        public boolean shouldSkipField(FieldAttributes field) {
            if (field.getDeclaringClass() == VodInfo.class && field.getName().equals("seriesFlags")) {
                return true;
            }
            if (field.getDeclaringClass() == VodInfo.class && field.getName().equals("seriesMap")) {
                return true;
            }
            return false;
        }

        @Override
        public boolean shouldSkipClass(Class<?> clazz) {
            return false;
        }
    };

    private static Gson getVodInfoGson() {
        return new GsonBuilder().addSerializationExclusionStrategy(vodInfoStrategy).create();
    }

    public static void insertVodRecord(String sourceKey, VodInfo vodInfo) {
        // 原来在主线程同步写（allowMainThreadQueries），现在丢后台：调用方从不读返回值，
        // fire-and-forget 语义不变。Gson 序列化 + 可能的 removeSameName 全表扫描都在后台。
        DbExecutors.execute(() -> insertVodRecordSync(sourceKey, vodInfo));
    }

    private static void insertVodRecordSync(String sourceKey, VodInfo vodInfo) {
        VodRecordDao dao = AppDataManager.get().getVodRecordDao();
        if (Hawk.get(HawkConfig.HISTORY_MERGE, false)) {
            removeSameNameVodRecords(dao, sourceKey, vodInfo);
        }
        VodRecord record = dao.getVodRecord(sourceKey, vodInfo.id);
        if (record == null) {
            record = new VodRecord();
        }
        record.sourceKey = sourceKey;
        record.vodId = vodInfo.id;
        record.updateTime = System.currentTimeMillis();
        record.dataJson = getVodInfoGson().toJson(vodInfo);
        dao.insert(record);
    }

    public static VodInfo getVodInfo(String sourceKey, String vodId) {
        VodRecord record = AppDataManager.get().getVodRecordDao().getVodRecord(sourceKey, vodId);
        try {
            if (record != null && record.dataJson != null && !TextUtils.isEmpty(record.dataJson)) {
                VodInfo vodInfo = getVodInfoGson().fromJson(record.dataJson, new TypeToken<VodInfo>() {
                }.getType());
                if (vodInfo.name == null)
                    return null;
                return vodInfo;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static void deleteVodRecord(String sourceKey, VodInfo vodInfo) {
        DbExecutors.execute(() -> deleteVodRecordSync(sourceKey, vodInfo));
    }

    /** 按 vodId 删（不需要完整 VodInfo 对象的场景，如首页"删除这条记录"） */
    public static void deleteVodRecord(String sourceKey, String vodId) {
        DbExecutors.execute(() -> {
            VodRecord record = AppDataManager.get().getVodRecordDao().getVodRecord(sourceKey, vodId);
            if (record != null) {
                AppDataManager.get().getVodRecordDao().delete(record);
            }
        });
    }

    private static void deleteVodRecordSync(String sourceKey, VodInfo vodInfo) {
        VodRecord record = AppDataManager.get().getVodRecordDao().getVodRecord(sourceKey, vodInfo.id);
        if (record != null) {
            AppDataManager.get().getVodRecordDao().delete(record);
        }
    }

    public static List<VodInfo> getAllVodRecord(int limit) {
        VodRecordDao dao = AppDataManager.get().getVodRecordDao();
        Integer index = Hawk.get(HawkConfig.HISTORY_NUM, 0);
        Integer hisNum = HistoryHelper.getHisNum(index);
        List<VodRecord> recordList = dao.getAll(Integer.MAX_VALUE);
        List<VodInfo> vodInfoList = new ArrayList<>();
        boolean historyMerge = Hawk.get(HawkConfig.HISTORY_MERGE, false);
        Set<String> historyNames = historyMerge ? new HashSet<String>() : null;
        if (recordList != null) {
            for (VodRecord record : recordList) {
                VodInfo info = null;
                try {
                    if (record.dataJson != null && !TextUtils.isEmpty(record.dataJson)) {
                        info = getVodInfoGson().fromJson(record.dataJson, new TypeToken<VodInfo>() {
                        }.getType());
                        info.sourceKey = record.sourceKey;
//                        SourceBean sourceBean = ApiConfig.get().getSource(info.sourceKey);
                        if (info.name == null)
                            info = null;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                String name = getVodRecordName(info);
                if (info != null) {
                    if (!historyMerge || TextUtils.isEmpty(name) || historyNames.add(name)) {
                        vodInfoList.add(info);
                    }
                }
            }
        }
        if (dao.getCount() > hisNum) {
            dao.reserver(hisNum);
        }
        int size = Math.min(vodInfoList.size(), Math.min(limit, hisNum));
        return new ArrayList<>(vodInfoList.subList(0, size));
    }

    private static void removeSameNameVodRecords(VodRecordDao dao, String sourceKey, VodInfo vodInfo) {
        String name = getVodRecordName(vodInfo);
        if (TextUtils.isEmpty(name)) return;
        for (VodRecord record : dao.getAll(Integer.MAX_VALUE)) {
            if (TextUtils.equals(sourceKey, record.sourceKey) && TextUtils.equals(vodInfo.id, record.vodId)) {
                continue;
            }
            try {
                VodInfo history = getVodInfoGson().fromJson(record.dataJson, new TypeToken<VodInfo>() {
                }.getType());
                if (TextUtils.equals(name, getVodRecordName(history))) {
                    dao.delete(record);
                }
            } catch (Exception ignored) {
            }
        }
    }

    private static String getVodRecordName(VodInfo vodInfo) {
        return vodInfo == null || vodInfo.name == null ? "" : vodInfo.name.trim();
    }

    public static void insertVodCollect(String sourceKey, VodInfo vodInfo) {
        DbExecutors.execute(() -> insertVodCollectSync(sourceKey, vodInfo));
    }

    private static void insertVodCollectSync(String sourceKey, VodInfo vodInfo) {
        VodCollect record = AppDataManager.get().getVodCollectDao().getVodCollect(sourceKey, vodInfo.id);
        if (record != null) {
            return;
        }
        record = new VodCollect();
        record.sourceKey = sourceKey;
        record.vodId = vodInfo.id;
        record.updateTime = System.currentTimeMillis();
        record.name = vodInfo.name;
        record.pic = vodInfo.pic;
        AppDataManager.get().getVodCollectDao().insert(record);
    }

    public static void deleteVodCollect(int id) {
        DbExecutors.execute(() -> AppDataManager.get().getVodCollectDao().delete(id));
    }

    public static void deleteVodCollect(String sourceKey, VodInfo vodInfo) {
        DbExecutors.execute(() -> deleteVodCollectSync(sourceKey, vodInfo));
    }

    private static void deleteVodCollectSync(String sourceKey, VodInfo vodInfo) {
        VodCollect record = AppDataManager.get().getVodCollectDao().getVodCollect(sourceKey, vodInfo.id);
        if (record != null) {
            AppDataManager.get().getVodCollectDao().delete(record);
        }
    }
    
    public static void deleteVodCollectAll() {
        DbExecutors.execute(() -> AppDataManager.get().getVodCollectDao().deleteAll());
    }

    public static void deleteVodRecordAll() {
        DbExecutors.execute(() -> AppDataManager.get().getVodRecordDao().deleteAll());
    }

    public static boolean isVodCollect(String sourceKey, String vodId) {
        VodCollect record = AppDataManager.get().getVodCollectDao().getVodCollect(sourceKey, vodId);
        return record != null;
    }

    public static List<VodCollect> getAllVodCollect() {
        return AppDataManager.get().getVodCollectDao().getAll();
    }

    // ===== 异步读版本：DB IO 走 DbExecutors，onResult 回主线程 =====
    // 读操作本身仍是原来的同步实现（isVodCollect / getVodInfo / getAllVodCollect /
    // getAllVodRecord），只是包了一层线程 + 主线程回调。异常时回调 null，与同步版
    // 返回 null 的降级路径一致。

    public static void getVodInfoAsync(String sourceKey, String vodId, Callback<VodInfo> cb) {
        DbExecutors.execute(() -> {
            final VodInfo r = getVodInfo(sourceKey, vodId);
            post(() -> cb.onResult(r));
        });
    }

    public static void isVodCollectAsync(String sourceKey, String vodId, Callback<Boolean> cb) {
        DbExecutors.execute(() -> {
            final boolean r = isVodCollect(sourceKey, vodId);
            post(() -> cb.onResult(r));
        });
    }

    public static void getAllVodCollectAsync(Callback<List<VodCollect>> cb) {
        DbExecutors.execute(() -> {
            final List<VodCollect> r = getAllVodCollect();
            post(() -> cb.onResult(r));
        });
    }

    public static void getAllVodRecordAsync(int limit, Callback<List<VodInfo>> cb) {
        DbExecutors.execute(() -> {
            final List<VodInfo> r = getAllVodRecord(limit);
            post(() -> cb.onResult(r));
        });
    }
}
