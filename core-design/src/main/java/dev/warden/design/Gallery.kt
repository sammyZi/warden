package dev.warden.design

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * Every component in every state. Shown as light/dark previews below and in-app from
 * Settings in debug builds, where the pressed spring and switch thumb can be felt.
 */
@Composable
fun ComponentGallery(modifier: Modifier = Modifier) {
    val c = Warden.colors
    Column(
        modifier
            .background(c.background)
            .verticalScroll(rememberScrollState())
            .padding(Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.xl),
    ) {
        Section("Type") {
            CountUpText(1284, Warden.type.display)
            Text("Headline 28/34", style = Warden.type.headline)
            Text("Title 20/26", style = Warden.type.title)
            Text("Body 16/24. Nothing blocked yet. Give it a few minutes.", style = Warden.type.body)
            Text("LABEL 13/16 +0.2", style = Warden.type.label, color = c.muted)
        }
        Section("Colour") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                listOf(
                    "bg" to c.background, "surface" to c.surface, "variant" to c.surfaceVariant, "outline" to c.outline,
                    "text" to c.text, "muted" to c.muted, "primary" to c.primary, "accentInk" to c.accentInk, "blocked" to c.blocked,
                    "warning" to c.warning,
                ).forEach { (name, color) -> Swatch(name, color) }
            }
        }
        Section("Buttons") {
            ButtonKind.entries.forEach { kind ->
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    WardenButton(kind.name, {}, kind = kind)
                    WardenButton("Disabled", {}, kind = kind, enabled = false)
                }
            }
            WardenButton("With icon", {}, Modifier.fillMaxWidth(), icon = R.drawable.ph_shield_check)
        }
        Section("Switch") {
            var wifi by remember { mutableStateOf(true) }
            var cell by remember { mutableStateOf(false) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                WardenSwitch(wifi, { wifi = it }, "Wi-Fi")
                WardenSwitch(cell, { cell = it }, "Mobile data")
                WardenSwitch(true, {}, "Disabled on", enabled = false)
                WardenSwitch(false, {}, "Disabled off", enabled = false)
            }
        }
        Section("Power button") {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                PowerState.entries.forEach { PowerButton(it, {}, size = 88.dp) }
            }
            var step by remember { mutableIntStateOf(0) }
            Text("Tap to cycle", style = Warden.type.label, color = c.muted)
            PowerButton(PowerState.entries[step % 3], { step++ })
        }
        Section("Empty state") {
            EmptyState(R.drawable.ph_pulse_light, "Nothing blocked yet.", "Give it a few minutes.")
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Text(title.uppercase(), style = Warden.type.label, color = Warden.colors.muted)
        HorizontalDivider(color = Warden.colors.outline)
        content()
    }
}

@Composable
private fun Swatch(name: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(48.dp)
                .background(color, RoundedCornerShape(Radii.small))
                .border(1.dp, Warden.colors.outline, RoundedCornerShape(Radii.small)),
        )
        Text(name, style = Warden.type.label, color = Warden.colors.muted)
    }
}

@Preview(name = "Light", heightDp = 1800)
@Composable
private fun GalleryLight() = WardenTheme(darkTheme = false) { ComponentGallery() }

@Preview(name = "Dark", heightDp = 1800, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun GalleryDark() = WardenTheme(darkTheme = true) { ComponentGallery() }
