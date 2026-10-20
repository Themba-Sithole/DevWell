package za.co.devwell.data.offline

import za.co.devwell.model.CheckInDraft

/** Thrown by saveCheckIn when there was no connection and the check-in was kept on the phone. */
class CheckInQueuedException : Exception("Saved on your phone. It will upload when you're back online.")

/** True for "couldn't reach the server"; false for rejections like an expired session or RLS. */
fun Throwable.isNetworkFailure(): Boolean {
    var cause: Throwable? = this
    var depth = 0
    while (cause != null && depth < 8) {
        if (cause is java.io.IOException) return true
        if (cause::class.simpleName.orEmpty().startsWith("HttpRequest")) return true
        cause = cause.cause
        depth++
    }
    return false
}

fun CheckInDraft.toPending(userId: String, localDate: String) = PendingCheckIn(
    id = java.util.UUID.randomUUID().toString(),
    userId = userId,
    localDate = localDate,
    wellbeing = wellbeing,
    workload = workload,
    energy = energy,
    focus = focus,
    afterHoursMinutes = afterHoursMinutes,
    note = note,
    createdAt = System.currentTimeMillis(),
)