package com.precisiontuner.ui.ear

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.precisiontuner.R
import com.precisiontuner.ear.RhythmPattern
import kotlin.math.min

private val BravuraFont = FontFamily(Font(R.font.bravura))

/**
 * Rhythm staff rendered with Bravura glyphs, exactly like the note-reading
 * staff: five lines, the time signature, then one complete standard note or
 * rest glyph per grid position (Unicode musical-symbol codepoints carry the
 * notehead, stem and flags as a single glyph).
 *
 * The font size is driven by the tightest adjacent-note gap, so eighths and
 * quarters render at the full 40 sp size and only dense sixteenth runs shrink.
 * Runs of equal short notes (eighths or sixteenths) share a horizontal beam,
 * like standard notation. All notes sit on the middle line (rhythm has no
 * pitch).
 */
@Composable
fun RhythmStaffView(pattern: RhythmPattern, modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
    val inkColor = MaterialTheme.colorScheme.onSurface
    val panelColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.84f)
    val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.74f)
    val textMeasurer = rememberTextMeasurer()
    val description = "节奏听写题：${pattern.name}"

    Canvas(modifier.semantics { contentDescription = description }) {
        val gap = min(12.dp.toPx(), size.height / 10f).coerceAtLeast(7.dp.toPx())
        val centerY = size.height / 2f
        val left = 16.dp.toPx()
        val right = size.width - 16.dp.toPx()
        fun yAt(lineOffset: Float): Float = centerY - (lineOffset - 2f) * gap

        drawRoundRect(
            color = panelColor,
            cornerRadius = CornerRadius(20.dp.toPx()),
        )
        drawRoundRect(
            color = borderColor,
            cornerRadius = CornerRadius(20.dp.toPx()),
            style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()),
        )

        for (line in 0 until 5) {
            val y = yAt(line.toFloat())
            drawLine(lineColor, Offset(left, y), Offset(right, y), 1.2.dp.toPx())
        }

        val notes = pattern.notes
        val onsets = pattern.onsetGrids
        val total = pattern.totalGrids
        val contentLeft = left + gap * 4f
        val usable = (right - contentLeft).coerceAtLeast(1f)
        fun centerX(onsetGrids: Int, grids: Int): Float =
            contentLeft + ((onsetGrids + grids / 2f) / total) * usable

        // Font em: full 40 sp unless the tightest note gap would overlap; the
        // widest glyph (sixteenth, 0.58 em bbox) sets the safety factor.
        val minGapGrids = onsets.zipWithNext { a, b -> b - a }.minOrNull() ?: 12
        val minGapPx = usable * minGapGrids / total
        val emPx = min(40f * (density * fontScale), minGapPx / GLYPH_WIDTH_EM)
        val musicStyle = TextStyle(
            color = inkColor,
            fontFamily = BravuraFont,
            fontSize = (emPx / (density * fontScale)).sp,
        )

        // Time signature (plain text, left of the notes).
        val timeSigLayout = textMeasurer.measure(
            text = AnnotatedString("${pattern.beatsPerBar}/${pattern.beatUnit}"),
            style = TextStyle(
                color = inkColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        drawText(
            textLayoutResult = timeSigLayout,
            topLeft = Offset(
                left + gap * 1.2f,
                centerY - timeSigLayout.size.height / 2f,
            ),
        )

        // Runs of consecutive equal short notes (eighth or sixteenth runs)
        // share a beam instead of individual flags.
        val beamGroups = findBeamGroups(notes, onsets)

        notes.forEachIndexed { i, note ->
            val center = Offset(centerX(onsets[i], note.grids), centerY)
            if (note.isRest) {
                drawGlyphCentered(restGlyph(note.grids), center, textMeasurer, musicStyle)
            } else {
                val beamed = beamGroups.any { i in it.start..it.end }
                // Beamed notes use the stem-only quarter glyph; the beam line is
                // drawn on top (its stem top matches the eighth/sixteenth glyphs).
                drawGlyphCentered(
                    if (beamed) UNICODE_QUARTER_NOTE else noteGlyph(note.grids),
                    center,
                    textMeasurer,
                    musicStyle,
                )
                // Augmentation dot for dotted values.
                if (note.grids in DOTTED_GRIDS) {
                    drawGlyphCentered(
                        UNICODE_AUGMENTATION_DOT,
                        Offset(center.x + emPx * 0.8f, center.y),
                        textMeasurer,
                        musicStyle,
                    )
                }
                // Triplet marker "3" above the note.
                if (note.grids == 4) {
                    val tripletLayout = textMeasurer.measure(
                        text = AnnotatedString("3"),
                        style = TextStyle(
                            color = inkColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    drawText(
                        textLayoutResult = tripletLayout,
                        topLeft = Offset(
                            center.x - tripletLayout.size.width / 2f,
                            center.y - 3.0f * gap - tripletLayout.size.height,
                        ),
                    )
                }
            }
        }

        // Beams sit on the stem tops (glyph bbox yMax ≈ 0.875 em above the
        // notehead centre) and span the two outermost stems only, with a small
        // overhang at each end; a sixteenth run adds a second beam just below.
        val beamTopY = centerY - STEM_TOP_EM * emPx
        val beamThickness = 0.08f * emPx
        beamGroups.forEach { (start, end, beams) ->
            val fromX = centerX(onsets[start], notes[start].grids) + STEM_X_EM * emPx - 0.06f * emPx
            val toX = centerX(onsets[end], notes[end].grids) + STEM_X_EM * emPx + 0.06f * emPx
            for (beam in 0 until beams) {
                val y = beamTopY + beam * 0.10f * emPx
                drawLine(inkColor, Offset(fromX, y), Offset(toX, y), beamThickness, StrokeCap.Round)
            }
        }
    }
}

private data class BeamGroup(val start: Int, val end: Int, val beams: Int)

/**
 * Groups consecutive audible notes of equal duration whose onsets are exactly
 * adjacent (gap == duration): 6-grid runs -> one beam, 3-grid runs -> two.
 */
private fun findBeamGroups(notes: List<com.precisiontuner.ear.RhythmNote>, onsets: List<Int>): List<BeamGroup> {
    val groups = mutableListOf<BeamGroup>()
    var i = 0
    while (i < notes.size) {
        if (notes[i].isRest || (notes[i].grids != 6 && notes[i].grids != 3)) {
            i++
            continue
        }
        val grids = notes[i].grids
        var j = i + 1
        while (j < notes.size && !notes[j].isRest && notes[j].grids == grids &&
            onsets[j] - onsets[j - 1] == grids
        ) {
            j++
        }
        if (j - i >= 2) {
            groups += BeamGroup(i, j - 1, beams = if (grids == 6) 1 else 2)
            i = j
        } else {
            i++
        }
    }
    return groups
}

private fun DrawScope.drawGlyphCentered(
    glyph: String,
    center: Offset,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    style: TextStyle,
): TextLayoutResult {
    val layout = textMeasurer.measure(
        text = AnnotatedString(glyph),
        style = style,
        maxLines = 1,
    )
    val bounds = layout.getBoundingBox(0)
    drawText(
        textLayoutResult = layout,
        topLeft = Offset(center.x - bounds.center.x, center.y - bounds.center.y),
    )
    return layout
}

/** Complete standard note glyph (notehead + stem + flags in one codepoint). */
private fun noteGlyph(grids: Int): String = when (grids) {
    48 -> UNICODE_WHOLE_NOTE
    36, 24 -> UNICODE_HALF_NOTE
    6 -> UNICODE_EIGHTH_NOTE
    3 -> UNICODE_SIXTEENTH_NOTE
    else -> UNICODE_QUARTER_NOTE // 18, 12, 4 and anything else
}

private fun restGlyph(grids: Int): String = when (grids) {
    48 -> UNICODE_WHOLE_REST
    24 -> UNICODE_HALF_REST
    6 -> UNICODE_EIGHTH_REST
    3 -> UNICODE_SIXTEENTH_REST
    else -> UNICODE_QUARTER_REST // 12, 18, 9, 4 and anything else
}

private val DOTTED_GRIDS = setOf(36, 18, 9)

// Measured from bravura.otf (em units, origin ≈ notehead centre).
private const val GLYPH_WIDTH_EM = 0.62f // widest glyph: sixteenth bbox 0.58 em
private const val STEM_TOP_EM = 0.875f // stem top above the notehead centre
private const val STEM_X_EM = 0.17f // stem x offset right of the notehead centre

// Unicode musical-symbol codepoints (surrogate pairs), present in bravura.otf.
private const val UNICODE_WHOLE_NOTE = "\uD834\uDD5D" // U+1D15D
private const val UNICODE_HALF_NOTE = "\uD834\uDD5E" // U+1D15E
private const val UNICODE_QUARTER_NOTE = "\uD834\uDD5F" // U+1D15F
private const val UNICODE_EIGHTH_NOTE = "\uD834\uDD60" // U+1D160
private const val UNICODE_SIXTEENTH_NOTE = "\uD834\uDD61" // U+1D161
private const val UNICODE_WHOLE_REST = "\uD834\uDD3B" // U+1D13B
private const val UNICODE_HALF_REST = "\uD834\uDD3C" // U+1D13C
private const val UNICODE_QUARTER_REST = "\uD834\uDD3D" // U+1D13D
private const val UNICODE_EIGHTH_REST = "\uD834\uDD3E" // U+1D13E
private const val UNICODE_SIXTEENTH_REST = "\uD834\uDD3F" // U+1D13F
private const val UNICODE_AUGMENTATION_DOT = "\uD834\uDD6D" // U+1D16D
