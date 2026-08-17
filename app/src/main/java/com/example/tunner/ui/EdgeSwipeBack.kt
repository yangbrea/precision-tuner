package com.example.tunner.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * Observes a rightward swipe that starts at the left screen edge without
 * consuming pointer changes, so vertically scrolling children keep working.
 */
fun Modifier.edgeSwipeBack(
    enabled: Boolean,
    onBack: () -> Unit,
): Modifier = if (!enabled) this else pointerInput(onBack) {
    val edgeWidth = 24.dp.toPx()
    val returnDistance = 72.dp.toPx()

    awaitEachGesture {
        val down = awaitFirstDown(
            requireUnconsumed = false,
            pass = PointerEventPass.Initial,
        )
        if (down.position.x > edgeWidth) return@awaitEachGesture
        val start = down.position
        var lastPosition: Offset = start
        var cancelled = false

        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.size != 1) cancelled = true
            val change = event.changes.firstOrNull { it.id == down.id }
            if (change == null) {
                cancelled = true
                break
            }
            lastPosition = change.position
            if (!change.pressed) break
        }

        val delta = lastPosition - start
        if (!cancelled && shouldTriggerEdgeSwipe(start.x, delta.x, delta.y, edgeWidth, returnDistance)) {
            onBack()
        }
    }
}

internal fun shouldTriggerEdgeSwipe(
    startX: Float,
    deltaX: Float,
    deltaY: Float,
    edgeWidth: Float,
    returnDistance: Float,
): Boolean = startX <= edgeWidth &&
    deltaX >= returnDistance &&
    deltaX > kotlin.math.abs(deltaY) * 1.5f
