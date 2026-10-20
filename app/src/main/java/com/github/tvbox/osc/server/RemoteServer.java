package com.github.tvbox.osc.server;

import static com.github.tvbox.osc.util.RegexUtils.getPattern;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Base64;
import android.view.KeyEvent;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.bean.VodInfo;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.event.ServerEvent;
import com.github.tvbox.osc.receiver.PushReceiver;
import com.github.tvbox.osc.util.FileUtils;
import com.github.tvbox.osc.util.LOG;
import com.github.tvbox.osc.util.OkGoHelper;
import com.github.tvbox.osc.util.Proxy;
import com.google.gson.JsonArray;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import org.greenrobot.eventbus.EventBus;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import fi.iki.elonen.NanoHTTPD;

/**
 * @author pj567
 * @date :2021/1/5
 * @description:
 */
public class RemoteServer extends NanoHTTPD {
    private Context mContext;
    public static int serverPort = 9978;
    private boolean isStarted = false;
    private DataReceiver mDataReceiver;
    public static String m3u8Content;
    private ArrayList<RequestProcess> getRequestList = new ArrayList<>();
    private ArrayList<RequestProcess> postRequestList = new ArrayList<>();

    public RemoteServer(int port, Context context) {
        super(port);
        mContext = context;
        addGetRequestProcess();
        addPostRequestProcess();
    }

    private void addGetRequestProcess() {
        getRequestList.add(new RawRequestProcess(this.mContext, "/", R.raw.index, NanoHTTPD.MIME_HTML));
        getRequestList.add(new RawRequestProcess(this.mContext, "/index.html", R.raw.index, NanoHTTPD.MIME_HTML));
        getRequestList.add(new RawRequestProcess(this.mContext, "/style.css", R.raw.style, "text/css"));
        getRequestList.add(new RawRequestProcess(this.mContext, "/ui.css", R.raw.ui, "text/css"));
        getRequestList.add(new RawRequestProcess(this.mContext, "/jquery.js", R.raw.jquery, "application/x-javascript"));
        getRequestList.add(new RawRequestProcess(this.mContext, "/script.js", R.raw.script, "application/x-javascript"));
        getRequestList.add(new RawRequestProcess(this.mContext, "/favicon.ico", R.drawable.app_icon, "image/x-icon"));
        // 手机端播放页：扫码打开的极简播放器，地址形如 http://<电视IP>:9978/m?u=<base64>&t=<片名>
        getRequestList.add(new RawRequestProcess(this.mContext, "/m", R.raw.mobile, NanoHTTPD.MIME_HTML));
        // 同一个 index.html 挂三个入口名，前端靠 URL 里的文件名决定落在哪个 tab：
        // push.html → 推送页，api.html → 文件/接口页，remote.html → 遥控页（摇杆+语音）
        getRequestList.add(new RawRequestProcess(this.mContext, "/push.html", R.raw.index, NanoHTTPD.MIME_HTML));
        getRequestList.add(new RawRequestProcess(this.mContext, "/api.html", R.raw.index, NanoHTTPD.MIME_HTML));
        getRequestList.add(new RawRequestProcess(this.mContext, "/remote.html", R.raw.index, NanoHTTPD.MIME_HTML));
        // hls.js 随包内置，不依赖外网 CDN（国内 CDN 时通时不通，扫码打不开很致命）
        getRequestList.add(new RawRequestProcess(this.mContext, "/hls.js", R.raw.hls, "application/x-javascript"));
        // PWA：加到主屏后按独立 App 打开，没有地址栏
        getRequestList.add(new RawRequestProcess(this.mContext, "/manifest.webmanifest", R.raw.manifest, "application/manifest+json"));
        getRequestList.add(new ImageRequestProcess(this.mContext, "/icon-192.png", R.drawable.app_icon, "image/png"));
        getRequestList.add(new ImageRequestProcess(this.mContext, "/icon-512.png", R.drawable.app_icon, "image/png"));
        getRequestList.add(new CacheRequestProcess());
    }

    private void addPostRequestProcess() {
        postRequestList.add(new InputRequestProcess(this));
        postRequestList.add(new CacheRequestProcess());
    }

    @Override
    public void start(int timeout, boolean daemon) throws IOException {
        isStarted = true;
        super.start(timeout, daemon);
        EventBus.getDefault().post(new ServerEvent(ServerEvent.SERVER_SUCCESS));
    }

    @Override
    public void stop() {
        super.stop();
        isStarted = false;
    }

    private Response getProxy(Object[] rs){
        try {
            if (rs == null || rs.length < 3) {
                LOG.e("echo-proxy error: empty proxy result");
                return NanoHTTPD.newFixedLengthResponse(Response.Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT, "500");
            }
            if (rs[0] instanceof NanoHTTPD.Response) return (NanoHTTPD.Response) rs[0];
            int code = (int) rs[0];
            String mime = (String) rs[1];
            InputStream stream = rs[2] != null ? (InputStream) rs[2] : null;
            Response response = NanoHTTPD.newChunkedResponse(
                    Response.Status.lookup(code),
                    mime,
                    stream
            );
            // 添加头部信息
            if (rs.length >= 4 && rs[3] instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, String> mapHeader = (Map<String, String>) rs[3];
                if(!mapHeader.isEmpty()){
                    for (String key : mapHeader.keySet()) {
                        response.addHeader(key, mapHeader.get(key));
                    }
                }
            }
            return response;
        } catch (Throwable th) {
            LOG.e("echo-proxy error: " + th.getMessage());
            return NanoHTTPD.newFixedLengthResponse(Response.Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT, "500");
        }
    }

    @Override
    public Response serve(IHTTPSession session) {
        EventBus.getDefault().post(new ServerEvent(ServerEvent.SERVER_CONNECTION));
        if (!session.getUri().isEmpty()) {
            String fileName = session.getUri().trim();
            if (fileName.indexOf('?') >= 0) {
                fileName = fileName.substring(0, fileName.indexOf('?'));
            }
            if (session.getMethod() == Method.GET) {
                if (isProxyRequest(fileName, session.getParms())) {
                    return handleProxy(session);
                }
                for (RequestProcess process : getRequestList) {
                    if (process.isRequest(session, fileName)) {
                        return process.doResponse(session, fileName, session.getParms(), null);
                    }
                }
                if (fileName.startsWith("/file/")) {
                    try {
                        String f = fileName.substring(6);
                        String root = Environment.getExternalStorageDirectory().getAbsolutePath();
                        String file = root + "/" + f;
                        File localFile = new File(file);
                        if (localFile.exists()) {
                            if (localFile.isFile()) {
                                return NanoHTTPD.newChunkedResponse(NanoHTTPD.Response.Status.OK, "application/octet-stream", new FileInputStream(localFile));
                            } else {
                                return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, NanoHTTPD.MIME_PLAINTEXT, fileList(root, f));
                            }
                        } else {
                            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT, "File " + file + " not found!");
                        }
                    } catch (Throwable th) {
                        return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT, th.getMessage());
                    }
                } else if (fileName.equals("/dns-query")) {
                    String name = session.getParms().get("name");
                    byte[] rs = null;
                    try {
                        rs = OkGoHelper.dnsOverHttps.lookupHttpsForwardSync(name);
                    } catch (Throwable th) {
                        rs = new byte[0];
                    }
                    return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/dns-message", new ByteArrayInputStream(rs), rs.length);
                } else if (fileName.startsWith("/push/")) {
                    String url = fileName.substring(6);
                    if (url.startsWith("b64:")) {
                        try {
                            url = new String(Base64.decode(url.substring(4), Base64.DEFAULT | Base64.URL_SAFE | Base64.NO_WRAP), "UTF-8");
                        } catch (UnsupportedEncodingException e) {
                            e.printStackTrace();
                        }
                    } else {
                        url = URLDecoder.decode(url);
                    }
                    PushReceiver.send(mContext, url);
                    return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, NanoHTTPD.MIME_PLAINTEXT, "ok");    
                } else if (fileName.equals("/action")) {
                    return handleAction(session.getParms());
                } else if (fileName.equals("/media")) {
                    return handleMedia();
                }  else if (fileName.startsWith("/proxyM3u8")) {
//                    com.github.tvbox.osc.util.LOG.i("echo-proxyM3u8 length:" + (m3u8Content == null ? 0 : m3u8Content.length()));
                    return NanoHTTPD.newFixedLengthResponse(Response.Status.OK, "application/vnd.apple.mpegurl", m3u8Content == null ? "" : m3u8Content);
                }
                 else if (fileName.startsWith("/dash/")) {
                    String dashData = App.getInstance().getDashData();
                    try {
                        String data = new String(Base64.decode(dashData, Base64.DEFAULT | Base64.NO_WRAP), "UTF-8");
                        return NanoHTTPD.newFixedLengthResponse(
                                Response.Status.OK,
                                "application/dash+xml",
                                data
                        );
                    } catch (Throwable th) {
                        return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT, dashData);
                    }
                }
            } else if (session.getMethod() == Method.POST) {
                Map<String, String> files = new HashMap<String, String>();
                try {
                    if (session.getHeaders().containsKey("content-type")) {
                        String hd = session.getHeaders().get("content-type");
                        if (hd != null) {
                            // cuke: 修正中文乱码问题
                            if (hd.toLowerCase().contains("multipart/form-data") && !hd.toLowerCase().contains("charset=")) {
                                Matcher matcher = getPattern("[ |\t]*(boundary[ |\t]*=[ |\t]*['|\"]?[^\"^'^;^,]*['|\"]?)", Pattern.CASE_INSENSITIVE).matcher(hd);
                                String boundary = matcher.find() ? matcher.group(1) : null;
                                if (boundary != null) {
                                    session.getHeaders().put("content-type", "multipart/form-data; charset=utf-8; " + boundary);
                                }
                            }
                        }
                    }
                    session.parseBody(files);
                } catch (IOException IOExc) {
                    return createPlainTextResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, "SERVER INTERNAL ERROR: IOException: " + IOExc.getMessage());
                } catch (NanoHTTPD.ResponseException rex) {
                    return createPlainTextResponse(rex.getStatus(), rex.getMessage());
                }
                for (RequestProcess process : postRequestList) {
                    if (process.isRequest(session, fileName)) {
                        return process.doResponse(session, fileName, session.getParms(), files);
                    }
                }
                try {
                    Map<String, String> params = session.getParms();
                    if (fileName.equals("/upload")) {
                        String path = params.get("path");
                        for (String k : files.keySet()) {
                            if (k.startsWith("files-")) {
                                String fn = params.get(k);
                                String tmpFile = files.get(k);
                                File tmp = new File(tmpFile);
                                String root = Environment.getExternalStorageDirectory().getAbsolutePath();
                                File file = new File(root + "/" + path + "/" + fn);
                                if (file.exists())
                                    file.delete();
                                if (tmp.exists()) {
                                    if (fn.toLowerCase().endsWith(".zip")) {
                                        unzip(tmp, root + "/" + path);
                                    } else {
                                        FileUtils.copyFile(tmp, file);
                                    }
                                }
                                if (tmp.exists())
                                    tmp.delete();
                            }
                        }
                        return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, NanoHTTPD.MIME_PLAINTEXT, "OK");
                    } else if (fileName.equals("/newFolder")) {
                        String path = params.get("path");
                        String name = params.get("name");
                        String root = Environment.getExternalStorageDirectory().getAbsolutePath();
                        File file = new File(root + "/" + path + "/" + name);
                        if (!file.exists()) {
                            file.mkdirs();
                            File flag = new File(root + "/" + path + "/" + name + "/.tvbox_folder");
                            if (!flag.exists())
                                flag.createNewFile();
                        }
                        return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, NanoHTTPD.MIME_PLAINTEXT, "OK");
                    } else if (fileName.equals("/delFolder")) {
                        String path = params.get("path");
                        String root = Environment.getExternalStorageDirectory().getAbsolutePath();
                        File file = new File(root + "/" + path);
                        if (file.exists()) {
                            FileUtils.recursiveDelete(file);
                        }
                        return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, NanoHTTPD.MIME_PLAINTEXT, "OK");
                    } else if (fileName.equals("/delFile")) {
                        String path = params.get("path");
                        String root = Environment.getExternalStorageDirectory().getAbsolutePath();
                        File file = new File(root + "/" + path);
                        if (file.exists()) {
                            file.delete();
                        }
                        return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, NanoHTTPD.MIME_PLAINTEXT, "OK");
                    }
                } catch (Throwable th) {
                    return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, NanoHTTPD.MIME_PLAINTEXT, "OK");
                }
            }
        }
        //default page: index.html
        return getRequestList.get(0).doResponse(session, "", null, null);
    }

    private boolean isProxyRequest(String fileName, Map<String, String> params) {
        if (params == null) return false;
        if (!params.containsKey("do") && !params.containsKey("go")) return false;
        return fileName.equals("/proxy") || fileName.equals("/");
    }

    private Response handleProxy(IHTTPSession session) {
        Map<String, String> params = session.getParms();
        params.putAll(session.getHeaders());
        Object[] rs;
        if (params.containsKey("do")) {
            boolean isDanmuProxy = "danmu".equals(params.get("do"));
            if (isDanmuProxy) normalizeDanmuParams(params);
            if (isDanmuProxy) LOG.i("echo-proxy-danmu params: " + params.toString());
            rs = ApiConfig.get().proxyLocal(params);
        } else if (params.containsKey("go")) {
            rs = Proxy.proxy(params);
        } else {
            rs = null;
        }
        return rewriteLoopbackForRemote(getProxy(rs), session);
    }

    /**
     * 手机扫码播放时，手机是拿「电视的局域网地址」来访问代理的。
     *
     * 但代理吐出来的 m3u8 里，分片地址全是写死的 http://127.0.0.1:9978/proxy?do=ts&...——
     * 这个地址在手机上指向手机自己，分片全部拉不到，表现为「黑屏 / 一直转」。
     *
     * 所以只要是外部设备来取的 m3u8，就把响应体里的回环地址替换成它实际请求用的 Host。
     * 电视自己播放时 Host 就是 127.0.0.1，这里直接原样返回，不影响现有播放链路。
     */
    private Response rewriteLoopbackForRemote(Response response, IHTTPSession session) {
        Map<String, String> headers = session.getHeaders();
        String host = headers == null ? null : headers.get("host");
        if (host == null || host.isEmpty()) return response;
        if (host.startsWith("127.0.0.1") || host.startsWith("localhost")) return response;
        String mime = response.getMimeType();
        if (mime == null || !mime.contains("mpegurl")) return response; // 只改 m3u8，ts 分片不碰
        try {
            InputStream in = response.getData();
            if (in == null) return response;
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) != -1) bos.write(buffer, 0, len);
            String body = new String(bos.toByteArray(), "UTF-8");
            String fixed = body.replaceAll("https?://(127\\.0\\.0\\.1|localhost)(:\\d+)?", "http://" + host);
            byte[] out = fixed.getBytes("UTF-8");
            return NanoHTTPD.newFixedLengthResponse(
                    Response.Status.lookup(response.getStatus().getRequestStatus()),
                    mime, new ByteArrayInputStream(out), out.length);
        } catch (Throwable th) {
            LOG.e("echo-mobile m3u8 rewrite failed: " + th.getMessage());
            // 流已经被读掉了，不能再把原响应返回去，给个明确的错误比黑屏好
            return createPlainTextResponse(Response.Status.INTERNAL_ERROR, "m3u8 rewrite failed");
        }
    }

    private Response handleAction(Map<String, String> params) {
        if (params == null) return createPlainTextResponse(Response.Status.OK, "ok");
        String action = params.get("do");
        if ("refresh".equals(action)) {
            handleRefreshAction(params);
        } else if ("key".equals(action)) {
            // 返回执行回执，网页端直接显示，遥控不通时一眼定位在哪一环
            return createPlainTextResponse(Response.Status.OK, handleKeyAction(params));
        }
        return createPlainTextResponse(Response.Status.OK, "ok");
    }

    /**
     * 手机遥控：把网页上的方向键/OK/返回等按键派发成真实的 KeyEvent 给当前界面。
     *
     * 直接用 Activity#dispatchKeyEvent 而不是 Instrumentation：
     * 1) 不需要任何权限，targetSdk 限制下也能用；
     * 2) dispatchKeyEvent 会走完整的 onKeyDown/onKeyUp 链路，
     *    首页 ViewPager、GridView 的焦点移动逻辑全都能正常响应。
     * 每按一次发 down + up 一对，长按连发交给网页端定时重复请求实现。
     */
    /* package 可见：POST /action 的 key 分支由 InputRequestProcess 调用（前端 $.post 走 POST）。
       返回执行回执文本：no-activity / bad-code / sent:<code>:<Activity名> */
    String handleKeyAction(Map<String, String> params) {
        final int code;
        try {
            code = Integer.parseInt(String.valueOf(params.get("code")).trim());
        } catch (Throwable th) {
            return "bad-code";
        }
        if (code <= 0) return "bad-code";
        LOG.i("echo-key action code=" + code);
        final android.app.Activity activity = App.getInstance().getCurrentActivity();
        if (activity == null || activity.isFinishing()) return "no-activity";
        final String actName = activity.getClass().getSimpleName();

        /*
         * 为什么不直接 dispatchKeyEvent：
         * 1) 方向键的"焦点查找"逻辑在 ViewRootImpl 输入管线里（ViewPostImeInputStage），
         *    手动 dispatchKeyEvent 走不到那一步——回执 sent 但焦点永远不动；
         * 2) 触摸模式下（鼠标/触屏操作过的窗口）没有任何 View 持有焦点，按键无处落地；
         * 3) Instrumentation 注入在 API 28+ 连自己的窗口都要 INJECT_EVENTS 权限。
         * 所以方向键自己做焦点移动（focusSearch+requestFocus，公开 API），
         * OK 键 performClick、返回键 onBackPressed，其余键才走 dispatchKeyEvent 兜底。
         */
        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        final String[] receipt = {"posted"};
        final int keyCode = code;
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                try {
                    if (activity.isFinishing()) {
                        receipt[0] = "no-activity";
                        latch.countDown();
                        return;
                    }
                    final android.view.View decor = activity.getWindow().getDecorView();
                    android.view.View focused = decor.findFocus();
                    if (focused == null) {
                        // requestFocusFromTouch：公开 API，内部会退出触摸模式并落初始焦点
                        decor.requestFocusFromTouch();
                        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                android.view.View f = decor.findFocus();
                                if (f == null) {
                                    receipt[0] = "no-focusable";
                                    latch.countDown();
                                    return;
                                }
                                handleWithFocus(activity, f, keyCode, receipt);
                                latch.countDown();
                            }
                        }, 160);
                        return;
                    }
                    handleWithFocus(activity, focused, keyCode, receipt);
                    latch.countDown();
                } catch (Throwable th) {
                    LOG.e("echo-key dispatch failed: " + th.getMessage());
                    receipt[0] = "dispatch-error";
                    latch.countDown();
                }
            }
        });
        try {
            latch.await(1, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
        }
        return receipt[0];
    }

    /** 已有焦点 View 后的按键语义：方向=焦点移动，OK=点击，返回=onBackPressed，其余走原派发 */
    private void handleWithFocus(android.app.Activity activity, android.view.View focused, int code, String[] receipt) {
        try {
            switch (code) {
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_DOWN:
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_DPAD_RIGHT: {
                    int dir;
                    if (code == KeyEvent.KEYCODE_DPAD_UP) dir = android.view.View.FOCUS_UP;
                    else if (code == KeyEvent.KEYCODE_DPAD_DOWN) dir = android.view.View.FOCUS_DOWN;
                    else if (code == KeyEvent.KEYCODE_DPAD_LEFT) dir = android.view.View.FOCUS_LEFT;
                    else dir = android.view.View.FOCUS_RIGHT;
                    android.view.View next = focused.focusSearch(dir);
                    if (next != null && next != focused) {
                        next.requestFocus(dir);
                        receipt[0] = "focus:" + code + ":" + next.getClass().getSimpleName();
                    } else {
                        receipt[0] = "edge:" + code;
                    }
                    return;
                }
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER: {
                    if (focused.performClick()) {
                        receipt[0] = "clicked:" + focused.getClass().getSimpleName();
                    } else {
                        receipt[0] = "no-click:" + focused.getClass().getSimpleName();
                    }
                    return;
                }
                case KeyEvent.KEYCODE_BACK: {
                    activity.onBackPressed();
                    receipt[0] = "back";
                    return;
                }
                default: {
                    dispatchTo(activity, code, receipt);
                }
            }
        } catch (Throwable th) {
            LOG.e("echo-key handleWithFocus failed: " + th.getMessage());
            receipt[0] = "dispatch-error";
        }
    }

    private void dispatchTo(android.app.Activity activity, int code, String[] receipt) {
        try {
            long now = SystemClock.uptimeMillis();
            activity.dispatchKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0));
            activity.dispatchKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0));
            receipt[0] = "sent:" + code + ":" + activity.getClass().getSimpleName();
        } catch (Throwable th) {
            LOG.e("echo-key dispatch failed: " + th.getMessage());
            receipt[0] = "dispatch-error";
        }
    }

    private void handleRefreshAction(Map<String, String> params) {
        String type = params.get("type");
        if ("danmaku".equals(type)) {
            String path = params.get("path");
            EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_DANMU_REFRESH, path == null ? "" : path));
        }
    }

    private Response handleMedia() {
        try {
            android.app.Activity activity = App.getInstance().getCurrentActivity();
            if (activity == null) return createJSONResponse(Response.Status.OK, "{}");
            final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
            final com.google.gson.JsonObject[] result = new com.google.gson.JsonObject[1];
            result[0] = new com.google.gson.JsonObject();
            final android.app.Activity act = activity;
            new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (act instanceof com.github.tvbox.osc.ui.activity.DetailActivity) {
                            com.github.tvbox.osc.ui.activity.DetailActivity detail = (com.github.tvbox.osc.ui.activity.DetailActivity) act;
                            com.github.tvbox.osc.ui.fragment.PlayFragment playFragment = detail.getPlayFragment();
                            if (playFragment != null) result[0] = playFragment.getMediaInfo();
                        }
                    } catch (Throwable th) {
                        LOG.e("echo-media handleMedia error: " + th.getMessage());
                    } finally {
                        latch.countDown();
                    }
                }
            });
            latch.await(2, java.util.concurrent.TimeUnit.SECONDS);
            return createJSONResponse(Response.Status.OK, result[0].toString());
        } catch (Throwable th) {
            LOG.e("echo-media error: " + th.getMessage());
            return createJSONResponse(Response.Status.OK, "{}");
        }
    }

    private void normalizeDanmuParams(Map<String, String> params) {
        try {
            VodInfo vodInfo = App.getInstance().getVodInfo();
            if (vodInfo == null) return;
            if (!TextUtils.isEmpty(vodInfo.name)) params.put("vodName", vodInfo.name);
            if (!isNumeric(params.get("vodIndex"))) {
                String episode = getCurrentEpisodeIndex(vodInfo);
                if (!TextUtils.isEmpty(episode)) params.put("vodIndex", episode);
            }
        } catch (Throwable th) {
            LOG.e("echo-proxy-danmu normalize error: " + th.getMessage());
        }
    }

    private String getCurrentEpisodeIndex(VodInfo vodInfo) {
        if (vodInfo.seriesMap != null && !TextUtils.isEmpty(vodInfo.playFlag)) {
            java.util.List<VodInfo.VodSeries> series = vodInfo.seriesMap.get(vodInfo.playFlag);
            if (series != null && vodInfo.playIndex >= 0 && vodInfo.playIndex < series.size()) {
                VodInfo.VodSeries current = series.get(vodInfo.playIndex);
                if (current != null && !TextUtils.isEmpty(current.name)) {
                    String number = extractNumber(current.name);
                    return TextUtils.isEmpty(number) ? current.name : number;
                }
            }
        }
        return String.valueOf(Math.max(0, vodInfo.playIndex) + 1);
    }

    private boolean isNumeric(String text) {
        return !TextUtils.isEmpty(text) && text.matches("\\d+");
    }

    private String extractNumber(String text) {
        if (TextUtils.isEmpty(text)) return "";
        Matcher matcher = getPattern("\\d+").matcher(text);
        return matcher.find() ? matcher.group() : "";
    }

    public void setDataReceiver(DataReceiver receiver) {
        mDataReceiver = receiver;
    }

    public DataReceiver getDataReceiver() {
        return mDataReceiver;
    }

    public boolean isStarting() {
        return isStarted;
    }

    public String getServerAddress() {
        String ipAddress = getLocalIPAddress(mContext);
        return "http://" + ipAddress + ":" + RemoteServer.serverPort + "/";
    }

    public String getLoadAddress() {
        return "http://127.0.0.1:" + RemoteServer.serverPort + "/";
    }

    public static Response createPlainTextResponse(Response.IStatus status, String text) {
        return newFixedLengthResponse(status, NanoHTTPD.MIME_PLAINTEXT, text);
    }

    public static Response createJSONResponse(Response.IStatus status, String text) {
        return newFixedLengthResponse(status, "application/json", text);
    }

    @SuppressLint("DefaultLocale")
    public static String getLocalIPAddress(Context context) {
        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        int ipAddress = wifiManager.getConnectionInfo().getIpAddress();
        if (ipAddress == 0) {
            try {
                Enumeration<NetworkInterface> enumerationNi = NetworkInterface.getNetworkInterfaces();
                while (enumerationNi.hasMoreElements()) {
                    NetworkInterface networkInterface = enumerationNi.nextElement();
                    String interfaceName = networkInterface.getDisplayName();
                    if (interfaceName.equals("eth0") || interfaceName.equals("wlan0")) {
                        Enumeration<InetAddress> enumIpAddr = networkInterface.getInetAddresses();
                        while (enumIpAddr.hasMoreElements()) {
                            InetAddress inetAddress = enumIpAddr.nextElement();
                            if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address) {
                                return inetAddress.getHostAddress();
                            }
                        }
                    }
                }
            } catch (SocketException e) {
                e.printStackTrace();
            }
        } else {
            return String.format("%d.%d.%d.%d", (ipAddress & 0xff), (ipAddress >> 8 & 0xff), (ipAddress >> 16 & 0xff), (ipAddress >> 24 & 0xff));
        }
        return "0.0.0.0";
    }

    String fileTime(long time, String fmt) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(time);
        Date date = calendar.getTime();
        SimpleDateFormat sdf = new SimpleDateFormat(fmt);
        return sdf.format(date);
    }

    String fileList(String root, String path) {
        File file = new File(root + "/" + path);
        File[] list = file.listFiles();
        JsonObject info = new JsonObject();
        info.addProperty("remote", getServerAddress().replace("http://", "clan://"));
        info.addProperty("del", 0);
        if (path.isEmpty()) {
            info.addProperty("parent", ".");
        } else {
            info.addProperty("parent", file.getParentFile().getAbsolutePath().replace(root + "/", "").replace(root, ""));
        }
        if (list == null || list.length == 0) {
            info.add("files", new JsonArray());
            return info.toString();
        }
        Arrays.sort(list, new Comparator<File>() {
            @Override
            public int compare(File o1, File o2) {
                if (o1.isDirectory() && o2.isFile()) return -1;
                return o1.isFile() && o2.isDirectory() ? 1 : o1.getName().compareTo(o2.getName());
            }
        });
        JsonArray result = new JsonArray();
        for (File f : list) {
            if (f.getName().startsWith(".")) {
                if (f.getName().equals(".tvbox_folder")) {
                    info.addProperty("del", 1);
                }
                continue;
            }
            JsonObject fileObj = new JsonObject();
            fileObj.addProperty("name", f.getName());
            fileObj.addProperty("path", f.getAbsolutePath().replace(root + "/", ""));
            fileObj.addProperty("time", fileTime(f.lastModified(), "yyyy/MM/dd aHH:mm:ss"));
            fileObj.addProperty("dir", f.isDirectory() ? 1 : 0);
            result.add(fileObj);
        }
        info.add("files", result);
        return info.toString();
    }

    void unzip(File zipFilePath, String destDirectory) throws Throwable {
        File destDir = new File(destDirectory);
        if (!destDir.exists()) {
            destDir.mkdirs();
        }
        ZipFile zip = new ZipFile(zipFilePath);
        Enumeration<ZipEntry> iter = (Enumeration<ZipEntry>) zip.entries();
        while (iter.hasMoreElements()) {
            ZipEntry entry = iter.nextElement();
            InputStream is = zip.getInputStream(entry);
            String filePath = destDirectory + File.separator + entry.getName();
            if (!entry.isDirectory()) {
                extractFile(is, filePath);
            } else {
                File dir = new File(filePath);
                if (!dir.exists())
                    dir.mkdirs();
                File flag = new File(dir + "/.tvbox_folder");
                if (!flag.exists())
                    flag.createNewFile();
            }
        }
    }

    void extractFile(InputStream inputStream, String destFilePath) throws Throwable {
        File dst = new File(destFilePath);
        if (dst.exists())
            dst.delete();
        BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(destFilePath));
        byte[] bytesIn = new byte[2048];
        int len = inputStream.read(bytesIn);
        while (len > 0) {
            bos.write(bytesIn, 0, len);
            len = inputStream.read(bytesIn);
        }
        bos.close();
    }

}
