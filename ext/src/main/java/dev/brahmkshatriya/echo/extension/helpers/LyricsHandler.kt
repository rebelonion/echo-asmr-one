package dev.brahmkshatriya.echo.extension.helpers

import dev.brahmkshatriya.echo.common.models.Lyrics
import me.bush.translator.Language

suspend fun String.toLyrics(language: Language): Lyrics.Timed {
    val isLrcFormat = this.trimStart().startsWith("[") &&
            this.contains(Regex("\\[\\d{2}:\\d{2}\\.\\d{2}]"))

    return if (isLrcFormat) {
        parseLrcFormat(language)
    } else {
        parseWebVttFormat(language)
    }
}

/**
 * Parse LRC format lyrics
 * Example: [00:04.46]欸 幹嘛?
 */
private suspend fun String.parseLrcFormat(language: Language): Lyrics.Timed {
    val lines = this.lines()
    val items = mutableListOf<Lyrics.Item>()

    // LRC files can have multiple timestamps on a single line
    for (line in lines) {
        val trimmedLine = line.trim()
        if (trimmedLine.isEmpty()) continue

        val timestampRegex = Regex("\\[(\\d{2}):(\\d{2})\\.(\\d{2})]")
        val matches = timestampRegex.findAll(trimmedLine)

        if (matches.count() > 0) {
            val textStartIndex = timestampRegex.findAll(trimmedLine)
                .last().range.last + 1

            val text = if (textStartIndex < trimmedLine.length) {
                trimmedLine.substring(textStartIndex)
            } else {
                ""  // Empty line or timestamp with no text
            }

            for (match in matches) {
                val minutes = match.groupValues[1].toLong()
                val seconds = match.groupValues[2].toLong()
                val centiseconds = match.groupValues[3].toLong()
                val startTime = (minutes * 60 + seconds) * 1000 + centiseconds * 10

                // For LRC files, we often need to calculate the end time based on the next timestamp
                val endTime = startTime + 5000

                // Only add non-empty text lines as items
                if (text.isNotEmpty()) {
                    items.add(
                        Lyrics.Item(
                            text = text,
                            startTime = startTime,
                            endTime = endTime  // Will be adjusted later
                        )
                    )
                }
            }
        }
    }

    // each item ends when the next one starts
    for (i in 0 until items.size - 1) {
        items[i] = items[i].copy(endTime = items[i + 1].startTime)
    }

    return Lyrics.Timed(list = items).translate(language)
}

/**
 * Parse WebVTT format lyrics
 */
private suspend fun String.parseWebVttFormat(language: Language): Lyrics.Timed {
    val lines = this.lines()
    val contentLines = if (lines.isNotEmpty() && lines[0].trim() == "WEBVTT") {
        lines.drop(1)
    } else {
        lines
    }

    val items = mutableListOf<Lyrics.Item>()
    var currentTimestamp: Pair<Long, Long>? = null
    val currentText = StringBuilder()

    for (line in contentLines) {
        val trimmedLine = line.trim()

        if (trimmedLine.isEmpty()) {
            if (currentTimestamp != null && currentText.isNotEmpty()) {
                items.add(
                    Lyrics.Item(
                        text = currentText.toString().trim(),
                        startTime = currentTimestamp.first,
                        endTime = currentTimestamp.second
                    )
                )
                currentTimestamp = null
                currentText.clear()
            }
        } else if (trimmedLine.contains("-->")) {
            currentTimestamp = parseTimestamp(trimmedLine)
        } else if (!isIndexLine(trimmedLine)) {
            // This is likely text content (not an index number or timestamp)
            if (currentTimestamp != null) {
                if (currentText.isNotEmpty()) {
                    currentText.append("\n")
                }
                currentText.append(trimmedLine)
            }
        }
    }
    if (currentTimestamp != null && currentText.isNotEmpty()) {
        items.add(
            Lyrics.Item(
                text = currentText.toString().trim(),
                startTime = currentTimestamp.first,
                endTime = currentTimestamp.second
            )
        )
    }
    return Lyrics.Timed(list = items).translate(language)
}

/**
 * Helper function to parse timestamp line into start and end times in milliseconds
 */
private fun parseTimestamp(line: String): Pair<Long, Long> {
    val parts = line.split("-->")
    if (parts.size != 2) {
        throw IllegalArgumentException("Invalid timestamp format: $line")
    }

    val startTime = parseTimeToMillis(parts[0].trim())
    val endTime = parseTimeToMillis(parts[1].trim())

    return Pair(startTime, endTime)
}

/**
 * Helper function to convert timestamp string to milliseconds
 * Handles formats like:
 * - HH:MM:SS.mmm
 * - MM:SS.mmm
 */
private fun parseTimeToMillis(timeStr: String): Long {
    val timeParts = timeStr.split(":")
    if (timeParts.size < 2 || timeParts.size > 3) {
        throw IllegalArgumentException("Invalid time format: $timeStr")
    }

    val hours = if (timeParts.size == 3) timeParts[0].toLong() else 0L
    val minutes = if (timeParts.size == 3) timeParts[1].toLong() else timeParts[0].toLong()

    val lastPart = if (timeParts.size == 3) timeParts[2] else timeParts[1]
    val secondsParts = lastPart.split(".")

    val seconds = secondsParts[0].toLong()
    val millis = if (secondsParts.size > 1) {
        val millisStr = secondsParts[1].padEnd(3, '0').take(3)
        millisStr.toLong()
    } else {
        0L
    }

    return (hours * 3600 + minutes * 60 + seconds) * 1000 + millis
}

/**
 * Helper function to check if a line is likely an index number
 */
private fun isIndexLine(line: String): Boolean {
    return line.trim().matches(Regex("^\\d+$"))
}