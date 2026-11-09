package com.safestep.mobile.core.session

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Tokens and identity of the signed-in user. */
data class Session(
    val username: String,
    val accessToken: String,
    val refreshToken: String,
    val roles: List<String> = emptyList(),
)

/** Where the session survives between launches. */
interface TokenStore {
    fun load(): Session?
    fun save(session: Session)
    fun clear()
}

/** Keeps the session only in memory; used by tests and previews. */
class InMemoryTokenStore(private var stored: Session? = null) : TokenStore {
    override fun load(): Session? = stored
    override fun save(session: Session) {
        stored = session
    }
    override fun clear() {
        stored = null
    }
}

/**
 * Stores the session encrypted with an AES-GCM key that never leaves the Android Keystore, so the
 * tokens are unreadable from a backup or from the preferences file alone.
 */
class SecureTokenStore(context: Context) : TokenStore {
    private val prefs = context.applicationContext.getSharedPreferences("safestep_session", Context.MODE_PRIVATE)

    override fun load(): Session? {
        val encoded = prefs.getString(KEY_PAYLOAD, null) ?: return null
        return runCatching {
            val bytes = Base64.decode(encoded, Base64.NO_WRAP)
            val iv = bytes.copyOfRange(0, IV_BYTES)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv))
            val plain = String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES), Charsets.UTF_8)
            val parts = plain.split(SEPARATOR)
            Session(parts[0], parts[1], parts[2], parts.getOrNull(3)?.split(',')?.filter { it.isNotBlank() } ?: emptyList())
        }.getOrElse {
            clear()
            null
        }
    }

    override fun save(session: Session) {
        val plain = listOf(session.username, session.accessToken, session.refreshToken, session.roles.joinToString(","))
            .joinToString(SEPARATOR)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        prefs.edit().putString(KEY_PAYLOAD, Base64.encodeToString(encrypted, Base64.NO_WRAP)).apply()
    }

    override fun clear() {
        prefs.edit().remove(KEY_PAYLOAD).apply()
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "safestep_session_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_PAYLOAD = "payload"
        const val SEPARATOR = "\u0000"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}

/** Single source of truth for who is signed in. */
class SessionManager(private val store: TokenStore) {
    private val state = MutableStateFlow(store.load())

    /** Current session, or null when nobody is signed in. */
    val session: StateFlow<Session?> = state.asStateFlow()

    val accessToken: String? get() = state.value?.accessToken
    val refreshToken: String? get() = state.value?.refreshToken
    val isSignedIn: Boolean get() = state.value != null

    fun start(session: Session) {
        store.save(session)
        state.value = session
    }

    fun end() {
        store.clear()
        state.value = null
    }
}
