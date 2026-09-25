package za.co.devwell.data.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** The SDK manages token refresh. This class only encrypts its saved session. */
class DeviceSessionManager(context: Context) : SessionManager {
    private val preferences = context.getSharedPreferences("devwell_session", Context.MODE_PRIVATE)
    private val keyAlias = "devwell_session_key"
    private val json = Json { ignoreUnknownKeys = true }

    private fun encryptionKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = store.getKey(keyAlias, null) as? SecretKey
        if (existing != null) return existing
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build())
        }.generateKey()
    }

    override suspend fun saveSession(session: UserSession) = withContext(Dispatchers.IO) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey())
        val encrypted = cipher.doFinal(json.encodeToString(session).toByteArray(Charsets.UTF_8))
        check(preferences.edit()
            .putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("session", Base64.encodeToString(encrypted, Base64.NO_WRAP)).commit())
    }

    override suspend fun loadSession(): UserSession? = withContext(Dispatchers.IO) {
        val saved = preferences.getString("session", null) ?: return@withContext null
        try {
            val iv = preferences.getString("iv", null) ?: error("Missing session IV")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
            val plain = cipher.doFinal(Base64.decode(saved, Base64.NO_WRAP))
            json.decodeFromString<UserSession>(String(plain, Charsets.UTF_8))
        } catch (_: Exception) {
            // A reinstalled app or invalidated device key must ask for sign-in again.
            preferences.edit().clear().commit()
            null
        }
    }

    override suspend fun deleteSession() = withContext(Dispatchers.IO) {
        check(preferences.edit().clear().commit())
    }
}
