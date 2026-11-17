package com.aeroplay.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class BrandResourcesDeviceTest {
    @Test fun splashUsesDedicatedArtworkWithNoPixelsOutsideTheSystemSafeCircle() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        if (Build.VERSION.SDK_INT < 31) return
        val theme = context.resources.newTheme().apply { applyStyle(R.style.Theme_AeroPlay, true) }
        val value = android.util.TypedValue()
        assertTrue(theme.resolveAttribute(android.R.attr.windowSplashScreenAnimatedIcon, value, true))
        assertEquals(R.drawable.brand_splash_icon, value.resourceId)
        val icon = requireNotNull(context.getDrawable(value.resourceId))
        assertFalse("The splash must not reuse the masked launcher icon", icon is AdaptiveIconDrawable)
        val bitmap = Bitmap.createBitmap(288, 288, Bitmap.Config.ARGB_8888)
        try {
            icon.setBounds(0, 0, 288, 288)
            icon.draw(Canvas(bitmap))
            var painted = 0
            for (y in 0 until 288) for (x in 0 until 288) {
                if (android.graphics.Color.alpha(bitmap.getPixel(x, y)) == 0) continue
                painted++
                val dx = x + 0.5 - 144
                val dy = y + 0.5 - 144
                assertTrue("Artwork would be clipped at ($x, $y)", dx * dx + dy * dy <= 96.0 * 96.0)
            }
            assertTrue("The complete image should remain visible", painted > 15_000)
        } finally { bitmap.recycle() }
    }

    @Test fun launcherIconInflatesAndRendersOnSupportedAndroidVersion() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val icon = context.packageManager.getApplicationIcon(context.packageName)
        assertTrue(icon is AdaptiveIconDrawable)
        if (Build.VERSION.SDK_INT >= 33) assertNotNull((icon as AdaptiveIconDrawable).monochrome)
        val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        try {
            icon.setBounds(0, 0, 256, 256)
            icon.draw(Canvas(bitmap))
            val colors = IntArray(256 * 256).also { bitmap.getPixels(it, 0, 256, 0, 0, 256, 256) }
            assertTrue("Launcher drawable must contain the supplied multicolor art", colors.distinct().size > 200)
            for ((x, y) in listOf(128 to 20, 236 to 128, 128 to 236, 20 to 128)) {
                val pixel = bitmap.getPixel(x, y)
                assertTrue("Launcher edge must be fully painted", android.graphics.Color.alpha(pixel) >= 240)
                assertTrue("Launcher must have a colored edge, not a black ring",
                    android.graphics.Color.blue(pixel) >= 100 &&
                        android.graphics.Color.red(pixel) + android.graphics.Color.green(pixel) + android.graphics.Color.blue(pixel) > 150)
            }
            val file = File(context.getExternalFilesDir(null), "launcher-icon.png")
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            for (command in listOf("mkdir -p /sdcard/Download/aeroplay-brand-review", "cp ${file.absolutePath} /sdcard/Download/aeroplay-brand-review/launcher-icon.png")) {
                instrumentation.uiAutomation.executeShellCommand(command).use {
                    java.io.FileInputStream(it.fileDescriptor).use { stream -> stream.readBytes() }
                }
            }
        } finally { bitmap.recycle() }
    }
}
