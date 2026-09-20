package com.ar.messenger.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ar.messenger.ui.theme.ArAvatarGradients
import com.ar.messenger.ui.theme.ArBackground
import com.ar.messenger.ui.theme.ArOnline
import kotlin.math.abs

/** Deterministic gradient pair for a name, so the same person always gets the same colors. */
private fun gradientFor(name: String): List<Color> {
    val index = abs(name.hashCode()) % ArAvatarGradients.size
    return ArAvatarGradients[index]
}

@Composable
fun GradientAvatar(
    name: String,
    size: Dp = 48.dp,
    showOnlineDot: Boolean = false,
    isOnline: Boolean = false
) {
    val initials = name.trim().split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifBlank { "?" }
    val colors = gradientFor(name.ifBlank { "?" })

    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(Brush.linearGradient(colors)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                initials,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value / 2.4).sp
            )
        }
        if (showOnlineDot && isOnline) {
            Box(
                modifier = Modifier
                    .size(size / 3.2f)
                    .align(Alignment.BottomEnd)
                    .offset(x = (-1).dp, y = (-1).dp)
                    .clip(CircleShape)
                    .background(ArOnline)
                    .border(2.dp, ArBackground, CircleShape)
            )
        }
    }
}
