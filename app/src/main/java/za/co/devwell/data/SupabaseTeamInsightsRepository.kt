package za.co.devwell.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import za.co.devwell.model.AccountStatus
import za.co.devwell.model.DevWellUser
import za.co.devwell.model.PrivacyPolicy
import za.co.devwell.model.Team
import za.co.devwell.model.TeamAggregate
import za.co.devwell.model.UserRole
import za.co.devwell.model.TrendsAnalysis
import za.co.devwell.model.TrendsPeriod

/** Shared safe aggregate source used by the engineer preview and manager dashboard. */
class SupabaseTeamInsightsRepository(
    private val client: SupabaseClient,
    currentUser: Flow<DevWellUser?>,
    managedTeam: Flow<Team?>,
    scope: CoroutineScope,
) : TeamInsightsRepository {
    private val aggregate = MutableStateFlow<TeamAggregate?>(null)
    override val teamAggregate: Flow<TeamAggregate?> = aggregate
    private val json = Json { ignoreUnknownKeys = true }

    init {
        scope.launch {
            combine(currentUser, managedTeam) { user, team -> user to team }
                .collectLatest { (user, team) ->
                    aggregate.value = null
                    val teamId = when (user?.role) {
                        UserRole.ENGINEER -> user.teamId
                        UserRole.MANAGER -> team?.id
                        else -> null
                    }
                    if (user == null || user.status != AccountStatus.ACTIVE || !user.onboardingDone || teamId.isNullOrBlank()) {
                        return@collectLatest
                    }
                    try {
                        val range = TrendsAnalysis.range(TrendsPeriod.LAST_THREE_MONTHS)
                        val response = client.functions.invoke(
                            "team-aggregate",
                            body = buildJsonObject {
                                put("team_id", teamId)
                                put("start_date", range.start.toString())
                                put("end_date", range.endInclusive.toString())
                            },
                        )
                        val result = json.decodeFromString<TeamAggregateReply>(response.bodyAsText())
                        if (result.teamId != teamId || result.responseCount < 0) return@collectLatest
                        aggregate.value = TeamAggregate(
                            teamId = result.teamId,
                            responseCount = result.responseCount,
                            averageWellbeing = result.averageWellbeing.takeIf { result.responseCount >= PrivacyPolicy.MINIMUM_ANONYMOUS_RESPONSES },
                            averageWorkload = result.averageWorkload.takeIf { result.responseCount >= PrivacyPolicy.MINIMUM_ANONYMOUS_RESPONSES },
                            averageEnergy = result.averageEnergy.takeIf { result.responseCount >= PrivacyPolicy.MINIMUM_ANONYMOUS_RESPONSES },
                            averageFocus = result.averageFocus.takeIf { result.responseCount >= PrivacyPolicy.MINIMUM_ANONYMOUS_RESPONSES },
                            averageAfterHoursMinutes = result.averageAfterHoursMinutes.takeIf { result.responseCount >= PrivacyPolicy.MINIMUM_ANONYMOUS_RESPONSES },
                        )
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        // Keep the preview and manager dashboard empty on network/auth failure.
                        aggregate.value = null
                    }
                }
        }
    }
}

@Serializable
private data class TeamAggregateReply(
    @SerialName("team_id") val teamId: String,
    @SerialName("response_count") val responseCount: Int,
    @SerialName("average_wellbeing") val averageWellbeing: Double? = null,
    @SerialName("average_workload") val averageWorkload: Double? = null,
    @SerialName("average_energy") val averageEnergy: Double? = null,
    @SerialName("average_focus") val averageFocus: Double? = null,
    @SerialName("average_after_hours_minutes") val averageAfterHoursMinutes: Double? = null,
)
