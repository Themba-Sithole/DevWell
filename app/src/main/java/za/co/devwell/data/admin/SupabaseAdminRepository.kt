package za.co.devwell.data.admin

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.ktor.client.call.body
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import za.co.devwell.data.AdminRepository
import za.co.devwell.model.AccountStatus
import za.co.devwell.model.DevWellUser
import za.co.devwell.model.UserRole

class SupabaseAdminRepository(
    private val client: SupabaseClient,
    currentUser: Flow<DevWellUser?>,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
) : AdminRepository {
    private val _stats = MutableStateFlow<Map<String, Int>>(emptyMap())
    override val stats: Flow<Map<String, Int>> = _stats.asStateFlow()

    private val _users = MutableStateFlow<List<DevWellUser>>(emptyList())
    override val users: Flow<List<DevWellUser>> = _users.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    override val isLoading: Flow<Boolean> = _isLoading.asStateFlow()

    private val _busyUserId = MutableStateFlow<String?>(null)
    override val busyUserId: Flow<String?> = _busyUserId.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    override val errorMessage: Flow<String?> = _errorMessage.asStateFlow()

    init {
        scope.launch {
            currentUser.collectLatest { user ->
                if (user?.role == UserRole.ADMIN && user.status == AccountStatus.ACTIVE && user.onboardingDone) {
                    loadDashboard()
                } else {
                    _stats.value = emptyMap()
                    _users.value = emptyList()
                    _errorMessage.value = null
                }
            }
        }
    }

    override suspend fun refresh() = loadDashboard()

    override fun clearError() {
        _errorMessage.value = null
    }

    override suspend fun setUserStatus(userId: String, active: Boolean) {
        if (_busyUserId.value != null) return
        _busyUserId.value = userId
        _errorMessage.value = null
        try {
            client.functions.invoke(
                "admin-dashboard",
                body = buildJsonObject {
                    put("action", "set_status")
                    put("user_id", userId)
                    put("status", if (active) "active" else "suspended")
                },
            )
            loadDashboard()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            _errorMessage.value = "Couldn't update this account. Check your access or connection, then retry."
        } finally {
            _busyUserId.value = null
        }
    }

    private suspend fun loadDashboard() {
        _isLoading.value = true
        try {
            val response = client.functions.invoke(
                "admin-dashboard",
                body = buildJsonObject { put("action", "list") },
            ).body<AdminDashboardReply>()
            _stats.value = response.stats
            _users.value = response.users.map(AdminUserRow::toDomain)
            _errorMessage.value = null
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            _stats.value = emptyMap()
            _users.value = emptyList()
            _errorMessage.value = "Couldn't load admin accounts. Check your access or connection, then retry."
        } finally {
            _isLoading.value = false
        }
    }
}

@Serializable
data class AdminDashboardReply(
    val stats: Map<String, Int>,
    val users: List<AdminUserRow>,
)

@Serializable
data class AdminUserRow(
    val id: String,
    val name: String,
    val email: String,
    val role: String,
    val status: String,
    val companyId: String? = null,
    val teamId: String? = null,
    val teamName: String? = null,
    val onboardingDone: Boolean,
) {
    fun toDomain() = DevWellUser(
        id = id,
        name = name,
        email = email,
        role = UserRole.valueOf(role.uppercase()),
        status = AccountStatus.valueOf(status.uppercase()),
        companyId = companyId,
        teamId = teamId,
        teamName = teamName,
        onboardingDone = onboardingDone,
    )
}
