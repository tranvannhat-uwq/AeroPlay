package com.aeroplay.app.feature.library

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts

/** Uses the photo picker, or the device gallery on versions without a photo picker. */
class GalleryVideoPicker : ActivityResultContract<Unit, List<Uri>>() {
    private val photoPicker = ActivityResultContracts.PickMultipleVisualMedia(50)

    override fun createIntent(context: Context, input: Unit): Intent {
        return if (ActivityResultContracts.PickVisualMedia.isPhotoPickerAvailable(context)) {
            photoPicker.createIntent(context, PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
        } else {
            Intent(Intent.ACTION_PICK).apply {
                setDataAndType(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "video/*")
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                putExtra(Intent.EXTRA_LOCAL_ONLY, true)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): List<Uri> =
        photoPicker.parseResult(resultCode, intent).distinct()
}
