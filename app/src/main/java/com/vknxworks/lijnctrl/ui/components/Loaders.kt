package com.vknxworks.lijnctrl.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vknxworks.lijnctrl.data.api.ApiException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.UnknownHostException
import kotlin.coroutines.cancellation.CancellationException
import androidx.compose.ui.res.stringResource
import com.vknxworks.lijnctrl.R
import com.vknxworks.lijnctrl.str

sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Ok<T>(val value: T) : Load<T>
    data class Err(val message: String) : Load<Nothing>
}

fun Throwable.friendlyMessage(): String = when (this) {
    is ApiException -> message ?: str(R.string.server_error)
    is UnknownHostException -> str(R.string.can_t_reach_de_lijn)
    is java.net.ConnectException -> str(R.string.can_t_reach_de_lijn)
    is java.net.SocketTimeoutException -> str(R.string.de_lijn_took_too_long)
    is javax.net.ssl.SSLException -> str(R.string.tls_failed, message ?: str(R.string.certificate_problem))
    is IOException -> message ?: str(R.string.network_error)
    else -> message ?: this::class.simpleName ?: str(R.string.something_went_wrong)
}

/** Loads a value once and keeps it for the life of the screen's back stack entry. */
class Loader<T>(private val scope: CoroutineScope, private val block: suspend () -> T) {
    var state by mutableStateOf<Load<T>>(Load.Loading)
        private set
    var refreshing by mutableStateOf(false)
        private set
    private var job: Job? = null

    val value: T? get() = (state as? Load.Ok<T>)?.value

    init {
        reload()
    }

    /** Re-fetch. A silent reload keeps the current content on screen while it runs. */
    fun reload(silent: Boolean = false) {
        job?.cancel()
        if (!silent || state !is Load.Ok) state = Load.Loading
        refreshing = silent
        job = scope.launch {
            try {
                state = Load.Ok(block())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (!silent || state !is Load.Ok) state = Load.Err(e.friendlyMessage())
            } finally {
                refreshing = false
            }
        }
    }

    fun set(value: T) {
        state = Load.Ok(value)
    }
}

/** Incrementally loaded list for big libraries. */
class PagedLoader<T>(
    private val scope: CoroutineScope,
    private val pageSize: Int = 100,
    private val fetch: suspend (start: Int, limit: Int) -> Pair<List<T>, Int>,
) {
    val items = mutableStateListOf<T>()
    var total by mutableStateOf(-1)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    private var job: Job? = null

    val endReached: Boolean get() = total >= 0 && items.size >= total

    init {
        loadMore()
    }

    fun loadMore() {
        if (loading || endReached) return
        loading = true
        job = scope.launch {
            try {
                val (page, count) = fetch(items.size, pageSize)
                items.addAll(page)
                total = if (page.isEmpty()) items.size else count
                error = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                error = e.friendlyMessage()
            } finally {
                loading = false
            }
        }
    }

    fun refresh() {
        job?.cancel()
        loading = false
        items.clear()
        total = -1
        error = null
        loadMore()
    }
}

class LoaderStore : ViewModel() {
    val map = mutableMapOf<String, Any>()
}

@Composable
fun <T> rememberLoader(key: String, block: suspend () -> T): Loader<T> {
    val store: LoaderStore = viewModel()
    @Suppress("UNCHECKED_CAST")
    return remember(key) { store.map.getOrPut("L:$key") { Loader(store.viewModelScope, block) } as Loader<T> }
}

@Composable
fun <T> rememberPagedLoader(key: String, pageSize: Int = 100, fetch: suspend (Int, Int) -> Pair<List<T>, Int>): PagedLoader<T> {
    val store: LoaderStore = viewModel()
    @Suppress("UNCHECKED_CAST")
    return remember(key) { store.map.getOrPut("P:$key") { PagedLoader(store.viewModelScope, pageSize, fetch) } as PagedLoader<T> }
}

@Composable
fun <T> LoadContent(
    loader: Loader<T>,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    when (val s = loader.state) {
        is Load.Loading -> LoadingView(modifier)
        is Load.Err -> ErrorView(s.message, modifier) { loader.reload() }
        is Load.Ok -> content(s.value)
    }
}

@Composable
fun LoadingView(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, trackColor = com.vknxworks.lijnctrl.ui.theme.CtrlColors.ProgressTrack, strokeWidth = 3.dp) }
}

@Composable
fun ErrorView(message: String, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    MessageView(Icons.Outlined.ErrorOutline, stringResource(R.string.couldn_t_load_this), message, modifier, actionLabel = onRetry?.let { stringResource(R.string.retry) }, onAction = onRetry)
}

@Composable
fun MessageView(
    icon: ImageVector,
    title: String,
    body: String? = null,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 360.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconTile(icon)
            Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            if (body != null) {
                Text(
                    body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            if (actionLabel != null && onAction != null) {
                CtrlSecondaryButton(actionLabel, onClick = onAction)
            }
        }
    }
}
