package dev.brahmkshatriya.echo.extension.asmrone.helpers

import dev.brahmkshatriya.echo.common.helpers.Page
import dev.brahmkshatriya.echo.common.helpers.PagedData
import dev.brahmkshatriya.echo.common.models.Album
import dev.brahmkshatriya.echo.common.models.Artist
import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import dev.brahmkshatriya.echo.common.models.EchoMediaItem.Companion.toMediaItem
import dev.brahmkshatriya.echo.common.models.Playlist
import dev.brahmkshatriya.echo.common.models.Shelf
import dev.brahmkshatriya.echo.common.models.Streamable
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.extension.asmrone.AsmrApi
import dev.brahmkshatriya.echo.extension.asmrone.AsmrPlaylist
import dev.brahmkshatriya.echo.extension.asmrone.MediaTreeItem
import dev.brahmkshatriya.echo.extension.asmrone.Tag
import dev.brahmkshatriya.echo.extension.asmrone.Work
import dev.brahmkshatriya.echo.extension.asmrone.WorksResponse

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
        isLiked = (album.subtitle?.findAll('★')?.size ?: 0) > 3,
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
    var sustitleString = ""
    userRating?.let {
        for (i in 1..userRating) sustitleString += "★"
        for (i in userRating until 5) sustitleString += "☆"
    }

    return Album(
        id = id.toString(),
        title = title,
        subtitle = sustitleString.ifEmpty { name },
        tracks = 0,
        cover = mainCoverUrl.buildImageHolder(),
        artists = vas.map { Artist(it.id, it.name) },
        releaseDate = release.toDate(),
        description = this.createDescription(),
        isExplicit = nsfw,
    )
}

//THIS IS A FAKE TRACK DON'T USE IT AS A REAL TRACK
fun Work.toTrack(): Track {
    return Track(
        id = id.toString(),
        title = "Open the album below to access the track\n\n",
        album = toAlbum(),
        artists = vas.map { Artist(it.id, it.name) },
        cover = mainCoverUrl.buildImageHolder(),
        duration = 0,
        isExplicit = nsfw,
        releaseDate = release.toDate(),
        streamables = Streamable.server(
                id = "OPEN_ALBUM",
                quality = 1,
                title = null,
            ).listOf()

    )
}

fun WorksResponse.filterToSubtitled(filter: Boolean): WorksResponse {
    if (!filter) return this
    return WorksResponse(
        works = works.filter { it.hasSubtitle },
        pagination = pagination
    )
}

fun Tag.toShelf(asmrApi: AsmrApi): Shelf.Category {
    return Shelf.Category(
        title = i18n.enUs.name ?: name,
        items = PagedData.Continuous { continuation ->
            val contInt = continuation?.toIntOrNull() ?: 0
            val newResponse =
                asmrApi.searchWorks(contInt + 1, keyword = "\$tag:${i18n.enUs.name ?: name}\$")
            val mediaItems: List<EchoMediaItem.Lists.AlbumItem> =
                newResponse.works.map { it.toAlbum().toMediaItem() }
            val newContinuation =
                if (newResponse.pagination.currentPage * newResponse.pagination.pageSize < newResponse.pagination.totalCount) {
                    (contInt + 1).toString()
                } else null
            Page(
                data = mediaItems.map { it.toShelf() },
                continuation = newContinuation
            )
        }
    )
}


fun Work.createDescription(): String {
    return "Tags: ${tags.joinToString { it.i18n.enUs.name ?: it.name }}\n" +
            "Full Title: ${title}\n" +
            "Downloads: $dlCount, Price: $price, Reviews: $reviewCount, Rating: $rateAverage2dp\n"
}

fun AsmrPlaylist.toMediaItem(): EchoMediaItem.Lists.PlaylistItem {
    return EchoMediaItem.Lists.PlaylistItem(
        this.toPlaylist()
    )
}

fun AsmrPlaylist.toPlaylist(): Playlist {
    val parsedName = when (name) {
        "__SYS_PLAYLIST_MARKED" -> "Marked"
        "__SYS_PLAYLIST_LIKED" -> "Liked"
        else -> name
    }
    return Playlist(
        id = id,
        title = parsedName,
        cover = mainCoverUrl.buildImageHolder(),
        description = description,
        creationDate = createdAt.toDate(),
        isPrivate = privacy == 0,
        tracks = worksCount,
        isEditable = true
    )
}