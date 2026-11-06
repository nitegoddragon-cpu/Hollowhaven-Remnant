package com.guardianpet;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.view.Choreographer;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.util.Random;

public class PetService extends Service {
    static final String STOP = "com.guardianpet.STOP";
    WindowManager wm;
    WebView web;
    WindowManager.LayoutParams lp;
    Choreographer.FrameCallback cb;
    boolean pageReady, moved;
    float d, x, y, vx, vy, t, dx0, dy0, offX, offY;
    int sw, sh, pad, screenW, screenH, top, bottom, dir = 1, wall = 1;
    long lt;
    String state = "fall";
    Random rnd = new Random();

    @Override
    public IBinder onBind(Intent i) { return null; }

    @Override
    public int onStartCommand(Intent in, int flags, int id) {
        if (in != null && STOP.equals(in.getAction())) { stopSelf(); return START_NOT_STICKY; }
        String look = in == null ? null : in.getStringExtra("look");
        if (look != null) {
            getSharedPreferences("pet", 0).edit().putString("look", look).apply();
            if (web != null) applyLook(); else stopSelf();
            return START_NOT_STICKY;
        }
        if (web == null) begin();
        return START_NOT_STICKY;
    }

    int dimen(String n) {
        int r = getResources().getIdentifier(n, "dimen", "android");
        return r > 0 ? getResources().getDimensionPixelSize(r) : 0;
    }

    void metrics() {
        if (Build.VERSION.SDK_INT >= 30) {
            Rect b = wm.getMaximumWindowMetrics().getBounds();
            screenW = b.width(); screenH = b.height();
        } else {
            DisplayMetrics m = new DisplayMetrics();
            wm.getDefaultDisplay().getRealMetrics(m);
            screenW = m.widthPixels; screenH = m.heightPixels;
        }
        top = dimen("status_bar_height");
        bottom = screenH - dimen("navigation_bar_height");
    }

    void begin() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel("pet", "Pet", NotificationManager.IMPORTANCE_LOW));
        PendingIntent pi = PendingIntent.getService(this, 0, new Intent(this, PetService.class).setAction(STOP),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Action stop = new Notification.Action.Builder(
                Icon.createWithResource(this, android.R.drawable.ic_delete), "Stop", pi).build();
        Notification n = new Notification.Builder(this, "pet")
                .setSmallIcon(android.R.drawable.star_on)
                .setContentTitle("Guardian Pet is roaming")
                .addAction(stop).build();
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(1, n);

        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        d = getResources().getDisplayMetrics().density;
        sw = Math.round(107 * d); sh = Math.round(150 * d); pad = Math.round(24 * d);
        metrics();

        web = new WebView(this);
        web.setBackgroundColor(0);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setAllowFileAccess(true);
        web.setVerticalScrollBarEnabled(false);
        web.setHorizontalScrollBarEnabled(false);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView v, String u) { pageReady = true; push(); applyLook(); }
        });
        web.setOnTouchListener((v, e) -> touch(e));
        web.loadUrl("file:///android_asset/pet.html");

        lp = new WindowManager.LayoutParams(sw + 2 * pad, sh + 2 * pad,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        x = screenW / 3f; y = top;
        lp.x = Math.round(x) - pad; lp.y = Math.round(y) - pad;
        wm.addView(web, lp);
        set("fall", 0);

        cb = new Choreographer.FrameCallback() {
            long last;
            @Override
            public void doFrame(long ns) {
                if (web == null) return;
                float dt = last == 0 ? .016f : Math.min((ns - last) / 1e9f, .05f);
                last = ns;
                step(dt);
                Choreographer.getInstance().postFrameCallback(this);
            }
        };
        Choreographer.getInstance().postFrameCallback(cb);
    }

    float rr(float a, float b) { return a + rnd.nextFloat() * (b - a); }
    float clamp(float v, float a, float b) { return Math.max(a, Math.min(b, v)); }

    void set(String s, float dur) { state = s; t = dur; push(); }

    void push() {
        if (web == null || !pageReady) return;
        web.evaluateJavascript("setPose('" + state + "'," + dir + "," + wall + "," + (vx / d) + "," + (vy / d) + ")", null);
    }

    void applyLook() {
        if (web == null || !pageReady) return;
        String l = getSharedPreferences("pet", 0).getString("look", "guardian");
        web.evaluateJavascript("setLook('" + l + "')", null);
    }

    void next() {
        float r = rnd.nextFloat();
        if (r < .45f) { dir = rnd.nextBoolean() ? 1 : -1; set("walk", rr(2, 5)); }
        else if (r < .65f) set("sit", rr(2, 5));
        else if (r < .8f) set("idle", rr(1, 3));
        else if (r < .9f) set("sleep", rr(4, 8));
        else { dir = rnd.nextBoolean() ? 1 : -1; vx = dir * 180 * d; vy = -650 * d; set("fall", 0); }
    }

    void step(float dt) {
        float floor = bottom - sh;
        if (!state.equals("drag")) {
            if (state.equals("fall")) {
                vy += 1800 * d * dt; x += vx * dt; y += vy * dt;
                if (x < 0) { x = 0; vx = Math.abs(vx) * .5f; }
                if (x > screenW - sw) { x = screenW - sw; vx = -Math.abs(vx) * .5f; }
                if (y < top) { y = top; vy = Math.abs(vy) * .3f; }
                if (y >= floor) { y = floor; vy = 0; vx = 0; set("idle", rr(.6f, 1.5f)); }
            } else if (state.equals("walk")) {
                x += dir * 60 * d * dt;
                if (x <= 0 || x >= screenW - sw) {
                    x = clamp(x, 0, screenW - sw);
                    if (rnd.nextFloat() < .5f) { wall = x <= 0 ? -1 : 1; set("climb", 0); }
                    else { dir = -dir; push(); }
                }
                t -= dt;
                if (t <= 0 && state.equals("walk")) next();
            } else if (state.equals("climb")) {
                y -= 70 * d * dt;
                if (y <= top) { y = top; vx = -wall * 120 * d; vy = 0; set("fall", 0); }
                else if (rnd.nextFloat() < dt * .15f) { vx = -wall * 140 * d; vy = -200 * d; set("fall", 0); }
            } else {
                t -= dt;
                if (t <= 0) next();
            }
        }
        lp.x = Math.round(x) - pad; lp.y = Math.round(y) - pad;
        wm.updateViewLayout(web, lp);
    }

    boolean touch(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dx0 = e.getRawX(); dy0 = e.getRawY();
                offX = e.getRawX() - x; offY = e.getRawY() - y;
                moved = false; lt = e.getEventTime();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!moved && Math.hypot(e.getRawX() - dx0, e.getRawY() - dy0) > 12 * d) { moved = true; vx = 0; vy = 0; set("drag", 0); }
                if (moved) {
                    float nx = clamp(e.getRawX() - offX, 0, screenW - sw);
                    float ny = clamp(e.getRawY() - offY, top, bottom - sh);
                    long n = e.getEventTime();
                    float dtm = Math.max(n - lt, 1) / 1000f;
                    vx = (nx - x) / dtm * .6f + vx * .4f;
                    vy = (ny - y) / dtm * .6f + vy * .4f;
                    lt = n; x = nx; y = ny;
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (moved) {
                    boolean stale = e.getEventTime() - lt > 80;
                    vx = stale ? 0 : clamp(vx, -900 * d, 900 * d);
                    vy = stale ? 0 : clamp(vy, -900 * d, 900 * d);
                    set("fall", 0);
                } else if (e.getActionMasked() == MotionEvent.ACTION_UP) {
                    if (state.equals("sleep")) set("idle", 1.5f);
                    else if (web != null) web.evaluateJavascript("roar()", null);
                }
                return true;
        }
        return true;
    }

    @Override
    public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        if (wm != null) metrics();
    }

    @Override
    public void onDestroy() {
        if (cb != null) Choreographer.getInstance().removeFrameCallback(cb);
        if (web != null) {
            try { wm.removeView(web); } catch (Exception ignored) { }
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
