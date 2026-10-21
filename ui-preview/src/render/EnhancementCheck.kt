@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
package render

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.Density
import com.pokedaisy.app.companion.CompanionSettings
import com.pokedaisy.app.companion.ui.*
import com.pokedaisy.app.companion.ui.theme.AppTheme
import com.pokedaisy.app.enhancements.*
import java.io.File
import javax.imageio.ImageIO

fun enhancementPreviewSession(known: Boolean = false): EnhancementSession {
    val root = File(System.getProperty("scratch"), "enhancements-${System.nanoTime()}").apply { mkdirs() }
    val revision = RomVerification.revisions.first()
    return EnhancementSession(EnhancementSettings(root)).apply {
        inspect(RomFingerprint("a".repeat(64), if (known) revision.sha1 else "b".repeat(40), 4096, true, revision.code, 0))
    }
}

@Composable
fun EnhancementPreview(session: EnhancementSession, back: CompanionBack = remember { CompanionBack() }) {
    val settings = remember(session) { object : CompanionSettings by FakeSettings() {
        override val enhancements = session
        override val gameName = "DS TEST GAME"
    } }
    AppTheme {
        CompositionLocalProvider(LocalCompanionBack provides back) {
            CompanionSettingsScreen(settings, initialPage = "ENHANCEMENTS")
        }
    }
}

fun main(args: Array<String>) {
    val out = File(args.first()).apply { mkdirs() }
    for ((name, size, density) in listOf(Triple("thor", Size(1240f, 1080f), 2.625f),
        Triple("wide", Size(1920f, 1080f), 2f), Triple("compact", Size(720f, 640f), 2f))) {
        runSkikoComposeUiTest(size, Density(density)) {
            mainClock.autoAdvance = false
            val session = enhancementPreviewSession()
            val back = CompanionBack()
            setContent { EnhancementPreview(session, back) }
            mainClock.advanceTimeBy(500)
            onAllNodesWithText("UNKNOWN REVISION").onFirst().assertExists()
            onNodeWithText("ALLOW ENHANCEMENTS").performSemanticsAction(SemanticsActions.RequestFocus)
            onRoot().performKeyInput { pressKey(Key.Enter) }
            mainClock.advanceTimeBy(300)
            runOnIdle { check(session.view.preferences.allowed) }
            onNodeWithText("ALLOW ENHANCEMENTS").performSemanticsAction(SemanticsActions.OnClick)
            runOnIdle { check(!session.view.preferences.allowed); check(session.view.active.isEmpty()) }
            ImageIO.write(captureAllRoots(), "png", File(out, "$name.png"))
            onNodeWithText("RESET ENHANCEMENTS").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
            runOnIdle { check(back.back()) }
            mainClock.advanceTimeBy(300)
            onNodeWithText("FF SPEED").assertExists()
            println("PASS $name: controller activation, touch, reset, back; unknown ROM never activates a feature")
        }
    }
    runSkikoComposeUiTest(Size(1240f, 1080f), Density(2.625f)) {
        mainClock.autoAdvance = false
        val session = enhancementPreviewSession(known = true)
        setContent { EnhancementPreview(session) }
        mainClock.advanceTimeBy(500)
        onAllNodesWithText("KNOWN REVISION").onFirst().assertExists()
        onAllNodesWithText("NOT IMPLEMENTED").onFirst().assertExists()
        runOnIdle { check(session.view.reasons.values.all { it == "NOT IMPLEMENTED" }) }
        ImageIO.write(captureAllRoots(), "png", File(out, "known.png"))
        println("PASS known revision: recognition does not imply modifier support")
    }
    runSkikoComposeUiTest(Size(1920f, 1080f), Density(2f)) {
        mainClock.autoAdvance = false
        val session = enhancementPreviewSession()
        val back = CompanionBack()
        var open by mutableStateOf(true)
        setContent {
            AppTheme { CompositionLocalProvider(LocalCompanionBack provides back) {
                if (open) EnhancementDialog(session, "DS TEST GAME") { open = false }
            } }
        }
        mainClock.advanceTimeBy(500)
        onNodeWithText("ALLOW ENHANCEMENTS").performSemanticsAction(SemanticsActions.OnClick)
        runOnIdle { check(session.view.preferences.allowed); check(session.view.active.isEmpty()) }
        mainClock.advanceTimeBy(300)
        ImageIO.write(captureAllRoots(), "png", File(out, "library.png"))
        runOnIdle { check(back.back()); check(!open) }
        println("PASS library: shared permission, visible game identity, back closes dialog")
    }
}
