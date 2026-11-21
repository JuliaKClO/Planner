package ru.workweek.planner.ui

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.util.Locale

internal fun weekColorHex(rgb: Int): String = String.format(Locale.ROOT, "#%06X", rgb)

@Composable
internal fun WeekColorChoice(title: String, selected: Color, label: String, onSelect: (Int) -> Unit) {
    var choosing by rememberSaveable { mutableStateOf(false) }
    OutlinedButton(onClick = { choosing = !choosing }, modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.size(20.dp).background(selected, CircleShape))
        Text("$title · $label", modifier = Modifier.weight(1f).padding(start = 12.dp))
    }
    if (choosing) WeekColorPalette(selected, onDismiss = { choosing = false }) {
        onSelect(it)
        choosing = false
    }
}

@Composable
private fun WeekColorPalette(selected: Color, onDismiss: () -> Unit, onSelect: (Int) -> Unit) {
    val initial = remember { FloatArray(3).also { AndroidColor.colorToHSV(selected.toArgb(), it) } }
    var hue by rememberSaveable { mutableFloatStateOf(initial[0]) }
    var saturation by rememberSaveable { mutableFloatStateOf(initial[1]) }
    var brightness by rememberSaveable { mutableFloatStateOf(initial[2]) }
    fun rgb(): Int = AndroidColor.HSVToColor(floatArrayOf(hue, saturation, brightness)) and 0xFFFFFF
    var hex by rememberSaveable { mutableStateOf(weekColorHex(rgb())) }
    fun updateHex() { hex = weekColorHex(rgb()) }
    val parsed = hex.removePrefix("#").takeIf { it.matches(Regex("[0-9a-fA-F]{6}")) }?.toIntOrNull(16)
    val preview = Color(0xFF000000.toInt() or (parsed ?: rgb()))
    val revealPalette = remember { BringIntoViewRequester() }
    LaunchedEffect(revealPalette) {
        withFrameNanos { }
        revealPalette.bringIntoView()
    }
    OutlinedCard(Modifier.fillMaxWidth().bringIntoViewRequester(revealPalette)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.fillMaxWidth().height(40.dp).background(preview))
            val hueColor = Color(AndroidColor.HSVToColor(floatArrayOf(hue, 1f, 1f)))
            fun choose(position: Offset, width: Int, height: Int) {
                saturation = (position.x / width).coerceIn(0f, 1f)
                brightness = 1f - (position.y / height).coerceIn(0f, 1f)
                updateHex()
            }
            Canvas(
                Modifier.fillMaxWidth().height(128.dp)
                    .semantics { contentDescription = "Палитра насыщенности и яркости" }
                    .pointerInput(hue) {
                        detectTapGestures { choose(it, size.width, size.height) }
                    }
                    .pointerInput(hue) {
                        detectDragGestures(
                            onDragStart = { choose(it, size.width, size.height) },
                            onDrag = { change, _ ->
                                change.consume()
                                choose(change.position, size.width, size.height)
                            }
                        )
                    }
            ) {
                drawRect(hueColor)
                drawRect(Brush.horizontalGradient(listOf(Color.White, Color.Transparent)))
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                val point = Offset(saturation * size.width, (1f - brightness) * size.height)
                drawCircle(Color.Black, 7.dp.toPx(), point, style = Stroke(3.dp.toPx()))
                drawCircle(Color.White, 7.dp.toPx(), point, style = Stroke(1.dp.toPx()))
            }
            Text("Цветовой тон")
            Slider(hue, { hue = it; updateHex() }, valueRange = 0f..360f,
                modifier = Modifier.semantics { contentDescription = "Цветовой тон" })
            Text("Насыщенность")
            Slider(saturation, { saturation = it; updateHex() },
                modifier = Modifier.semantics { contentDescription = "Насыщенность" })
            Text("Яркость")
            Slider(brightness, { brightness = it; updateHex() },
                modifier = Modifier.semantics { contentDescription = "Яркость" })
            OutlinedTextField(
                value = hex,
                onValueChange = { value ->
                    hex = value
                    value.removePrefix("#").takeIf { it.matches(Regex("[0-9a-fA-F]{6}")) }?.toIntOrNull(16)?.let {
                        val hsv = FloatArray(3)
                        AndroidColor.colorToHSV(0xFF000000.toInt() or it, hsv)
                        hue = hsv[0]
                        saturation = hsv[1]
                        brightness = hsv[2]
                    }
                },
                label = { Text("HEX") },
                singleLine = true,
                isError = parsed == null,
                supportingText = { if (parsed == null) Text("Введите цвет в формате #RRGGBB") },
                modifier = Modifier.fillMaxWidth()
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Отмена") }
                TextButton(onClick = { parsed?.let(onSelect) }, enabled = parsed != null) { Text("Применить") }
            }
        }
    }
}
