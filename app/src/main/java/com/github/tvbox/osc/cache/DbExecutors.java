package com.github.tvbox.osc.cache;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Room 数据库专用线程池。
 *
 * AppDataManager 原来开着 allowMainThreadQueries()，所有 insert/query/delete 都跑在
 * 主线程：详情页每次打开 2~3 次主线程 IO，收藏/历史页打开时一次全表查询 + 逐条
 * Gson 反序列化，记录几百条时卡几十毫秒。现在统一走这里：
 * - 单线程串行（Room 写冲突最小化，且与原来"主线程串行"语义一致）
 * - core=0 + keepAlive=30s：空闲自动回收，不常驻线程
 * - 无界队列：DB 操作都是短任务，不会堆积
 */
public class DbExecutors {
    private DbExecutors() {
    }

    private static final ExecutorService IO = new ThreadPoolExecutor(
            0, 1, 30L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<Runnable>(),
            new ThreadFactory() {
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "tvbox-db-io");
                    t.setPriority(Thread.NORM_PRIORITY - 1);
                    return t;
                }
            });

    public static void execute(Runnable r) {
        IO.execute(r);
    }
}
