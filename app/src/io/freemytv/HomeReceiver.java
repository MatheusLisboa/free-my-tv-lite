package io.freemytv;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * System notices for the main process: the TV booted, this app was updated, the sleep timer ended, or the installer
 * answered about an update (Updates). Not exported.
 */
public class HomeReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            HomeMode.bootCheck(context);
            Updates.schedule(context);
            launchHomeWithoutAdb(context);
        } else if (Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            HomeMode.verify(context);   // app updated: complete Free My TV mode if something is missing
            HomeMode.ensureRemoteService(context, 3_000);
            Updates.schedule(context);
            later(() -> Updates.cleanUp(context));
        } else if (Updates.ACTION.equals(action)) {
            later(() -> Updates.status(context, intent));
        } else if (SleepTimer.ACTION.equals(action)) {
            long setAt = intent.getLongExtra("setAt", 0);
            later(() -> SleepTimer.fire(context, setAt));   // ADB is network
        }
    }

    /**
     * No-ADB mode: on a TV where debugging over the network is missing the app can't become the home screen, so it opens
     * itself a few seconds after boot. Android 9 and older let a receiver start an activity; from Android 10 the
     * "Display over other apps" permission (SYSTEM_ALERT_WINDOW) does the same.
     */
    private static void launchHomeWithoutAdb(Context context) {
        final Context app = context.getApplicationContext();
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            try {
                Intent home = new Intent(Intent.ACTION_MAIN)
                        .setClassName(app.getPackageName(), app.getPackageName() + ".HomeActivity")
                        .addCategory(Intent.CATEGORY_HOME)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                app.startActivity(home);
            } catch (RuntimeException ignored) {
                // The system refused (background start on Android 10+ without the overlay permission).
            }
        }, 8_000);
    }

    /** Off the main thread (Updates may wait for a check under way), with the notice held open until it's done. */
    private void later(Runnable work) {
        PendingResult result = goAsync();
        new Thread(() -> {
            try {
                work.run();
            } finally {
                result.finish();
            }
        }).start();
    }
}
