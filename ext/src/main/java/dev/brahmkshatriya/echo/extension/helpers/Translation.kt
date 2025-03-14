package dev.brahmkshatriya.echo.extension.helpers

import dev.brahmkshatriya.echo.common.models.Lyrics
import dev.brahmkshatriya.echo.extension.MediaTreeItem
import dev.brahmkshatriya.echo.extension.Work
import dev.brahmkshatriya.echo.extension.WorksResponse
import me.bush.translator.Language
import me.bush.translator.Translator


suspend fun Work.translate(): Work {
    val translator = Translator()
    val translated = translator.translate(this.title, Language.ENGLISH, Language.AUTO)
    this.title = translated.translatedText.moveFirstGroupToEnd()
    return this
}

fun String.moveFirstGroupToEnd(): String {
    val regex = """^([\[【][^]】]*[]】])""".toRegex()
    val match = regex.find(this)
    return if (match != null) {
        val firstGroup = match.groupValues[1]
        this.replaceFirst(firstGroup, "") + firstGroup
    } else {
        this
    }
}

suspend fun Lyrics.Timed.translate(): Lyrics.Timed {
    val stings = this.list.map { it.text }
    val translationMap = translateList(stings) ?: return this
    val items = mutableListOf<Lyrics.Item>()
    this.list.forEach {
        val translatedText = translationMap[it.text]?.moveFirstGroupToEnd() ?: it.text
        items.add(Lyrics.Item(translatedText, it.startTime, it.endTime))
    }
    return Lyrics.Timed(items)
}

suspend fun WorksResponse.translate(): WorksResponse {
    val titles = this.works.map { it.title }
    val subtitles = this.works.map { it.name }
    val translationMap = translateList(titles + subtitles) ?: return this
    this.works.forEach { it.title = translationMap[it.title]?.moveFirstGroupToEnd() ?: it.title }
    this.works.forEach { it.name = translationMap[it.name]?.moveFirstGroupToEnd() ?: it.name }
    return this
}

suspend fun MediaTreeItem.Folder.translate(): MediaTreeItem.Folder {
    val titles = this.getAllTitles()
    val translationMap = translateList(titles) ?: return this
    this.applyTranslations(translationMap)
    return this
}

suspend fun translateList(list: List<String>): Map<String, String>? {
    val translator = Translator()
    val titleString = list.joinToString("\n")
    val translated = translator.translate(titleString, Language.ENGLISH, Language.AUTO)
    val translatedList = translated.translatedText.split("\n")
    if (translatedList.size != list.size) return null
    return list.zip(translatedList).toMap()
}

fun MediaTreeItem.Folder.applyTranslations(translationMap: Map<String, String>) {
    for (item in children) {
        when (item) {
            is MediaTreeItem.Audio -> item.title = translationMap[item.title] ?: item.title
            is MediaTreeItem.Folder -> {
                item.title = translationMap[item.title] ?: item.title
                item.applyTranslations(translationMap)
            }
            else -> {}
        }
    }
}

