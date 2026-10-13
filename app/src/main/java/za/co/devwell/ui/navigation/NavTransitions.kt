package za.co.devwell.ui.navigation

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

/**
 * Shared screen transitions used across the app so navigation feels quick,
 * consistent and deliberate.
 *
 * Pushed screens share a horizontal axis - the incoming screen slides in over
 * the outgoing one while both cross-fade. Top-level tabs simply cross-fade so
 * the persistent bottom bar never appears to slide.
 */

private const val SLIDE_DURATION_MS = 320
private const val FADE_DURATION_MS = 240
private const val TAB_FADE_DURATION_MS = 220

private val slideSpec: FiniteAnimationSpec<IntOffset> =
    tween(SLIDE_DURATION_MS, easing = FastOutSlowInEasing)
private val fadeSpec: FiniteAnimationSpec<Float> =
    tween(FADE_DURATION_MS, easing = FastOutSlowInEasing)

/** New screen enters from the right (forward navigation). */
val forwardEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    slideInHorizontally(slideSpec) { fullWidth -> fullWidth } + fadeIn(fadeSpec)
}

/** Current screen exits to the left (forward navigation). */
val forwardExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    slideOutHorizontally(slideSpec) { fullWidth -> -fullWidth } + fadeOut(fadeSpec)
}

/** Previous screen returns from the left (back navigation). */
val backwardEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    slideInHorizontally(slideSpec) { fullWidth -> -fullWidth } + fadeIn(fadeSpec)
}

/** Current screen exits to the right (back navigation). */
val backwardExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    slideOutHorizontally(slideSpec) { fullWidth -> fullWidth } + fadeOut(fadeSpec)
}

/** Tab destinations cross-fade instead of sliding. */
val tabEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    fadeIn(tween(TAB_FADE_DURATION_MS, easing = FastOutSlowInEasing))
}

val tabExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    fadeOut(tween(TAB_FADE_DURATION_MS, easing = FastOutSlowInEasing))
}

/**
 * Registers a top-level destination that cross-fades, keeping the persistent
 * bottom bar visually anchored as tabs are switched.
 */
fun NavGraphBuilder.tabComposable(
    route: String,
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) = composable(
    route = route,
    enterTransition = tabEnter,
    exitTransition = tabExit,
    popEnterTransition = tabEnter,
    popExitTransition = tabExit,
    content = content,
)
