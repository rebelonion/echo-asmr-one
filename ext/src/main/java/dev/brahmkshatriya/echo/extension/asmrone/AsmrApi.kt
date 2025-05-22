package dev.brahmkshatriya.echo.extension.asmrone

import dev.brahmkshatriya.echo.common.settings.Settings
import dev.brahmkshatriya.echo.extension.asmrone.helpers.TimeBasedLRUCache
import dev.brahmkshatriya.echo.extension.asmrone.helpers.deepCopy
import dev.brahmkshatriya.echo.extension.asmrone.helpers.filterToSubtitled
import dev.brahmkshatriya.echo.extension.asmrone.helpers.getTranslationLanguage
import dev.brahmkshatriya.echo.extension.asmrone.helpers.removeEmptyFolders
import dev.brahmkshatriya.echo.extension.asmrone.helpers.translate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.mapSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.TimeUnit.SECONDS

class AsmrApi {
    private var uuid: String = UUID.randomUUID().toString()
    private var token: String? = null
    private val baseUrl = "https://api.${getMirror()}.com/api"
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, SECONDS)
        .readTimeout(10, SECONDS)
        .writeTimeout(10, SECONDS).build()
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }
    private var settings: Settings? = null

    fun updateUser(newUuid: String?, newToken: String?) {
        uuid = newUuid ?: UUID.randomUUID().toString()
        token = newToken
    }

    ////--------------------------------------------------------------------------------------------
    //// settings api functions
    fun initSettings(settings: Settings) {
        this.settings = settings
    }

    private fun getMirror() = settings?.getString("siteMirror") ?: "asmr-200"

    private fun SortOrder.onlyShowSfw(): String {
        val onlySfw = settings!!.getBoolean("onlyShowSfw")
        return if (onlySfw == true) {
            SortOrder.NSFW.apiValue
        } else {
            this.apiValue
        }
    }

    private fun SortType.onlyShowSfw(): String {
        val onlySfw = settings!!.getBoolean("onlyShowSfw")
        return if (onlySfw == true) {
            SortType.ASC.apiValue
        } else {
            this.apiValue
        }
    }

    private fun onlySubtitled(): Boolean {
        return settings!!.getBoolean("onlyShowSubtitled") == true
    }

    ////--------------------------------------------------------------------------------------------
    //// raw api functions
    fun login(name: String, password: String): LoginResponse {
        val url = "https://api.asmr.one/api/auth/me"
        val body = jsonBodyBuilder(
            mapOf(
                "name" to name,
                "password" to password
            )
        )
        try {
            return sendRequest<LoginResponse>(url, RequestType.POST, body)
        } catch (e: Exception) {
            throw Exception("Failed to login: ${e.message}")
        }
    }


    private val workTreeCache = TimeBasedLRUCache<MediaTreeItem.Folder>(20)
    suspend fun getWorkMediaTree(workId: String): MediaTreeItem.Folder {
        val url = "$baseUrl/tracks/$workId?v=1"
        workTreeCache.get(workId)?.let { cachedFolder ->
            return cachedFolder.deepCopy()
        }

        val response: List<MediaTreeItem> = sendRequest(url, RequestType.GET)
        val folders = MediaTreeItem.Folder(
            "folder",
            "root",
            "root",
            response
        ).removeEmptyFolders()
            .translate(settings.getTranslationLanguage())

        workTreeCache.put(workId, folders.deepCopy())

        return folders
    }

    suspend fun getWork(workId: String): Work {
        val url = "$baseUrl/workInfo/$workId"
        return sendRequest<Work>(url, RequestType.GET)
            .translate(settings.getTranslationLanguage())
    }

    suspend fun getPopularWorks(
        page: Int = 1,
        keyword: String = " ",
        subtitle: Int = 0,
        //localSubtitledWorks: List<> = emptyList(),
        //withPlaylistStatus: List<> = emptyList()
    ): WorksResponse {
        val url = "$baseUrl/recommender/popular"
        val body = jsonBodyBuilder(
            mapOf(
                "keyword" to keyword,
                "page" to page,
                "subtitle" to subtitle,
                //"local_subtitled_works" to localSubtitledWorks,
                //"with_playlist_status" to withPlaylistStatus
            )
        )
        return sendRequest<WorksResponse>(url, RequestType.POST, body)
            .filterToSubtitled(onlySubtitled())
            .translate(settings.getTranslationLanguage())
    }

    suspend fun getRecommendedWorks(
        page: Int = 1,
        keyword: String = " ",
        recommenderUuid: String = uuid,
        subtitle: Int = 0,
        //localSubtitledWorks: List<> = emptyList(),
        //withPlaylistStatus: List<> = emptyList()
    ): WorksResponse {
        val url = "$baseUrl/recommender/recommend-for-user"
        val body = jsonBodyBuilder(
            mapOf(
                "keyword" to keyword,
                "recommenderUuid" to recommenderUuid,
                "page" to page,
                "subtitle" to subtitle,
                //"localSubtitledWorks" to localSubtitledWorks,
                //"withPlaylistStatus" to withPlaylistStatus
            )
        )
        return sendRequest<WorksResponse>(url, RequestType.POST, body)
            .filterToSubtitled(onlySubtitled())
            .translate(settings.getTranslationLanguage())
    }

    suspend fun getWorks(
        page: Int = 1,
        order: SortOrder = SortOrder.RELEASE,
        sort: SortType = SortType.DESC,
        subtitle: Int = 0,
        //withPlaylistStatus: List<> = emptyList()
    ): WorksResponse {
        val url = parametersBuilder(
            "$baseUrl/works", mapOf(
                "order" to order.onlyShowSfw(),
                "sort" to sort.onlyShowSfw(),
                "page" to page,
                "subtitle" to subtitle,
                //"withPlaylistStatus[]" to withPlaylistStatus
            )
        )
        return sendRequest<WorksResponse>(url, RequestType.GET)
            .filterToSubtitled(onlySubtitled())
            .translate(settings.getTranslationLanguage())
    }

    suspend fun searchWorks(
        page: Int = 1,
        keyword: String = " ",
        order: SortOrder = SortOrder.RELEASE,
        sort: SortType = SortType.DESC,
        seed: Int = 64,
        subtitle: Int = 0,
        includeTranslationWorks: Boolean = true,
        //withPlaylistStatus: List<> = emptyList()
    ): WorksResponse {
        val url = parametersBuilder(
            withContext(Dispatchers.IO) {
                "$baseUrl/search/${keyword.encodeKeyword()}"
            },
            mapOf(
                "page" to page,
                "order" to order.onlyShowSfw(),
                "sort" to sort.onlyShowSfw(),
                "seed" to seed,
                "subtitle" to subtitle,
                "includeTranslationWorks" to includeTranslationWorks,
                //"withPlaylistStatus[]" to withPlaylistStatus
            )
        )
        //println("asmr-logging: $url")
        return sendRequest<WorksResponse>(url, RequestType.GET)
            .filterToSubtitled(onlySubtitled())
            .translate(settings.getTranslationLanguage())

    }

    suspend fun getRelatedWorks(
        itemId: String,
        keyword: String = " ",
        //localSubtitledWorks: List<> = emptyList(),
        //withPlaylistStatus: List<> = emptyList()
    ): WorksResponse {
        val url = "$baseUrl/recommender/item-neighbors"
        val body = jsonBodyBuilder(
            mapOf(
                "keyword" to keyword,
                "itemId" to itemId,
                //"localSubtitledWorks" to localSubtitledWorks,
                //"withPlaylistStatus" to withPlaylistStatus
            )
        )
        return sendRequest<WorksResponse>(url, RequestType.POST, body)
            .filterToSubtitled(onlySubtitled())
            .translate(settings.getTranslationLanguage())
    }

    suspend fun getFavorites(
        page: Int = 1,
        order: SortOrder = SortOrder.RELEASE,
        sort: SortType = SortType.DESC
    ): WorksResponse {
        if (token == null) {
            return WorksResponse(emptyList(), Pagination(0, 0, 0))
        }
        val url = parametersBuilder(
            "$baseUrl/review",
            mapOf(
                "order" to order.onlyShowSfw(),
                "sort" to sort.onlyShowSfw(),
                "page" to page
            )
        )
        return sendRequest<WorksResponse>(url, RequestType.GET)
            .filterToSubtitled(onlySubtitled())
            .translate(settings.getTranslationLanguage())
    }

    fun getPlaylists(
        page: Int = 1,
        pageSize: Int = 96,
        filterBy: String = "all"
    ): PlaylistsResponse {
        if (token == null) {
            return PlaylistsResponse(emptyList(), Pagination(0, 0, 0))
        }
        val url = parametersBuilder(
            "https://api.asmr.one/api/playlist/get-playlists",
            mapOf(
                "page" to page,
                "pageSize" to pageSize,
                "filterBy" to filterBy
            )
        )
        return sendRequest(url, RequestType.GET)
    }

    ////--------------------------------------------------------------------------------------------

    suspend fun getPlaylistWorks(
        playlistId: String,
        page: Int = 1,
        pageSize: Int = 96
    ): WorksResponse {
        val url = parametersBuilder(
            "https://api.asmr.one/api/playlist/get-playlist-works",
            mapOf(
                "id" to playlistId,
                "page" to page,
                "pageSize" to pageSize
            )
        )
        return sendRequest<WorksResponse>(url, RequestType.GET)
            .filterToSubtitled(onlySubtitled())
            .translate(settings.getTranslationLanguage())
    }

    fun addWorksToPlaylist(playlistId: String, workId: List<String>) {
        val url = "https://api.asmr.one/api/playlist/add-works-to-playlist"
        val body = jsonBodyBuilder(
            mapOf(
                "id" to playlistId,
                "works" to workId
            )
        )
        return sendRequest(url, RequestType.POST, body)
    }

    fun removeWorksFromPlaylist(playlistId: String, workId: List<String>) {
        val url = "https://api.asmr.one/api/playlist/remove-works-from-playlist"
        val body = jsonBodyBuilder(
            mapOf(
                "id" to playlistId,
                "works" to workId
            )
        )
        return sendRequest(url, RequestType.POST, body)
    }

    fun createPlaylist(
        name: String,
        description: String = "",
        privacy: Int = 0,
        locale: String = "en",
        works: List<String> = emptyList()
    ): AsmrPlaylist {
        val url = "https://api.asmr.one/api/playlist/create-playlist"
        val body = jsonBodyBuilder(
            mapOf(
                "name" to name,
                "privacy" to privacy,
                "locale" to locale,
                "description" to description,
                "works" to works
            )
        )
        return sendRequest(url, RequestType.POST, body)
    }

    fun deletePlaylist(playlistId: String) {
        val url = "https://api.asmr.one/api/playlist/delete-playlist"
        val body = jsonBodyBuilder(
            mapOf(
                "id" to playlistId
            )
        )
        return sendRequest(url, RequestType.POST, body)
    }

    fun editPlaylist(
        playlistId: String,
        name: String,
        description: String = "",
        privacy: Int = 0
    ) {
        val url = "https://api.asmr.one/api/playlist/edit-playlist-metadata"
        val body = jsonBodyBuilder(
            mapOf(
                "id" to playlistId,
                "data" to mapOf(
                    "name" to name,
                    "privacy" to privacy,
                    "description" to description
                )
            )
        )
        return sendRequest(url, RequestType.POST, body)
    }

    ////--------------------------------------------------------------------------------------------

    private var tagsCache: List<Tag>? = null
    fun getTags(): List<Tag> {
        val url = "$baseUrl/tags/"
        if (tagsCache != null) {
            return tagsCache!!
        }
        val response =
            sendRequest<List<Tag>>(url, RequestType.GET).sortedBy { it.i18n.enUs.name ?: it.name }
        tagsCache = response
        return response
    }

    fun getSubTitleFile(url: String): String {
        val request = requestBuilder(url, RequestType.GET)
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw Exception("Failed to send request: ${response.code}: ${response.body.string()}")
        }
        return response.body.string()
    }

    fun rateWork(workId: String, rating: Int?) {
        val url = "https://api.asmr.one/api/review"
        val body = jsonBodyBuilder(
            mapOf(
                "work_id" to workId,
                "rating" to rating
            )
        )
        return sendRequest(url, RequestType.PUT, body)
    }

    fun deleteRating(workId: String) {
        val url = parametersBuilder(
            "https://api.asmr.one/api/review",
            mapOf(
                "work_id" to workId
            )
        )
        return sendRequest(url, RequestType.DELETE)
    }

    ////--------------------------------------------------------------------------------------------
    //// helper functions
    private fun String.encodeKeyword(): String {
        return URLEncoder.encode(this, "UTF-8").replace("+", "%20")
    }

    private enum class RequestType {
        GET,
        POST,
        PUT,
        DELETE
    }

    private fun requestBuilder(
        url: String,
        type: RequestType,
        body: RequestBody? = null,
        headers: Map<String, String> = emptyMap(),
    ): Request {
        val host = if (url.contains("api.${getMirror()}")) "api.${getMirror()}.com" else null
        val requestBuilder = Request.Builder().url(url)
        headers.forEach { (key, value) -> requestBuilder.addHeader(key, value) }
        val requestBody = if (body != null) {
            requestBuilder.addHeader("Content-Length", body.contentLength().toString())
            body
        } else {
            requestBuilder.addHeader("Content-Length", "0")
            "{}".toRequestBody()
        }
        requestBuilder.addHeader("Origin", "https://asmr.one")
        requestBuilder.addHeader("Referer", "https://asmr.one/")
        if (host != null) {
            requestBuilder.addHeader("Host", host)
        }
        requestBuilder.addHeader("Content-Type", "application/json")
        if (token != null) {
            requestBuilder.addHeader("Authorization", "Bearer $token")
        }
        return when (type) {
            RequestType.GET -> requestBuilder.get()
            RequestType.POST -> requestBuilder.post(requestBody)
            RequestType.PUT -> requestBuilder.put(requestBody)
            RequestType.DELETE -> requestBuilder.delete(requestBody)
        }.build()
    }

    object AnyValueMapSerializer : KSerializer<Map<String, Any?>> {
        @OptIn(ExperimentalSerializationApi::class)
        override val descriptor =
            mapSerialDescriptor(String.serializer().descriptor, JsonElement.serializer().descriptor)

        override fun serialize(encoder: Encoder, value: Map<String, Any?>) {
            val jsonObject = JsonObject(value.mapValues { (_, value) ->
                when (value) {
                    null -> JsonNull
                    is String -> JsonPrimitive(value)
                    is Number -> JsonPrimitive(value)
                    is Boolean -> JsonPrimitive(value)
                    is List<*> -> {
                        buildJsonArray {
                            value.forEach { item ->
                                when (item) {
                                    null -> add(JsonNull)
                                    is String -> add(JsonPrimitive(item))
                                    is Number -> add(JsonPrimitive(item))
                                    is Boolean -> add(JsonPrimitive(item))
                                    else -> throw IllegalArgumentException("Unsupported list item type: ${item::class}")
                                }
                            }
                        }
                    }

                    else -> throw IllegalArgumentException("Unsupported type: ${value::class}")
                }
            })
            encoder.encodeSerializableValue(JsonObject.serializer(), jsonObject)
        }

        override fun deserialize(decoder: Decoder): Map<String, Any?> {
            throw NotImplementedError("Deserialize not implemented")
        }
    }

    private fun jsonBodyBuilder(body: Map<String, Any?>): RequestBody {
        return json.encodeToString(AnyValueMapSerializer, body)
            .toRequestBody("application/json".toMediaType())
    }

    private fun parametersBuilder(url: String, parameters: Map<String, Any>): String {
        // Parse the base URL
        val httpUrlBuilder = url.toHttpUrlOrNull()?.newBuilder()
            ?: throw IllegalArgumentException("Invalid URL format: $url")

        parameters.forEach { (key, value) ->
            when (value) {
                is String -> httpUrlBuilder.addQueryParameter(key, value)
                is Number -> httpUrlBuilder.addQueryParameter(key, value.toString())
                is Boolean -> httpUrlBuilder.addQueryParameter(key, value.toString())
                else -> throw IllegalArgumentException("Unsupported type: ${value::class}")
            }
        }

        return httpUrlBuilder.build().toString()
    }


    private inline fun <reified T> sendRequest(
        url: String,
        type: RequestType,
        body: RequestBody? = null,
        headers: Map<String, String> = emptyMap(),
    ): T {
        val request = requestBuilder(url, type, body, headers)
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            println("asmr-logging: Failed to send request: ${response.code}: ${response.body.string()}")
            throw Exception("Failed to send request: ${response.code}: ${response.body.string()}")
        }
        return json.decodeFromString<T>(response.body.string())
    }

    companion object {
        enum class SortOrder(val apiValue: String) {
            RELEASE("release"),
            NEWEST("create_date"),
            MY_RATING("rating"),
            PRICE("price"),
            RATING("rate_average_2dp"),
            REVIEW_COUNT("review_count"),
            RJ_CODE("id"),
            NSFW("nsfw"), //asc is sfw, desc is nsfw
            RANDOM("random"),
        }

        enum class SortType(val apiValue: String) {
            ASC("asc"),
            DESC("desc"),
        }
    }
}