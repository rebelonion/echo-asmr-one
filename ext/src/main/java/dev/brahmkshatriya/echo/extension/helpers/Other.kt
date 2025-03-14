package dev.brahmkshatriya.echo.extension.helpers

import dev.brahmkshatriya.echo.common.models.Date
import dev.brahmkshatriya.echo.common.models.ImageHolder
import dev.brahmkshatriya.echo.common.models.ImageHolder.Companion.toImageHolder

fun String.buildImageHolder(): ImageHolder {
    return this.toImageHolder(
        crop = true
    )
}

fun String.toDate(): Date {
    val parts = this.split("-")
    return Date(
        year = parts[0].toInt(),
        month = parts[1].toInt(),
        day = parts[2].toInt()
    )
}
