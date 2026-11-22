package com.beeregg2001.komorebi.ui.setting

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.datastore.preferences.core.Preferences
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import com.beeregg2001.komorebi.common.AppStrings
import com.beeregg2001.komorebi.common.safeRequestFocus
import com.beeregg2001.komorebi.data.SettingsRepository
import com.beeregg2001.komorebi.ui.components.MonochromeButton
import com.beeregg2001.komorebi.ui.theme.KomorebiTheme
import kotlin.math.roundToInt

internal enum class CommentSliderSetting(
    val title: String,
    val preferenceKey: Preferences.Key<String>,
    val positions: IntRange,
    private val divisionsPerUnit: Int,
    private val defaultPosition: Int
) {
    SPEED(AppStrings.SETTINGS_INPUT_COMMENT_SPEED, SettingsRepository.COMMENT_SPEED, 5..20, 10, 10),
    SIZE(AppStrings.SETTINGS_INPUT_COMMENT_SIZE, SettingsRepository.COMMENT_FONT_SIZE, 5..20, 10, 10),
    OPACITY(AppStrings.SETTINGS_INPUT_COMMENT_OPACITY, SettingsRepository.COMMENT_OPACITY, 0..20, 20, 20),
    MAX_LINES(AppStrings.SETTINGS_INPUT_COMMENT_MAX_LINES, SettingsRepository.COMMENT_MAX_LINES, 0..20, 1, 0);

    fun positionOf(value: String): Int {
        val number = value.toFloatOrNull()?.takeIf { it.isFinite() } ?: return defaultPosition
        return (number * divisionsPerUnit).roundToInt().coerceIn(positions)
    }

    fun storedValue(position: Int): String =
        if (this == MAX_LINES) position.toString() else (position.toFloat() / divisionsPerUnit).toString()

    fun label(value: String): String = when (this) {
        SPEED, SIZE -> "${value}x"
        OPACITY -> value.toFloatOrNull()?.takeIf { it.isFinite() }?.let { "${(it * 100).roundToInt()}%" } ?: value
        MAX_LINES -> if (value.toIntOrNull() == 0) "制限なし" else "${value} 行"
    }
}

@Composable
internal fun CommentSliderDialog(
    setting: CommentSliderSetting,
    initialValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val colors = KomorebiTheme.colors
    val initialPosition = remember(setting, initialValue) { setting.positionOf(initialValue) }
    var editedPosition by remember(setting, initialValue) { mutableStateOf<Int?>(null) }
    val position = editedPosition ?: initialPosition
    val displayedValue = editedPosition?.let(setting::storedValue) ?: initialValue
    val sliderFocus = remember { FocusRequester() }
    val saveFocus = remember { FocusRequester() }
    val sliderInteraction = remember { MutableInteractionSource() }
    val sliderFocused by sliderInteraction.collectIsFocusedAsState()
    val trackColor = if (sliderFocused) MaterialTheme.colorScheme.primary else colors.textSecondary

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.width(400.dp),
            shape = MaterialTheme.shapes.large,
            colors = SurfaceDefaults.colors(containerColor = colors.surface, contentColor = colors.textPrimary)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(setting.title, style = MaterialTheme.typography.titleLarge)
                Text(setting.label(displayedValue), style = MaterialTheme.typography.headlineSmall)
                Slider(
                    value = position.toFloat(),
                    onValueChange = { editedPosition = it.roundToInt().coerceIn(setting.positions) },
                    valueRange = setting.positions.first.toFloat()..setting.positions.last.toFloat(),
                    steps = setting.positions.last - setting.positions.first - 1,
                    interactionSource = sliderInteraction,
                    colors = SliderDefaults.colors(
                        thumbColor = trackColor,
                        activeTrackColor = trackColor,
                        inactiveTrackColor = colors.textSecondary.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(sliderFocus)
                        .focusProperties { down = saveFocus }
                        .semantics { contentDescription = setting.title }
                        .onPreviewKeyEvent { event ->
                            when (event.key) {
                                Key.DirectionLeft, Key.DirectionRight -> {
                                    if (event.type == KeyEventType.KeyDown) {
                                        val delta = if (event.key == Key.DirectionLeft) -1 else 1
                                        val current = editedPosition ?: initialPosition
                                        editedPosition = (current + delta).coerceIn(setting.positions)
                                    }
                                    true
                                }
                                Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                    if (event.type == KeyEventType.KeyUp) saveFocus.requestFocus()
                                    true
                                }
                                else -> false
                            }
                        }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(setting.label(setting.storedValue(setting.positions.first)))
                    Text(setting.label(setting.storedValue(setting.positions.last)))
                }
                Text("左右キーで調整", style = MaterialTheme.typography.bodySmall)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MonochromeButton(
                        text = "キャンセル",
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).focusProperties { up = sliderFocus }
                    )
                    MonochromeButton(
                        text = "保存",
                        onClick = {
                            val edited = editedPosition
                            if (edited == null) onDismiss() else onConfirm(setting.storedValue(edited))
                        },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(saveFocus)
                            .focusProperties { up = sliderFocus },
                        isPrimary = true
                    )
                }
            }
        }
        LaunchedEffect(Unit) {
            sliderFocus.safeRequestFocus("CommentSliderDialog")
        }
    }
}
