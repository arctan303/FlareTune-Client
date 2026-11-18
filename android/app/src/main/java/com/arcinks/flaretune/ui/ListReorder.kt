package com.arcinks.flaretune.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.LocalPinnableContainer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.isActive

/** One owner for the entire handle gesture, independent of the moving LazyColumn item. */
@Stable
internal class ListReorderState(
    val listState: LazyListState,
    private val keys: () -> List<String>,
    private val move: (Int, Int) -> Unit,
    private val started: (String) -> Unit,
    private val finished: (String, List<String>, Boolean) -> Unit,
    private val ownershipChanged: (Boolean) -> Unit,
) {
    var activeKey by mutableStateOf<String?>(null)
        private set
    val owned: Boolean get() = activeKey != null
    private var pointerWindowY by mutableFloatStateOf(0f)
    private var fingerOffsetFromCenter = 0f
    private var itemHeight = 0f
    private var originPointerWindowY = 0f
    private var originalKeys = emptyList<String>()
    private val handles = mutableMapOf<String, Rect>()
    private val itemTops = mutableStateMapOf<String, Float>()
    var coordinates: LayoutCoordinates? = null
    var viewportHeight by mutableFloatStateOf(0f)
        private set
    private var listTop by mutableFloatStateOf(0f)

    fun positionList(value: LayoutCoordinates) {
        coordinates = value
        listTop = value.positionInWindow().y
        viewportHeight = value.size.height.toFloat()
    }

    fun positionHandle(key: String, bounds: Rect) { handles[key] = bounds }
    fun removeHandle(key: String) { handles.remove(key); itemTops.remove(key) }
    fun hitHandle(point: Offset): String? = handles.entries.firstOrNull {
        point in it.value && it.key in keys()
    }?.key

    fun positionItem(key: String, value: LayoutCoordinates) {
        itemTops[key] = value.positionInWindow().y - listTop
    }

    fun begin(key: String, windowY: Float): Boolean {
        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return false
        originalKeys = keys().toList()
        pointerWindowY = windowY
        originPointerWindowY = windowY
        itemHeight = item.size.toFloat()
        fingerOffsetFromCenter = windowY - listTop - (item.offset + item.size / 2f)
        started(key)
        activeKey = key
        ownershipChanged(true)
        return true
    }

    private val pointerCenter: Float get() = pointerWindowY - listTop - fingerOffsetFromCenter
    private val drawnCenter: Float get() = clampReorderCenter(pointerCenter, itemHeight, viewportHeight)

    fun translation(key: String): Float {
        if (key != activeKey) return 0f
        val top = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }?.offset?.toFloat()
            ?: itemTops[key] ?: return 0f
        return drawnCenter - top - itemHeight / 2f
    }

    fun update(windowY: Float) {
        if (!owned) return
        pointerWindowY = windowY
        reorder()
    }

    fun reorder() {
        val key = activeKey ?: return
        val currentKeys = keys()
        val from = currentKeys.indexOf(key)
        if (from < 0) return
        val visible = listState.layoutInfo.visibleItemsInfo
        // A taller dragged row must still be able to cross a shorter neighbor at either edge.
        val center = pointerCenter.coerceIn(0f, viewportHeight)
        val slot = visible.firstOrNull { it.key == key }
        val slotCenter = slot?.let { it.offset + it.size / 2f } ?: center
        val target = if (center >= slotCenter) {
            visible.filter { info ->
                val index = currentKeys.indexOfFirst { it == info.key }
                index > from && center >= info.offset + info.size / 2f
            }.maxOfOrNull { currentKeys.indexOfFirst { key -> key == it.key } }
        } else {
            visible.filter { info ->
                val index = currentKeys.indexOfFirst { it == info.key }
                index in 0 until from && center <= info.offset + info.size / 2f
            }.minOfOrNull { currentKeys.indexOfFirst { key -> key == it.key } }
        }
        if (target != null && target != from) {
            // Reordering the first visible key must not scroll the viewport with that row.
            listState.requestScrollToItem(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
            move(from, target)
        }
    }

    fun scrollDelta(edgePx: Float, maxStepPx: Float, minTravelPx: Float): Float = reorderScrollDelta(
        pointerY = pointerWindowY - listTop,
        originY = originPointerWindowY - listTop,
        height = viewportHeight,
        edge = edgePx,
        maxStep = maxStepPx,
        minTravel = minTravelPx,
        canScrollBack = listState.canScrollBackward,
        canScrollForward = listState.canScrollForward,
    )

    fun finish(commit: Boolean) {
        val key = activeKey ?: return
        activeKey = null
        if (!commit) {
            // Restoring the draft must keep the viewport at the same list position.
            listState.requestScrollToItem(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
        }
        try { finished(key, originalKeys, commit) } finally { ownershipChanged(false) }
    }
}

internal fun clampReorderCenter(center: Float, itemHeight: Float, viewportHeight: Float): Float {
    val half = (itemHeight / 2f).coerceAtMost(viewportHeight / 2f).coerceAtLeast(0f)
    return center.coerceIn(half, (viewportHeight - half).coerceAtLeast(half))
}

internal fun reorderScrollDelta(
    pointerY: Float, originY: Float, height: Float, edge: Float, maxStep: Float,
    minTravel: Float, canScrollBack: Boolean, canScrollForward: Boolean,
): Float {
    if (edge <= 0f || height <= edge * 2f) return 0f
    return when {
        pointerY < edge && pointerY < originY - minTravel && canScrollBack -> {
            val ratio = ((edge - pointerY) / edge).coerceIn(0f, 1f)
            -maxStep * ratio * ratio
        }
        pointerY > height - edge && pointerY > originY + minTravel && canScrollForward -> {
            val ratio = ((pointerY - (height - edge)) / edge).coerceIn(0f, 1f)
            maxStep * ratio * ratio
        }
        else -> 0f
    }
}

@Composable
internal fun rememberListReorderState(
    listState: LazyListState,
    keys: () -> List<String>,
    onMove: (Int, Int) -> Unit,
    onStart: (String) -> Unit = {},
    onFinish: (String, List<String>, Boolean) -> Unit,
    onOwnershipChanged: (Boolean) -> Unit = {},
): ListReorderState {
    val latestKeys by rememberUpdatedState(keys)
    val latestMove by rememberUpdatedState(onMove)
    val latestStart by rememberUpdatedState(onStart)
    val latestFinish by rememberUpdatedState(onFinish)
    val latestOwnership by rememberUpdatedState(onOwnershipChanged)
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val state = remember(listState) {
        ListReorderState(listState, { latestKeys() }, { from, to ->
            latestMove(from, to)
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }, { key ->
            latestStart(key)
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }, { key, original, commit -> latestFinish(key, original, commit) }, { latestOwnership(it) })
    }
    val edge = with(density) { 48.dp.toPx() }
    val speedPerSecond = with(density) { 300.dp.toPx() }
    val minTravel = with(density) { 12.dp.toPx() }
    val edgeScrolling by remember(state, edge, minTravel) {
        derivedStateOf { state.owned && state.scrollDelta(edge, 1f, minTravel) != 0f }
    }
    LaunchedEffect(state.activeKey, edgeScrolling, speedPerSecond) {
        if (!edgeScrolling) return@LaunchedEffect
        var previousFrame = withFrameNanos { it }
        while (isActive && state.owned) {
            val frame = withFrameNanos { it }
            val elapsed = (frame - previousFrame).coerceIn(0L, 48_000_000L) / 1_000_000_000f
            previousFrame = frame
            val delta = state.scrollDelta(edge, speedPerSecond * elapsed, minTravel)
            if (delta == 0f) break
            listState.scrollBy(delta)
            state.reorder()
        }
    }
    return state
}

/** Claims a down on the handle in Initial pass, before swipe deletion or sheet dragging. */
internal fun Modifier.reorderListInput(state: ListReorderState, enabled: Boolean = true): Modifier =
    onGloballyPositioned(state::positionList).pointerInput(state, enabled) {
        if (!enabled) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val coords = state.coordinates?.takeIf { it.isAttached } ?: return@awaitEachGesture
            val point = coords.localToWindow(down.position)
            val key = state.hitHandle(point) ?: return@awaitEachGesture
            if (!state.begin(key, point.y)) return@awaitEachGesture
            down.consume()
            var released = false
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) {
                        released = !change.isConsumed
                        change.consume()
                        break
                    }
                    event.changes.forEach { it.consume() }
                    val currentCoords = state.coordinates?.takeIf { it.isAttached } ?: break
                    state.update(currentCoords.localToWindow(change.position).y)
                }
            } finally {
                state.finish(commit = released)
            }
        }
    }

internal fun Modifier.reorderHandle(state: ListReorderState, key: String, enabled: Boolean = true): Modifier = composed {
    DisposableEffect(state, key, enabled) { onDispose { state.removeHandle(key) } }
    onGloballyPositioned { if (enabled) state.positionHandle(key, it.boundsInWindow()) else state.removeHandle(key) }
}

/** Pin the dragged item while edge scrolling; its gesture stays on the list container. */
internal fun Modifier.reorderItem(state: ListReorderState, key: String): Modifier = composed {
    val active = state.activeKey == key
    val pinnable = LocalPinnableContainer.current
    DisposableEffect(pinnable, active) {
        val pin = if (active) pinnable?.pin() else null
        onDispose { pin?.release() }
    }
    onGloballyPositioned { state.positionItem(key, it) }
        .zIndex(if (active) 10f else 0f)
        .graphicsLayer { translationY = state.translation(key) }
}
