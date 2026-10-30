package dev.warden.apps

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import dev.warden.design.Motion
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import dev.warden.data.History
import dev.warden.data.Range
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

/** The grade and the penalties behind it. The formula is documented in docs/report-card.md. */
internal data class Grade(val companies: Int, val trackerShare: Float, val screenOffShare: Float) {
    val companyPenalty = PER_COMPANY * minOf(companies, MAX_COMPANIES)
    val trackerPenalty = (TRACKER_WEIGHT * trackerShare).roundToInt()
    val screenOffPenalty = (SCREEN_OFF_WEIGHT * screenOffShare).roundToInt()
    val score = (100 - companyPenalty - trackerPenalty - screenOffPenalty).coerceIn(0, 100)
    val letter = when {
        score >= 90 -> "A"
        score >= 80 -> "B"
        score >= 65 -> "C"
        score >= 50 -> "D"
        else -> "F"
    }
}

internal fun grade(companies: Int, trackerShare: Float, screenOffShare: Float) = Grade(companies, trackerShare, screenOffShare)

private const val PER_COMPANY = 9
private const val MAX_COMPANIES = 6
private const val TRACKER_WEIGHT = 30f
private const val SCREEN_OFF_WEIGHT = 15f

/** One app's last 7 days, graded. */
internal class Report(val grade: Grade, val connections: Int, val blocked: Int, val companies: List<String>)

@Composable
internal fun rememberReport(uid: Int): Report {
    val domains by remember(uid) { History.appDomains(uid, Range.Week) }.collectAsState(emptyList())
    val all by remember { History.apps(Range.Week, limit = 500) }.collectAsState(emptyList())
    val off by remember { History.apps(Range.Week, screenOffOnly = true, limit = 500) }.collectAsState(emptyList())
    val totals = all.firstOrNull { it.uid == uid }
    val offTotals = off.firstOrNull { it.uid == uid }
    val trackers = summarizeTrackers(domains.map { it.domain })
    val events = domains.sumOf { it.count }.coerceAtLeast(1)
    val trackerHits = domains.filter { Trackers.lookup(it.domain) != null }.sumOf { it.count }
    val offShare = ((offTotals?.connections ?: 0) + (offTotals?.blocked ?: 0)).toFloat() /
        ((totals?.connections ?: 0) + (totals?.blocked ?: 0)).coerceAtLeast(1)
    return Report(
        grade(trackers.companies, trackerHits.toFloat() / events, offShare),
        totals?.connections ?: 0,
        totals?.blocked ?: 0,
        trackers.byCompany.map { it.first },
    )
}

/** Full-height sheet: the card, Share (as a PNG), then how this app's grade was worked out. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReportCardSheet(report: Report, name: String, icon: ImageBitmap?, onDismiss: () -> Unit) {
    val c = Warden.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val layer = rememberGraphicsLayer()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = c.background) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Spacing.xl).padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            // The card is recorded into a layer so Share can export exactly what's on screen.
            Box(
                Modifier.drawWithContent {
                    layer.record { this@drawWithContent.drawContent() }
                    drawLayer(layer)
                },
            ) {
                Card(name, icon, report)
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
            Breakdown(report.grade)
        }
    }
}

/** The shareable card itself: always dark, so it looks the same wherever it's posted. */
@Composable
private fun Card(name: String, icon: ImageBitmap?, r: Report) {
    val ink = Color(0xFF0E0F0D)
    val paper = Color(0xFFF3F4EE)
    val muted = Color(0xFF9A9C97)
    val g = r.grade
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
                Text(name, style = Warden.type.title, color = paper, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(stringResource(R.string.report_kicker).uppercase(), style = Warden.type.caption, color = muted)
            }
            GradeBadge(g.letter, 72.dp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            val m = Modifier.weight(1f)
            Stat(r.connections.toString(), pluralStringResource(R.plurals.report_connections, r.connections), paper, muted, m)
            Stat(g.companies.toString(), pluralStringResource(R.plurals.report_companies, g.companies), paper, muted, m)
            Stat(r.blocked.toString(), stringResource(R.string.report_blocked), paper, muted, m)
        }
        if (r.companies.isNotEmpty()) {
            Text(r.companies.take(MAX_LISTED).joinToString("  ·  "), style = Warden.type.body, color = paper)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Icon(painterResource(DesignR.drawable.ph_shield_check), null, Modifier.size(16.dp), tint = Color(0xFFD8F23C))
            Text(stringResource(R.string.report_footer, g.score), style = Warden.type.caption, color = muted)
        }
    }
}

/** The letter in a filled circle: lime for A/B, amber for C, vermilion below. Same on any theme. */
@Composable
internal fun GradeBadge(letter: String, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    val fill = when (letter) {
        "A", "B" -> Color(0xFFD8F23C)
        "C" -> Color(0xFFF2BC45)
        else -> Color(0xFFFF6B4A)
    }
    Box(modifier.size(size).background(fill, CircleShape), contentAlignment = Alignment.Center) {
        Text(
            letter,
            style = Warden.type.display.copy(fontSize = (size.value * 0.55f).sp, fontWeight = FontWeight.SemiBold),
            color = Color(0xFF0E0F0D),
        )
    }
}

@Composable
private fun Stat(value: String, label: String, paper: Color, muted: Color, modifier: Modifier) {
    Column(modifier) {
        Text(value, style = Warden.type.headline.tabular(), color = paper)
        Text(label.uppercase(), style = Warden.type.caption, color = muted, maxLines = 2)
    }
}

/** This app's grade worked out: start at 100, minus each penalty, then the letter bands. Folded until asked for. */
@Composable
private fun Breakdown(g: Grade) {
    val c = Warden.colors
    var open by rememberSaveable { mutableStateOf(false) }
    val turn by animateFloatAsState(if (open) 90f else 0f, Motion.emphasized(Motion.MEDIUM), label = "caret")
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { open = !open },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.report_formula), Modifier.weight(1f), style = Warden.type.body.copy(fontWeight = FontWeight.Medium))
        Icon(
            painterResource(DesignR.drawable.ph_caret_right),
            null,
            Modifier.size(16.dp).graphicsLayer { rotationZ = turn },
            tint = c.muted,
        )
    }
    AnimatedVisibility(open) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Line(stringResource(R.string.report_start), "100")
            HorizontalDivider(color = c.outline)
            Line(pluralStringResource(R.plurals.report_line_companies, g.companies, g.companies), "−${g.companyPenalty}")
            Line(stringResource(R.string.report_line_trackers, (g.trackerShare * 100).roundToInt()), "−${g.trackerPenalty}")
            Line(stringResource(R.string.report_line_screen_off, (g.screenOffShare * 100).roundToInt()), "−${g.screenOffPenalty}")
            HorizontalDivider(color = c.outline)
            Line(stringResource(R.string.report_line_score, g.letter), g.score.toString(), strong = true)
            Text(stringResource(R.string.report_bands), style = Warden.type.label, color = c.muted)
        }
    }
}

@Composable
private fun Line(label: String, value: String, strong: Boolean = false) {
    val style = if (strong) Warden.type.body.copy(fontWeight = FontWeight.Medium) else Warden.type.body
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Text(label, Modifier.weight(1f), style = style)
        Text(value, style = style.tabular(), color = if (strong) Warden.colors.text else Warden.colors.muted)
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

private const val MAX_LISTED = 5
