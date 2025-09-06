package dev.brahmkshatriya.echo.extension.asmrone.helpers

import dev.brahmkshatriya.echo.common.helpers.Page
import dev.brahmkshatriya.echo.common.helpers.PagedData
import dev.brahmkshatriya.echo.common.models.Album
import dev.brahmkshatriya.echo.common.models.Artist
import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import dev.brahmkshatriya.echo.common.models.Feed.Companion.toFeed
import dev.brahmkshatriya.echo.common.models.Playlist
import dev.brahmkshatriya.echo.common.models.Shelf
import dev.brahmkshatriya.echo.common.models.Streamable
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.extension.AsmrOne.PendingAction
import dev.brahmkshatriya.echo.extension.asmrone.AsmrApi
import dev.brahmkshatriya.echo.extension.asmrone.AsmrPlaylist
import dev.brahmkshatriya.echo.extension.asmrone.MediaTreeItem
import dev.brahmkshatriya.echo.extension.asmrone.Tag
import dev.brahmkshatriya.echo.extension.asmrone.Work
import dev.brahmkshatriya.echo.extension.asmrone.WorksResponse

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
        isLikeable = true,
        isRadioSupported = true,
        isShareable = true,
        isPlayable = Track.Playable.Yes,
        isSaveable = true,
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
            "id" to album.id,
            "isLiked" to (album.extras["isLiked"] ?: "false")
        )
    )
}

fun MediaTreeItem.Folder.toCategory(album: Album): Shelf.Category {
    return Shelf.Category(
        title = title, feed = PagedData.Single {
            children.mapNotNull { it.toShelf(album) }
        }.toFeed(), id = untranslatedTitle
    )
}

fun MediaTreeItem.toShelf(album: Album): Shelf? {
    return when (this) {
        is MediaTreeItem.Folder -> this.toCategory(album)
        is MediaTreeItem.Audio -> this.toTrack(album, this.title).toShelf()
        else -> null
    }
}

fun Work.toAlbum(pendingLikes: MutableMap<Int, PendingAction>): Album {
    pendingLikes.remove(id)
    var subtitleString = ""
    userRating?.let {
        subtitleString += "★".repeat(userRating)
        subtitleString += "☆".repeat(5 - userRating)
    }

    return Album(
        id = id.toString(),
        title = title,
        subtitle = subtitleString.ifEmpty { name },
        trackCount = 0,
        isLikeable = true,
        isRadioSupported = true,
        isShareable = true,
        isSaveable = true,
        cover = mainCoverUrl.buildImageHolder(),
        artists = vas.map { Artist(it.id, it.name) },
        releaseDate = release.toDate(),
        description = this.createDescription(),
        isExplicit = nsfw,
        extras = mapOf(
            "isLiked" to ((userRating ?: 0) > 3).toString(),
        )
    )
}

//THIS IS A FAKE TRACK DON'T USE IT AS A REAL TRACK
fun Work.toFakeTrack(): Track {
    return Track(
        id = id.toString(),
        title = title,
        album = toAlbum(mutableMapOf()),
        artists = vas.map { Artist(it.id, it.name) },
        cover = mainCoverUrl.buildImageHolder(),
        duration = 0,
        isExplicit = nsfw,
        releaseDate = release.toDate(),
        isPlayable = Track.Playable.No(reason = "Album Required"),
        isRadioSupported = false,
        isShareable = false,
        isLikeable = false,
        isSaveable = false,
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
        works = works.filter { it.hasSubtitle }, pagination = pagination
    )
}

fun Tag.toShelf(asmrApi: AsmrApi, pendingLikes: MutableMap<Int, PendingAction>): Shelf.Category {
    return Shelf.Category(
        id = id.toString(),
        title = i18n.enUs.name ?: name,
        feed = PagedData.Continuous<Shelf> { continuation ->
            val contInt = continuation?.toIntOrNull() ?: 0
            val newResponse =
                asmrApi.searchWorks(contInt + 1, keyword = "\$tag:${i18n.enUs.name ?: name}$")
            val mediaItems: List<EchoMediaItem> = newResponse.works.map { it.toAlbum(pendingLikes) }
            val newContinuation =
                if (newResponse.pagination.currentPage * newResponse.pagination.pageSize < newResponse.pagination.totalCount) {
                    (contInt + 1).toString()
                } else null
            Page(
                data = mediaItems.map { it.toShelf() }, continuation = newContinuation
            )
        }.toFeed()
    )
}


fun Work.createDescription(): String {
    return "Tags: ${tags.joinToString { it.i18n.enUs.name ?: it.name }}\n" + "Full Title: ${title}\n" + "Downloads: $dlCount, Price: $price, Reviews: $reviewCount, Rating: $rateAverage2dp\n"
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
        trackCount = worksCount.toLong(),
        isEditable = true
    )
}