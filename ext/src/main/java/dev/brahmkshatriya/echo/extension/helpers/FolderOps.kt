package dev.brahmkshatriya.echo.extension.helpers

import dev.brahmkshatriya.echo.extension.MediaTreeItem
import dev.brahmkshatriya.echo.extension.Work

fun MediaTreeItem.Folder.getAllTitles(): List<String> {
    val result = mutableListOf<String>()
    for (item in children) {
        when (item) {
            is MediaTreeItem.Audio -> result.add(item.title)
            is MediaTreeItem.Folder -> {
                result.add(item.title)
                result.addAll(item.getAllTitles())
            }
            else -> {}
        }
    }
    return result
}

fun MediaTreeItem.Folder.removeEmptyFolders(): MediaTreeItem.Folder {
    /*val processedChildren = children.map {
        if (it is MediaTreeItem.Folder) {
            it.removeEmptyFolders()
        } else {
            it
        }
    }

    val hasAudioFiles = processedChildren.any { it is MediaTreeItem.Audio }
    val newChildren = processedChildren.filter {
        when (it) {
            is MediaTreeItem.Folder -> it.children.isNotEmpty()
            is MediaTreeItem.Audio -> true
            is MediaTreeItem.Text -> hasAudioFiles
            else -> false
        }
    }
    this.children = newChildren*/
    return this
}

fun MediaTreeItem.Folder.findMainAudioFolder(): String? {
    // First: look for all folders titled "mp3" (case insensitive)
    // Return the path to the one with the most audio files
    // If none, return the path to the folder with the most audio files
    val mp3FolderPaths = findAllFoldersWithTitle("mp3")
    if (mp3FolderPaths.isNotEmpty()) {
        return mp3FolderPaths.maxByOrNull { getFolder(it).getAllAudioFiles(false).size }
    }
    return findFolderWithMostAudioFiles()
}

fun MediaTreeItem.Folder.findAllFoldersWithTitle(title: String, currentPath: String = ""): List<String> {
    val result = mutableListOf<String>()
    for (item in children) {
        val itemPath = if (currentPath.isEmpty()) item.title else "$currentPath/${item.title}"

        if (item is MediaTreeItem.Folder && item.title.equals(title, ignoreCase = true)) {
            result.add(itemPath)
        } else if (item is MediaTreeItem.Folder) {
            result.addAll(item.findAllFoldersWithTitle(title, itemPath))
        }
    }
    return result
}

fun MediaTreeItem.Folder.findFolderWithMostAudioFiles(currentPath: String = ""): String? {
    var maxCount = 0
    var maxFolderPath: String? = null

    for (item in children) {
        if (item is MediaTreeItem.Folder) {
            val itemPath = if (currentPath.isEmpty()) item.title else "$currentPath/${item.title}"

            val count = item.getAllAudioFiles(false).size
            if (count > maxCount) {
                maxCount = count
                maxFolderPath = itemPath
            }

            // Recursively check subfolders
            val subfolderPath = item.findFolderWithMostAudioFiles(itemPath)
            if (subfolderPath != null) {
                val subfolderCount = getFolder(subfolderPath).getAllAudioFiles(false).size
                if (subfolderCount > maxCount) {
                    maxCount = subfolderCount
                    maxFolderPath = subfolderPath
                }
            }
        }
    }

    return maxFolderPath
}

fun MediaTreeItem.Folder.getFolder(path: String): MediaTreeItem.Folder {
    val pathParts = path.split("/")
    var currentFolder: MediaTreeItem.Folder = this

    for (element in pathParts) {
        val nextFolder = currentFolder.children.find {
            it is MediaTreeItem.Folder && it.title == element
        } as? MediaTreeItem.Folder

        if (nextFolder != null) {
            currentFolder = nextFolder
        } else {
            // Path not found, return current folder
            break
        }
    }
    return currentFolder
}

fun MediaTreeItem.Folder.deepCopy(): MediaTreeItem.Folder {
    val newChildren = children.map {
        when (it) {
            is MediaTreeItem.Folder -> it.deepCopy()
            is MediaTreeItem.Audio -> it.copy()
            is MediaTreeItem.Text -> it.copy()
            else -> it

        }
    }
    return MediaTreeItem.Folder(
        type = type,
        title = title,
        children = newChildren
    )
}

fun MediaTreeItem.Folder.deleteFolderAudio(path: String): MediaTreeItem.Folder {
    val pathParts = path.split("/")
    var currentFolder: MediaTreeItem.Folder = this

    for (element in pathParts) {
        val nextFolder = currentFolder.children.find {
            it is MediaTreeItem.Folder && it.title == element
        } as? MediaTreeItem.Folder

        if (nextFolder != null) {
            currentFolder = nextFolder
        } else {
            // Path not found, return current folder
            break
        }
    }
    currentFolder.children = currentFolder.children.filterNot { it is MediaTreeItem.Audio }
    return this.removeEmptyFolders()
}

fun MediaTreeItem.Folder.findFolderWithAudio(hash: String): MediaTreeItem.Folder? {
    for (item in children) {
        if (item is MediaTreeItem.Audio && item.hash == hash) {
            return this
        } else if (item is MediaTreeItem.Folder) {
            val foundInSubfolder = item.findFolderWithAudio(hash)
            if (foundInSubfolder != null) {
                return foundInSubfolder
            }
        }
    }
    return null
}

fun MediaTreeItem.Folder.findFile(title: String): MediaTreeItem? {
    for (item in children) {
        if (item.title == title || item.untranslatedTitle == title) {
            return item
        } else if (item is MediaTreeItem.Folder) {
            val foundInSubfolder = item.findFile(title)
            if (foundInSubfolder != null) {
                return foundInSubfolder
            }
        }
    }
    return null
}

fun MediaTreeItem.Folder.getAllAudioFiles(
    recursively: Boolean
): List<MediaTreeItem.Audio> {
    val result = mutableListOf<MediaTreeItem.Audio>()
    for (item in children) {
        when (item) {
            is MediaTreeItem.Audio -> result.add(item)
            is MediaTreeItem.Folder -> {
                if (recursively) {
                    result.addAll(item.children.filterIsInstance<MediaTreeItem.Audio>())
                    item.children.filterIsInstance<MediaTreeItem.Folder>().forEach {
                        result.addAll(it.getAllAudioFiles(true))
                    }
                }
            }

            else -> {}
        }
    }
    return result
}

fun Work.createDescription(): String {
    //return "Downloads: $dlCount, Price: $price, Reviews: $reviewCount, Rating: $rateAverage2dp\n" +
    return "Tags: ${tags.joinToString { it.i18n.enUs.name ?: it.name }}"
}