package za.co.devwell.auth

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import za.co.devwell.data.auth.DeviceSessionManager

class DeviceSessionManagerTest {
    @Test fun savedSessionIsEncryptedAndCanBeRestoredThenDeleted() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = DeviceSessionManager(context)
        val session = UserSession(accessToken = "test-access-token", refreshToken = "test-refresh-token",
            expiresIn = 3600, tokenType = "bearer", user = null)
        try {
            manager.saveSession(session)
            val raw = context.getSharedPreferences("devwell_session", Context.MODE_PRIVATE).getString("session", "")!!
            assertFalse(raw.contains("test-access-token"))
            assertFalse(raw.contains("test-refresh-token"))
            assertEquals(session.accessToken, DeviceSessionManager(context).loadSession()?.accessToken)
            manager.deleteSession()
            assertNull(manager.loadSession())
        } finally { manager.deleteSession() }
    }

    @Test fun corruptedSessionReturnsToSignedOutState() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = DeviceSessionManager(context)
        try {
            context.getSharedPreferences("devwell_session", Context.MODE_PRIVATE).edit()
                .putString("session", "not-a-valid-session").putString("iv", "invalid").commit()
            assertNull(manager.loadSession())
        } finally { manager.deleteSession() }
    }
}
