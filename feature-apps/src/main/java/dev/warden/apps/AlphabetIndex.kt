package dev.warden.apps

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.warden.design.Pixel
import dev.warden.design.Spacing
import dev.warden.design.Warden
import dev.warden.design.displayGradient
import kotlinx.coroutines.launch

/**
 * A–Z strip on the right of an alphabetical list, like a contacts app. Tap or slide along it to jump to
 * the first name with that letter; a bubble shows the letter under your finger. The letter of the
 * section you're scrolled to is marked even when you're not touching it. Letters with no names are dim.
 */
@Composable
internal fun AlphabetIndex(names: List<String>, listState: LazyListState, firstRow: Int, modifier: Modifier = Modifier) {
    val c = Warden.colors
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    // First row for each letter; anything not A–Z (digits, symbols, other scripts) files under #.
    val firstIndex = remember(names) {
        val out = LinkedHashMap<Char, Int>()
        names.forEachIndexed { i, name -> out.getOrPut(keyOf(name)) { i } }
        out
    }
    var height by remember { mutableStateOf(1) }
    var touched by remember { mutableStateOf<Char?>(null) }
    // The section at the top of the list right now.
    val current by remember(names) {
        derivedStateOf { names.getOrNull(listState.firstVisibleItemIndex - firstRow)?.let(::keyOf) }
    }

    fun jumpTo(y: Float) {
        val letter = LETTERS[(y / height * LETTERS.size).toInt().coerceIn(0, LETTERS.lastIndex)]
        if (letter == touched) return
        touched = letter
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        // Nearest letter at or after this one that has names, so empty letters still land somewhere sensible.
        val target = LETTERS.drop(LETTERS.indexOf(letter)).firstNotNullOfOrNull { firstIndex[it] }
            ?: firstIndex.values.lastOrNull() ?: return
        scope.launch { listState.scrollToItem(firstRow + target) }
    }

    Row(modifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
        // The bubble rides beside the strip while a finger is on it.
        AnimatedVisibility(touched != null, enter = fadeIn() + scaleIn(initialScale = 0.6f), exit = fadeOut() + scaleOut()) {
            Box(
                Modifier
                    .padding(end = Spacing.s)
                    .size(64.dp)
                    .background(c.surface, RoundedCornerShape(20.dp))
                    .border(1.dp, c.outline, RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    (touched ?: ' ').toString(),
                    style = Warden.type.display.copy(fontFamily = Pixel, fontSize = 36.sp, brush = displayGradient()),
                )
            }
        }
        Column(
            Modifier
                .padding(vertical = Spacing.xxl, horizontal = Spacing.xs)
                .width(22.dp)
                .background(if (touched != null) c.surfaceVariant else c.surface.copy(alpha = 0.85f), RoundedCornerShape(11.dp))
                .padding(vertical = Spacing.s)
                .onSizeChanged { height = it.height.coerceAtLeast(1) }
                .semantics { contentDescription = "A to Z index" }
                .pointerInput(firstIndex) {
                    // One handler for tap and slide: the first touch jumps, every move after it follows.
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        jumpTo(down.position.y)
                        do {
                            val event = awaitPointerEvent()
                            event.changes.forEach { change ->
                                if (change.pressed) {
                                    jumpTo(change.position.y)
                                    change.consume()
                                }
                            }
                        } while (event.changes.any { it.pressed })
                        touched = null
                    }
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            LETTERS.forEach { letter ->
                val on = letter == (touched ?: current)
                Text(
                    letter.toString(),
                    style = Warden.type.caption.copy(fontSize = 11.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium),
                    color = when {
                        on -> c.accentInk
                        letter in firstIndex -> c.text
                        else -> c.muted.copy(alpha = 0.35f)
                    },
                )
            }
        }
    }
}

private fun keyOf(name: String): Char = name.trim().firstOrNull()?.uppercaseChar()?.takeIf { it in 'A'..'Z' } ?: '#'

private val LETTERS = listOf('#') + ('A'..'Z').toList()
