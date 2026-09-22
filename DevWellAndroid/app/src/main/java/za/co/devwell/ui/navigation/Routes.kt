package za.co.devwell.ui.navigation

/** Type-safe route ids for Navigation Compose. */
object Routes {
    const val LOGIN = "login"
    const val ONBOARDING = "onboarding"
    const val PENDING = "pending"

    // Engineer
    const val ENGINEER_HOME = "engineer/home"
    const val CHECK_IN = "engineer/check_in"
    const val TRENDS = "engineer/trends"
    const val PROJECTS = "engineer/projects"
    const val PRIVACY_PREVIEW = "engineer/privacy_preview"
    const val AI_ASSISTANT = "engineer/ai"
    const val ENGINEER_PROFILE = "engineer/profile"
    const val NOTIFICATIONS = "engineer/notifications"

    // Manager
    const val MANAGER_HOME = "manager/home"
    const val JOIN_REQUESTS = "manager/join_requests"
    const val TEAM_SETTINGS = "manager/team_settings"
    const val MANAGER_PROFILE = "manager/profile"

    // Admin
    const val ADMIN_HOME = "admin/home"
    const val ADMIN_PROFILE = "admin/profile"
}
