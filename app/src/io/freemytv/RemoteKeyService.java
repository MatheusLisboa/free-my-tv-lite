package io.freemytv;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Sees the remote's keys before any app does, so the app buttons work from inside Netflix, Disney and the rest. The
 * system waits for it on every key (up to half a second), so it runs in a process of its own and does next to nothing.
 *
 * No-ADB mode: when the TV's own home screen comes to the front, Free My TV opens over it, so it behaves as the home
 * screen on a TV where it can't be set as one. Turn the accessibility service off to get the TV's home back.
 */
public class RemoteKeyService extends AccessibilityService {
    private static final long COOLDOWN_MS = 1500;
    private String stockHome;       // the package of the TV's own home screen, once found
    private long lastRedirect;

    @Override protected boolean onKeyEvent(KeyEvent event) {
        return RemoteKeys.onKey(this, event);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence pkg = event.getPackageName();
        if (pkg == null) return;
        String home = stockHome();
        if (home == null || !home.contentEquals(pkg)) return;
        long now = SystemClock.uptimeMillis();
        if (now - lastRedirect < COOLDOWN_MS) return;
        lastRedirect = now;
        try {
            startActivity(new Intent(Intent.ACTION_MAIN)
                    .setClassName(getPackageName(), getPackageName() + ".HomeActivity")
                    .addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
        } catch (RuntimeException ignored) {
        }
    }

    /** The home app Android would open (not ours): the one to cover. Null when Free My TV already is the home. */
    private String stockHome() {
        if (stockHome != null) return stockHome;
        try {
            ResolveInfo r = getPackageManager().resolveActivity(
                    new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY);
            String p = r == null || r.activityInfo == null ? null : r.activityInfo.packageName;
            if (p != null && !p.equals(getPackageName()) && !p.equals("android")) stockHome = p;
            if (stockHome == null) {
                // The chooser answered (no default set): take the other home app with the highest priority.
                int best = Integer.MIN_VALUE;
                for (ResolveInfo i : getPackageManager().queryIntentActivities(
                        new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)) {
                    String q = i.activityInfo.packageName;
                    if (q.equals(getPackageName()) || q.equals("android") || q.contains("setup") || q.contains("settings")) continue;
                    if (i.priority > best) { best = i.priority; stockHome = q; }
                }
            }
        } catch (RuntimeException ignored) {
        }
        return stockHome;
    }

    @Override protected void onServiceConnected() {
        stockHome = null;   // looked up again each time the service starts
    }

    @Override public void onInterrupt() {}
}
