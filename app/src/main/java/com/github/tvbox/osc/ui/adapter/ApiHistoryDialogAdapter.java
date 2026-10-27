package com.github.tvbox.osc.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.util.HistoryHelper;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class ApiHistoryDialogAdapter extends ListAdapter<String, ApiHistoryDialogAdapter.SelectViewHolder> {

    class SelectViewHolder extends RecyclerView.ViewHolder {

        public SelectViewHolder(@NonNull @NotNull View itemView) {
            super(itemView);
        }
    }

    public interface SelectDialogInterface {
        void click(String value);

        void del(String value, ArrayList<String> data);
    }


    private ArrayList<String> data = new ArrayList<>();

    private String select = "";

    /** 测速结果：key = 原始条目串，value = 展示文案（823ms / 2.4s / 测速中… / 非链接 / 超时） */
    private final java.util.Map<String, String> speedMap = new java.util.HashMap<>();

    private final android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());

    /**
     * 测速专用 client，进程内只建一次。
     * 原来每次点 ⚡ 都 new OkHttpClient.Builder()，一个 client = 一套连接池 + Dispatcher 线程池，
     * 点十几条就残留十几套（旧的不 close），纯浪费。
     */
    private static final okhttp3.OkHttpClient SPEED_CLIENT = new okhttp3.OkHttpClient.Builder()
            .connectTimeout(4, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(6, java.util.concurrent.TimeUnit.SECONDS)
            .build();

    /** 测速线程池：daemon + 固定 4 并发，避免连点十几条时堆出一堆线程 */
    private static final java.util.concurrent.ExecutorService SPEED_POOL =
            java.util.concurrent.Executors.newFixedThreadPool(4, new java.util.concurrent.ThreadFactory() {
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "cfg-speed");
                    t.setDaemon(true);
                    return t;
                }
            });

    private SelectDialogInterface dialogInterface = null;

    public ApiHistoryDialogAdapter(SelectDialogInterface dialogInterface) {
        super(new DiffUtil.ItemCallback<String>() {
            @Override
            public boolean areItemsTheSame(@NonNull @NotNull String oldItem, @NonNull @NotNull String newItem) {
                return oldItem.equals(newItem);
            }

            @Override
            public boolean areContentsTheSame(@NonNull @NotNull String oldItem, @NonNull @NotNull String newItem) {
                return oldItem.equals(newItem);
            }
        });
        this.dialogInterface = dialogInterface;
    }

    public void setData(List<String> newData, int defaultSelect) {
        data.clear();
        data.addAll(newData);
        select = data.get(defaultSelect);
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return data.size();
    }


    @Override
    public ApiHistoryDialogAdapter.SelectViewHolder onCreateViewHolder(@NonNull @NotNull ViewGroup parent, int viewType) {
        return new ApiHistoryDialogAdapter.SelectViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_dialog_api_history, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull @NotNull ApiHistoryDialogAdapter.SelectViewHolder holder, int position) {
        String value = data.get(position);
        // 条目可能带「名称\t地址」前缀；没有分隔符时 getApiLineName 会原样返回地址
        String name = HistoryHelper.getApiLineName(value);
        if (HistoryHelper.isApiLineSource(value))
            name = "[仓] " + name;
        if (select.equals(value))
            name = "√ " + name;
        // 测速结果拼在名字后面，随条目一起刷新
        String speed = speedMap.get(value);
        if (speed != null)
            name = name + " · " + speed;
        ((TextView) holder.itemView.findViewById(R.id.tvName)).setText(name);
        holder.itemView.findViewById(R.id.tvName).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (select.equals(value))
                    return;
                notifyItemChanged(data.indexOf(select));
                select = value;
                notifyItemChanged(data.indexOf(value));
                dialogInterface.click(value);
            }
        });
        holder.itemView.findViewById(R.id.tvSpeed).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startSpeedTest(value);
            }
        });
        holder.itemView.findViewById(R.id.tvDel).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (select.equals(value))
                    return;
                notifyItemRemoved(data.indexOf(value));
                data.remove(value);
                dialogInterface.del(value, data);
            }
        });
    }

    /**
     * 单条配置测速：真实下载配置文件（和 App 加载配置走的是同一个动作），量总耗时。
     * 非链接条目（本地文件/片段仓）标「非链接」；6 秒读超时/4 秒连超按失败算。
     * 不阻塞 UI，结果回来刷对应行。
     */
    private void startSpeedTest(final String value) {
        String testing = speedMap.get(value);
        if ("测速中…".equals(testing)) return;
        String url = HistoryHelper.getApiLineUrl(value).trim();
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            speedMap.put(value, "非链接");
            notifyDataSetChanged();
            return;
        }
        speedMap.put(value, "测速中…");
        notifyDataSetChanged();
        final String fUrl = url;
        SPEED_POOL.execute(new Runnable() {
            @Override
            public void run() {
                String result;
                long t0 = System.currentTimeMillis();
                try {
                    okhttp3.Response resp = SPEED_CLIENT.newCall(
                            new okhttp3.Request.Builder().url(fUrl).build()).execute();
                    // 配置要整个下下来才能用，所以量到 body 读完为止，跟真实加载口径一致
                    String body = resp.body() == null ? "" : resp.body().string();
                    long ms = System.currentTimeMillis() - t0;
                    if (resp.isSuccessful() && body.length() > 0) {
                        result = ms < 1000 ? ms + "ms" : String.format("%.1fs", ms / 1000.0);
                    } else if (!resp.isSuccessful()) {
                        result = "HTTP " + resp.code();
                    } else {
                        result = "空响应";
                    }
                    resp.close();
                } catch (Throwable t) {
                    result = "超时/失败";
                }
                final String fResult = result;
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        speedMap.put(value, fResult);
                        notifyDataSetChanged();
                    }
                });
            }
        });
    }
}
