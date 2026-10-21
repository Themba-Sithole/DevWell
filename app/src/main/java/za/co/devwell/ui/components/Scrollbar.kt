package za.co.devwell.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import za.co.devwell.ui.theme.DevGreen

/**
 * A slim, theme-coloured scrollbar that stays hidden until the user touches or scrolls
 * the page, then fades out again. It never appears when the content fits on screen.
 */
fun Modifier.devWellScrollbar(state: ScrollState): Modifier = themedScrollbar(
    scrolling = { state.isScrollInProgress },
    thumb = {
        val max = state.maxValue
        val viewport = state.viewportSize
        if (max <= 0 || viewport <= 0) null
        else {
            val fraction = (viewport.toFloat() / (viewport + max)).coerceIn(0.08f, 1f)
            (state.value / max.toFloat()) to fraction
        }
    },
)

/** Lazy-list variant: estimates the thumb from the visible items. */
fun Modifier.devWellScrollbar(state: LazyListState): Modifier = themedScrollbar(
    scrolling = { state.isScrollInProgress },
    thumb = {
        val info = state.layoutInfo
        val total = info.totalItemsCount
        val first = info.visibleItemsInfo.firstOrNull()
        if (total <= 1 || first == null) null
        else {
            val itemHeight = first.size.coerceAtLeast(1)
            val visibleCount = info.visibleItemsInfo.size.coerceAtLeast(1)
            val firstPosition = first.index + state.firstVisibleItemScrollOffset.toFloat() / itemHeight
            val maxFirst = (total - visibleCount).coerceAtLeast(1).toFloat()
            val fraction = (visibleCount.toFloat() / total).coerceIn(0.08f, 1f)
            (firstPosition / maxFirst).coerceIn(0f, 1f) to fraction
        }
    },
)

private fun Modifier.themedScrollbar(
    scrolling: () -> Boolean,
    thumb: DrawScope.() -> Pair<Float, Float>?,
): Modifier = composed {
    var touched by remember { mutableStateOf(false) }
    val active = touched || scrolling()
    val alpha by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "scrollbarAlpha",
    )

    LaunchedEffect(touched) {
        if (touched) {
            delay(1_200)
            touched = false
        }
    }

    pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.any { it.pressed }) touched = true
            }
        }
    }.drawWithContent {
        drawContent()
        if (alpha <= 0.01f) return@drawWithContent
        val data = thumb() ?: return@drawWithContent
        val (position, fraction) = data
        if (fraction >= 1f) return@drawWithContent

        val width = 3.dp.toPx()
        val margin = 2.dp.toPx()
        val minHeight = 36.dp.toPx().coerceAtMost(size.height)
        val height = (size.height * fraction).coerceIn(minHeight, size.height)
        val top = ((size.height - height) * position).coerceIn(0f, size.height - height)
        drawRoundRect(
            color = scrollbarColour(alpha),
            topLeft = Offset(size.width - width - margin, top),
            size = Size(width, height),
            cornerRadius = CornerRadius(width / 2f),
        )
    }
}

private fun scrollbarColour(alpha: Float): Color = DevGreen.copy(alpha = 0.5f * alpha)
