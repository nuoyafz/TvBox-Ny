package com.github.tvbox.osc.util;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 全局线程池 + 主线程回调的唯一出口。
 *
 * 为什么要收口：
 * - Room 的同步查询跑在主线程等于主线程做磁盘 IO，低端电视盒子上直接卡顿甚至 ANR；
 * - 业务代码里散落着几十处 newSingleThreadExecutor / newFixedThreadPool，
 *   没有统一生命周期，Activity 销毁后任务还在跑，线程一多盒子就抢不到 CPU。
 *
 * 使用约定：
 * - 数据库 / 文件操作用 {@link #io(Runnable)}（单线程串行，避免 SQLite 并发锁）；
 * - 解析 / 搜索 / 体检等可并发的网络前置任务用 {@link #bg(Runnable)}（固定 4 线程）；
 * - 需要回到界面时在后台任务末尾调 {@link #main(Runnable)}，并在里面判 isAdded()/isFinishing()。
 */
public final class Threads {

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    /** 数据库 / 文件：单线程串行，SQLite 并发写会锁冲突，串行反而更快 */
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(daemon("noya-io"));

    /** 可并发的后台任务：固定 4 线程，电视盒子多为 2~4 核，开多了只会互相抢 */
    private static final ExecutorService BG = Executors.newFixedThreadPool(4, daemon("noya-bg"));

    private Threads() {
    }

    private static ThreadFactory daemon(final String prefix) {
        final AtomicInteger seq = new AtomicInteger(0);
        return r -> {
            Thread t = new Thread(r, prefix + "-" + seq.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }

    /** 提交一个数据库 / 文件任务（串行执行，异常只记日志不中断线程池） */
    public static void io(Runnable task) {
        if (task == null) return;
        IO.execute(safe(task));
    }

    /** 提交一个可并发的后台任务 */
    public static void bg(Runnable task) {
        if (task == null) return;
        BG.execute(safe(task));
    }

    /** 切回主线程执行 */
    public static void main(Runnable ui) {
        if (ui == null) return;
        if (Looper.myLooper() == Looper.getMainLooper()) {
            ui.run();
        } else {
            MAIN.post(ui);
        }
    }

    /** 后台任务抛异常不应该连累线程池：这里的异常吞掉，但一定写日志，否则真机问题无法定位 */
    private static Runnable safe(final Runnable task) {
        return () -> {
            try {
                task.run();
            } catch (Throwable th) {
                LOG.longE("noya-thread", "background task failed: " + th.getClass().getSimpleName() + " " + th.getMessage());
            }
        };
    }
}
