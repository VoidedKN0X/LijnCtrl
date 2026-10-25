package com.vknxworks.lijnctrl

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.lijnctrl.ui.lock.AppLockOverlay
import com.vknxworks.lijnctrl.ui.lock.ScreenPrivacy
import com.vknxworks.lijnctrl.ui.lock.WindowPrivacy
import com.vknxworks.lijnctrl.ui.lock.lockCovering
import com.vknxworks.lijnctrl.ui.lock.lockLocked
import com.vknxworks.lijnctrl.ui.nav.AppRoot
import com.vknxworks.lijnctrl.ui.theme.LijnCtrlTheme

/** The Ctrl backgrounds (dark #08080A, light #F5F5F1), also behind the status and navigation bars. */
private const val BAR_DARK = 0xFF08080A.toInt()
private const val BAR_LIGHT = 0xFFF5F5F1.toInt()

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Recents shows a blank card until the settings are read (then "Hide in recent apps" decides).
        setRecentsScreenshotEnabled(false)
        val c = container
        setContent {
            val settings by c.settings.settings.collectAsStateWithLifecycle()
            // Follows the phone's dark/light setting: bars in the background colour, icons light on dark and dark on light.
            val dark = isSystemInDarkTheme()
            DisposableEffect(dark) {
                val bars = if (dark) SystemBarStyle.dark(BAR_DARK) else SystemBarStyle.light(BAR_LIGHT, BAR_DARK)
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
                onDispose { }
            }
            val covered = lockCovering(c.vault)
            // "Hide in screenshots", screens where the API key is typed or shown and the lock and PIN screens keep the
            // window out of screenshots and recordings; "Hide in recent apps" blanks the recent-apps card.
            val secure = settings.hideScreenshots || covered || ScreenPrivacy.forced > 0
            // With the app lock on, Recents always shows a blank card: a preview there would show what's behind the PIN.
            WindowPrivacy(this, secure, hideRecents = settings.hideRecents || c.vault.enabled)
            // Locked (not just asked for the PIN again): the app's screens aren't composed at all, so dialogs, menus
            // and sheets can't stay usable above the lock screen. Where you were comes back on unlock.
            val locked = lockLocked(c.vault)
            val savedRoot = rememberSaveableStateHolder()
            LijnCtrlTheme(dark) {
                Box(Modifier.fillMaxSize()) {
                    if (!locked) {
                        Box(if (covered) Modifier.fillMaxSize().clearAndSetSemantics { } else Modifier.fillMaxSize()) {
                            savedRoot.SaveableStateProvider("root") { AppRoot() }
                        }
                    }
                    AppLockOverlay()
                }
            }
        }
    }
}
