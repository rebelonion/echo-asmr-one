package dev.brahmkshatriya.echo.extension.helpers

import dev.brahmkshatriya.echo.common.helpers.PagedData
import dev.brahmkshatriya.echo.common.models.Album
import dev.brahmkshatriya.echo.common.models.Artist
import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import dev.brahmkshatriya.echo.common.models.Shelf
import dev.brahmkshatriya.echo.common.models.Streamable
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.extension.MediaTreeItem
import dev.brahmkshatriya.echo.extension.Tag
import dev.brahmkshatriya.echo.extension.Work
import dev.brahmkshatriya.echo.extension.WorksResponse

fun MediaTreeItem.Audio.toMediaItem(album: Album, folderTitle: String): EchoMediaItem {
    return EchoMediaItem.TrackItem(
        this.toTrack(album, folderTitle)
    )
}

fun MediaTreeItem.Audio.toTrack(album: Album, folderTitle: String): Track {
    return Track(
        id = hash,
        title = title,
        album = album,
        artists = album.artists,
        cover = album.cover,
        duration = (duration * 1000).toLong(),
        isExplicit = album.isExplicit,
        releaseDate = album.releaseDate,
        streamables = listOf(
            Streamable.server(
                id = mediaStreamUrl,
                quality = 1,
                title = title,
            )
        ),
        extras = mapOf(
            "folderTitle" to folderTitle,
            "untranslatedTitle" to untranslatedTitle,
            "id" to album.id
        )
    )
}

fun MediaTreeItem.Folder.toCategory(album: Album): Shelf.Category {
    return Shelf.Category(
        title = title,
        items = PagedData.Single {
            children.mapNotNull { it.toShelf(album) }
        }
    )
}

fun MediaTreeItem.toShelf(album: Album): Shelf? {
    return when (this) {
        is MediaTreeItem.Folder -> this.toCategory(album)
        is MediaTreeItem.Audio -> this.toMediaItem(album, this.title).toShelf()
        else -> null
    }
}

fun Work.toAlbum(): Album {
    return Album(
        id = id.toString(),
        title = title,
        tracks = 0,
        cover = mainCoverUrl.buildImageHolder(),
        artists = vas.map { Artist(it.id, it.name) },
        releaseDate = release.toDate(),
        description = this.createDescription(),
        isExplicit = nsfw,
        subtitle = name
    )
}

fun WorksResponse.filterToSubtitled(filter: Boolean): WorksResponse {
    if (!filter) return this
    return WorksResponse(
        works = works.filter { it.hasSubtitle },
        pagination = pagination
    )
}