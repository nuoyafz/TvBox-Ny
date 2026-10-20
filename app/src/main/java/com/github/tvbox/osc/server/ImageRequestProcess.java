package com.github.tvbox.osc.server;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

/**
 * 把 drawable 里的图片资源当 HTTP 图片发出去。
 *
 * PWA 的 manifest 需要 192 / 512 两种尺寸的图标，而图标本身在 R.drawable 里
 * （app_icon.png 已按 hdpi=192 / xxxhdpi=512 出过一套），直接复用，
 * 不用再为网页另存一份 png。
 */
public class ImageRequestProcess implements RequestProcess {

    private final Context mContext;
    private final String fileName;
    private final int drawableId;
    private final String mimeType;

    public ImageRequestProcess(Context context, String fileName, int drawableId, String mimeType) {
        this.mContext = context;
        this.fileName = fileName;
        this.drawableId = drawableId;
        this.mimeType = mimeType;
    }

    @Override
    public boolean isRequest(NanoHTTPD.IHTTPSession session, String fileName) {
        return session.getMethod() == NanoHTTPD.Method.GET && this.fileName.equalsIgnoreCase(fileName);
    }

    @Override
    public NanoHTTPD.Response doResponse(NanoHTTPD.IHTTPSession session, String fileName, Map<String, String> params, Map<String, String> files) {
        try {
            Drawable drawable = mContext.getResources().getDrawable(drawableId);
            Bitmap bitmap;
            if (drawable instanceof BitmapDrawable && ((BitmapDrawable) drawable).getBitmap() != null) {
                bitmap = ((BitmapDrawable) drawable).getBitmap();
            } else {
                int w = Math.max(1, drawable.getIntrinsicWidth());
                int h = Math.max(1, drawable.getIntrinsicHeight());
                bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                drawable.setBounds(0, 0, w, h);
                drawable.draw(canvas);
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, bos);
            byte[] bytes = bos.toByteArray();
            return RemoteServer.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, mimeType,
                    new java.io.ByteArrayInputStream(bytes), bytes.length);
        } catch (Throwable th) {
            return RemoteServer.createPlainTextResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR,
                    "IMAGE ERROR: " + th.getMessage());
        }
    }
}