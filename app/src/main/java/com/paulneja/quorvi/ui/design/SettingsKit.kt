package com.paulneja.quorvi.ui.design

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun Section(title: String) {
    Text(title.uppercase(), style = Quorvi.type.section, color = Quorvi.colors.textTertiary, modifier = Modifier.padding(start = 30.dp, top = 22.dp, bottom = 8.dp))
}

@Composable
fun Panel(content: @Composable () -> Unit) {
    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .liquidGlass(Quorvi.shapes.panel, GlassLevel.PANEL)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) { content() }
}

@Composable
fun Label(text: String) {
    Text(text, style = Quorvi.type.body, color = Quorvi.colors.text, modifier = Modifier.padding(top = 10.dp))
}

@Composable
fun Hint(text: String) {
    Text(text, style = Quorvi.type.caption, color = Quorvi.colors.textSecondary, modifier = Modifier.padding(vertical = 10.dp))
}

@Composable
fun SliderRow(title: String, value: Float, range: ClosedFloatingPointRange<Float>, format: ((Float) -> String)? = null, onDone: (Float) -> Unit) {
    var local by remember(value) { mutableFloatStateOf(value) }
    val shown = format?.invoke(local) ?: if (range.endInclusive <= 1f) "${(local * 100).roundToInt()} %" else "%.1f".format(local)
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = Quorvi.type.body, color = Quorvi.colors.text, modifier = Modifier.weight(1f))
        Text(shown, style = Quorvi.type.mono, color = Quorvi.colors.textSecondary)
    }
    QuorviSlider(value = local, onChange = { local = it }, range = range, height = 30.dp, onDone = onDone, modifier = Modifier.padding(vertical = 10.dp))
}

@Composable
fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = Quorvi.type.body, color = Quorvi.colors.text, modifier = Modifier.weight(1f))
        QuorviSwitch(checked = checked, onChange = onChange)
    }
}

@Composable
fun ChoiceRow(title: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = Quorvi.type.body, color = Quorvi.colors.text, modifier = Modifier.weight(1f))
        Box(Modifier.size(18.dp).clip(CircleShape).border(2.dp, if (selected) Quorvi.colors.accent else Quorvi.colors.border, CircleShape).padding(4.dp)) {
            if (selected) Box(Modifier.fillMaxSize().clip(CircleShape).background(Quorvi.colors.accent))
        }
    }
}

@Composable
fun ActionRow(title: String, subtitle: String?, danger: Boolean = false, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(onClick = onClick).padding(vertical = 10.dp), verticalArrangement = Arrangement.Center) {
        Text(title, style = Quorvi.type.body, color = if (danger) Quorvi.colors.danger else Quorvi.colors.text)
        if (subtitle != null) Text(subtitle, style = Quorvi.type.caption, color = Quorvi.colors.textSecondary)
    }
}

@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    QuorviSegmented(options, selected, onSelect, Modifier.padding(vertical = 8.dp))
}

@Composable
fun Stepper(title: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = Quorvi.type.body, color = Quorvi.colors.text, modifier = Modifier.weight(1f))
        StepButton("−", value > range.first) { onChange(value - 1) }
        Text("$value", style = Quorvi.type.action.copy(fontFeatureSettings = "tnum"), color = Quorvi.colors.text, modifier = Modifier.padding(horizontal = 14.dp))
        StepButton("+", value < range.last) { onChange(value + 1) }
    }
}

@Composable
fun StepButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(36.dp).clip(Quorvi.shapes.control).background(Quorvi.colors.field).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = Quorvi.type.title, color = if (enabled) Quorvi.colors.text else Quorvi.colors.border) }
}

@Composable
fun IconButton(glyph: ImageVector, onClick: () -> Unit, enabled: Boolean = true) {
    Box(Modifier.size(44.dp).clip(Quorvi.shapes.control).clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        Image(rememberVectorPainter(glyph), null, Modifier.size(22.dp), colorFilter = ColorFilter.tint(if (enabled) Quorvi.colors.text else Quorvi.colors.border))
    }
}

@Composable
fun InputField(value: String, onChange: (String) -> Unit, placeholder: String, secret: Boolean = false, singleLine: Boolean = true, onSubmit: (() -> Unit)? = null) {
    Box(Modifier.padding(vertical = 6.dp).fillMaxWidth().clip(Quorvi.shapes.control).background(Quorvi.colors.field).padding(14.dp)) {
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = singleLine,
            textStyle = Quorvi.type.body.copy(color = Quorvi.colors.text),
            cursorBrush = SolidColor(Quorvi.colors.accent),
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = if (onSubmit != null) androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Send) else androidx.compose.foundation.text.KeyboardOptions.Default,
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSend = { onSubmit?.invoke() }),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                if (value.isEmpty()) Text(placeholder, style = Quorvi.type.body, color = Quorvi.colors.textSecondary)
                inner()
            },
        )
    }
}
