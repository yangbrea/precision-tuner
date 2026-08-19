package com.precisiontuner.ui.ear

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.precisiontuner.R
import com.precisiontuner.ear.Accidental
import com.precisiontuner.ear.StaffClef
import com.precisiontuner.ear.StaffNotation
import com.precisiontuner.ear.StaffPosition
import kotlin.math.min

private val BravuraFont = FontFamily(Font(R.font.bravura))

/** Professional single-note staff rendered with Bravura SMuFL glyphs. */
@Composable
fun StaffView(notation: StaffNotation, modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
    val inkColor = MaterialTheme.colorScheme.onSurface
    val panelColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.84f)
    val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.74f)
    val textMeasurer = rememberTextMeasurer()
    val description = "${notation.clef.accessibilityName}五线谱识谱题"

    Canvas(modifier.semantics { contentDescription = description }) {
        val gap = min(12.dp.toPx(), size.height / 10f).coerceAtLeast(8.dp.toPx())
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

        for (line in 0 until StaffPosition.LINE_COUNT) {
            val y = yAt(line.toFloat())
            drawLine(lineColor, Offset(left, y), Offset(right, y), 1.2.dp.toPx())
        }

        // SMuFL uses one em for four staff spaces. Calculating sp from the
        // desired pixel em avoids the old px-as-sp density multiplication bug.
        val musicFontSp = (gap * 4f / (density * fontScale)).sp
        val musicStyle = TextStyle(
            color = inkColor,
            fontFamily = BravuraFont,
            fontSize = musicFontSp,
        )

        fun measureGlyph(glyph: String): TextLayoutResult = textMeasurer.measure(
            text = AnnotatedString(glyph),
            style = musicStyle,
            maxLines = 1,
        )

        fun DrawScope.drawGlyphCentered(glyph: String, center: Offset): TextLayoutResult {
            val layout = measureGlyph(glyph)
            val bounds = layout.getBoundingBox(0)
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(center.x - bounds.center.x, center.y - bounds.center.y),
            )
            return layout
        }

        // Clef glyph origins are registered on their defining staff line:
        // treble on G4 (line 2 from bottom), bass on F3 (line 4 from bottom).
        val (clefGlyph, clefAnchor) = when (notation.clef) {
            StaffClef.TREBLE -> SMUFL_G_CLEF to 1f
            StaffClef.BASS -> SMUFL_F_CLEF to 3f
        }
        val clefLayout = measureGlyph(clefGlyph)
        val clefBounds = clefLayout.getBoundingBox(0)
        drawText(
            textLayoutResult = clefLayout,
            topLeft = Offset(
                x = left + gap * 1.55f - clefBounds.center.x,
                y = yAt(clefAnchor) - clefLayout.firstBaseline,
            ),
        )

        val staffNote = StaffPosition.staffNote(notation)
        val noteY = yAt(staffNote.lineOffset)
        val noteCenterX = size.width * 0.64f
        val noteCenter = Offset(noteCenterX, noteY)
        val headWidth = 1.2f * gap

        staffNote.ledgerOffsets.forEach { ledger ->
            val ledgerY = yAt(ledger.toFloat())
            drawLine(
                inkColor,
                Offset(noteCenterX - gap * 0.9f, ledgerY),
                Offset(noteCenterX + gap * 0.9f, ledgerY),
                1.4.dp.toPx(),
            )
        }

        val stemUp = staffNote.lineOffset < 2f
        val stemX = noteCenterX + if (stemUp) headWidth * 0.43f else -headWidth * 0.43f
        val stemEndY = noteY + if (stemUp) -3.45f * gap else 3.45f * gap
        drawLine(
            color = inkColor,
            start = Offset(stemX, noteY),
            end = Offset(stemX, stemEndY),
            strokeWidth = 1.55.dp.toPx(),
        )
        drawGlyphCentered(SMUFL_NOTEHEAD_BLACK, noteCenter)

        val accidentalGlyph = when (staffNote.accidental) {
            Accidental.NATURAL -> null
            Accidental.SHARP -> SMUFL_ACCIDENTAL_SHARP
            Accidental.FLAT -> SMUFL_ACCIDENTAL_FLAT
        }
        if (accidentalGlyph != null) {
            drawGlyphCentered(
                accidentalGlyph,
                Offset(noteCenterX - gap * 1.75f, noteY),
            )
        }
    }
}

private const val SMUFL_G_CLEF = "\uE050"
private const val SMUFL_F_CLEF = "\uE062"
private const val SMUFL_NOTEHEAD_BLACK = "\uE0A4"
private const val SMUFL_ACCIDENTAL_FLAT = "\uE260"
private const val SMUFL_ACCIDENTAL_SHARP = "\uE262"
