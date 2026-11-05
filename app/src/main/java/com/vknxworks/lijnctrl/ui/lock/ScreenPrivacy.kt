package com.vknxworks.lijnctrl.ui.lock

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * Screens that show or take secrets (passwords, API keys, server logs) keep the window out of screenshots,
 * screen recordings and the recent-apps preview while they're shown, whatever "Hide in screenshots and
 * recent apps" is set to. MainActivity applies it (FLAG_SECURE).
 */
object ScreenPrivacy {
    var forced by mutableIntStateOf(0)
        private set

    internal fun add() { forced++ }
    internal fun remove() { forced = (forced - 1).coerceAtLeast(0) }
}

/** Keeps the window secure while this is shown (and [active]). */
@Composable
fun SecureScreen(active: Boolean = true) {
    DisposableEffect(active) {
        if (active) ScreenPrivacy.add()
        onDispose { if (active) ScreenPrivacy.remove() }
    }
}

/**
 * Applies "Hide in screenshots" ([secure]: FLAG_SECURE, which also blanks the recent-apps preview) and
 * "Hide in recent apps" ([hideRecents]: a blank card in Recents, screenshots still work) to [activity]'s
 * window, and again whenever they change, so a switch works at once in every open screen. Callers pass the
 * app lock being on as part of [hideRecents]: Android takes the Recents snapshot when you leave, before the
 * app locks, so a preview would otherwise show what's behind the PIN.
 */
@Composable
fun WindowPrivacy(activity: android.app.Activity, secure: Boolean, hideRecents: Boolean) {
    androidx.compose.runtime.LaunchedEffect(secure) {
        if (secure) activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        else activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
    }
    androidx.compose.runtime.LaunchedEffect(hideRecents) { activity.setRecentsScreenshotEnabled(!hideRecents) }
}

/**
 * Lock, PIN and confirmation screens. While one is showing, other apps' floating windows (chat heads, overlays)
 * are hidden from its window so they can't cover it or fake a button, and on lock and PIN screens apps that
 * aren't real accessibility tools can't read the window (Android 14+). Counted per window, so nesting works.
 */
object ScreenGuard {
    private val counts = java.util.WeakHashMap<android.view.Window, IntArray>()

    fun acquire(window: android.view.Window, sensitive: Boolean) {
        val c = counts.getOrPut(window) { IntArray(2) }
        c[0]++
        if (sensitive) c[1]++
        apply(window, c)
    }

    fun release(window: android.view.Window, sensitive: Boolean) {
        val c = counts[window] ?: return
        c[0] = (c[0] - 1).coerceAtLeast(0)
        if (sensitive) c[1] = (c[1] - 1).coerceAtLeast(0)
        apply(window, c)
        if (c[0] == 0 && c[1] == 0) counts.remove(window)
    }

    private fun apply(window: android.view.Window, c: IntArray) {
        runCatching { window.setHideOverlayWindows(c[0] > 0) }
        if (android.os.Build.VERSION.SDK_INT >= 34) runCatching {
            window.decorView.setAccessibilityDataSensitive(
                if (c[1] > 0) android.view.View.ACCESSIBILITY_DATA_SENSITIVE_YES else android.view.View.ACCESSIBILITY_DATA_SENSITIVE_AUTO,
            )
        }
    }
}

/** Guards the window this screen is shown in (its own dialog window, or the activity's) while it's shown. */
@Composable
fun GuardedScreen(sensitive: Boolean = false) {
    val view = androidx.compose.ui.platform.LocalView.current
    DisposableEffect(view, sensitive) {
        val window = (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
            ?: view.context.findActivity()?.window
        window?.let { ScreenGuard.acquire(it, sensitive) }
        onDispose { window?.let { ScreenGuard.release(it, sensitive) } }
    }
}
