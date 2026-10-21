package com.pokedaisy.app.companion.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import com.pokedaisy.app.companion.NewFeatures
import com.pokedaisy.app.companion.i18n.tr

val LocalExplicitTextEntry = staticCompositionLocalOf { false }

/** The existing companion's focus/overlay rules on the DS lower display, with no window chrome. */
@Composable
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
fun DsCompanionNavigation(
    keys: CompanionKeys,
    focusKeys: (MenuKey, Boolean, () -> Unit) -> Boolean,
    content: @Composable () -> Unit,
) {
    val starts = remember { FocusStarts() }
    val overlays = remember { OverlayStack() }
    var focused by remember { mutableStateOf(false) }
    val inputMode = androidx.compose.ui.platform.LocalInputModeManager.current
    keys.Handler(true) { key, repeat ->
        inputMode.requestInputMode(androidx.compose.ui.input.InputMode.Keyboard)
        focusKeys(key, repeat) { if (!focused) starts.focusNewest() }
    }
    CompositionLocalProvider(
        LocalFocusStarts provides starts, LocalOverlayStack provides overlays,
        LocalCompanionWindow provides true,
        LocalExplicitTextEntry provides true,
    ) {
        Box(Modifier.fillMaxSize().onFocusChanged { focused = it.hasFocus }.focusGroup()) {
            SidePanelCompanion(content = content)
        }
    }
}

/** Kept outside the stylus area in both modes; all labels use the existing pixel menu style. */
@Composable
fun DsScreenSwitch(companion: Boolean, onSwitch: () -> Unit) {
    val m = rememberGbaTextMetrics()
    Column(Modifier.fillMaxSize().background(OptionColors.listFill), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        OptionButton(if (companion) tr("GAME SCREEN") else tr("COMPANION"), m, onClick = onSwitch)
        if (companion) GbaText(tr("A: SELECT  B: BACK  START: GAME"), OptionColors.label, OptionColors.labelShadow, rememberGbaTextMetrics(1f))
        else if (NewFeatures.isNew(NewFeatures.DS_NAVIGATION)) GbaText(tr("NEW"), OptionColors.newBadge, OptionColors.labelShadow, rememberGbaTextMetrics(1f))
    }
}
