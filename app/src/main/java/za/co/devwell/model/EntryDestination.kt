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

private val EMAIL_PATTERN = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

fun emailValidation(email: String): String? = when {
    email.isBlank() -> "Enter your email address."
    !EMAIL_PATTERN.matches(email.trim()) -> "Enter a valid email address."
    else -> null
}

fun nameValidation(name: String): String? =
    if (name.trim().length < 2) "Enter your full name." else null

fun passwordChangeValidation(password: String, confirmation: String): String? = when {
    password.isBlank() -> "Enter a new password."
    password.length < 8 -> "Use a password with at least 8 characters."
    password != confirmation -> "The passwords do not match."
    else -> null
}

fun loginValidation(email: String, password: String): String? =
    emailValidation(email) ?: when {
        password.isBlank() -> "Enter your password."
        else -> null
    }

fun registrationValidation(name: String, email: String, password: String, confirmation: String): String? =
    loginValidation(email, password) ?: when {
        nameValidation(name) != null -> "Enter your full name."
        password.length < 8 -> "Use a password with at least 8 characters."
        password != confirmation -> "The passwords do not match."
        else -> null
    }
