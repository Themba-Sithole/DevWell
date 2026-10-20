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
    const val INSIGHTS = "engineer/insights"
    const val TEAM_OVERVIEW = "engineer/team_overview"
    const val PROJECTS = "engineer/projects"
    const val PRIVACY_PREVIEW = "engineer/privacy_preview"
    const val AI_ASSISTANT = "engineer/ai"
    const val ENGINEER_PROFILE = "engineer/profile"
    const val NOTIFICATIONS = "engineer/notifications"
    const val TIPS = "engineer/tips"
    const val EXERCISES = "engineer/exercises"
    // General settings are shared by every role.
    const val SETTINGS = "settings"
    const val ACCOUNT_SETTINGS = "settings/account"
    const val MANAGER_VISIBILITY = "settings/manager_visibility"
    const val HELP_ABOUT = "settings/help_about"
    const val PRIVACY_POLICY = "settings/privacy_policy"
    const val TERMS_OF_SERVICE = "settings/terms_of_service"

    // Manager
    const val MANAGER_HOME = "manager/home"
    const val MANAGER_TEAM = "manager/team"
    const val MANAGER_INSIGHTS = "manager/insights"
    const val JOIN_REQUESTS = "manager/join_requests"
    const val TEAM_SETTINGS = "manager/team_settings"
    const val MANAGER_PROFILE = "manager/profile"

    // Admin
    const val ADMIN_HOME = "admin/home"
    const val ADMIN_PROFILE = "admin/profile"
}
