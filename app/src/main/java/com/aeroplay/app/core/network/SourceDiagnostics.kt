package com.aeroplay.app.core.network

import java.util.logging.Logger

/** Stage/count diagnostics only; never log queries, response bodies or signed media URLs. */
object SourceDiagnostics {
    private val logger = Logger.getLogger("AeroPlaySource")

    fun event(stage: String, detail: String) {
        logger.info("$stage $detail thread=${Thread.currentThread().name}")
    }

    fun failure(stage: String, error: Throwable) {
        val causes = generateSequence(error) { it.cause }.take(8)
            .joinToString(" <- ") { it.javaClass.simpleName }
        logger.warning("$stage failed=$causes thread=${Thread.currentThread().name}")
    }
}
