package dev.brahmkshatriya.echo.extension.asmrone.helpers

import dev.brahmkshatriya.echo.common.models.Lyrics
import dev.brahmkshatriya.echo.extension.asmrone.MediaTreeItem
import dev.brahmkshatriya.echo.extension.asmrone.Work
import dev.brahmkshatriya.echo.extension.asmrone.WorksResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import dev.rebelonion.translator.Language
import dev.rebelonion.translator.Translator


suspend fun Work.translate(language: Language): Work {
    val translator = Translator()
    val translated = translator.translate(this.title, language, Language.AUTO)
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

suspend fun Lyrics.Timed.translate(language: Language): Lyrics.Timed {
    val stings = this.list.map { it.text }
    val translationMap = translateList(stings, language, hardFail = true) ?: return this
    val items = mutableListOf<Lyrics.Item>()
    this.list.forEach {
        val translatedText = translationMap[it.text]?.moveFirstGroupToEnd() ?: it.text
        items.add(Lyrics.Item(translatedText, it.startTime, it.endTime))
    }
    return Lyrics.Timed(items)
}

suspend fun WorksResponse.translate(language: Language): WorksResponse {
    val titles = this.works.map { it.title }
    val subtitles = this.works.map { it.name }
    val translationMap = translateList(titles + subtitles, language) ?: return this
    this.works.forEach { it.title = translationMap[it.title]?.moveFirstGroupToEnd() ?: it.title }
    this.works.forEach { it.name = translationMap[it.name]?.moveFirstGroupToEnd() ?: it.name }
    return this
}

suspend fun MediaTreeItem.Folder.translate(language: Language): MediaTreeItem.Folder {
    val titles = this.getAllTitles()
    val translationMap = translateList(titles, language) ?: return this
    this.applyTranslations(translationMap)
    return this
}

private val translationCache = TimeBasedLRUCache<Map<String, String>>(1000)

private suspend fun translateList(
    list: List<String>,
    language: Language,
    hardFail: Boolean = false
): Map<String, String>? {
    if (list.isEmpty()) {
        return emptyMap()
    }
    val cacheKey = list.joinToString("").hashCode().toString()
    translationCache.get(cacheKey)?.let { cachedTranslation ->
        if (list.all { cachedTranslation.containsKey(it) }) {
            println("asmr-logging: Translation cache hit")
            return cachedTranslation
        }
    }

    val translator = Translator()
    val chunks = splitIntoChunks(list.map { it.replace("\n", " ") })
    if (chunks.flatten().size != list.size) {
        println("asmr-logging: Chunking failed")
        if (hardFail)
            throw Exception("Chunking failed")
        else return null
    }

    return withContext(Dispatchers.IO) {
        coroutineScope {
            val translatedChunks = chunks.map { chunk ->
                async {
                    val titleString = chunk.joinToString("\n")

                    val translatedCatch = translator.translateCatching(
                        titleString, language, Language.AUTO
                    )

                    if (translatedCatch.isFailure) {
                        val exception = translatedCatch.exceptionOrNull()
                        println("asmr-logging: Translation failed: $exception")
                        return@async null
                    }

                    val translated = translatedCatch.getOrNull() ?: return@async null
                    translated.translatedText.split("\n")
                }
            }.awaitAll()

            if (translatedChunks.any { it == null }) {
                if (hardFail)
                    throw Exception("Chunk failed to translate")
                else return@coroutineScope null
            }

            val allTranslatedItems = translatedChunks.filterNotNull().flatten()

            if (allTranslatedItems.size != list.size) {
                println("asmr-logging: Translation count mismatch. Original: ${list.size}, Translated: ${allTranslatedItems.size}")
                println("asmr-logging: Original: $list")
                println("asmr-logging: Translated: $allTranslatedItems")
                if (hardFail)
                    throw Exception("Translation count mismatch")
                else return@coroutineScope null
            }
            val result = list.zip(allTranslatedItems).toMap()
            translationCache.put(cacheKey, result)
            result
        }
    }
}

private fun splitIntoChunks(list: List<String>): List<List<String>> {
    val chunks = mutableListOf<List<String>>()
    val currentChunk = mutableListOf<String>()
    var currentLength = 0
    val maxLength = 1800

    val totalLength = list.sumOf { it.length } + (list.size - 1)
    if (totalLength <= maxLength) {
        return listOf(list)
    }

    for (item in list) {
        // +1 for the newline character that will be added when joining
        val itemLength = item.length + (if (currentChunk.isEmpty()) 0 else 1)

        if (currentLength + itemLength > maxLength && currentChunk.isNotEmpty()) {
            chunks.add(currentChunk.toList())
            currentChunk.clear()
            currentLength = 0
        }

        currentChunk.add(item)
        currentLength += itemLength
    }

    if (currentChunk.isNotEmpty()) {
        chunks.add(currentChunk.toList())
    }

    // Second pass: balance the last two chunks if needed
    if (chunks.size > 1) {
        val lastChunk = chunks.removeAt(chunks.size - 1)
        val secondLastChunk = chunks.removeAt(chunks.size - 1)

        if (lastChunk.size < secondLastChunk.size / 3) { // Arbitrary threshold
            val combined = secondLastChunk + lastChunk
            val halfSize = combined.size / 2

            var firstHalfLength = 0
            var splitIndex = 0
            for (i in combined.indices) {
                val itemLength = combined[i].length + (if (i > 0) 1 else 0)
                if (firstHalfLength + itemLength > maxLength || i >= halfSize) {
                    splitIndex = i
                    break
                }
                firstHalfLength += itemLength
            }
            chunks.add(combined.subList(0, splitIndex))
            chunks.add(combined.subList(splitIndex, combined.size))
        } else {
            chunks.add(secondLastChunk)
            chunks.add(lastChunk)
        }
    }

    return chunks
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

