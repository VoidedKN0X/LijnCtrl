package com.vknxworks.lijnctrl.data.auth

import android.content.Context
import com.vknxworks.lijnctrl.data.lock.LockVault
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The De Lijn API key. On the phone it's always encrypted: with a Keystore key that never leaves the phone's
 * secure hardware, and, while the app lock is on, with the app PIN as well (sealed in [LockVault]). The
 * readable key only lives in memory while the app is unlocked, and is dropped the moment it locks.
 */
class ApiKeyStore(context: Context, private val vault: LockVault, scope: CoroutineScope, name: String = "api_key") {
    private val prefs = context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)

    private val _hasKey = MutableStateFlow(prefs.contains(K_STORED))

    /** A key is saved (it may be unreadable right now, while the app is locked). */
    val hasKey: StateFlow<Boolean> = _hasKey.asStateFlow()

    private val _key = MutableStateFlow<String?>(null)

    /** The readable key, or null when there's none or the app is locked. */
    val key: StateFlow<String?> = _key.asStateFlow()

    private val _loaded = MutableStateFlow(false)

    /**
     * The saved key has been read (or there's none, or it's sealed until the app is unlocked). Until then [key]
     * is still null and says nothing about whether the key can be read.
     */
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val loadLock = Any()

    init {
        // Without the app lock the key can be read straight away; with it, only after the first unlock. Reading
        // it asks the phone's secure hardware (slow, StrongBox especially), so that happens off the main thread.
        val s = stored()
        if (s == null || LockVault.isSealed(s)) _loaded.value = true
        else scope.launch(Dispatchers.Default) { ensureLoaded() }
    }

    /**
     * Reads the saved key now if the background read hasn't finished yet (it waits for it when it's running).
     * Everything that reads or changes the key outside the UI's own state goes through here first.
     */
    private fun ensureLoaded() {
        synchronized(loadLock) {
            if (_loaded.value) return
            stored()?.let { if (!LockVault.isSealed(it)) _key.value = KeystoreCrypto.decrypt(it.removePrefix(KS_PREFIX)) }
            _loaded.value = true
        }
    }

    /** The readable key right now, waiting for the saved key to be read first. May block briefly: not for composition. */
    fun current(): String? {
        ensureLoaded()
        return _key.value
    }

    private fun stored(): String? = prefs.getString(K_STORED, null)

    private fun write(value: String?) {
        prefs.edit().apply { if (value == null) remove(K_STORED) else putString(K_STORED, value) }.commit()
        _hasKey.value = value != null
    }

    /** Saves a new key: sealed with the PIN when the app lock is on, otherwise with the Keystore only. */
    fun set(key: String) {
        ensureLoaded()
        val trimmed = key.trim()
        val sealed = if (vault.enabled) vault.seal(trimmed) else null
        write(sealed ?: (KS_PREFIX + KeystoreCrypto.encrypt(trimmed)))
        _key.value = trimmed
    }

    fun clear() {
        ensureLoaded()
        write(null)
        _key.value = null
    }

    /** The app lock was just turned on: seal the key with the PIN from now on. */
    fun seal() {
        ensureLoaded()
        val plain = _key.value ?: return
        vault.seal(plain)?.let(::write)
    }

    /** The app lock is being turned off (the vault is still open): back to Keystore-only encryption. */
    fun unseal() {
        ensureLoaded()
        val plain = _key.value ?: stored()?.let(vault::open) ?: return
        write(KS_PREFIX + KeystoreCrypto.encrypt(plain))
        _key.value = plain
    }

    /** Locked: the readable key leaves memory. */
    fun onLocked() {
        ensureLoaded()
        if (vault.enabled) _key.value = null
    }

    /** Unlocked: read the key again, and seal it if it somehow isn't sealed yet. */
    fun onUnlocked() {
        ensureLoaded()
        val s = stored() ?: return
        if (LockVault.isSealed(s)) {
            _key.value = vault.open(s)
        } else {
            val plain = KeystoreCrypto.decrypt(s.removePrefix(KS_PREFIX))
            _key.value = plain
            if (plain != null && vault.enabled) vault.seal(plain)?.let(::write)
        }
    }

    private companion object {
        const val K_STORED = "stored"
        const val KS_PREFIX = "ks1:"
    }
}
