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
