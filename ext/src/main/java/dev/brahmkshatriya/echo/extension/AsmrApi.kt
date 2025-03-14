package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.settings.Settings
import dev.brahmkshatriya.echo.extension.helpers.deepCopy
import dev.brahmkshatriya.echo.extension.helpers.filterToSubtitled
import dev.brahmkshatriya.echo.extension.helpers.findMainAudioFolder
import dev.brahmkshatriya.echo.extension.helpers.removeEmptyFolders
import dev.brahmkshatriya.echo.extension.helpers.translate
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Date
import java.util.UUID
import java.util.concurrent.TimeUnit.SECONDS

class AsmrApi(
    private val uuid: String = UUID.randomUUID().toString()
) {
    private val baseUrl = "https://api.asmr-200.com/api"
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, SECONDS)
        .readTimeout(10, SECONDS)
        .writeTimeout(10, SECONDS).build()
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    private var settings: Settings? = null

    ////--------------------------------------------------------------------------------------------
    //// settings api functions
    fun initSettings(settings: Settings) {
        this.settings = settings
    }

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
    private val workTreeCache = mutableMapOf<String, Pair<Date, MediaTreeItem.Folder>>()
    private val workTreeCacheLimit = 20
    private val cacheMutex = Mutex()
    suspend fun getWorkMediaTree(workId: String): MediaTreeItem.Folder {
        val url = "$baseUrl/tracks/$workId?v=1"
        cacheMutex.withLock {
            if (workTreeCache.containsKey(workId)) {
                workTreeCache[workId]!!.first.time = Date().time
                return workTreeCache[workId]!!.second.deepCopy()
            }
        }
        val response: List<MediaTreeItem> = sendRequest(url, RequestType.GET)
        val folders = MediaTreeItem.Folder(
            "folder",
            "root",
            "root",
            response
        ).removeEmptyFolders()
            .translate()
        val mainFolder = folders.findMainAudioFolder()
        cacheMutex.withLock {
            workTreeCache[workId] = Pair(Date(), folders.deepCopy())
            if (workTreeCache.size > workTreeCacheLimit) {
                val oldest = workTreeCache.minByOrNull { it.value.first.time }!!.key
                workTreeCache.remove(oldest)
            }
        }
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
        val url = parametersBuilder("$baseUrl/search/$keyword",
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
        //println("asmrone-logging: $url")
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

    private var tagsCache: List<Tag>? = null
    fun getTags(): List<Tag> {
        val url = "$baseUrl/tags/"
        if (tagsCache != null) {
            return tagsCache!!
        }
        val response = sendRequest<List<Tag>>(url, RequestType.GET)
        tagsCache = response
        return response
    }

    fun getSubTitleFile(url: String): String {
        val request = requestBuilder(url, RequestType.GET, host = null)
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw Exception("Failed to send request: ${response.code}: ${response.body.string()}")
        }
        return response.body.string()
    }

    ////--------------------------------------------------------------------------------------------
    //// helper functions
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
        host: String? = "api.asmr-200.com"
    ): Request {
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
        return parameters.entries.joinToString("&") { (key, value) -> "$key=$value" }.let {
            if (url.contains("?")) {
                "$url&$it"
            } else {
                "$url?$it"
            }
        }
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