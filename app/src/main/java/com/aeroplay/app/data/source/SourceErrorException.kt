package com.aeroplay.app.data.source

import com.aeroplay.app.core.model.SourceError

class SourceErrorException(
    val sourceError: SourceError,
    cause: Throwable? = null
) : Exception(sourceError.message, cause)
