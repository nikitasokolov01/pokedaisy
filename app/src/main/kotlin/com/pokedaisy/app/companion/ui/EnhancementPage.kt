package com.pokedaisy.app.companion.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pokedaisy.app.enhancements.*
import com.pokedaisy.app.companion.NewFeatures
import com.pokedaisy.app.companion.i18n.tr

@Composable
fun EnhancementDialog(session: EnhancementSession, game: String, onDismiss: () -> Unit) {
    val m = rememberGbaTextMetrics()
    OptionOverlay(onDismiss, Modifier.widthIn(max = 1100.dp).fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().heightIn(max = 640.dp)) {
            OptionTitleWindow(tr("ENHANCEMENTS"), m, trailing = game, onBack = onDismiss)
            OptionListWindow(m, Modifier.fillMaxWidth().weight(1f, fill = false)) {
                EnhancementPage(session, Modifier.fillMaxWidth())
            }
        }
    }
}

/** The same page in the library and companion; no keyboard or ROM/save access during composition. */
@Composable
fun EnhancementPage(session: EnhancementSession?, modifier: Modifier = Modifier) {
    var state by remember(session) { mutableStateOf(session?.view ?: EnhancementView()) }
    LaunchedEffect(session) {
        session?.changes?.collect { state = it }
    }
    BoxWithConstraints(modifier) {
        val m = rememberGbaTextMetrics(if (maxWidth < 400.dp) 1f else 1.25f)
        val small = rememberGbaTextMetrics(1f)
        val rowHeight = m.rowHeight * 1.2f + subtitleMetrics(m).lineHeight
        var selected by remember { mutableStateOf(-1) }
        var selector by remember(session) { mutableStateOf<EnhancementFeature?>(null) }
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            GbaText(tr(state.identity), OptionColors.label, OptionColors.labelShadow, small,
                Modifier.padding(m.u * 8))
            GbaText(tr("Only this exact ROM. All features start off."), OptionColors.label, OptionColors.labelShadow, small,
                Modifier.padding(horizontal = m.u * 8, vertical = m.u * 4), maxLines = 3)
            OptionLine(tr("ALLOW ENHANCEMENTS"), tr(if (state.preferences.allowed) "ON" else "OFF"), selected == 0, m,
                height = rowHeight, labelWeight = 0.82f,
                labelBadge = NewFeatures.badge(NewFeatures.ENHANCEMENTS), enabled = state.editable,
                subtitle = tr("Permission only. Unavailable features stay off."), onFocus = { selected = 0 }) {
                session?.allow(!state.preferences.allowed); state = session?.view ?: state
            }
            EnhancementFeature.entries.forEachIndexed { i, feature ->
                val reason = state.reasons[feature]
                val enabled = state.editable && state.preferences.allowed && reason == null
                OptionLine(tr(feature.label), tr(if (reason != null) "N/A" else state.preferences.choice(feature)), selected == i + 1, m,
                    height = rowHeight, labelWeight = 0.82f,
                    enabled = enabled, onFocus = { selected = i + 1 },
                    subtitle = tr(reason ?: if (feature in state.active) "ACTIVE" else "Applies at a safe point; rechecked after loading.")) {
                    val values = feature.options
                    if (values.size > 2) selector = feature
                    else session?.choose(feature, values[(values.indexOf(state.preferences.choice(feature)) + 1) % values.size])
                    state = session?.view ?: state
                }
            }
            state.error?.let { GbaText(tr(it), OptionColors.label, OptionColors.labelShadow, small, Modifier.padding(m.u * 8)) }
            OptionLine(tr("RESET ENHANCEMENTS"), null, selected == 5, m, enabled = state.editable,
                onFocus = { selected = 5 }, height = m.rowHeight * 1.2f, labelWeight = 0.95f, onClick = {
                session?.reset(); state = session?.view ?: state
            })
        }
        selector?.let { feature ->
            OptionSelector(tr(feature.label), feature.options, state.preferences.choice(feature), { tr(it) }, m,
                onPick = { choice ->
                    session?.choose(feature, choice)
                    state = session?.view ?: state
                    selector = null
                }, onDismiss = { selector = null })
        }
    }
}
