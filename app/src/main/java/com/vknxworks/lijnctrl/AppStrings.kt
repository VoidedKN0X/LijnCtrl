package com.vknxworks.lijnctrl

import android.content.Context
import androidx.annotation.StringRes

/**
 * Text from strings.xml for code that runs outside a composable (errors, toasts, the player service).
 * Composables use stringResource() instead.
 */
object AppStrings {
    lateinit var context: Context
}

fun str(@StringRes id: Int, vararg args: Any?): String =
    if (args.isEmpty()) AppStrings.context.getString(id) else AppStrings.context.getString(id, *args)
