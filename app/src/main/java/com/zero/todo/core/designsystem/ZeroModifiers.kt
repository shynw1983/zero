package com.zero.todo.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object ZeroDimens {
    val HairlineWidth: Dp = 0.5.dp
    val CardRadius: Dp = 10.dp
    val CapsuleRadius: Dp = 24.dp
    val SmallRadius: Dp = 6.dp
    val CompleteHitboxWidth: Dp = 48.dp
    val DragHandleWidth: Dp = 40.dp
    val TreeIndentPerLevel: Dp = 16.dp
    const val MaxVisibleTreeDepth: Int = 4
}

val ZeroCardShape: Shape = RoundedCornerShape(ZeroDimens.CardRadius)
val ZeroCapsuleShape: Shape = RoundedCornerShape(ZeroDimens.CapsuleRadius)
val ZeroSmallShape: Shape = RoundedCornerShape(ZeroDimens.SmallRadius)

/**
 * Applies a crisp 0.5.dp hairline border using the active ZERO theme border token by default.
 * Near-Future Minimalism defines hierarchy through surface contrast and 0.5.dp hairlines
 * rather than drop shadows.
 */
fun Modifier.hairlineBorder(
    shape: Shape = ZeroCardShape,
    color: Color? = null,
    width: Dp = ZeroDimens.HairlineWidth
): Modifier = composed {
    val borderColor = color ?: ZeroTheme.colors.hairlineBorder
    border(BorderStroke(width = width, color = borderColor), shape = shape)
}

/**
 * Draws a crisp 0.5.dp hairline border on the top edge without affecting layout padding.
 */
fun Modifier.hairlineDividerTop(
    color: Color? = null,
    width: Dp = ZeroDimens.HairlineWidth,
    startIndent: Dp = 0.dp,
    endIndent: Dp = 0.dp
): Modifier = composed {
    val strokeColor = color ?: ZeroTheme.colors.hairlineBorder
    drawWithContent {
        drawContent()
        val strokePx = width.toPx()
        val y = strokePx / 2f
        drawLine(
            color = strokeColor,
            start = Offset(startIndent.toPx(), y),
            end = Offset(size.width - endIndent.toPx(), y),
            strokeWidth = strokePx
        )
    }
}

/**
 * Draws a crisp 0.5.dp hairline border on the bottom edge without affecting layout padding.
 */
fun Modifier.hairlineDividerBottom(
    color: Color? = null,
    width: Dp = ZeroDimens.HairlineWidth,
    startIndent: Dp = 0.dp,
    endIndent: Dp = 0.dp
): Modifier = composed {
    val strokeColor = color ?: ZeroTheme.colors.hairlineBorder
    drawWithContent {
        drawContent()
        val strokePx = width.toPx()
        val y = size.height - strokePx / 2f
        drawLine(
            color = strokeColor,
            start = Offset(startIndent.toPx(), y),
            end = Offset(size.width - endIndent.toPx(), y),
            strokeWidth = strokePx
        )
    }
}

/**
 * Draws a crisp 0.5.dp hairline border on the start (left in LTR) edge.
 */
fun Modifier.hairlineDividerStart(
    color: Color? = null,
    width: Dp = ZeroDimens.HairlineWidth
): Modifier = composed {
    val strokeColor = color ?: ZeroTheme.colors.hairlineBorder
    drawWithContent {
        drawContent()
        val strokePx = width.toPx()
        val x = strokePx / 2f
        drawLine(
            color = strokeColor,
            start = Offset(x, 0f),
            end = Offset(x, size.height),
            strokeWidth = strokePx
        )
    }
}

/**
 * Draws a crisp 0.5.dp hairline border on the end (right in LTR) edge — ideal for
 * multi-pane split separators on foldables and tablets.
 */
fun Modifier.hairlineDividerEnd(
    color: Color? = null,
    width: Dp = ZeroDimens.HairlineWidth
): Modifier = composed {
    val strokeColor = color ?: ZeroTheme.colors.hairlineBorder
    drawWithContent {
        drawContent()
        val strokePx = width.toPx()
        val x = size.width - strokePx / 2f
        drawLine(
            color = strokeColor,
            start = Offset(x, 0f),
            end = Offset(x, size.height),
            strokeWidth = strokePx
        )
    }
}

@Composable
fun hairlineBorderStroke(
    color: Color = ZeroTheme.colors.hairlineBorder,
    width: Dp = ZeroDimens.HairlineWidth
): BorderStroke = BorderStroke(width = width, color = color)

fun Modifier.hairlineRectBorder(
    color: Color? = null,
    width: Dp = ZeroDimens.HairlineWidth
): Modifier = hairlineBorder(shape = RectangleShape, color = color, width = width)
