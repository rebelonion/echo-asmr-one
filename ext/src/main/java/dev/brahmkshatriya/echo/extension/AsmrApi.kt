package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.settings.Settings
import dev.brahmkshatriya.echo.extension.helpers.TimeBasedLRUCache
import dev.brahmkshatriya.echo.extension.helpers.deepCopy
import dev.brahmkshatriya.echo.extension.helpers.filterToSubtitled
import dev.brahmkshatriya.echo.extension.helpers.removeEmptyFolders
import dev.brahmkshatriya.echo.extension.helpers.translate
import io.ktor.http.URLBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
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
            .translate()

        workTreeCache.put(workId, folders.deepCopy())

        return folders
    }

    suspend fun getWork(workId: String): Work {
        val url = "$baseUrl/workInfo/$workId"
        return sendRequest<Work>(url, RequestType.GET)
            .translate()
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
            .translate()
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
            .translate()
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
            .translate()
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
            .translate()

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
            .translate()
    }

    fun getPlaylists(
        page: Int = 1,
        pageSize: Int = 96,
        filterBy: String = "all"
    ): PlaylistsResponse? {
        if (token == null) {
            return null
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

    suspend fun getPlaylistWorks(
        playlistId: String,
        page: Int = 1,
        pageSize: Int = 12
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
            .translate()
    }

    private var tagsCache: List<Tag>? = null
    fun getTags(): List<Tag> {
        val url = "$baseUrl/tags/"
        if (tagsCache != null) {
            return tagsCache!!
        }
        val response = sendRequest<List<Tag>>(url, RequestType.GET).sortedBy { it.i18n.enUs.name ?: it.name }
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

    private fun jsonBodyBuilder(body: Map<String, Any>): RequestBody {
        val jsonObject = buildJsonObject {
            body.forEach { (key, value) ->
                when (value) {
                    is String -> put(key, JsonPrimitive(value))
                    is Number -> put(key, JsonPrimitive(value))
                    else -> throw IllegalArgumentException("Unsupported type: ${value::class}")
                }
            }
        }
        return jsonObject.toString().toRequestBody("application/json".toMediaType())
    }

    private fun parametersBuilder(url: String, parameters: Map<String, Any>): String {
        val builder = URLBuilder(url)
        parameters.forEach { (key, value) ->
            when (value) {
                is String -> builder.parameters.append(key, value)
                is Number -> builder.parameters.append(key, value.toString())
                is Boolean -> builder.parameters.append(key, value.toString())
                else -> throw IllegalArgumentException("Unsupported type: ${value::class}")
            }
        }
        return builder.buildString()
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