package com.aeroplay.app.core.network

import com.aeroplay.app.BuildConfig
import java.util.logging.Logger

/** Debug-build diagnostics only; Release builds never log queries or signed media URLs. */
object SourceDiagnostics {
    private val logger = Logger.getLogger("AeroPlaySource")

    fun event(stage: String, detail: String) {
        if (!BuildConfig.DEBUG) return
        logger.info("$stage $detail thread=${Thread.currentThread().name}")
    }

    fun failure(stage: String, error: Throwable) {
        if (!BuildConfig.DEBUG) return
        val causes = generateSequence(error) { it.cause }.take(8)
            .joinToString(" <- ") { it.javaClass.simpleName }
        logger.warning("$stage failed=$causes thread=${Thread.currentThread().name}")
    }
}
