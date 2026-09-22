package za.co.devwell.model

/**
 * Shared anonymity rule used by:
 * - Manager Team Insights (FR8, Story 3)
 * - Engineer "What my manager sees" (FR9, Story 2)
 *
 * Enforce the same threshold in UI AND eventually in a Supabase Edge Function.
 * Hiding a Compose button is not a privacy control.
 */
object PrivacyPolicy {
    const val MINIMUM_ANONYMOUS_RESPONSES = 5

    fun canShowTeamInsights(responseCount: Int): Boolean =
        responseCount >= MINIMUM_ANONYMOUS_RESPONSES

    /**
     * Returns the aggregate only when the threshold is met; otherwise null.
     * Both manager and engineer preview screens must call this (or the same server API).
     */
    fun safeAggregateOrNull(aggregate: TeamAggregate): TeamAggregate? =
        if (canShowTeamInsights(aggregate.responseCount)) aggregate else null
}
