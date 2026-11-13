package com.vknxworks.lijnctrl.data.api

import com.vknxworks.lijnctrl.R
import com.vknxworks.lijnctrl.str
import okhttp3.ResponseBody
import okio.Buffer
import java.io.IOException

/** Most a De Lijn or Transitous JSON answer may be. The largest real ones (a day's timetable) are far smaller. */
const val MAX_JSON_BYTES = 16L * 1024 * 1024

/** Most the GTFS Realtime feed (about 250 KB today) may be as downloaded, and once unpacked. */
const val MAX_FEED_BYTES = 32L * 1024 * 1024
const val MAX_FEED_UNPACKED_BYTES = 64L * 1024 * 1024

/**
 * Reads at most [max] bytes of a response body. A bigger answer is refused instead of filling the phone's
 * memory (a broken or hostile server can't crash the app this way).
 */
fun ResponseBody.bytesAtMost(max: Long): ByteArray {
    if (contentLength() > max) throw IOException(str(R.string.answer_too_large))
    val source = source()
    val buffer = Buffer()
    while (buffer.size <= max) {
        if (source.read(buffer, 64L * 1024) == -1L) break
    }
    if (buffer.size > max) throw IOException(str(R.string.answer_too_large))
    return buffer.readByteArray()
}

/** [bytesAtMost] as UTF-8 text (every API LijnCtrl talks to answers JSON in UTF-8). */
fun ResponseBody.textAtMost(max: Long = MAX_JSON_BYTES): String = bytesAtMost(max).decodeToString()
