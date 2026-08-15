package com.mlh.skinanalyzer.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Botón con relieve discreto (tablet / guantes). Mínimo 56 dp de alto.
 */
@Composable
fun RaisedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = Accent,
    contentColor: Color = Color.White,
    minHeight: Dp = 56.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val elevation = if (pressed) 1.dp else 6.dp
    val y = if (pressed) 1.dp else 0.dp
    val shape = RoundedCornerShape(14.dp)

    Surface(
        shape = shape,
        tonalElevation = if (pressed) 1.dp else 2.dp,
        shadowElevation = elevation,
        color = Color.Transparent,
        modifier = modifier
            .defaultMinSize(minHeight = minHeight)
            .offset(y = y)
            .clip(shape)
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(minHeight)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            if (enabled) containerColor.copy(alpha = 0.95f) else containerColor.copy(alpha = 0.35f),
                            if (enabled) containerColor.copy(alpha = 0.78f) else containerColor.copy(alpha = 0.25f),
                        ),
                    ),
                )
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text,
                color = if (enabled) contentColor else contentColor.copy(alpha = 0.5f),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            )
        }
    }
}

@Composable
fun RaisedOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    RaisedButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        containerColor = Slate,
        contentColor = Paper,
    )
}
