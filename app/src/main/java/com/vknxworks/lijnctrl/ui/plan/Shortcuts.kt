package com.vknxworks.lijnctrl.ui.plan

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import com.vknxworks.lijnctrl.MainActivity
import com.vknxworks.lijnctrl.R
import com.vknxworks.lijnctrl.data.plan.SavedPlacesRepository
import com.vknxworks.lijnctrl.ui.nav.LaunchRequests

/**
 * "Go home" and "Go to work" launcher shortcuts (long-press the app icon), and pinning them to the home screen.
 * They open the Plan tab and plan from where you are to the saved place, leaving now; with no place saved yet,
 * they open the place picker for it. They're dynamic shortcuts, so they work for debug builds too.
 */
object Shortcuts {
    private fun id(slot: SavedPlacesRepository.Slot) = if (slot == SavedPlacesRepository.Slot.HOME) "go_home" else "go_to_work"

    private fun info(context: Context, slot: SavedPlacesRepository.Slot, rank: Int): ShortcutInfo {
        val home = slot == SavedPlacesRepository.Slot.HOME
        val intent = Intent(context, MainActivity::class.java)
            .setAction(if (home) LaunchRequests.ACTION_GO_HOME else LaunchRequests.ACTION_GO_TO_WORK)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        LaunchRequests.sign(context, intent)
        return ShortcutInfo.Builder(context, id(slot))
            .setShortLabel(context.getString(if (home) R.string.go_home else R.string.go_to_work))
            .setLongLabel(context.getString(if (home) R.string.shortcut_go_home_long else R.string.shortcut_go_to_work_long))
            .setIcon(Icon.createWithResource(context, if (home) R.drawable.ic_shortcut_home else R.drawable.ic_shortcut_work))
            .setIntent(intent)
            .setRank(rank)
            .build()
    }

    /** Publishes both shortcuts (cheap; called when the app starts). */
    fun publish(context: Context) {
        runCatching {
            val sm = context.getSystemService(ShortcutManager::class.java) ?: return
            sm.setDynamicShortcuts(
                listOf(
                    info(context, SavedPlacesRepository.Slot.HOME, 0),
                    info(context, SavedPlacesRepository.Slot.WORK, 1),
                ),
            )
        }
    }

    fun canPin(context: Context): Boolean =
        runCatching { context.getSystemService(ShortcutManager::class.java)?.isRequestPinShortcutSupported == true }.getOrDefault(false)

    /** Asks the launcher to put the shortcut on the home screen (it shows its own confirmation). */
    fun pin(context: Context, slot: SavedPlacesRepository.Slot): Boolean = runCatching {
        val sm = context.getSystemService(ShortcutManager::class.java) ?: return false
        sm.isRequestPinShortcutSupported && sm.requestPinShortcut(info(context, slot, 0), null)
    }.getOrDefault(false)

    /** Counts a use, so the launcher can suggest the shortcut you use most. */
    fun reportUsed(context: Context, slot: SavedPlacesRepository.Slot) {
        runCatching { context.getSystemService(ShortcutManager::class.java)?.reportShortcutUsed(id(slot)) }
    }
}
