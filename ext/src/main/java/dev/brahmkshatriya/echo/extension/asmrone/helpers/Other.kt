package dev.brahmkshatriya.echo.extension.asmrone.helpers

import dev.brahmkshatriya.echo.common.models.Date
import dev.brahmkshatriya.echo.common.models.ImageHolder
import dev.brahmkshatriya.echo.common.models.ImageHolder.Companion.toImageHolder
import dev.brahmkshatriya.echo.common.settings.Settings
import dev.rebelonion.translator.Language

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
        day = parts[2].split(" ").first().toInt()
    )
}

fun String.findAll(char: Char) =
    this.mapIndexed { index, c -> if (c == char) index else null }.filterNotNull()

fun <T> T.listOf(): List<T> {
    return listOf(this)
}


fun Settings?.getTranslationLanguage(): Language {
    val language = this?.getString("translationLanguage") ?: return Language.ENGLISH
    return Language.entries.find { it.code == language } ?: Language.ENGLISH
}