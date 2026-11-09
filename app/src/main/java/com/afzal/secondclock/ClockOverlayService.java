package com.afzal.secondclock;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.util.DisplayMetrics;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import java.util.Date;
import java.util.Locale;

public class ClockOverlayService extends Service {
    private static final String TAG = "ClockOverlayService";
    private static final String CHANNEL_ID = "clock_overlay";
    private static final String PREFERENCES = "clock_overlay";
    private static final String KEY_RUNNING = "running";
    private static final String KEY_X = "x";
    private static final String KEY_Y = "y";
    private static final String KEY_SYSTEM_BAR_MODE = "allow_system_bar_area";
    private static final String KEY_CLOCK_FONT = "clock_font";
    private static final String KEY_SHADOW_ENABLED = "shadow_enabled";
    private static final String KEY_SHADOW_OPACITY = "shadow_opacity";
    private static final String KEY_SHOW_AMPM = "show_ampm";
    private static final String ACTION_STOP = "com.afzal.secondclock.STOP_OVERLAY";
    private static final String ACTION_UPDATE_MODE = "com.afzal.secondclock.UPDATE_OVERLAY_MODE";
    private static final String ACTION_UPDATE_FONT = "com.afzal.secondclock.UPDATE_OVERLAY_FONT";
    private static final String ACTION_UPDATE_APPEARANCE = "com.afzal.secondclock.UPDATE_OVERLAY_APPEARANCE";
    private static final String ACTION_MOVE_OVERLAY = "com.afzal.secondclock.MOVE_OVERLAY";
    static final String EXTRA_MOVE_X = "move_x";
    static final String EXTRA_MOVE_Y = "move_y";
    private static final int NOTIFICATION_ID = 41;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable clockTick = new Runnable() {
        @Override
        public void run() {
            updateClock();
            long delay = 60_000L - (System.currentTimeMillis() % 60_000L);
            handler.postDelayed(this, delay);
        }
    };

    private WindowManager windowManager;
    private MovableClockView clockView;
    private WindowManager.LayoutParams layoutParams;
    private SharedPreferences preferences;
    private boolean receiverRegistered;

    private final BroadcastReceiver timeChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateClock();
            if (Intent.ACTION_CONFIGURATION_CHANGED.equals(intent.getAction()) && clockView != null) {
                clockView.post(ClockOverlayService.this::clampOverlayToScreen);
            }
            handler.removeCallbacks(clockTick);
            handler.post(clockTick);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        preferences = getSharedPreferences(PREFERENCES, MODE_PRIVATE);
        createNotificationChannel();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, buildNotification());
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }

        if (intent != null && ACTION_MOVE_OVERLAY.equals(intent.getAction())) {
            if (clockView != null && Settings.canDrawOverlays(this)) {
                moveOverlay(intent.getIntExtra(EXTRA_MOVE_X, 0), intent.getIntExtra(EXTRA_MOVE_Y, 0));
            } else {
                if (!Settings.canDrawOverlays(this)) {
                    Log.e(TAG, "Overlay permission was revoked; stopping the clock service.");
                }
                stopSelf(startId);
            }
            return START_NOT_STICKY;
        }

        if (intent != null && ACTION_UPDATE_MODE.equals(intent.getAction())) {
            if (clockView == null) {
                showClock();
            } else {
                updateOverlayMode();
            }
            return START_STICKY;
        }

        if (intent != null && ACTION_UPDATE_FONT.equals(intent.getAction())) {
            if (clockView != null) {
                applyClockFont();
            }
            return START_NOT_STICKY;
        }

        if (intent != null && ACTION_UPDATE_APPEARANCE.equals(intent.getAction())) {
            if (clockView != null) {
                applyClockAppearance();
                updateClock();
            }
            return START_NOT_STICKY;
        }

        if (!Settings.canDrawOverlays(this)) {
            Log.e(TAG, "Overlay permission was revoked; stopping the clock service.");
            stopSelf();
            return START_NOT_STICKY;
        }

        if (clockView == null) {
            showClock();
        }
        return START_STICKY;
    }

    private void showClock() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        clockView = new MovableClockView(this);
        clockView.setTextSize(14);
        clockView.setTextColor(Color.WHITE);
        applyClockFont();
        applyClockAppearance();
        clockView.setLetterSpacing(0.007f);
        clockView.setPadding(dp(4), dp(2), dp(4), dp(2));
        clockView.setSingleLine(true);
        clockView.setContentDescription(getString(R.string.overlay_content_description));
        clockView.setOnClickListener(view -> {
            Intent openIntent = new Intent(this, MainActivity.class);
            openIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(openIntent);
        });
        clockView.setOnTouchListener(new View.OnTouchListener() {
            private float downRawX;
            private float downRawY;
            private int startX;
            private int startY;
            private boolean moved;

            @Override
            public boolean onTouch(View view, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        downRawX = event.getRawX();
                        downRawY = event.getRawY();
                        startX = layoutParams.x;
                        startY = layoutParams.y;
                        moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int deltaX = Math.round(event.getRawX() - downRawX);
                        int deltaY = Math.round(event.getRawY() - downRawY);
                        if (Math.abs(deltaX) > dp(3) || Math.abs(deltaY) > dp(3)) {
                            moved = true;
                        }
                        if (moved) {
                            DisplayMetrics metrics = new DisplayMetrics();
                            windowManager.getDefaultDisplay().getRealMetrics(metrics);
                            layoutParams.x = clamp(startX + deltaX, metrics.widthPixels, view.getWidth());
                            layoutParams.y = clampOverlayY(startY + deltaY, metrics.heightPixels, view.getHeight());
                            try {
                                windowManager.updateViewLayout(clockView, layoutParams);
                            } catch (IllegalArgumentException exception) {
                                Log.e(TAG, "Could not move the clock overlay.", exception);
                            }
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (moved) {
                            preferences.edit()
                                    .putInt(KEY_X, layoutParams.x)
                                    .putInt(KEY_Y, layoutParams.y)
                                    .apply();
                        } else {
                            clockView.performClick();
                        }
                        return true;
                    default:
                        return true;
                }
            }
        });

        boolean allowSystemBarArea = preferences.getBoolean(KEY_SYSTEM_BAR_MODE, false);
        int windowFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        if (allowSystemBarArea) {
            windowFlags |= WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        }

        layoutParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                windowFlags,
                PixelFormat.TRANSLUCENT);
        layoutParams.gravity = Gravity.TOP | Gravity.START;
        layoutParams.x = preferences.getInt(KEY_X, dp(16));
        layoutParams.y = preferences.getInt(KEY_Y, allowSystemBarArea ? 0 : dp(32));
        if (!allowSystemBarArea) {
            layoutParams.y = Math.max(layoutParams.y, dp(32));
        }

        try {
            windowManager.addView(clockView, layoutParams);
            clockView.post(this::clampOverlayToScreen);
            updateClock();
            registerTimeReceiver();
            handler.removeCallbacks(clockTick);
            handler.post(clockTick);
            preferences.edit().putBoolean(KEY_RUNNING, true).apply();
        } catch (SecurityException | WindowManager.BadTokenException exception) {
            Log.e(TAG, "Could not display the clock overlay.", exception);
            preferences.edit().putBoolean(KEY_RUNNING, false).apply();
            stopSelf();
        }
    }

    private void updateClock() {
        if (clockView != null) {
            java.text.DateFormat timeFormat = getClockTimeFormat();
            clockView.setText(timeFormat.format(new Date()));
        }
    }

    private java.text.DateFormat getClockTimeFormat() {
        if (preferences.getBoolean(KEY_SHOW_AMPM, true)
                || DateFormat.is24HourFormat(this)) {
            return DateFormat.getTimeFormat(this);
        }

        Locale locale = getResources().getConfiguration().getLocales().get(0);
        String pattern = DateFormat.getBestDateTimePattern(locale, "hm");
        return new java.text.SimpleDateFormat(stripDayPeriod(pattern), locale);
    }

    private String stripDayPeriod(String pattern) {
        StringBuilder result = new StringBuilder(pattern.length());
        boolean quoted = false;
        boolean skipFollowingSpace = false;

        for (int i = 0; i < pattern.length(); i++) {
            char current = pattern.charAt(i);
            if (current == '\'') {
                if (i + 1 < pattern.length() && pattern.charAt(i + 1) == '\'') {
                    if (skipFollowingSpace) {
                        skipFollowingSpace = false;
                    }
                    result.append("''");
                    i++;
                    continue;
                }
                quoted = !quoted;
            }

            if (!quoted && (current == 'a' || current == 'b' || current == 'B')) {
                while (result.length() > 0 && isPatternSpace(result.charAt(result.length() - 1))) {
                    result.deleteCharAt(result.length() - 1);
                }
                skipFollowingSpace = true;
                continue;
            }

            if (skipFollowingSpace && isPatternSpace(current)) {
                continue;
            }
            skipFollowingSpace = false;
            result.append(current);
        }

        return result.toString().trim();
    }

    private boolean isPatternSpace(char value) {
        return Character.isWhitespace(value) || value == '\u00a0' || value == '\u202f';
    }

    private void applyClockAppearance() {
        if (clockView == null) {
            return;
        }

        if (preferences.getBoolean(KEY_SHADOW_ENABLED, true)) {
            int opacity = preferences.getInt(KEY_SHADOW_OPACITY, 18);
            int alpha = Math.round(255f * opacity / 100f);
            clockView.setShadowLayer(dp(1.5f), 0, dp(0.5f), Color.argb(alpha, 0, 0, 0));
        } else {
            clockView.setShadowLayer(0, 0, 0, Color.TRANSPARENT);
        }
    }

    private void registerTimeReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        filter.addAction(Intent.ACTION_LOCALE_CHANGED);
        filter.addAction(Intent.ACTION_CONFIGURATION_CHANGED);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(timeChangedReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(timeChangedReceiver, filter);
        }
        receiverRegistered = true;
    }

    private Notification buildNotification() {
        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent openPendingIntent = PendingIntent.getActivity(
                this, 0, openIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent stopIntent = new Intent(this, ClockOverlayService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPendingIntent = PendingIntent.getService(
                this, 1, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder builder = new Notification.Builder(this, CHANNEL_ID);
        builder.setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(getString(R.string.notification_text))
                .setContentIntent(openPendingIntent)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(
                        android.R.drawable.ic_media_pause,
                        getString(R.string.notification_stop),
                        stopPendingIntent).build());
        return builder.build();
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(getString(R.string.notification_text));
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(channel);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (clockView != null) {
            updateClock();
        }
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(clockTick);
        if (receiverRegistered) {
            unregisterReceiver(timeChangedReceiver);
            receiverRegistered = false;
        }
        if (clockView != null && windowManager != null) {
            windowManager.removeView(clockView);
            clockView = null;
        }
        if (preferences != null) {
            preferences.edit().putBoolean(KEY_RUNNING, false).apply();
        }
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private Typeface statusBarClockTypeface() {
        String font = preferences.getString(KEY_CLOCK_FONT, "pixel");
        switch (font) {
            case "sans":
                return Typeface.create("sans-serif", Typeface.NORMAL);
            case "sans_bold":
                return Typeface.create("sans-serif", Typeface.BOLD);
            case "serif":
                return Typeface.create("serif", Typeface.NORMAL);
            case "monospace":
                return Typeface.create("monospace", Typeface.NORMAL);
            case "pixel":
            default:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    return Typeface.create(Typeface.create("sans-serif", Typeface.NORMAL), 500, false);
                }
                return Typeface.create("sans-serif-medium", Typeface.NORMAL);
        }
    }

    private void applyClockFont() {
        if (clockView == null) {
            return;
        }
        clockView.setTypeface(statusBarClockTypeface());
        clockView.requestLayout();
        if (windowManager != null && layoutParams != null) {
            try {
                windowManager.updateViewLayout(clockView, layoutParams);
            } catch (IllegalArgumentException exception) {
                Log.e(TAG, "Could not apply the selected clock font.", exception);
            }
        }
    }

    private int clamp(int requested, int screenSize, int viewSize) {
        return Math.max(0, Math.min(requested, Math.max(0, screenSize - viewSize)));
    }

    private int clampOverlayY(int requested, int screenSize, int viewSize) {
        int minimumY = preferences.getBoolean(KEY_SYSTEM_BAR_MODE, false) ? 0 : dp(32);
        int maximumY = Math.max(minimumY, screenSize - viewSize);
        return Math.max(minimumY, Math.min(requested, maximumY));
    }

    private void clampOverlayToScreen() {
        if (clockView == null || layoutParams == null || windowManager == null) {
            return;
        }
        DisplayMetrics metrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getRealMetrics(metrics);
        layoutParams.x = clamp(layoutParams.x, metrics.widthPixels, clockView.getWidth());
        layoutParams.y = clampOverlayY(layoutParams.y, metrics.heightPixels, clockView.getHeight());
        try {
            windowManager.updateViewLayout(clockView, layoutParams);
            preferences.edit().putInt(KEY_X, layoutParams.x).putInt(KEY_Y, layoutParams.y).apply();
        } catch (IllegalArgumentException exception) {
            Log.e(TAG, "Could not reposition the clock overlay after a display change.", exception);
        }
    }

    private void updateOverlayMode() {
        boolean allowSystemBarArea = preferences.getBoolean(KEY_SYSTEM_BAR_MODE, false);
        layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        if (allowSystemBarArea) {
            layoutParams.flags |= WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
            layoutParams.y = 0;
        } else {
            layoutParams.y = dp(32);
        }
        clampOverlayToScreen();
    }

    private void moveOverlay(int dx, int dy) {
        DisplayMetrics metrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getRealMetrics(metrics);
        int nextX = clamp(layoutParams.x + dx, metrics.widthPixels, clockView.getWidth());
        int nextY = clampOverlayY(layoutParams.y + dy, metrics.heightPixels, clockView.getHeight());
        layoutParams.x = nextX;
        layoutParams.y = nextY;
        try {
            windowManager.updateViewLayout(clockView, layoutParams);
            preferences.edit().putInt(KEY_X, nextX).putInt(KEY_Y, nextY).apply();
        } catch (IllegalArgumentException exception) {
            Log.e(TAG, "Could not move the clock using the position controls.", exception);
        }
    }

    private static class MovableClockView extends TextView {
        MovableClockView(Context context) {
            super(context);
        }

        @Override
        public boolean performClick() {
            super.performClick();
            return true;
        }
    }
}
