package com.vknxworks.lijnctrl.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.vknxworks.lijnctrl.BuildConfig
import com.vknxworks.lijnctrl.R
import com.vknxworks.lijnctrl.container
import com.vknxworks.lijnctrl.data.backup.ExportFormatException
import com.vknxworks.lijnctrl.data.backup.SettingsExport
import com.vknxworks.lijnctrl.data.backup.SettingsFile
import com.vknxworks.lijnctrl.ui.components.ConfirmDialog
import com.vknxworks.lijnctrl.ui.components.Group
import com.vknxworks.lijnctrl.ui.components.GroupRow
import com.vknxworks.lijnctrl.ui.lock.AppLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * Export and import of saved stops and settings: a JSON file in a place you pick. No API keys, no PIN, no security
 * switches; an import shows what it will replace and asks first.
 */
@Composable
fun ExportGroup() {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<SettingsExport?>(null) }

    fun toast(res: Int) = Toast.makeText(context, context.getString(res), Toast.LENGTH_LONG).show()

    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = runCatching {
                val text = SettingsFile.export(c.saved.data.value, c.settings.settings.value, BuildConfig.VERSION_NAME, OffsetDateTime.now().toString())
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) } ?: error("no file")
                }
            }.isSuccess
            toast(if (ok) R.string.export_saved else R.string.export_failed)
        }
    }

    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = runCatching {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val bytes = input.readNBytes(SettingsFile.MAX_BYTES + 1)
                        if (bytes.size > SettingsFile.MAX_BYTES) throw ExportFormatException(newer = false)
                        bytes.toString(Charsets.UTF_8)
                    } ?: throw ExportFormatException(newer = false)
                }
                SettingsFile.parse(text)
            }
            result.onSuccess { pending = it }.onFailure { e ->
                toast(if (e is ExportFormatException && e.newer) R.string.import_too_new else R.string.import_not_valid)
            }
        }
    }

    Group(stringResource(R.string.export_title), footer = stringResource(R.string.export_footer)) {
        GroupRow(
            stringResource(R.string.export_save),
            stringResource(R.string.export_save_sub),
            icon = Icons.Outlined.FileDownload,
            onClick = {
                AppLock.expectReturn()
                save.launch("lijnctrl-saved-${LocalDate.now()}.json")
            },
        )
        GroupRow(
            stringResource(R.string.import_open),
            stringResource(R.string.import_open_sub),
            icon = Icons.Outlined.FileUpload,
            onClick = {
                AppLock.expectReturn()
                open.launch(arrayOf("application/json", "text/*", "application/octet-stream"))
            },
        )
    }

    pending?.let { e ->
        val current = c.saved.data.value
        val on = stringResource(R.string.import_on)
        val off = stringResource(R.string.import_off)
        val places = listOfNotNull(
            e.home?.let { stringResource(R.string.home) },
            e.work?.let { stringResource(R.string.work) },
        ).joinToString(", ").ifBlank { stringResource(R.string.import_none) }
        ConfirmDialog(
            title = stringResource(R.string.import_confirm_title),
            body = stringResource(
                R.string.import_confirm_body,
                e.created?.take(10) ?: "?",
                e.stops.size,
                current.stops.size,
                e.lines.size,
                current.lines.size,
                places,
                e.settings.useTransitous?.let { if (it) on else off } ?: stringResource(R.string.import_unchanged),
            ),
            confirm = stringResource(R.string.import_confirm),
            onDismiss = { pending = null },
        ) {
            pending = null
            scope.launch {
                if (c.saved.unreadable.value) {
                    toast(R.string.import_failed)
                    return@launch
                }
                val ok = runCatching {
                    c.saved.update { SettingsFile.applyTo(it, e) }
                    c.settings.update { SettingsFile.applyTo(it, e.settings) }
                    // Recent routes off in the file: the ones on this phone go, as when you turn it off in Settings.
                    if (e.settings.planHistory == false) c.planHistory.clear()
                }.isSuccess
                toast(if (ok) R.string.import_done else R.string.import_failed)
            }
        }
    }
}
