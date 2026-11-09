# Second Clock

A native Android app that displays a movable, Pixel-inspired clock over other apps. It uses Android's current locale and 12/24-hour preference and updates at the next minute boundary.

## Run

Open the project in Android Studio and let Gradle sync. Build and install the `app` configuration on a device running Android 8.0 (API 26) or later.

On the device:

1. Open **Second Clock**.
2. Grant **Display over other apps** when prompted.
3. Tap **Start clock overlay**. Allow notifications if requested so Android can show the ongoing service notification.
4. Optionally enable **Allow clock in status-bar area** to start the clock at the top edge and allow dragging it into the system-bar region.
5. Hold and drag the clock to move it, or use the directional **Clock position** controls in the app to nudge it by 8 dp per tap. Stop it from the app or the notification's **Stop** action.
6. Choose **Clock font** to preview and select the Pixel-inspired system font, sans-serif, bold sans-serif, serif, or monospace. Changes apply immediately while the overlay is running.
7. Use **Enable subtle dark shadow** and its opacity slider to tune the clock's shadow. The shadow is enabled by default at 18% opacity. **Show AM/PM** controls the day-period marker in 12-hour time; 24-hour time is unchanged.

The app must be opened by the user to start the overlay. The foreground service keeps it visible while the user switches apps; Android's battery-management policies may still affect long-running services.

## Status-bar limitation

Android does not allow an ordinary third-party app to replace the system status-bar clock. The optional **Allow clock in status-bar area** setting adds `FLAG_LAYOUT_NO_LIMITS` and positions the separate overlay at the top edge; the clock remains draggable and its position is saved. Android may still draw the status bar and its icons above the overlay, depending on OS version and device, so visibility over system-bar content is not guaranteed. The built-in clock remains unchanged.
