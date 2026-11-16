package io.freemytv;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
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
    private java.util.Set<String> homes;   // packages of every home app (read when the service starts)
    private int toasts;
    private long lastRedirect;

    @Override protected boolean onKeyEvent(KeyEvent event) {
        return RemoteKeys.onKey(this, event);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence pkg = event.getPackageName();
        if (pkg == null) return;
        if (!isStockHome(pkg.toString())) return;
        long now = SystemClock.uptimeMillis();
        if (now - lastRedirect < COOLDOWN_MS) return;
        lastRedirect = now;
        if (toasts++ < 3) android.widget.Toast.makeText(this, "Free My TV: home detectada (" + pkg + ")", android.widget.Toast.LENGTH_SHORT).show();
        try {
            startActivity(new Intent(Intent.ACTION_MAIN)
                    .setClassName(getPackageName(), getPackageName() + ".HomeActivity")
                    .addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
        } catch (RuntimeException ignored) {
        }
    }

    /** True for the package of a home app that isn't ours (the TV's own home screen). The setup and settings apps are left alone. */
    private boolean isStockHome(String pkg) {
        if (pkg.equals(getPackageName()) || pkg.equals("android") || pkg.contains("setup") || pkg.contains("settings")) return false;
        if (homes == null) {
            homes = new java.util.HashSet<>();
            try {
                for (ResolveInfo i : getPackageManager().queryIntentActivities(
                        new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)) {
                    homes.add(i.activityInfo.packageName);
                }
            } catch (RuntimeException ignored) {
            }
        }
        return homes.contains(pkg);
    }

    @Override protected void onServiceConnected() {
        homes = null;   // looked up again each time the service starts
        android.widget.Toast.makeText(this, "Free My TV: serviço de acessibilidade ligado", android.widget.Toast.LENGTH_LONG).show();
    }

    @Override public void onInterrupt() {}
}
