package dev.warden.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.warden.design.EmptyState
import dev.warden.design.IconBadge
import dev.warden.design.SectionLabel
import dev.warden.design.BarSpacer
import dev.warden.design.Spacing
import dev.warden.design.Warden
import dev.warden.rules.AppRule
import dev.warden.rules.AppRules
import dev.warden.rules.Rules
import dev.warden.rules.Verdict
import dev.warden.design.R as DesignR

/** Every rule the user has set, in one place, each removable with one tap. */
@Composable
internal fun RulesScreen(appLabel: (Int) -> String, appIcon: (Int) -> ImageBitmap?, onBack: () -> Unit) {
    val c = Warden.colors
    val context = LocalContext.current
    val appRules by AppRules.rules.collectAsState()
    val excluded by AppRules.excluded.collectAsState()
    val domains by Rules.domains.collectAsState()
    // Exclusions are stored by package; show them by app like everything else.
    val skipped = remember(excluded) {
        excluded.mapNotNull { pkg -> runCatching { context.packageManager.getApplicationInfo(pkg, 0).uid }.getOrNull()?.let { pkg to it } }
    }
    val domainRules = remember(domains) { domains.entries.sortedBy { it.key.substringAfter(':') } }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onBack).padding(horizontal = Spacing.l),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                Icon(
                    painterResource(DesignR.drawable.ph_arrow_left),
                    stringResource(R.string.settings_back),
                    Modifier.size(20.dp),
                    tint = c.text,
                )
                SectionLabel(stringResource(R.string.settings_title_sans))
            }
            Column(Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.s)) {
                Text(stringResource(R.string.settings_rules), style = Warden.type.headline)
                Text(stringResource(R.string.settings_rules_hint), style = Warden.type.body, color = c.muted)
            }
        }
        if (appRules.isEmpty() && skipped.isEmpty() && domainRules.isEmpty()) {
            item {
                EmptyState(
                    DesignR.drawable.ph_shield_check_light,
                    stringResource(R.string.settings_rules_empty_title),
                    stringResource(R.string.settings_rules_empty_body),
                    Modifier.fillMaxWidth(),
                )
            }
        }
        if (appRules.isNotEmpty()) {
            item { Header(stringResource(R.string.settings_rules_apps)) }
            items(appRules.entries.toList(), key = { "app" + it.key }) { (uid, rule) ->
                RuleRow(
                    leading = { AppIcon(uid, appIcon) },
                    title = appLabel(uid),
                    caption = stringResource(
                        when {
                            rule.blockWifi && rule.blockCellular -> R.string.settings_blocked_both
                            rule.blockWifi -> R.string.settings_blocked_wifi
                            else -> R.string.settings_blocked_cellular
                        },
                    ),
                    tag = stringResource(R.string.settings_tag_blocked),
                    tagColor = c.blocked,
                ) { AppRules.set(uid, AppRule()) }
            }
        }
        if (skipped.isNotEmpty()) {
            item { Header(stringResource(R.string.settings_rules_skipped)) }
            items(skipped, key = { "skip" + it.first }) { (pkg, uid) ->
                RuleRow(
                    leading = { AppIcon(uid, appIcon) },
                    title = appLabel(uid),
                    caption = stringResource(R.string.settings_skipped_caption),
                    tag = stringResource(R.string.settings_tag_skipped),
                    tagColor = c.warning,
                ) { AppRules.setExcluded(listOf(pkg), false) }
            }
        }
        if (domainRules.isNotEmpty()) {
            item { Header(stringResource(R.string.settings_rules_domains)) }
            items(domainRules, key = { "dom" + it.key }) { (key, verdict) ->
                val scope = key.substringBefore(':')
                val uid = scope.toIntOrNull()
                RuleRow(
                    leading = {
                        if (uid != null) AppIcon(uid, appIcon) else IconBadge(DesignR.drawable.ph_globe_hemisphere_west, c.palette[4])
                    },
                    title = key.substringAfter(':'),
                    caption = if (uid != null) appLabel(uid) else stringResource(R.string.settings_everywhere),
                    tag = stringResource(if (verdict == Verdict.Block) R.string.settings_tag_blocked else R.string.settings_tag_allowed),
                    tagColor = if (verdict == Verdict.Block) c.blocked else c.accentInk,
                ) { Rules.remove(key) }
            }
        }
        item { BarSpacer() }
    }
}

@Composable
private fun Header(text: String) {
    SectionLabel(text, Modifier.padding(horizontal = Spacing.xl).padding(top = Spacing.xl, bottom = Spacing.s))
}

@Composable
private fun AppIcon(uid: Int, appIcon: (Int) -> ImageBitmap?) {
    val icon = appIcon(uid)
    if (icon != null) Image(icon, null, Modifier.size(36.dp)) else IconBadge(DesignR.drawable.ph_squares_four, Warden.colors.muted)
}

/** A rule: who it covers, what it does, a tag (word, not colour alone), and a Remove action. */
@Composable
private fun RuleRow(
    leading: @Composable () -> Unit,
    title: String,
    caption: String,
    tag: String,
    tagColor: Color,
    onRemove: () -> Unit,
) {
    val c = Warden.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = Spacing.xl, vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        leading()
        // Tag rides beside the title; the caption gets its own line (and a second if it needs one).
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Text(
                    title,
                    Modifier.weight(1f, fill = false),
                    style = Warden.type.body,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    tag.uppercase(),
                    Modifier
                        .background(tagColor.copy(alpha = 0.16f), RoundedCornerShape(50))
                        .padding(horizontal = Spacing.s, vertical = 2.dp),
                    style = Warden.type.caption,
                    color = tagColor,
                    maxLines = 1,
                )
            }
            Text(caption, style = Warden.type.label, color = c.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Box(
            Modifier.heightIn(min = 48.dp).clickable(onClick = onRemove).padding(horizontal = Spacing.s),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.settings_remove), style = Warden.type.label, color = c.accentInk)
        }
    }
    HorizontalDivider(Modifier.padding(start = Spacing.xl + 36.dp + Spacing.m), color = c.outline)
}
