package com.afzal.secondclock;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int REQUEST_NOTIFICATIONS = 100;
    private static final String PREFERENCES = "clock_overlay";
    private static final String KEY_RUNNING = "running";
    private static final String KEY_SYSTEM_BAR_MODE = "allow_system_bar_area";
    private static final String KEY_CLOCK_FONT = "clock_font";
    private static final String KEY_SHADOW_ENABLED = "shadow_enabled";
    private static final String KEY_SHADOW_OPACITY = "shadow_opacity";
    private static final String KEY_SHOW_AMPM = "show_ampm";
    private static final String ACTION_UPDATE_MODE = "com.afzal.secondclock.UPDATE_OVERLAY_MODE";
    private static final String ACTION_UPDATE_FONT = "com.afzal.secondclock.UPDATE_OVERLAY_FONT";
    private static final String ACTION_UPDATE_APPEARANCE = "com.afzal.secondclock.UPDATE_OVERLAY_APPEARANCE";
    private static final String ACTION_MOVE_OVERLAY = "com.afzal.secondclock.MOVE_OVERLAY";
    private static final int MOVE_STEP_DP = 8;

    private TextView permissionStatus;
    private TextView overlayStatus;
    private Button toggleButton;
    private Button permissionButton;
    private Button moveUpButton;
    private Button moveLeftButton;
    private Button moveRightButton;
    private Button moveDownButton;
    private Button fontButton;
    private TextView shadowOpacityLabel;
    private Switch shadowSwitch;
    private Switch amPmSwitch;
    private SeekBar shadowOpacitySeekBar;
    private SharedPreferences preferences;

    private static final String[] CLOCK_FONT_KEYS = {
            "pixel", "sans", "sans_bold", "serif", "monospace"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = getSharedPreferences(PREFERENCES, MODE_PRIVATE);
        buildScreen();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    private void buildScreen() {
        int page = Color.rgb(247, 245, 250);
        int ink = Color.rgb(29, 27, 32);
        int muted = Color.rgb(95, 92, 100);
        int purple = Color.rgb(103, 80, 164);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(page);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(36), dp(24), dp(28));
        scroll.addView(content);

        TextView eyebrow = text(getString(R.string.eyebrow), 12, purple, Typeface.create("sans-serif-medium", Typeface.NORMAL));
        eyebrow.setLetterSpacing(0.08f);
        content.addView(eyebrow);

        TextView title = text(getString(R.string.screen_title), 32, ink, Typeface.create("sans-serif-medium", Typeface.NORMAL));
        LinearLayout.LayoutParams titleParams = params(-1, -2);
        titleParams.topMargin = dp(8);
        content.addView(title, titleParams);

        TextView description = text(
                getString(R.string.screen_description),
                16, muted, Typeface.create("sans-serif", Typeface.NORMAL));
        LinearLayout.LayoutParams descriptionParams = params(-1, -2);
        descriptionParams.topMargin = dp(12);
        content.addView(description, descriptionParams);

        LinearLayout preview = new LinearLayout(this);
        preview.setGravity(Gravity.CENTER_VERTICAL);
        preview.setPadding(dp(20), dp(22), dp(20), dp(22));
        preview.setBackground(background(Color.WHITE, dp(24)));
        LinearLayout.LayoutParams previewParams = params(-1, -2);
        previewParams.topMargin = dp(28);
        content.addView(preview, previewParams);

        TextView previewTime = text(getString(R.string.preview_time), 30, ink, Typeface.create("sans-serif-medium", Typeface.NORMAL));
        preview.addView(previewTime);
        TextView previewCaption = text(getString(R.string.preview_caption), 14, muted, Typeface.create("sans-serif", Typeface.NORMAL));
        preview.addView(previewCaption);

        TextView permissionsTitle = text(getString(R.string.permissions_title), 18, ink, Typeface.create("sans-serif-medium", Typeface.NORMAL));
        LinearLayout.LayoutParams sectionParams = params(-1, -2);
        sectionParams.topMargin = dp(30);
        content.addView(permissionsTitle, sectionParams);

        permissionStatus = text("", 14, muted, Typeface.create("sans-serif", Typeface.NORMAL));
        LinearLayout.LayoutParams statusParams = params(-1, -2);
        statusParams.topMargin = dp(8);
        content.addView(permissionStatus, statusParams);

        permissionButton = new Button(this);
        permissionButton.setText(R.string.button_allow_overlay);
        permissionButton.setAllCaps(false);
        LinearLayout.LayoutParams permissionButtonParams = params(-1, -2);
        permissionButtonParams.topMargin = dp(10);
        content.addView(permissionButton, permissionButtonParams);
        permissionButton.setOnClickListener(view -> openOverlaySettings());

        toggleButton = new Button(this);
        toggleButton.setAllCaps(false);
        LinearLayout.LayoutParams toggleParams = params(-1, -2);
        toggleParams.topMargin = dp(20);
        content.addView(toggleButton, toggleParams);
        toggleButton.setOnClickListener(view -> toggleOverlay());

        overlayStatus = text("", 14, muted, Typeface.create("sans-serif", Typeface.NORMAL));
        LinearLayout.LayoutParams overlayStatusParams = params(-1, -2);
        overlayStatusParams.topMargin = dp(12);
        content.addView(overlayStatus, overlayStatusParams);

        TextView positionTitle = text(
                getString(R.string.position_controls_title),
                18, ink, Typeface.create("sans-serif-medium", Typeface.NORMAL));
        LinearLayout.LayoutParams positionTitleParams = params(-1, -2);
        positionTitleParams.topMargin = dp(24);
        content.addView(positionTitle, positionTitleParams);

        TextView positionDescription = text(
                getString(R.string.position_controls_description),
                14, muted, Typeface.create("sans-serif", Typeface.NORMAL));
        LinearLayout.LayoutParams positionDescriptionParams = params(-1, -2);
        positionDescriptionParams.topMargin = dp(4);
        content.addView(positionDescription, positionDescriptionParams);

        LinearLayout directionPad = new LinearLayout(this);
        directionPad.setOrientation(LinearLayout.VERTICAL);
        directionPad.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams directionPadParams = params(-1, -2);
        directionPadParams.topMargin = dp(8);
        content.addView(directionPad, directionPadParams);

        moveUpButton = directionButton(R.string.move_up, "↑", 0, -MOVE_STEP_DP);
        LinearLayout topRow = new LinearLayout(this);
        topRow.setGravity(Gravity.CENTER);
        topRow.addView(moveUpButton, params(dp(64), -2));
        directionPad.addView(topRow, params(-1, -2));

        LinearLayout middleRow = new LinearLayout(this);
        middleRow.setGravity(Gravity.CENTER);
        moveLeftButton = directionButton(R.string.move_left, "←", -MOVE_STEP_DP, 0);
        moveRightButton = directionButton(R.string.move_right, "→", MOVE_STEP_DP, 0);
        middleRow.addView(moveLeftButton, params(dp(64), -2));
        TextView center = text("•", 20, muted, Typeface.create("sans-serif", Typeface.NORMAL));
        center.setGravity(Gravity.CENTER);
        middleRow.addView(center, params(dp(40), -2));
        middleRow.addView(moveRightButton, params(dp(64), -2));
        directionPad.addView(middleRow, params(-1, -2));

        moveDownButton = directionButton(R.string.move_down, "↓", 0, MOVE_STEP_DP);
        LinearLayout bottomRow = new LinearLayout(this);
        bottomRow.setGravity(Gravity.CENTER);
        bottomRow.addView(moveDownButton, params(dp(64), -2));
        directionPad.addView(bottomRow, params(-1, -2));

        fontButton = new Button(this);
        fontButton.setAllCaps(false);
        LinearLayout.LayoutParams fontButtonParams = params(-1, -2);
        fontButtonParams.topMargin = dp(20);
        content.addView(fontButton, fontButtonParams);
        fontButton.setOnClickListener(view -> showFontPicker());

        shadowSwitch = new Switch(this);
        shadowSwitch.setText(R.string.shadow_enabled_title);
        shadowSwitch.setTextSize(16);
        shadowSwitch.setChecked(preferences.getBoolean(KEY_SHADOW_ENABLED, true));
        LinearLayout.LayoutParams shadowSwitchParams = params(-1, -2);
        shadowSwitchParams.topMargin = dp(16);
        content.addView(shadowSwitch, shadowSwitchParams);
        shadowSwitch.setOnCheckedChangeListener((button, enabled) -> {
            preferences.edit().putBoolean(KEY_SHADOW_ENABLED, enabled).apply();
            updateShadowOpacityEnabled(enabled);
            notifyAppearanceChanged();
        });

        shadowOpacityLabel = text("", 14, muted, Typeface.create("sans-serif", Typeface.NORMAL));
        LinearLayout.LayoutParams shadowOpacityLabelParams = params(-1, -2);
        shadowOpacityLabelParams.topMargin = dp(4);
        content.addView(shadowOpacityLabel, shadowOpacityLabelParams);

        shadowOpacitySeekBar = new SeekBar(this);
        shadowOpacitySeekBar.setMax(40);
        shadowOpacitySeekBar.setProgress(preferences.getInt(KEY_SHADOW_OPACITY, 18));
        content.addView(shadowOpacitySeekBar, params(-1, -2));
        updateShadowOpacityLabel(shadowOpacitySeekBar.getProgress());
        updateShadowOpacityEnabled(shadowSwitch.isChecked());
        shadowOpacitySeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateShadowOpacityLabel(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                preferences.edit().putInt(KEY_SHADOW_OPACITY, seekBar.getProgress()).apply();
                notifyAppearanceChanged();
            }
        });

        amPmSwitch = new Switch(this);
        amPmSwitch.setText(R.string.show_ampm_title);
        amPmSwitch.setTextSize(16);
        amPmSwitch.setChecked(preferences.getBoolean(KEY_SHOW_AMPM, true));
        LinearLayout.LayoutParams amPmSwitchParams = params(-1, -2);
        amPmSwitchParams.topMargin = dp(12);
        content.addView(amPmSwitch, amPmSwitchParams);
        amPmSwitch.setOnCheckedChangeListener((button, show) -> {
            preferences.edit().putBoolean(KEY_SHOW_AMPM, show).apply();
            notifyAppearanceChanged();
        });

        Switch systemBarModeSwitch = new Switch(this);
        systemBarModeSwitch.setText(R.string.system_bar_mode_title);
        systemBarModeSwitch.setTextSize(16);
        systemBarModeSwitch.setChecked(preferences.getBoolean(KEY_SYSTEM_BAR_MODE, false));
        LinearLayout.LayoutParams modeSwitchParams = params(-1, -2);
        modeSwitchParams.topMargin = dp(24);
        content.addView(systemBarModeSwitch, modeSwitchParams);
        systemBarModeSwitch.setOnCheckedChangeListener((button, enabled) -> {
            preferences.edit().putBoolean(KEY_SYSTEM_BAR_MODE, enabled).apply();
            if (preferences.getBoolean(KEY_RUNNING, false)) {
                try {
                    Intent intent = new Intent(this, ClockOverlayService.class);
                    intent.setAction(ACTION_UPDATE_MODE);
                    startService(intent);
                } catch (IllegalStateException | SecurityException exception) {
                    Toast.makeText(this, getString(R.string.overlay_start_error, exception.getMessage()), Toast.LENGTH_LONG).show();
                }
            }
        });

        TextView modeDescription = text(
                getString(R.string.system_bar_mode_description),
                14, muted, Typeface.create("sans-serif", Typeface.NORMAL));
        LinearLayout.LayoutParams modeDescriptionParams = params(-1, -2);
        modeDescriptionParams.topMargin = dp(4);
        content.addView(modeDescription, modeDescriptionParams);

        TextView note = text(
                getString(R.string.overlay_limitation),
                14, muted, Typeface.create("sans-serif", Typeface.NORMAL));
        LinearLayout.LayoutParams noteParams = params(-1, -2);
        noteParams.topMargin = dp(30);
        content.addView(note, noteParams);

        setContentView(scroll);
    }

    private void refreshStatus() {
        if (permissionStatus == null) {
            return;
        }

        boolean canDraw = Settings.canDrawOverlays(this);
        permissionStatus.setText(canDraw
                ? R.string.overlay_permission_allowed
                : R.string.overlay_permission_needed);
        permissionButton.setText(canDraw
                ? R.string.button_manage_overlay
                : R.string.button_allow_overlay);
        boolean running = preferences.getBoolean(KEY_RUNNING, false);
        toggleButton.setText(running ? R.string.button_stop_overlay : R.string.button_start_overlay);
        overlayStatus.setText(running ? R.string.overlay_status_running : R.string.overlay_status_off);
        setPositionControlsEnabled(running);
        updateFontButtonText();
    }

    private void updateShadowOpacityLabel(int progress) {
        if (shadowOpacityLabel != null) {
            shadowOpacityLabel.setText(getString(R.string.shadow_opacity_value, progress));
        }
    }

    private void updateShadowOpacityEnabled(boolean enabled) {
        if (shadowOpacityLabel != null) {
            shadowOpacityLabel.setEnabled(enabled);
            shadowOpacitySeekBar.setEnabled(enabled);
        }
    }

    private void notifyAppearanceChanged() {
        if (!preferences.getBoolean(KEY_RUNNING, false)) {
            return;
        }
        try {
            Intent intent = new Intent(this, ClockOverlayService.class);
            intent.setAction(ACTION_UPDATE_APPEARANCE);
            startService(intent);
        } catch (IllegalStateException | SecurityException exception) {
            Toast.makeText(this, getString(R.string.overlay_start_error, exception.getMessage()), Toast.LENGTH_LONG).show();
        }
    }

    private void showFontPicker() {
        String[] fontNames = getResources().getStringArray(R.array.clock_font_names);
        String selectedFont = preferences.getString(KEY_CLOCK_FONT, "pixel");
        int selectedIndex = 0;
        for (int i = 0; i < CLOCK_FONT_KEYS.length; i++) {
            if (CLOCK_FONT_KEYS[i].equals(selectedFont)) {
                selectedIndex = i;
                break;
            }
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.clock_font_title)
                .setSingleChoiceItems(fontNames, selectedIndex, (dialog, which) -> {
                    preferences.edit().putString(KEY_CLOCK_FONT, CLOCK_FONT_KEYS[which]).apply();
                    updateFontButtonText();
                    if (preferences.getBoolean(KEY_RUNNING, false)) {
                        try {
                            Intent intent = new Intent(this, ClockOverlayService.class);
                            intent.setAction(ACTION_UPDATE_FONT);
                            startService(intent);
                        } catch (IllegalStateException | SecurityException exception) {
                            Toast.makeText(this, getString(R.string.overlay_start_error, exception.getMessage()), Toast.LENGTH_LONG).show();
                        }
                    }
                    dialog.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void updateFontButtonText() {
        if (fontButton == null) {
            return;
        }
        String[] fontNames = getResources().getStringArray(R.array.clock_font_names);
        String selectedFont = preferences.getString(KEY_CLOCK_FONT, "pixel");
        for (int i = 0; i < CLOCK_FONT_KEYS.length; i++) {
            if (CLOCK_FONT_KEYS[i].equals(selectedFont)) {
                fontButton.setText(getString(R.string.clock_font_button, fontNames[i]));
                return;
            }
        }
        fontButton.setText(getString(R.string.clock_font_button, fontNames[0]));
    }

    private Button directionButton(int descriptionResource, String arrow, int dx, int dy) {
        Button button = new Button(this);
        button.setText(arrow);
        button.setTextSize(24);
        button.setContentDescription(getString(descriptionResource));
        button.setAllCaps(false);
        button.setOnClickListener(view -> moveOverlay(dx, dy));
        return button;
    }

    private void moveOverlay(int dx, int dy) {
        if (!preferences.getBoolean(KEY_RUNNING, false)) {
            return;
        }
        try {
            Intent intent = new Intent(this, ClockOverlayService.class);
            intent.setAction(ACTION_MOVE_OVERLAY);
            intent.putExtra(ClockOverlayService.EXTRA_MOVE_X, dp(dx));
            intent.putExtra(ClockOverlayService.EXTRA_MOVE_Y, dp(dy));
            startService(intent);
        } catch (IllegalStateException | SecurityException exception) {
            Toast.makeText(this, getString(R.string.overlay_start_error, exception.getMessage()), Toast.LENGTH_LONG).show();
        }
    }

    private void setPositionControlsEnabled(boolean enabled) {
        if (moveUpButton != null) {
            moveUpButton.setEnabled(enabled);
            moveLeftButton.setEnabled(enabled);
            moveRightButton.setEnabled(enabled);
            moveDownButton.setEnabled(enabled);
        }
    }

    private void toggleOverlay() {
        if (preferences.getBoolean(KEY_RUNNING, false)) {
            stopService(new Intent(this, ClockOverlayService.class));
            preferences.edit().putBoolean(KEY_RUNNING, false).apply();
            refreshStatus();
            return;
        }

        if (!Settings.canDrawOverlays(this)) {
            openOverlaySettings();
            return;
        }

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
            return;
        }

        startOverlayService();
    }

    private void openOverlaySettings() {
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
        startActivity(intent);
    }

    private void startOverlayService() {
        try {
            Intent intent = new Intent(this, ClockOverlayService.class);
            startForegroundService(intent);
            preferences.edit().putBoolean(KEY_RUNNING, true).apply();
            refreshStatus();
        } catch (IllegalStateException | SecurityException exception) {
            Toast.makeText(this, getString(R.string.overlay_start_error, exception.getMessage()), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_NOTIFICATIONS) {
            startOverlayService();
        }
    }

    private TextView text(String value, int sizeSp, int color, Typeface typeface) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        view.setTypeface(typeface);
        return view;
    }

    private GradientDrawable background(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private LinearLayout.LayoutParams params(int width, int height) {
        return new LinearLayout.LayoutParams(width, height);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
