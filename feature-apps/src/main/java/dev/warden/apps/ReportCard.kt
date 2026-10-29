package dev.warden.apps

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import dev.warden.data.History
import dev.warden.data.Range
import dev.warden.design.ButtonKind
import dev.warden.design.Spacing
import dev.warden.design.Warden
import dev.warden.design.WardenButton
import dev.warden.design.tabular
import dev.warden.rules.Trackers
import dev.warden.rules.summarizeTrackers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt
import dev.warden.design.R as DesignR

/** Inputs to the grade, and the grade itself. The formula is documented in docs/report-card.md. */
internal data class Grade(
    val letter: String,
    val score: Int,
    val companies: Int,
    val trackerShare: Float,
    val screenOffShare: Float,
)

internal fun grade(companies: Int, trackerShare: Float, screenOffShare: Float): Grade {
    val score = (100 - PER_COMPANY * minOf(companies, MAX_COMPANIES) - TRACKER_WEIGHT * trackerShare - SCREEN_OFF_WEIGHT * screenOffShare)
        .roundToInt().coerceIn(0, 100)
    val letter = when {
        score >= 90 -> "A"
        score >= 80 -> "B"
        score >= 65 -> "C"
        score >= 50 -> "D"
        else -> "F"
    }
    return Grade(letter, score, companies, trackerShare, screenOffShare)
}

private const val PER_COMPANY = 9
private const val MAX_COMPANIES = 6
private const val TRACKER_WEIGHT = 30f
private const val SCREEN_OFF_WEIGHT = 15f

/** Bottom sheet with the card and a Share button that sends it as a PNG. Last 7 days of history. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReportCardSheet(uid: Int, name: String, icon: ImageBitmap?, onDismiss: () -> Unit) {
    val c = Warden.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val domains by remember(uid) { History.appDomains(uid, Range.Week) }.collectAsState(emptyList())
    val all by remember { History.apps(Range.Week, limit = 500) }.collectAsState(emptyList())
    val off by remember { History.apps(Range.Week, screenOffOnly = true, limit = 500) }.collectAsState(emptyList())
    val totals = all.firstOrNull { it.uid == uid }
    val offTotals = off.firstOrNull { it.uid == uid }
    val trackers = summarizeTrackers(domains.map { it.domain })
    val events = domains.sumOf { it.count }.coerceAtLeast(1)
    val trackerHits = domains.filter { Trackers.lookup(it.domain) != null }.sumOf { it.count }
    val connections = totals?.connections ?: 0
    val offShare = ((offTotals?.connections ?: 0) + (offTotals?.blocked ?: 0)).toFloat() /
        ((totals?.connections ?: 0) + (totals?.blocked ?: 0)).coerceAtLeast(1)
    val g = grade(trackers.companies, trackerHits.toFloat() / events, offShare)

    val layer = rememberGraphicsLayer()
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.background) {
        Column(
            Modifier.padding(horizontal = Spacing.xl).padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            // The card is recorded into a layer so Share can export exactly what's on screen.
            Box(
                Modifier.drawWithContent {
                    layer.record { this@drawWithContent.drawContent() }
                    drawLayer(layer)
                },
            ) {
                Card(name, icon, g, connections, totals?.blocked ?: 0, trackers.byCompany.map { it.first })
            }
            WardenButton(
                stringResource(R.string.report_share),
                {
                    scope.launch {
                        val bitmap = layer.toImageBitmap().asAndroidBitmap()
                        share(context, bitmap, name)
                    }
                },
                Modifier.fillMaxWidth(),
            )
            WardenButton(stringResource(R.string.report_formula), { openFormula(context) }, Modifier.fillMaxWidth(), kind = ButtonKind.Text)
        }
    }
}

/** The shareable card itself: always dark, so it looks the same wherever it's posted. */
@Composable
private fun Card(name: String, icon: ImageBitmap?, g: Grade, connections: Int, blocked: Int, companies: List<String>) {
    val ink = Color(0xFF0E0F0D)
    val lime = Color(0xFFD8F23C)
    val paper = Color(0xFFF3F4EE)
    val muted = Color(0xFF9A9C97)
    val gradeColor = when (g.letter) {
        "A", "B" -> lime
        "C" -> Color(0xFFF2BC45)
        else -> Color(0xFFFF6B4A)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(ink)
            .padding(Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            if (icon != null) Image(icon, null, Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)))
            Column(Modifier.weight(1f)) {
                Text(name, style = Warden.type.title, color = paper, maxLines = 1)
                Text(stringResource(R.string.report_kicker).uppercase(), style = Warden.type.caption, color = muted)
            }
            Box(Modifier.size(72.dp).background(gradeColor, CircleShape), contentAlignment = Alignment.Center) {
                Text(g.letter, style = Warden.type.display.copy(fontSize = 40.sp, fontWeight = FontWeight.SemiBold), color = ink)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xl)) {
            Stat(connections.toString(), pluralStringResource(R.plurals.report_connections, connections), paper, muted)
            Stat(g.companies.toString(), pluralStringResource(R.plurals.report_companies, g.companies), paper, muted)
            Stat(blocked.toString(), stringResource(R.string.report_blocked), paper, muted)
        }
        if (companies.isNotEmpty()) {
            Text(companies.take(MAX_LISTED).joinToString("  ·  "), style = Warden.type.body, color = paper)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Icon(painterResource(DesignR.drawable.ph_shield_check), null, Modifier.size(16.dp), tint = lime)
            Text(stringResource(R.string.report_footer, g.score), style = Warden.type.caption, color = muted)
        }
    }
}

@Composable
private fun Stat(value: String, label: String, paper: Color, muted: Color) {
    Column {
        Text(value, style = Warden.type.headline.tabular(), color = paper)
        Text(label.uppercase(), style = Warden.type.caption, color = muted)
    }
}

private suspend fun share(context: Context, bitmap: Bitmap, name: String) {
    val file = withContext(Dispatchers.IO) {
        File(context.cacheDir, "reports").apply { mkdirs() }.let { dir ->
            File(dir, "warden-report.png").also { f -> f.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        }
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.reports", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("image/png")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_TEXT, context.getString(R.string.report_share_text, name))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, null))
}

private fun openFormula(context: Context) {
    context.startActivity(
        Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/sammyZi/warden/blob/main/docs/report-card.md")),
    )
}

private const val MAX_LISTED = 5
