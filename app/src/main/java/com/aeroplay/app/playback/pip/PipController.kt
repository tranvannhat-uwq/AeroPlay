package com.aeroplay.app.playback.pip

import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational
import com.aeroplay.app.R

/**
 * Official Android Picture-in-Picture (PiP) helper (API 26+).
 * Never uses SYSTEM_ALERT_WINDOW (Requirement V.5 & VIII).
 */
object PipController {

    const val ACTION_PIP_TOGGLE_PLAY = "com.aeroplay.app.ACTION_PIP_TOGGLE_PLAY"

    fun isPipSupported(context: Context): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    }

    fun buildPipParams(
        context: Context,
        isPlaying: Boolean,
        autoEnterEnabled: Boolean,
        videoWidth: Int = 16,
        videoHeight: Int = 9
    ): PictureInPictureParams {
        val safeWidth = videoWidth.takeIf { it > 0 } ?: 16
        val safeHeight = videoHeight.takeIf { it > 0 } ?: 9
        val ratioFloat = (safeWidth.toFloat() / safeHeight.toFloat()).coerceIn(0.42f, 2.38f)
        val aspectRatio = Rational((ratioFloat * 1000).toInt(), 1000)

        val toggleIntent = Intent(ACTION_PIP_TOGGLE_PLAY).setPackage(context.packageName)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            101,
            toggleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val iconRes = if (isPlaying) {
            android.R.drawable.ic_media_pause
        } else {
            android.R.drawable.ic_media_play
        }
        val title = context.getString(if (isPlaying) R.string.pip_pause else R.string.pip_play)

        val playPauseAction = RemoteAction(
            Icon.createWithResource(context, iconRes),
            title,
            title,
            pendingIntent
        )

        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(aspectRatio)
            .setActions(listOf(playPauseAction))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(autoEnterEnabled && isPlaying)
            builder.setSeamlessResizeEnabled(true)
        }

        return builder.build()
    }

    fun updatePipParams(
        activity: Activity,
        isPlaying: Boolean,
        autoEnterEnabled: Boolean,
        videoWidth: Int = 16,
        videoHeight: Int = 9
    ) {
        if (!isPipSupported(activity)) return
        runCatching {
            val params = buildPipParams(
                context = activity,
                isPlaying = isPlaying,
                autoEnterEnabled = autoEnterEnabled,
                videoWidth = videoWidth,
                videoHeight = videoHeight
            )
            activity.setPictureInPictureParams(params)
        }
    }

    fun enterPip(
        activity: Activity,
        isPlaying: Boolean,
        autoEnterEnabled: Boolean,
        videoWidth: Int = 16,
        videoHeight: Int = 9
    ): Boolean {
        if (!isPipSupported(activity)) return false
        return runCatching {
            val params = buildPipParams(
                context = activity,
                isPlaying = isPlaying,
                autoEnterEnabled = autoEnterEnabled,
                videoWidth = videoWidth,
                videoHeight = videoHeight
            )
            activity.enterPictureInPictureMode(params)
        }.getOrDefault(false)
    }
}
