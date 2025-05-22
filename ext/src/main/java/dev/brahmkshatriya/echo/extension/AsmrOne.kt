package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.clients.AlbumClient
import dev.brahmkshatriya.echo.common.clients.ArtistClient
import dev.brahmkshatriya.echo.common.clients.ExtensionClient
import dev.brahmkshatriya.echo.common.clients.HomeFeedClient
import dev.brahmkshatriya.echo.common.clients.LibraryFeedClient
import dev.brahmkshatriya.echo.common.clients.LoginClient
import dev.brahmkshatriya.echo.common.clients.LyricsClient
import dev.brahmkshatriya.echo.common.clients.PlaylistClient
import dev.brahmkshatriya.echo.common.clients.PlaylistEditClient
import dev.brahmkshatriya.echo.common.clients.PlaylistEditPrivacyClient
import dev.brahmkshatriya.echo.common.clients.RadioClient
import dev.brahmkshatriya.echo.common.clients.SearchFeedClient
import dev.brahmkshatriya.echo.common.clients.ShareClient
import dev.brahmkshatriya.echo.common.clients.TrackClient
import dev.brahmkshatriya.echo.common.clients.TrackLikeClient
import dev.brahmkshatriya.echo.common.helpers.ClientException
import dev.brahmkshatriya.echo.common.helpers.Page
import dev.brahmkshatriya.echo.common.helpers.PagedData
import dev.brahmkshatriya.echo.common.models.Album
import dev.brahmkshatriya.echo.common.models.Artist
import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import dev.brahmkshatriya.echo.common.models.EchoMediaItem.Companion.toMediaItem
import dev.brahmkshatriya.echo.common.models.Lyrics
import dev.brahmkshatriya.echo.common.models.Playlist
import dev.brahmkshatriya.echo.common.models.QuickSearchItem
import dev.brahmkshatriya.echo.common.models.Radio
import dev.brahmkshatriya.echo.common.models.Shelf
import dev.brahmkshatriya.echo.common.models.Streamable
import dev.brahmkshatriya.echo.common.models.Streamable.Media.Companion.toServerMedia
import dev.brahmkshatriya.echo.common.models.Tab
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.common.models.User
import dev.brahmkshatriya.echo.common.settings.Setting
import dev.brahmkshatriya.echo.common.settings.SettingList
import dev.brahmkshatriya.echo.common.settings.SettingMultipleChoice
import dev.brahmkshatriya.echo.common.settings.SettingSwitch
import dev.brahmkshatriya.echo.common.settings.Settings
import dev.brahmkshatriya.echo.extension.asmrone.AsmrApi
import dev.brahmkshatriya.echo.extension.asmrone.MediaTreeItem
import dev.brahmkshatriya.echo.extension.asmrone.Tag
import dev.brahmkshatriya.echo.extension.asmrone.WorksResponse
import dev.brahmkshatriya.echo.extension.asmrone.helpers.findFile
import dev.brahmkshatriya.echo.extension.asmrone.helpers.findFolderWithAudio
import dev.brahmkshatriya.echo.extension.asmrone.helpers.findMainAudioFolder
import dev.brahmkshatriya.echo.extension.asmrone.helpers.getAllAudioFiles
import dev.brahmkshatriya.echo.extension.asmrone.helpers.getFolder
import dev.brahmkshatriya.echo.extension.asmrone.helpers.getTranslationLanguage
import dev.brahmkshatriya.echo.extension.asmrone.helpers.listOf
import dev.brahmkshatriya.echo.extension.asmrone.helpers.toAlbum
import dev.brahmkshatriya.echo.extension.asmrone.helpers.toLyrics
import dev.brahmkshatriya.echo.extension.asmrone.helpers.toMediaItem
import dev.brahmkshatriya.echo.extension.asmrone.helpers.toPlaylist
import dev.brahmkshatriya.echo.extension.asmrone.helpers.toShelf
import dev.brahmkshatriya.echo.extension.asmrone.helpers.toTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import dev.rebelonion.translator.Language


class AsmrOne : ExtensionClient,
    HomeFeedClient, LibraryFeedClient, AlbumClient, TrackClient, RadioClient,
    LyricsClient, ArtistClient, ShareClient, SearchFeedClient, LoginClient.CustomInput,
    PlaylistClient, PlaylistEditClient, PlaylistEditPrivacyClient, TrackLikeClient {
    val asmrApi = AsmrApi()

    ////--------------------------------------------------------------------------------------------
    //// ExtensionClient
    override val settingItems: List<Setting> by lazy {
        listOf(
            SettingSwitch(
                title = "Only Show Works With Subtitles",
                key = "onlyShowSubtitled",
                defaultValue = false
            ),
            SettingSwitch(
                title = "Only Show SFW Works In Search",
                key = "onlyShowSfw",
                defaultValue = false
            ),
            SettingList(
                title = "Site Mirror",
                key = "siteMirror",
                entryTitles = listOf("asmr-100", "asmr-200", "asmr-300"),
                entryValues = listOf("asmr-100", "asmr-200", "asmr-300"),
                defaultEntryIndex = 1
            ),
            SettingList(
                title = "Translation Language",
                key = "translationLanguage",
                entryTitles = Language.entries.filter { it.code != "auto" }.sortedBy { it.name }
                    .map { it.name },
                entryValues = Language.entries.filter { it.code != "auto" }.sortedBy { it.name }
                    .map { it.code },
                defaultEntryIndex = Language.entries.find { it.code == "en" }
                    ?.let { Language.entries.indexOf(it) - 1 } // -1 to account for auto
                    ?: 0
            ),
            SettingMultipleChoice(
                title = "Show Tags On Home Page",
                key = "showTags",
                entryTitles = getTagNames,
                entryValues = getTagNames,
                defaultEntryIndices = emptySet()
            )
        )
    }

    private val getTagNames: List<String>
        get() = asmrApi.getTags().map { it.i18n.enUs.name ?: it.name }.sorted()

    private lateinit var settings: Settings
    override fun setSettings(settings: Settings) {
        asmrApi.initSettings(settings)
        this.settings = settings
    }

    ////--------------------------------------------------------------------------------------------
    //// HomeFeedClient
    override fun getHomeFeed(tab: Tab?) = PagedData.Single {
        val showTags = settings.getStringSet("showTags") ?: emptySet()
        val defaultShelfTitles = listOf("Popular", "Recommended", "All")
        val defaultShelves = withContext(Dispatchers.IO) {
            defaultShelfTitles.map { shelf ->
                async {
                    when (shelf) {
                        "Popular" -> getHomeShelf(
                            title = "Popular",
                            function = asmrApi::getPopularWorks
                        )

                        "Recommended" -> getHomeShelf(
                            title = "Recommended",
                            function = asmrApi::getRecommendedWorks
                        )

                        "All" -> getHomeShelf(
                            title = "All",
                            function = asmrApi::getWorks
                        )

                        else -> null
                    }
                }
            }
        }
        val tagsShelf = withContext(Dispatchers.IO) {
            async { getTagsShelf() }
        }
        val tagShelves = showTags.map { tag ->
            withContext(Dispatchers.IO) {
                async {
                    asmrApi.getTags().firstOrNull { it.i18n.enUs.name == tag || it.name == tag }
                        ?.let {
                            getTagFeed(it)
                        }
                }
            }
        }
        (defaultShelves.awaitAll() + tagShelves.awaitAll() + listOf(tagsShelf.await())).filterNotNull()
    }

    override suspend fun getHomeTabs() = listOf<Tab>()

    ////--------------------------------------------------------------------------------------------
    //// LibraryFeedClient
    override fun getLibraryFeed(tab: Tab?) = PagedData.Single {
        val shelves: List<String> = listOf("Playlists", "Favorites")
        withContext(Dispatchers.IO) {
            shelves.map { shelf ->
                async {
                    when (shelf) {
                        "Playlists" -> getPlaylists()

                        "Favorites" -> getFavorites()

                        else -> null
                    }
                }
            }.awaitAll()
                .filterNotNull()
        }
    }

    override suspend fun getLibraryTabs(): List<Tab> = emptyList()

    ////--------------------------------------------------------------------------------------------
    //// Helpers
    private suspend fun getHomeShelf(
        title: String,
        function: suspend (page: Int) -> WorksResponse,
    ): Shelf {
        val response = function(1)
        return Shelf.Lists.Items(
            title = title,
            list = response.works.map { it.toAlbum().toMediaItem() },
            more = if (response.pagination.currentPage * response.pagination.pageSize < response.pagination.totalCount) {
                PagedData.Continuous { continuation ->
                    val contInt = continuation?.toIntOrNull() ?: 0
                    val newResponse = function(contInt + 1)
                    val mediaItems = newResponse.works.map { it.toAlbum().toMediaItem() }
                    val newContinuation =
                        if (newResponse.pagination.currentPage * newResponse.pagination.pageSize < newResponse.pagination.totalCount) {
                            (contInt + 1).toString()
                        } else null
                    Page(
                        data = mediaItems,
                        continuation = newContinuation
                    )
                }
            } else null
        )
    }

    private fun getTagsShelf(): Shelf {
        val title = "Tags"
        val tags = asmrApi.getTags()
        return Shelf.Lists.Categories(
            title = title,
            list = tags.map { it.toShelf(asmrApi) },
            type = Shelf.Lists.Type.Grid
        )
    }

    private fun getPlaylists(): Shelf {
        val title = "Playlists"
        val playlists = asmrApi.getPlaylists()
        return Shelf.Lists.Items(
            title = title,
            list = playlists.playlists.map { it.toMediaItem() },
            type = Shelf.Lists.Type.Linear,
            more = if (playlists.pagination.currentPage * playlists.pagination.pageSize < playlists.pagination.totalCount) {
                PagedData.Continuous { continuation ->
                    val contInt = continuation?.toIntOrNull() ?: 0
                    val newResponse = asmrApi.getPlaylists(contInt + 1)
                    val mediaItems = newResponse.playlists.map { it.toMediaItem() }
                    val newContinuation =
                        if (newResponse.pagination.currentPage * newResponse.pagination.pageSize < newResponse.pagination.totalCount) {
                            (contInt + 1).toString()
                        } else null
                    Page(
                        data = mediaItems,
                        continuation = newContinuation
                    )
                }
            } else null
        )
    }

    private suspend fun getFavorites(): Shelf {
        val title = "Favorites"
        val favorites: WorksResponse = asmrApi.getFavorites()
        return Shelf.Lists.Items(
            title = title,
            list = favorites.works.map { it.toAlbum().toMediaItem() },
            more = if (favorites.pagination.currentPage * favorites.pagination.pageSize < favorites.pagination.totalCount) {
                PagedData.Continuous { continuation ->
                    val contInt = continuation?.toIntOrNull() ?: 0
                    val newResponse = asmrApi.getFavorites(contInt + 1)
                    val mediaItems = newResponse.works.map { it.toAlbum().toMediaItem() }
                    val newContinuation =
                        if (newResponse.pagination.currentPage * newResponse.pagination.pageSize < newResponse.pagination.totalCount) {
                            (contInt + 1).toString()
                        } else null
                    Page(
                        data = mediaItems,
                        continuation = newContinuation
                    )
                }
            } else null
        )
    }

    private suspend fun getTagFeed(tag: Tag): Shelf {
        val title = tag.i18n.enUs.name ?: tag.name
        val response = asmrApi.searchWorks(1, keyword = "\$tag:${tag.i18n.enUs.name ?: tag.name}\$")
        return Shelf.Lists.Items(
            title = title,
            list = response.works.map { it.toAlbum().toMediaItem() },
            more = if (response.pagination.currentPage * response.pagination.pageSize < response.pagination.totalCount) {
                PagedData.Continuous { continuation ->
                    val contInt = continuation?.toIntOrNull() ?: 0
                    val newResponse = asmrApi.searchWorks(
                        contInt + 1,
                        keyword = "\$tag:${tag.i18n.enUs.name ?: tag.name}\$"
                    )
                    val mediaItems = newResponse.works.map { it.toAlbum().toMediaItem() }
                    val newContinuation =
                        if (newResponse.pagination.currentPage * newResponse.pagination.pageSize < newResponse.pagination.totalCount) {
                            (contInt + 1).toString()
                        } else null
                    Page(
                        data = mediaItems,
                        continuation = newContinuation
                    )
                }
            } else null
        )
    }

    ////--------------------------------------------------------------------------------------------
    //// AlbumClient
    override fun getShelves(album: Album): PagedData<Shelf> = PagedData.Single {
        withContext(Dispatchers.IO) {
            val shelves: MutableList<Shelf> = mutableListOf()
            val tree = asmrApi.getWorkMediaTree(album.id)
            /*val mainAudioFolderPath = tree.findMainAudioFolder()
            mainAudioFolderPath?.let { path ->
                tree.deleteFolderAudio(path) // do I want to do this?
            }*/
            val shelfDeferred = async {
                tree.children.mapNotNull { it.toShelf(album) }
            }
            val relatedWorksDeferred = async {
                val relatedWorks = asmrApi.getRelatedWorks(album.id).works
                Shelf.Lists.Items(
                    title = "Related",
                    list = relatedWorks.map { it.toAlbum().toMediaItem() }
                )
            }
            shelves.addAll(shelfDeferred.await())
            shelves.add(relatedWorksDeferred.await())
            shelves
        }
    }


    override suspend fun loadAlbum(album: Album): Album {
        if (album.description != null) {
            return album
        }
        return asmrApi.getWork(album.id).toAlbum()
    }

    ////--------------------------------------------------------------------------------------------
    //// TrackClient
    override fun loadTracks(album: Album): PagedData<Track> = PagedData.Single {
        val tree = asmrApi.getWorkMediaTree(album.id)
        val mainAudioFolderPath = tree.findMainAudioFolder()
        mainAudioFolderPath?.let { path ->
            val folder = tree.getFolder(path)
            val tracks: List<Track> = folder.getAllAudioFiles(false)
                .map { it.toTrack(album = album, folderTitle = folder.title) }
                .sortedBy { it.title }
            return@Single tracks
        }
        emptyList()
    }

    override fun getShelves(track: Track): PagedData<Shelf> = PagedData.empty()

    override suspend fun loadStreamableMedia(
        streamable: Streamable,
        isDownload: Boolean
    ): Streamable.Media {
        if (streamable.id == "OPEN_ALBUM") {
            throw Exception("Open the album below to access the track")
        }
        return streamable.id.toServerMedia()
    }

    override suspend fun loadTrack(track: Track): Track = track

    ////--------------------------------------------------------------------------------------------
    //// RadioClient
    override fun loadTracks(radio: Radio): PagedData<Track> = PagedData.Single {
        if (radio.id.startsWith("TRACK_")) {
            if (radio.id == "TRACK_NO_ID") {
                return@Single emptyList()
            }
            val trackId = radio.id.removePrefix("TRACK_")
            val folderTitle = radio.title
            val hash = radio.extras.getOrElse("hash") {
                throw Exception("Expected hash in extras")
            }
            val tree = asmrApi.getWorkMediaTree(trackId)
            val album = asmrApi.getWork(trackId).toAlbum()
            val folder = tree.findFolderWithAudio(hash)
                ?: throw Exception("Could not find folder with audio")
            folder.getAllAudioFiles(false)
                .map { it.toTrack(album = album, folderTitle = folderTitle) }
                .sortedBy { it.title }
                .dropWhile { it.id != hash }
                .drop(1)
        } else if (radio.id.startsWith("ALBUM_")) {
            val albumId = radio.id.removePrefix("ALBUM_")
            val tree = asmrApi.getWorkMediaTree(albumId)
            val album = asmrApi.getWork(albumId).toAlbum()
            tree.getAllAudioFiles(true)
                .map { it.toTrack(album = album, folderTitle = album.title) }
                .sortedBy { it.title }
        } else if (radio.id.startsWith("ARTIST_")) {
            val works = asmrApi.searchWorks(
                page = 1,
                keyword = "\$va:${radio.title}\$",
                order = AsmrApi.Companion.SortOrder.RANDOM
            ).works
            val tree = asmrApi.getWorkMediaTree(works.first().id.toString())
            val album = works.first().toAlbum()
            tree.findMainAudioFolder()?.let { folderPath ->
                tree.getFolder(folderPath).getAllAudioFiles(false)
                    .map { it.toTrack(album = album, folderTitle = album.title) }
                    .sortedBy { it.title }
            } ?: tree.getAllAudioFiles(true)
                .map { it.toTrack(album = album, folderTitle = album.title) }
                .sortedBy { it.title }
        } else if (radio.id.startsWith("USER_")) {
            val works = asmrApi.getRecommendedWorks().works
            val work = works.random()
            val tree = asmrApi.getWorkMediaTree(work.id.toString())
            val album = work.toAlbum()
            tree.findMainAudioFolder()?.let { folderPath ->
                tree.getFolder(folderPath).getAllAudioFiles(false)
                    .map { it.toTrack(album = album, folderTitle = album.title) }
                    .sortedBy { it.title }
            } ?: tree.getAllAudioFiles(true)
                .map { it.toTrack(album = album, folderTitle = album.title) }
                .sortedBy { it.title }
        } else if (radio.id.startsWith("PLAYLIST_")) {
            val playlistId = radio.id.removePrefix("PLAYLIST_")
            val works = asmrApi.getPlaylistWorks(playlistId, 1).works
            println("asmr-logging: $works")
            val work = works.random()
            val tree = asmrApi.getWorkMediaTree(work.id.toString())
            val album = work.toAlbum()
            tree.findMainAudioFolder()?.let { folderPath ->
                tree.getFolder(folderPath).getAllAudioFiles(false)
                    .map { it.toTrack(album = album, folderTitle = album.title) }
                    .sortedBy { it.title }
            } ?: tree.getAllAudioFiles(true)
                .map { it.toTrack(album = album, folderTitle = album.title) }
                .sortedBy { it.title }
        } else {
            emptyList()
        }
    }

    override suspend fun radio(track: Track, context: EchoMediaItem?): Radio {
        if (track.title == "Open the album below to access the track\n\n") {
            return Radio(
                id = "TRACK_NO_ID",
                title = "Open the album below to access the track",
            )
        }
        val folderTitle = track.extras.getOrElse("folderTitle") {
            throw Exception("Expected folderTitle in extras")
        }
        return Radio(
            id = "TRACK_${track.album!!.id}",
            title = folderTitle,
            cover = track.cover,
            extras = mapOf("hash" to track.id)
        )
    }

    override suspend fun radio(album: Album) = Radio(
        id = "ALBUM_${album.id}",
        title = album.title,
        cover = album.cover
    )


    override suspend fun radio(artist: Artist) = Radio(
        id = "ARTIST_${artist.id}",
        title = artist.name,
        cover = artist.cover
    )


    override suspend fun radio(user: User) = Radio(
        id = "USER_${user.id}",
        title = user.name,
        cover = user.cover
    )


    override suspend fun radio(playlist: Playlist) = Radio(
        id = "PLAYLIST_${playlist.id}",
        title = playlist.title,
        cover = playlist.cover
    )

    ////--------------------------------------------------------------------------------------------
    //// LyricsClient
    override suspend fun loadLyrics(lyrics: Lyrics): Lyrics {
        val workId = lyrics.extras.getOrElse("workId") {
            throw Exception("Expected workId in extras")
        }
        val untranslatedTitle = lyrics.id
        val timedLyrics = getLyrics(workId, untranslatedTitle)
        return lyrics.copy(lyrics = timedLyrics)
    }

    override fun searchTrackLyrics(clientId: String, track: Track): PagedData<Lyrics> =
        PagedData.Single {
            val album = track.album ?: return@Single emptyList()
            val untranslatedTitle = track.extras["untranslatedTitle"] ?: track.title
            val lyrics = getLyrics(album.id, untranslatedTitle) ?: return@Single emptyList()
            if (lyrics.list.isEmpty()) {
                emptyList()
            } else {
                listOf(
                    Lyrics(
                        id = untranslatedTitle,
                        title = track.title,
                        lyrics = lyrics,
                        extras = mapOf("workId" to album.id)
                    )
                )
            }
        }

    private suspend fun getLyrics(workId: String, untranslatedTitle: String): Lyrics.Timed? {
        val tree = asmrApi.getWorkMediaTree(workId)
        val file = tree.findFile("$untranslatedTitle.vtt") as? MediaTreeItem.Text
            ?: tree.findFile(
                "${
                    untranslatedTitle.removeSuffix(".mp3").removeSuffix(".wav")
                }.lrc"
            ) as? MediaTreeItem.Text ?: return null
        return asmrApi.getSubTitleFile(file.mediaDownloadUrl)
            .toLyrics(settings.getTranslationLanguage())
    }

    ////--------------------------------------------------------------------------------------------
    //// ArtistClient
    override fun getShelves(artist: Artist): PagedData<Shelf> =
        PagedData.Continuous { continuation ->
            val contInt = continuation?.toIntOrNull() ?: 0
            val newResponse = asmrApi.searchWorks(contInt + 1, keyword = "\$va:${artist.name}\$")
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

    override suspend fun loadArtist(artist: Artist): Artist = artist

    ////--------------------------------------------------------------------------------------------
    //// ShareClient
    override suspend fun onShare(item: EchoMediaItem): String {
        val id = item.extras["id"] ?: return "https://asmr.one"
        return "https://asmr.one/work/RJ${id}"
    }

    ////--------------------------------------------------------------------------------------------
    //// SearchFeedClient
    override suspend fun deleteQuickSearch(item: QuickSearchItem) {}

    override suspend fun quickSearch(query: String): List<QuickSearchItem> {
        val tags = asmrApi.getTags()
        val usedTags =
            tags.filter { query.contains((it.i18n.enUs.name ?: it.name), ignoreCase = true) }
        //combine used tags into a single string in the format $tag:name$
        val combinedUsedTags: String =
            usedTags.joinToString(separator = " ") { "\$tag:${it.i18n.enUs.name ?: it.name}\$" }
        //remove used tags that are in $tag:name$ format
        var queryWithoutTags = query.replace(Regex("\\$.{0}tag:.*?\\$"), "")
        usedTags.forEach {
            queryWithoutTags =
                queryWithoutTags.replace((it.i18n.enUs.name ?: it.name), "", ignoreCase = true)
        }
        queryWithoutTags = queryWithoutTags.trim()
        //look for new tags in the query
        val lastWord = queryWithoutTags.split(" ").last()
        val newTags =
            tags.filter { (it.i18n.enUs.name ?: it.name).contains(lastWord, ignoreCase = true) }
        if (newTags.isNotEmpty()) {
            queryWithoutTags = queryWithoutTags.split(" ").dropLast(1).joinToString(" ")
        }
        val newOptions = mutableListOf(
            QuickSearchItem.Query(
                query = "$combinedUsedTags $queryWithoutTags $lastWord",
                searched = false
            )
        )
        val newQueries = newTags.map { tag ->
            QuickSearchItem.Query(
                query = "$combinedUsedTags $queryWithoutTags ${'$'}tag:${tag.i18n.enUs.name ?: tag.name}${'$'}",
                searched = false
            )
        }
        newOptions.addAll(newQueries)
        return newOptions
    }

    override fun searchFeed(query: String, tab: Tab?): PagedData<Shelf> =
        PagedData.Continuous { continuation ->
            val contInt = continuation?.toIntOrNull() ?: 0
            val newResponse = asmrApi.searchWorks(contInt + 1, keyword = query)
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

    override suspend fun searchTabs(query: String): List<Tab> = emptyList()

    ////--------------------------------------------------------------------------------------------
    //// LoginClient.CustomInput
    private var user: User? = null
    override val forms: List<LoginClient.Form>
        get() = LoginClient.Form(
            key = "login",
            label = "Login",
            icon = LoginClient.InputField.Type.Username,
            inputFields = kotlin.collections.listOf(
                LoginClient.InputField(
                    type = LoginClient.InputField.Type.Username,
                    key = "username",
                    label = "Username",
                    isRequired = true,
                ),
                LoginClient.InputField(
                    type = LoginClient.InputField.Type.Password,
                    key = "password",
                    label = "Password",
                    isRequired = true,
                )
            )
        ).listOf()

    override suspend fun getCurrentUser(): User? = user
    override suspend fun onLogin(key: String, data: Map<String, String?>): List<User> {
        val username = data["username"] ?: return emptyList()
        val password = data["password"] ?: return emptyList()
        val res = asmrApi.login(username, password)
        return User(
            id = res.user.recommenderUuid,
            name = res.user.name,
            extras = mapOf("token" to res.token)
        ).listOf()
    }

    override suspend fun onSetLoginUser(user: User?) {
        this.user = user
        asmrApi.updateUser(user?.id, user?.extras?.get("token"))
    }

    ////--------------------------------------------------------------------------------------------
    //// PlaylistClient
    override fun getShelves(playlist: Playlist): PagedData<Shelf> =
        PagedData.Continuous { continuation ->
            val contInt = continuation?.toIntOrNull() ?: 0
            val newResponse = asmrApi.getPlaylistWorks(playlist.id, contInt + 1)
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

    override suspend fun loadPlaylist(playlist: Playlist): Playlist = playlist

    //to allow deletion, we need to return works converted to fake tracks
    override fun loadTracks(playlist: Playlist): PagedData<Track> = PagedData.Single {
        val works = asmrApi.getPlaylistWorks(playlist.id, 1).works
        works.map { it.toTrack() }
    }

    ////--------------------------------------------------------------------------------------------
    //// PlaylistEditClient
    override suspend fun addTracksToPlaylist(
        playlist: Playlist,
        tracks: List<Track>,
        index: Int,
        new: List<Track>
    ) {
        val trackIds =
            new.map { it.album?.id ?: it.extras["id"] ?: throw Exception("Expected album id") }
                .distinct()
        asmrApi.addWorksToPlaylist(playlist.id, trackIds)
    }

    override suspend fun removeTracksFromPlaylist(
        playlist: Playlist,
        tracks: List<Track>,
        indexes: List<Int>
    ) {
        val trackIds =
            tracks.map { it.album?.id ?: it.extras["id"] ?: throw Exception("Expected album id") }
        val tracksToRemove = indexes.map { trackIds[it] }.distinct()
        asmrApi.removeWorksFromPlaylist(playlist.id, tracksToRemove)
    }

    override suspend fun createPlaylist(title: String, description: String?): Playlist {
        return asmrApi.createPlaylist(title, description ?: "").toPlaylist()
    }

    override suspend fun deletePlaylist(playlist: Playlist) {
        if (playlist.title == "Marked" || playlist.title == "Liked") {
            throw Exception("Cannot delete default playlist")
        }
        asmrApi.deletePlaylist(playlist.id)
    }

    override suspend fun editPlaylistMetadata(
        playlist: Playlist,
        title: String,
        description: String?
    ) {
        val newTitle = when (playlist.title) {
            "Marked" -> "Marked"
            "Liked" -> "Liked"
            else -> title
        }
        asmrApi.editPlaylist(playlist.id, newTitle, description ?: "")
    }


    override suspend fun moveTrackInPlaylist(
        playlist: Playlist,
        tracks: List<Track>,
        fromIndex: Int,
        toIndex: Int
    ) = throw ClientException.NotSupported("Move track in playlist not supported")

    ////--------------------------------------------------------------------------------------------
    //// PlaylistEditPrivacyClient
    override suspend fun setPrivacy(playlist: Playlist, isPrivate: Boolean) {
        val private = if (isPrivate) 0 else 1
        asmrApi.editPlaylist(playlist.id, playlist.title, playlist.description ?: "", private)
    }

    override suspend fun likeTrack(track: Track, isLiked: Boolean) {
        if (isLiked) {
            asmrApi.rateWork(
                track.album?.id ?: track.extras["id"] ?: throw Exception("Expected album id"), 5
            )
        } else {
            asmrApi.deleteRating(
                track.album?.id ?: track.extras["id"] ?: throw Exception("Expected album id")
            )
        }
    }

    override suspend fun listEditablePlaylists(track: Track?): List<Pair<Playlist, Boolean>> {
        val playlists = asmrApi.getPlaylists().playlists.map { it.toPlaylist() }
        return playlists.map { playlist ->
            val works = asmrApi.getPlaylistWorks(playlist.id, 1).works
            val isInPlaylist = works.any { it.id.toString() == track?.id }
            Pair(playlist, isInPlaylist)
        }
    }
}