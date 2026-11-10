package com.bigoke.grainlab.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

val Amber = Color(0xFFFFB347)
val Ink = Color(0xFF0E0D0B)
val Panel = Color(0xFF1A1815)
val Muted = Color(0xFFA59E92)

@Composable
fun GrainLabTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Amber,
            onPrimary = Color(0xFF2B1A00),
            secondary = Color(0xFFE8D5B5),
            background = Ink,
            surface = Panel,
            surfaceVariant = Color(0xFF26231F),
            onSurface = Color(0xFFF2ECE2),
            onSurfaceVariant = Muted,
        ),
        content = content,
    )
}

fun Context.toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

/** Slider with label and value; double-tap the label to reset to default. */
@Composable
fun AdjustSlider(
    label: String,
    value: Float,
    min: Float,
    max: Float,
    default: Float,
    onChange: (Float) -> Unit,
    onDone: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)) {
        Row(
            Modifier.fillMaxWidth().pointerInput(default) {
                detectTapGestures(onDoubleTap = { onChange(default); onDone() })
            },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
            Text(formatValue(value, min, max), fontSize = 12.sp, color = if (abs(value - default) > 1e-3f) Amber else Muted)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            onValueChangeFinished = onDone,
            valueRange = min..max,
            colors = SliderDefaults.colors(thumbColor = Amber, activeTrackColor = Amber),
        )
    }
}

fun formatValue(v: Float, min: Float, max: Float): String = when {
    max > 20f -> "${v.roundToInt()}°"
    max >= 10f -> String.format(Locale.US, "%.1f°", v)
    min < 0f -> {
        val n = (v * 100).roundToInt()
        if (n > 0) "+$n" else "$n"
    }
    else -> "${(v * 100).roundToInt()}"
}

@Composable
fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontSize = 12.sp) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Amber,
            selectedLabelColor = Color(0xFF2B1A00),
        ),
    )
}

@Composable
fun Pill(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier
            .background(Color(0xAA000000), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        color = Color.White,
        fontSize = 13.sp,
    )
}
