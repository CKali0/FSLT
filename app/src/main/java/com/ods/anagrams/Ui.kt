package com.ods.anagrams

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ods.anagrams.engine.formatClock
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

object Pal {
    val Bg = Color(0xFF111315)
    val Surface = Color(0xFF1C1F22)
    val Surface2 = Color(0xFF282C30)
    val Accent = Color(0xFFE0B25A)
    val OnAccent = Color(0xFF1B1405)
    val Text = Color(0xFFE8E8E8)
    val Muted = Color(0xFF9AA0A6)
    val TileBg = Color(0xFFE9DDB8)
    val TileText = Color(0xFF2B2416)
    val Found = Color(0xFF1F3A2A)
    val Missed = Color(0xFF4A2326)
    val Ok = Color(0xFF7FD39A)
    val Bad = Color(0xFFE88A8A)
}

@Composable
fun OdsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Pal.Accent,
            onPrimary = Pal.OnAccent,
            background = Pal.Bg,
            onBackground = Pal.Text,
            surface = Pal.Surface,
            onSurface = Pal.Text,
            surfaceVariant = Pal.Surface2,
            onSurfaceVariant = Pal.Muted
        )
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Pal.Bg, contentColor = Pal.Text) {
            content()
        }
    }
}

@Composable
fun ScreenFrame(
    title: String,
    onBack: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Pal.Bg)
            .safeDrawingPadding()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                TextButton(onClick = onBack) {
                    Text("←", fontSize = 24.sp, color = Pal.Accent)
                }
            }
            Text(
                title,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            trailing()
        }
        content()
    }
}

@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 52.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (primary) Pal.Accent else Pal.Surface2,
            contentColor = if (primary) Pal.OnAccent else Pal.Text
        )
    ) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun SettingLabel(text: String) {
    Text(
        text,
        color = Pal.Muted,
        fontSize = 14.sp,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
    )
}

@Composable
fun <T> ChoiceRow(label: String, options: List<Pair<String, T>>, selected: T, onSelect: (T) -> Unit) {
    SettingLabel(label)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((text, value) in options) {
            AppButton(
                text = text,
                onClick = { onSelect(value) },
                modifier = Modifier.weight(1f),
                primary = value == selected
            )
        }
    }
}

@Composable
fun Stepper(label: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    SettingLabel(label)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        AppButton("−", { if (value > min) onChange(value - 1) }, Modifier.width(64.dp))
        Text(
            value.toString(),
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold
        )
        AppButton("+", { if (value < max) onChange(value + 1) }, Modifier.width(64.dp))
    }
}

@Composable
fun SliderSetting(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 20.dp)
    ) {
        Text(label, color = Pal.Muted, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(valueText, fontWeight = FontWeight.SemiBold)
    }
    Slider(
        value = value,
        onValueChange = onChange,
        valueRange = range,
        steps = steps,
        colors = SliderDefaults.colors(
            thumbColor = Pal.Accent,
            activeTrackColor = Pal.Accent,
            inactiveTrackColor = Pal.Surface2
        )
    )
}

/** Time limit slider in 30 second steps, from 0:30 to 30:00. */
@Composable
fun TimeSlider(seconds: Int, onChange: (Int) -> Unit) {
    SliderSetting(
        label = "Time limit",
        valueText = formatClock(seconds),
        value = seconds / 30f,
        range = 1f..60f,
        steps = 58,
        onChange = { onChange(it.roundToInt() * 30) }
    )
}

@Composable
fun NumberField(label: String, value: Int, onChange: (Int) -> Unit) {
    var text by remember { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { t ->
            val digits = t.filter { it.isDigit() }.take(6)
            text = digits
            onChange(digits.toIntOrNull() ?: 0)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        colors = fieldColors(),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
    )
}

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Pal.Accent,
    cursorColor = Pal.Accent,
    focusedLabelColor = Pal.Accent
)

@Composable
fun WordInput(onSubmit: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        try {
            focus.requestFocus()
        } catch (e: Exception) {
            // focus can fail if the field isn't laid out yet; the user can tap it
        }
    }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it.uppercase() },
        placeholder = { Text("Type a word and press enter", color = Pal.Muted) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Characters,
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(onDone = {
            if (text.isNotBlank()) onSubmit(text)
            text = ""
        }),
        colors = fieldColors(),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 8.dp)
            .focusRequester(focus)
    )
}

@Composable
fun Tile(letter: Char, size: Dp) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape((size.value / 6f).dp))
            .background(Pal.TileBg),
        contentAlignment = Alignment.Center
    ) {
        Text(
            letter.toString(),
            color = Pal.TileText,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.55f).sp
        )
    }
}

@Composable
fun RackRow(
    letters: String,
    modifier: Modifier = Modifier,
    maxTile: Dp = 44.dp,
    center: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    BoxWithConstraints(
        modifier.fillMaxWidth(),
        contentAlignment = if (center) Alignment.Center else Alignment.CenterStart
    ) {
        val gap = 4.dp
        val n = maxOf(letters.length, 1)
        val fit = (maxWidth - gap * (n - 1)) / n
        val size = if (fit < maxTile) fit else maxTile
        Row(
            horizontalArrangement = Arrangement.spacedBy(gap),
            modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
        ) {
            for (ch in letters) Tile(ch, size)
        }
    }
}

@Composable
fun WordRow(word: String, found: Boolean) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (found) Pal.Found else Pal.Missed)
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Text(word, fontSize = 17.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
fun SectionHeader(left: String, right: String = "", done: Boolean = false) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 4.dp)
    ) {
        Text(
            left,
            modifier = Modifier.weight(1f),
            fontWeight = FontWeight.SemiBold,
            color = if (done) Pal.Ok else Pal.Text
        )
        if (right.isNotEmpty()) Text(right, color = if (done) Pal.Ok else Pal.Muted)
    }
}

@Composable
fun FeedbackLine(message: String) {
    Text(
        message,
        color = if (message.startsWith("✓")) Pal.Ok else Pal.Bad,
        fontSize = 15.sp,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 22.dp)
    )
}

/**
 * Seconds left until [endAtMs] (SystemClock.elapsedRealtime based). Calls [onExpired] once
 * when the time runs out.
 */
@Composable
fun rememberSecondsLeft(endAtMs: Long, key: Any, onExpired: () -> Unit): Int {
    var now by remember(key) { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val latest by rememberUpdatedState(onExpired)
    LaunchedEffect(key) {
        while (true) {
            now = SystemClock.elapsedRealtime()
            if (now >= endAtMs) {
                latest()
                break
            }
            delay(200)
        }
    }
    return ((endAtMs - now + 999) / 1000).toInt().coerceAtLeast(0)
}

@Composable
fun TimerText(seconds: Int) {
    Text(
        formatClock(seconds),
        fontSize = 28.sp,
        fontWeight = FontWeight.Bold,
        color = if (seconds <= 30) Pal.Bad else Pal.Text
    )
}

val tightButtonPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp)
