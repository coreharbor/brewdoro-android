// Copyright (c) 2026 Brewdoro contributors. SPDX-License-Identifier: MIT
package io.github.coreharbor.brewdoro.ui

import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real Canvas captures, fixed fill level and manually advanced Compose clock. */
@RunWith(AndroidJUnit4::class)
class CoffeeMotionTest {
    @get:Rule val compose = createComposeRule()
    private fun shell(command: String) {
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).use {
            java.io.FileInputStream(it.fileDescriptor).use { stream -> stream.readBytes() }
        }
    }
    private fun pixels(): IntArray {
        val image = compose.onNodeWithTag("coffee_cup").captureToImage()
        val map = image.toPixelMap()
        return IntArray(image.width * image.height) { index -> map[index % image.width, index / image.width].hashCode() }
    }
    @Test fun midlevelSurfaceRendersDifferentFramesThenSettlesAndHonorsReducedMotion() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val original = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        var active by mutableStateOf(true)
        try {
            shell("settings put global animator_duration_scale 1")
            compose.mainClock.autoAdvance = false
            compose.setContent { BrewdoroTheme { CoffeeCup(.5f, animate = active) } }
            compose.mainClock.advanceTimeBy(32)
            val first = pixels()
            compose.mainClock.advanceTimeBy(1400)
            val second = pixels()
            val changed = first.indices.count { first[it] != second[it] }
            assertTrue("Actual rendered midlevel coffee pixels must change", changed > 0)
            compose.runOnUiThread { active = false }
            compose.mainClock.advanceTimeBy(32)
            val paused = pixels()
            compose.mainClock.advanceTimeBy(1400)
            assertArrayEquals("Inactive coffee is static", paused, pixels())
            shell("settings put global animator_duration_scale 0")
            compose.runOnUiThread { active = true }
            compose.mainClock.advanceTimeBy(64)
            // Allow the ContentObserver to publish scale=0, without advancing an infinite clock.
            compose.waitUntil(5000) {
                Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
            }
            compose.mainClock.advanceTimeBy(64)
            val reduced = pixels()
            compose.mainClock.advanceTimeBy(1400)
            assertArrayEquals("System animation scale zero disables coffee motion", reduced, pixels())
            android.util.Log.i("BrewdoroComfort", "PASS: animation rendered frames changedPixels=$changed; inactive and system-scale-zero coffee frames static; manual Compose clock")
        } finally {
            compose.runOnUiThread { active = false }
            compose.mainClock.advanceTimeBy(32)
            shell("settings put global animator_duration_scale $original")
            compose.mainClock.autoAdvance = true
        }
    }
}
