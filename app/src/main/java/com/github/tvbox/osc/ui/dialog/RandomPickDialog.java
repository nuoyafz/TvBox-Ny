package com.github.tvbox.osc.ui.dialog;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.lifecycle.Observer;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.AbsXml;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.bean.MovieSort;
import com.github.tvbox.osc.util.ImgUtil;
import com.github.tvbox.osc.viewmodel.SourceViewModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.HashSet;
import java.util.Set;

import me.jessyan.autosize.utils.AutoSizeUtils;

/** TV remote friendly roulette for choosing something from the visible home recommendations. */
public class RandomPickDialog extends BaseDialog {
    public interface OnPickListener {
        void onPick(Movie.Video video);
    }

    private final List<Movie.Video> videos;
    private final List<MovieSort.SortData> sourceCategories;
    private final List<MovieSort.SortData> movieCategories = new ArrayList<>();
    private final List<MovieSort.SortData> seriesCategories = new ArrayList<>();
    private final OnPickListener listener;
    private final Random random = new Random();
    private final Set<String> attemptedCategoryIds = new HashSet<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (!spinning) return;
            List<Movie.Video> pool = mode == 0 ? videos : (categoryLoaded ? activeVideos : null);
            if (pool == null || pool.isEmpty()) {
                tvTitle.setText("正在读取「" + selectedCategoryName + "」");
            } else {
                Movie.Video video = pool.get(random.nextInt(pool.size()));
                tvTitle.setText(video.name == null ? "正在为你抽选" : video.name);
            }
            handler.postDelayed(this, 75);
        }
    };

    private TextView tvTitle;
    private TextView tvState;
    private TextView tvMeta;
    private TextView btnPlay;
    private TextView btnRecommend;
    private TextView btnMovies;
    private TextView btnSeries;
    private ImageView poster;
    private RandomWheelView wheel;
    private Movie.Video selected;
    private Movie.Video lastSelected;
    private boolean spinning;
    private boolean waitingForCategory;
    private boolean animationDone;
    private boolean categoryLoaded;
    private int mode;
    private String selectedCategoryName;
    private List<Movie.Video> activeVideos;
    private SourceViewModel categoryViewModel;
    private Observer<AbsXml> categoryObserver;
    private final Runnable categoryTimeout = () -> retryCategory("分类响应较慢");

    public RandomPickDialog(@NonNull Context context, List<Movie.Video> videos,
                            List<MovieSort.SortData> categories, OnPickListener listener) {
        super(context);
        this.videos = new ArrayList<>(videos);
        this.activeVideos = this.videos;
        this.sourceCategories = categories == null ? new ArrayList<>() : new ArrayList<>(categories);
        classifyCategories();
        mode = !this.videos.isEmpty() ? 0 : !movieCategories.isEmpty() ? 1 : !seriesCategories.isEmpty() ? 2 : 0;
        selectedCategoryName = mode == 0 ? "首页推荐" : mode == 1 ? "电影" : "电视剧";
        this.listener = listener;
        setContentView(R.layout.dialog_random_pick);
        setCanceledOnTouchOutside(true);
        tvTitle = findViewById(R.id.tvPickTitle);
        tvSubtitle = findViewById(R.id.tvPickSubtitle);
        tvState = findViewById(R.id.tvPickState);
        tvMeta = findViewById(R.id.tvPickMeta);
        btnPlay = findViewById(R.id.btnPickPlay);
        btnRecommend = findViewById(R.id.btnPickRecommend);
        btnMovies = findViewById(R.id.btnPickMovies);
        btnSeries = findViewById(R.id.btnPickSeries);
        poster = findViewById(R.id.ivPickPoster);
        wheel = findViewById(R.id.pickWheel);
        TextView again = findViewById(R.id.btnPickAgain);
        btnRecommend.setNextFocusDownId(R.id.btnPickAgain);
        btnMovies.setNextFocusDownId(R.id.btnPickAgain);
        btnSeries.setNextFocusDownId(R.id.btnPickAgain);
        again.setNextFocusUpId(R.id.btnPickRecommend);
        again.setNextFocusRightId(R.id.btnPickPlay);
        btnPlay.setNextFocusLeftId(R.id.btnPickAgain);
        again.setOnClickListener(v -> spin());
        btnRecommend.setOnClickListener(v -> setMode(0));
        btnMovies.setOnClickListener(v -> setMode(1));
        btnSeries.setOnClickListener(v -> setMode(2));
        btnMovies.setEnabled(!movieCategories.isEmpty());
        btnSeries.setEnabled(!seriesCategories.isEmpty());
        btnRecommend.setEnabled(!this.videos.isEmpty());
        btnRecommend.setFocusable(!this.videos.isEmpty());
        btnMovies.setFocusable(!movieCategories.isEmpty());
        btnSeries.setFocusable(!seriesCategories.isEmpty());
        btnMovies.setText("电影　" + movieCategories.size() + "类");
        btnSeries.setText("电视剧　" + seriesCategories.size() + "类");
        if (movieCategories.isEmpty()) btnMovies.setAlpha(0.42f);
        if (seriesCategories.isEmpty()) btnSeries.setAlpha(0.42f);
        if (this.videos.isEmpty()) btnRecommend.setAlpha(0.42f);
        setMode(mode);
        btnPlay.setOnClickListener(v -> {
            if (selected != null && listener != null) {
                Movie.Video result = selected;
                dismiss();
                listener.onPick(result);
            }
        });
        setOnShowListener(dialog -> {
            Window window = getWindow();
            if (window != null) {
                window.setBackgroundDrawableResource(android.R.color.transparent);
                window.setLayout(AutoSizeUtils.mm2px(context, 820), WindowManager.LayoutParams.WRAP_CONTENT);
            }
            View initialFocus = mode == 0 ? btnRecommend : mode == 1 ? btnMovies : btnSeries;
            initialFocus.post(initialFocus::requestFocus);
        });
        setOnDismissListener(dialog -> {
            spinning = false;
            handler.removeCallbacksAndMessages(null);
            wheel.animate().setListener(null).cancel();
            detachCategoryObserver();
        });
    }

    private void spin() {
        if (spinning || (mode == 0 && videos.isEmpty())) return;
        spinning = true;
        attemptedCategoryIds.clear();
        Movie.Video previous = lastSelected;
        selected = null;
        animationDone = false;
        categoryLoaded = mode == 0;
        waitingForCategory = mode != 0;
        btnPlay.setVisibility(View.GONE);
        tvState.setText("正在抽选");
        tvMeta.setText(mode == 0 ? "正在从首页推荐中抽选…" : "正在读取当前源的" + (mode == 1 ? "电影" : "电视剧") + "分类…");
        poster.setImageDrawable(null);
        if (mode == 0) {
            activeVideos = videos;
            selectedCategoryName = "首页推荐";
        } else {
            try {
                requestSourceCategory();
            } catch (Throwable error) {
                com.github.tvbox.osc.util.LOG.e("Random category request failed: " + error);
                failCategory("当前源暂时无法读取分类，再试一次吧");
                return;
            }
        }
        handler.removeCallbacks(ticker);
        handler.post(ticker);
        float turn = 1440f + random.nextInt(720);
        // Clear the old listener before cancel: ViewPropertyAnimator can report end on cancel,
        // which otherwise would instantly finish a second spin.
        wheel.animate().setListener(null).cancel();
        wheel.animate().rotationBy(turn).setDuration(2600)
                .setInterpolator(new DecelerateInterpolator())
                .setListener(new AnimatorListenerAdapter() {
                    @Override public void onAnimationEnd(Animator animation) {
                        if (!spinning || !isShowing()) return;
                        animationDone = true;
                        if (categoryLoaded) finishSpin(previous);
                        else {
                            tvState.setText("正在读取" + selectedCategoryName);
                            tvMeta.setText("正在从当前片源获取这个分类的片单…");
                        }
                    }
                }).start();
    }

    private void classifyCategories() {
        for (MovieSort.SortData category : sourceCategories) {
            if (category == null || category.id == null || category.name == null
                    || "my0".equals(category.id)) continue;
            String value = (category.name + " " + category.flag).toLowerCase();
            if (containsAny(value, "剧场版", "电影", "影片", "movie", "film", "片")) {
                movieCategories.add(category);
            } else if (containsAny(value, "电视剧", "连续剧", "剧集", "短剧", "series", "tv", "动漫", "番剧", "剧")) {
                seriesCategories.add(category);
            }
        }
    }

    private boolean containsAny(String value, String... keys) {
        for (String key : keys) if (value.contains(key)) return true;
        return false;
    }

    private void setMode(int selectedMode) {
        if (spinning) return;
        if ((selectedMode == 0 && videos.isEmpty())
                || (selectedMode == 1 && movieCategories.isEmpty())
                || (selectedMode == 2 && seriesCategories.isEmpty())) {
            Toast.makeText(getContext(), "当前源没有可识别的这个分类", Toast.LENGTH_SHORT).show();
            return;
        }
        mode = selectedMode;
        btnRecommend.setSelected(mode == 0);
        btnMovies.setSelected(mode == 1);
        btnSeries.setSelected(mode == 2);
        btnRecommend.setTextColor(mode == 0 ? com.github.tvbox.osc.util.ThemeManager.accent() : 0xFFABB4AF);
        btnMovies.setTextColor(mode == 1 ? com.github.tvbox.osc.util.ThemeManager.accent() : 0xFFABB4AF);
        btnSeries.setTextColor(mode == 2 ? com.github.tvbox.osc.util.ThemeManager.accent() : 0xFFABB4AF);
        selectedCategoryName = mode == 0 ? "首页推荐" : mode == 1 ? "电影" : "电视剧";
        tvSubtitle.setText(mode == 0
                ? "从首页推荐片单中，替你挑一部今晚想看的"
                : "按当前源分类抽取 · " + (mode == 1 ? movieCategories.size() : seriesCategories.size()) + " 个" + selectedCategoryName + "分类");
        tvTitle.setText("准备好，转一转");
        tvState.setText(mode == 0 ? "首页推荐" : "已选" + selectedCategoryName);
        tvMeta.setText(mode == 0 ? "按确认开始 · 抽中后进入影片详情" : "将随机挑选源内分类，再从片单中抽一部");
        poster.setImageDrawable(null);
        btnPlay.setVisibility(View.GONE);
    }

    private TextView tvSubtitle;

    private void requestSourceCategory() {
        List<MovieSort.SortData> categories = mode == 1 ? movieCategories : seriesCategories;
        List<MovieSort.SortData> available = new ArrayList<>();
        for (MovieSort.SortData item : categories) {
            if (item != null && item.id != null && !attemptedCategoryIds.contains(item.id)) available.add(item);
        }
        if (available.isEmpty()) {
            failCategory("已试过当前源的可用分类，暂时没有片单");
            return;
        }
        MovieSort.SortData category = available.get(random.nextInt(available.size()));
        attemptedCategoryIds.add(category.id);
        selectedCategoryName = category.name;
        tvState.setText("正在抽取「" + category.name + "」");
        MovieSort.SortData request = new MovieSort.SortData(category.id, category.name);
        categoryViewModel = new SourceViewModel();
        categoryObserver = result -> {
            if (!waitingForCategory || !spinning) return;
            waitingForCategory = false;
            handler.removeCallbacks(categoryTimeout);
            List<Movie.Video> loaded = new ArrayList<>();
            if (result != null && result.movie != null && result.movie.videoList != null) {
                for (Movie.Video video : result.movie.videoList) {
                    if (isPlayable(video)) loaded.add(video);
                }
            }
            detachCategoryObserver();
            if (loaded.isEmpty()) {
                retryCategory("「" + selectedCategoryName + "」暂时没有片单");
                return;
            }
            activeVideos = loaded;
            categoryLoaded = true;
            if (animationDone) finishSpin(lastSelected);
        };
        categoryViewModel.listResult.observeForever(categoryObserver);
        handler.postDelayed(categoryTimeout, 18000);
        categoryViewModel.getList(request, 1);
    }

    private void retryCategory(String reason) {
        if (!spinning) return;
        waitingForCategory = false;
        handler.removeCallbacks(categoryTimeout);
        detachCategoryObserver();
        int limit = Math.min(3, mode == 1 ? movieCategories.size() : seriesCategories.size());
        if (attemptedCategoryIds.size() >= limit) {
            failCategory(reason + "，已尝试多个分类");
            return;
        }
        tvState.setText("换个分类再试…");
        tvMeta.setText(reason + "，正在为你换一个分类");
        handler.postDelayed(() -> {
            if (!spinning) return;
            try {
                requestSourceCategory();
            } catch (Throwable error) {
                com.github.tvbox.osc.util.LOG.e("Random category retry failed: " + error);
                failCategory("当前源暂时无法读取分类，再试一次吧");
            }
        }, 320);
    }

    private boolean isPlayable(Movie.Video video) {
        return video != null && video.id != null && !video.id.isEmpty()
                && video.sourceKey != null && !video.sourceKey.isEmpty()
                && video.action == null && !video.id.startsWith("msearch:");
    }

    private void finishSpin(Movie.Video previous) {
        if (!spinning || activeVideos == null || activeVideos.isEmpty()) return;
        spinning = false;
        waitingForCategory = false;
        handler.removeCallbacks(ticker);
        handler.removeCallbacks(categoryTimeout);
        selected = chooseDifferent(activeVideos, previous);
        lastSelected = selected;
        showResult(selected);
    }

    private void failCategory(String message) {
        if (!spinning) return;
        spinning = false;
        waitingForCategory = false;
        handler.removeCallbacks(ticker);
        handler.removeCallbacks(categoryTimeout);
        wheel.animate().setListener(null).cancel();
        detachCategoryObserver();
        tvState.setText("暂时没抽到");
        tvMeta.setText(message);
        tvTitle.setText("再试一次吧");
        findViewById(R.id.btnPickAgain).requestFocus();
    }

    private void detachCategoryObserver() {
        if (categoryViewModel != null && categoryObserver != null) {
            categoryViewModel.listResult.removeObserver(categoryObserver);
        }
        categoryObserver = null;
        categoryViewModel = null;
    }

    private Movie.Video chooseDifferent(List<Movie.Video> pool, Movie.Video previous) {
        if (pool.size() == 1) return pool.get(0);
        Movie.Video result = pool.get(random.nextInt(pool.size()));
        for (int i = 0; i < 8 && sameVideo(result, previous); i++) result = pool.get(random.nextInt(pool.size()));
        return result;
    }

    private boolean sameVideo(Movie.Video left, Movie.Video right) {
        return left != null && right != null && left.id != null && left.id.equals(right.id)
                && left.sourceKey != null && left.sourceKey.equals(right.sourceKey);
    }

    private void showResult(Movie.Video result) {
        if (result == null) return;
        tvTitle.setText(result.name == null ? "为你选好了" : result.name);
        tvState.setText("今晚就看这部");
        tvMeta.setText("来自「" + selectedCategoryName + "」 · 不合口味可再转一次");
        if (result.pic != null && !result.pic.trim().isEmpty()) {
            ImgUtil.load(result.pic, poster, AutoSizeUtils.mm2px(getContext(), 6),
                    AutoSizeUtils.mm2px(getContext(), 92), AutoSizeUtils.mm2px(getContext(), 92), result.name);
        }
        btnPlay.setVisibility(View.VISIBLE);
        btnPlay.requestFocus();
    }

}
