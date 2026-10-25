package com.vknxworks.lijnctrl.data.auth

import android.content.Context
import com.vknxworks.lijnctrl.data.lock.LockVault
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The De Lijn API key. On the phone it's always encrypted: with a Keystore key that never leaves the phone's
 * secure hardware, and, while the app lock is on, with the app PIN as well (sealed in [LockVault]). The
 * readable key only lives in memory while the app is unlocked, and is dropped the moment it locks.
 */
class ApiKeyStore(context: Context, private val vault: LockVault, name: String = "api_key") {
    private val prefs = context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)

    private val _hasKey = MutableStateFlow(prefs.contains(K_STORED))

    /** A key is saved (it may be unreadable right now, while the app is locked). */
    val hasKey: StateFlow<Boolean> = _hasKey.asStateFlow()

    private val _key = MutableStateFlow<String?>(null)

    /** The readable key, or null when there's none or the app is locked. */
    val key: StateFlow<String?> = _key.asStateFlow()

    init {
        // Without the app lock the key can be read straight away; with it, only after the first unlock.
        stored()?.let { if (!LockVault.isSealed(it)) _key.value = KeystoreCrypto.decrypt(it.removePrefix(KS_PREFIX)) }
    }

    private fun stored(): String? = prefs.getString(K_STORED, null)

    private fun write(value: String?) {
        prefs.edit().apply { if (value == null) remove(K_STORED) else putString(K_STORED, value) }.commit()
        _hasKey.value = value != null
    }

    /** Saves a new key: sealed with the PIN when the app lock is on, otherwise with the Keystore only. */
    fun set(key: String) {
        val trimmed = key.trim()
        val sealed = if (vault.enabled) vault.seal(trimmed) else null
        write(sealed ?: (KS_PREFIX + KeystoreCrypto.encrypt(trimmed)))
        _key.value = trimmed
    }

    fun clear() {
        write(null)
        _key.value = null
    }

    /** The app lock was just turned on: seal the key with the PIN from now on. */
    fun seal() {
        val plain = _key.value ?: return
        vault.seal(plain)?.let(::write)
    }

    /** The app lock is being turned off (the vault is still open): back to Keystore-only encryption. */
    fun unseal() {
        val plain = _key.value ?: stored()?.let(vault::open) ?: return
        write(KS_PREFIX + KeystoreCrypto.encrypt(plain))
        _key.value = plain
    }

    /** Locked: the readable key leaves memory. */
    fun onLocked() {
        if (vault.enabled) _key.value = null
    }

    /** Unlocked: read the key again, and seal it if it somehow isn't sealed yet. */
    fun onUnlocked() {
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
