package za.co.devwell.model

enum class EntryDestination { LOGIN, ONBOARDING, PENDING, ENGINEER, MANAGER, ADMIN }

/** A pending new engineer can choose a team, but cannot enter protected screens. */
fun entryDestination(user: DevWellUser?): EntryDestination = when {
    user == null -> EntryDestination.LOGIN
    user.status == AccountStatus.REJECTED -> EntryDestination.PENDING
    user.status == AccountStatus.SUSPENDED -> EntryDestination.PENDING
    user.role == UserRole.ENGINEER && !user.onboardingDone -> EntryDestination.ONBOARDING
    user.status != AccountStatus.ACTIVE || !user.onboardingDone -> EntryDestination.PENDING
    user.role == UserRole.ENGINEER && (user.companyId.isNullOrBlank() || user.teamId.isNullOrBlank()) -> EntryDestination.ONBOARDING
    user.role == UserRole.ENGINEER -> EntryDestination.ENGINEER
    user.role == UserRole.MANAGER -> EntryDestination.MANAGER
    else -> EntryDestination.ADMIN
}

fun loginValidation(email: String, password: String): String? = when {
    email.isBlank() -> "Enter your email address."
    !Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(email.trim()) -> "Enter a valid email address."
    password.isBlank() -> "Enter your password."
    else -> null
}

fun registrationValidation(name: String, email: String, password: String, confirmation: String): String? =
    loginValidation(email, password) ?: when {
        name.trim().length < 2 -> "Enter your full name."
        password.length < 8 -> "Use a password with at least 8 characters."
        password != confirmation -> "The passwords do not match."
        else -> null
    }
