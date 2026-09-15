package com.example.njupter.ui.timetable.dialog

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.njupter.R
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SectionRangePicker(
    startSection: Int,
    endSection: Int,
    maxSection: Int,
    conflictingSections: Set<Int>,
    onRangeChange: (Int, Int) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val selectedConflicts = conflictingSections.filter { it in startSection..endSection }.sorted()
    val hasConflict = selectedConflicts.isNotEmpty()
    val description = if (!hasConflict) "" else stringResource(
        R.string.section_conflict_blocked,
        selectedConflicts.joinToString(", ")
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(stringResource(R.string.start_sec), style = MaterialTheme.typography.bodySmall)
        Text(
            stringResource(R.string.section_range, startSection.toString(), endSection.toString()),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = if (hasConflict) colors.error else colors.primary
        )
        Text(stringResource(R.string.end_sec), style = MaterialTheme.typography.bodySmall)
    }
    RangeSlider(
        value = startSection.toFloat()..endSection.toFloat(),
        onValueChange = { onRangeChange(it.start.roundToInt(), it.endInclusive.roundToInt()) },
        valueRange = 1f..maxSection.coerceAtLeast(2).toFloat(),
        enabled = maxSection > 1,
        steps = (maxSection - 2).coerceAtLeast(0),
        track = { state -> SectionRangeTrack(state, conflictingSections) }
    )
    if (description.isNotEmpty()) {
        Text(description, style = MaterialTheme.typography.bodySmall, color = colors.error)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SectionRangeTrack(state: RangeSliderState, conflictingSections: Set<Int>) {
    val colors = MaterialTheme.colorScheme
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Box {
        // Keep Material 3's corner alignment line, thumb gaps and endpoint insets.
        SliderDefaults.Track(rangeSliderState = state)
        if (conflictingSections.isNotEmpty()) {
            SliderDefaults.Track(
                rangeSliderState = state,
                colors = SliderDefaults.colors(
                    activeTrackColor = colors.error,
                    activeTickColor = colors.onError,
                    inactiveTrackColor = colors.onSurface.copy(alpha = 0.12f),
                    inactiveTickColor = colors.onSurface.copy(alpha = 0.38f)
                ),
                modifier = Modifier.matchParentSize().drawWithContent {
                    // Default ticks run between the centers of the rounded end caps.
                    val inset = size.height / 2f
                    val stepWidth = (size.width - 2 * inset).coerceAtLeast(0f) /
                        (state.valueRange.endInclusive - state.valueRange.start).coerceAtLeast(1f)
                    for (section in conflictingSections) {
                        val center = inset + (section - state.valueRange.start) * stepWidth
                        val left = if (section.toFloat() == state.valueRange.start) 0f else center - stepWidth / 2f
                        val right = if (section.toFloat() == state.valueRange.endInclusive) size.width else center + stepWidth / 2f
                        clipRect(
                            left = if (rtl) size.width - right else left,
                            right = if (rtl) size.width - left else right
                        ) { this@drawWithContent.drawContent() }
                    }
                }
            )
        }
    }
}
