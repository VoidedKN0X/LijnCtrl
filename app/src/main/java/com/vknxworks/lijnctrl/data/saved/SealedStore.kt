package com.vknxworks.lijnctrl.data.saved

import com.vknxworks.lijnctrl.data.crypto.DataKeyStore
import com.vknxworks.lijnctrl.data.crypto.SealedFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Where the saved data lived before it was encrypted (the settings store), and still lives on a phone whose
 * secure hardware can't make a key.
 */
interface OldHome<T> {
    /**
     * What's there now.
     * - [present]: at least one saved value is there.
     * - [complete]: every value that's there could be read. When false, nothing is moved.
     */
    class Found<T>(val data: T, val present: Boolean, val complete: Boolean)

    suspend fun read(): Found<T>

    /** Writes what changed between [prev] and [next], the way the app always did. */
    suspend fun write(prev: T, next: T)

    /** Removes the saved values. Only called once they're safely in the encrypted file. */
    suspend fun clear()
}

/**
 * One value of type [T] kept in one file in the app's private storage, encrypted at rest with the app's data
 * key ([DataKeyStore], [SealedFile]).
 *
 * The data used to live in the settings store ([old]). It moves into the encrypted file once, at the first
 * start that has a data key, and in this order: the encrypted file is written to a temporary file, synced,
 * read back, decrypted and compared; it is renamed into place; and only then are the old values removed. If the
 * app dies anywhere on the way, the next start finds either the old values (and moves them again) or the
 * finished file (and removes old values that are still the same as the file). When there is no data key to be
 * had, nothing moves: the data stays in the settings store and is read and written there, exactly as before.
 *
 * A file that is there but can't be opened (the key in the phone's secure storage is gone or changed, or the
 * file is damaged) is never written over or deleted: [unreadable] turns on, every [update] does nothing, and
 * the app says what happened. [startEmpty] moves the file aside, it doesn't delete it.
 */
class SealedStore<T : Any>(
    dir: File,
    name: String,
    private val keys: DataKeyStore,
    private val old: OldHome<T>,
    private val empty: T,
    private val encode: (T) -> ByteArray,
    private val decode: (ByteArray) -> T,
    private val scope: CoroutineScope,
) {
    private val file = File(dir, name)
    private val tmp = File(dir, "$name.tmp")
    private val asideName = "$name.unreadable"
    private val lock = Mutex()

    /** The data is in the settings store (no data key on this phone), not in the file. Only changed under [lock]. */
    @Volatile private var inOldHome = false

    private val _data = MutableStateFlow(empty)
    val data: StateFlow<T> = _data

    private val _ready = MutableStateFlow(false)
    /** The saved data has been read (or there is none). Until then [data] is still empty. */
    val ready: StateFlow<Boolean> = _ready

    private val _unreadable = MutableStateFlow(false)
    val unreadable: StateFlow<Boolean> = _unreadable

    init {
        scope.launch(Dispatchers.IO) { open() }
    }

    /** Waits until the saved data has been read. */
    suspend fun awaitReady() {
        _ready.first { it }
    }

    private suspend fun open() = lock.withLock {
        try {
            val loaded = load()
            if (loaded != null) _data.value = loaded
            _unreadable.value = loaded == null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // The settings store itself couldn't be read. As before, the lists then show empty; a file that
            // is there is treated as unreadable so nothing is written over it.
            val there = file.exists()
            inOldHome = !there
            _unreadable.value = there
        } finally {
            _ready.value = true
        }
    }

    /** The contents of a sealed or ordinary file, or null when it's sealed and can't be opened. */
    private fun plainOf(bytes: ByteArray): ByteArray? {
        if (!SealedFile.isSealed(bytes)) return bytes
        val key = keys.get(create = false) ?: return null
        return try {
            SealedFile.open(key, bytes)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * When the file is missing but a complete temporary copy was left next to it (the app died after writing
     * the newest save and before it took the file's place), that copy is put in place. Only a copy that opens
     * and reads as saved data is used; anything else is left where it is.
     */
    private fun recoverMissing() {
        try {
            if (!tmp.exists()) return
            val plain = plainOf(tmp.readBytes()) ?: return
            decode(plain)
            tmp.renameTo(file)
        } catch (e: Exception) {
            // Not a usable copy.
        }
    }

    /** Null means: there is a file and it can't be opened. It is left untouched. */
    private suspend fun load(): T? {
        if (!file.exists()) recoverMissing()
        if (file.exists()) {
            inOldHome = false
            val bytes = try {
                file.readBytes()
            } catch (e: Exception) {
                return null
            }
            val sealed = SealedFile.isSealed(bytes)
            val plain = plainOf(bytes) ?: return null
            val loaded = try {
                decode(plain)
            } catch (e: Exception) {
                return null
            }
            if (!sealed) {
                // Written as an ordinary file while there was no key: sealed now if there is one.
                try {
                    val key = keys.get(create = true)
                    if (key != null) SealedFile.sealInPlace(key, file, tmp)
                } catch (e: Throwable) {
                    // The ordinary file stays in use.
                }
            }
            // Old values that were left behind when the app died right after the move: removed when they
            // still say exactly what the file says. Anything else is left alone, never deleted.
            try {
                val found = old.read()
                if (found.present && found.complete && found.data == loaded) old.clear()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
            }
            return loaded
        }

        // No file yet: the data is still where it always was.
        val found = old.read()
        val key = keys.get(create = true)
        if (key == null || !found.complete) {
            inOldHome = true
            return found.data
        }
        inOldHome = false
        if (!found.present) return empty
        val moved = try {
            val plain = encode(found.data)
            decode(plain) == found.data && SealedFile.writeNewChecked(key, plain, file, tmp)
        } catch (e: Throwable) {
            false
        }
        if (!moved) {
            // Nothing changed: it stays in the settings store for this run and is tried again at the next start.
            inOldHome = true
            return found.data
        }
        try {
            old.clear()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // Removed at the next start (see above).
        }
        return found.data
    }

    /**
     * Changes the saved data and writes it out. Waits for the first read; does nothing while [unreadable].
     * Like the settings store before, a failed write throws and leaves the data as it was.
     */
    suspend fun update(transform: (T) -> T) {
        awaitReady()
        withContext(Dispatchers.IO) {
            lock.withLock {
                if (_unreadable.value) return@withLock
                val prev = _data.value
                val next = transform(prev)
                if (next == prev) return@withLock
                if (inOldHome) {
                    old.write(prev, next)
                } else {
                    val plain = encode(next)
                    // Encrypted when there's a data key; an ordinary file otherwise.
                    val sealed = keys.get(create = true)?.let { SealedFile.sealChecked(it, plain) }
                    SealedFile.writeAtomic(file, tmp, sealed ?: plain)
                }
                _data.value = next
            }
        }
    }

    /** Tries to open the saved data again, after [unreadable]. */
    fun retry() {
        scope.launch(Dispatchers.IO) {
            keys.reset()
            open()
        }
    }

    /**
     * Gives up on a file that can't be opened and starts empty. The file is renamed to `<name>.unreadable`
     * (and a key that no longer works to `data.key.unreadable`), not deleted. Does nothing unless [unreadable].
     */
    suspend fun startEmpty() = withContext(Dispatchers.IO) {
        lock.withLock {
            if (!_unreadable.value) return@withLock
            try {
                if (file.exists()) {
                    var aside = File(file.parentFile, asideName)
                    if (aside.exists()) aside = File(file.parentFile, "$asideName.${System.currentTimeMillis()}")
                    file.renameTo(aside)
                }
            } catch (e: Throwable) {
            }
            // Only when the file really is out of the way: nothing new may ever be written over it.
            if (file.exists()) return@withLock
            // A key that still works stays; one that's gone makes way for a new one.
            if (keys.get(create = false) == null) keys.discard(keep = true)
            inOldHome = false
            _data.value = empty
            _unreadable.value = false
        }
    }
}
