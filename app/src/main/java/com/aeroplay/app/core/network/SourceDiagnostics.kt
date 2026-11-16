package com.aeroplay.app.core.network

import com.aeroplay.app.BuildConfig
import com.google.android.gms.common.api.ApiException
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

    /** OAuth diagnostics intentionally omit exception messages, account identifiers, and credentials. */
    fun oauthFailure(stage: String, error: Throwable) {
        if (!BuildConfig.DEBUG) return
        val causes = generateSequence(error) { it.cause }.take(8).toList()
        val causeTypes = causes.joinToString("<-") { it.javaClass.simpleName }
        val statusCodes = causes.filterIsInstance<ApiException>()
            .joinToString(",") { it.statusCode.toString() }
            .ifBlank { "none" }
        logger.warning("youtube.oauth stage=$stage causeTypes=$causeTypes googleStatusCodes=$statusCodes thread=${Thread.currentThread().name}")
    }
}
