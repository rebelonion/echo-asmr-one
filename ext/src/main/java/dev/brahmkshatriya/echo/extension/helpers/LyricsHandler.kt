package dev.brahmkshatriya.echo.extension.helpers

import dev.brahmkshatriya.echo.common.models.Lyrics

suspend fun String.toLyrics(): Lyrics.Timed {
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
            // Empty line, process the current entry if we have timestamp and text
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
    return Lyrics.Timed(list = items).translate()
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