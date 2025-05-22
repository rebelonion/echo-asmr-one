package dev.brahmkshatriya.echo.extension.asmrone

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.buildJsonArray


@Serializable
data class AsmrPlaylist(
    @SerialName("id")
    val id: String,
    @SerialName("user_name")
    val userName: String,
    @SerialName("privacy")
    val privacy: Int,
    @SerialName("locale")
    val locale: String,
    @SerialName("playback_count")
    val playbackCount: Int,
    @SerialName("name")
    val name: String,
    @SerialName("description")
    val description: String,
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("updated_at")
    val updatedAt: String,
    @SerialName("works_count")
    val worksCount: Int,
    @SerialName("latestWorkID")
    val latestWorkID: Int?,
    @SerialName("mainCoverUrl")
    val mainCoverUrl: String
)

@Serializable
data class PlaylistsResponse(
    @SerialName("playlists")
    val playlists: List<AsmrPlaylist>,
    @SerialName("pagination")
    val pagination: Pagination
)

////--------------------------------------------------------------------------------------------

@Serializable
data class User(
    @SerialName("loggedIn")
    val loggedIn: Boolean,
    @SerialName("name")
    val name: String,
    @SerialName("group")
    val group: String,
    @SerialName("email")
    val email: String?,
    @SerialName("recommenderUuid")
    val recommenderUuid: String
)

@Serializable
data class LoginResponse(
    @SerialName("user")
    val user: User,
    @SerialName("token")
    val token: String
)

////--------------------------------------------------------------------------------------------

@Serializable
sealed class MediaTreeItem {
    abstract val type: String
    abstract var title: String
    abstract val untranslatedTitle: String

    @Serializable
    @SerialName("folder")
    data class Folder(
        override val type: String,
        @SerialName("title")
        override var title: String,
        override val untranslatedTitle: String = title,
        var children: List<MediaTreeItem>
    ) : MediaTreeItem()

    @Serializable
    @SerialName("audio")
    data class Audio(
        override val type: String,
        override var title: String,
        override val untranslatedTitle: String = title,
        val hash: String,
        val work: FolderWork,
        val workTitle: String,
        val mediaStreamUrl: String,
        val mediaDownloadUrl: String,
        val streamLowQualityUrl: String? = "",
        val duration: Double,
        val size: Long
    ) : MediaTreeItem()

    @Serializable
    @SerialName("text")
    data class Text(
        override val type: String,
        override var title: String,
        override val untranslatedTitle: String = title,
        val hash: String,
        val work: FolderWork,
        val workTitle: String,
        val mediaStreamUrl: String,
        val mediaDownloadUrl: String,
        val duration: Double? = null,
        val size: Long
    ) : MediaTreeItem()

    @Serializable
    @SerialName("image")
    data class Image(
        override val type: String,
        override var title: String,
        override val untranslatedTitle: String = title,
        val hash: String,
        val work: FolderWork,
        val workTitle: String,
        val mediaStreamUrl: String,
        val mediaDownloadUrl: String,
        val size: Long
    ) : MediaTreeItem()

    @Serializable
    @SerialName("other")
    data class Other(
        override val type: String,
        override var title: String,
        override val untranslatedTitle: String = title,
        val hash: String,
        val work: FolderWork,
        val workTitle: String,
        val mediaStreamUrl: String,
        val mediaDownloadUrl: String,
        val size: Long
    ) : MediaTreeItem()
}

@Serializable
data class FolderWork(
    val id: Int,
    @SerialName("source_id")
    val sourceId: String,
    @SerialName("source_type")
    val sourceType: String
)

////--------------------------------------------------------------------------------------------

@Serializable
data class WorksResponse(
    @SerialName("works")
    val works: List<Work>,
    @SerialName("pagination")
    val pagination: Pagination
)

@Serializable
data class Pagination @OptIn(ExperimentalSerializationApi::class) constructor(
    @SerialName("currentPage")
    @JsonNames("page")
    val currentPage: Int,
    @SerialName("pageSize")
    val pageSize: Int,
    @SerialName("totalCount")
    val totalCount: Int
)

@Serializable
data class Work(
    @SerialName("id")
    val id: Int,
    @SerialName("title")
    var title: String,
    @SerialName("circle_id")
    val circleId: Int,
    @SerialName("name")
    var name: String,
    @SerialName("nsfw")
    val nsfw: Boolean,
    @SerialName("release")
    val release: String,
    @SerialName("dl_count")
    val dlCount: Int,
    @SerialName("price")
    val price: Int,
    @SerialName("review_count")
    val reviewCount: Int,
    @SerialName("rate_count")
    val rateCount: Int,
    @SerialName("rate_average_2dp")
    val rateAverage2dp: Double,
    @SerialName("rate_count_detail")
    val rateCountDetail: List<RateCountDetail>,
    @SerialName("rank")
    val rank: List<Rank>? = null,
    @SerialName("has_subtitle")
    val hasSubtitle: Boolean,
    @SerialName("create_date")
    val createDate: String,
    @SerialName("vas")
    val vas: List<VA>,
    @SerialName("tags")
    val tags: List<Tag>,
    @Serializable(with = LanguageEditionsDeserializer::class)
    @SerialName("language_editions")
    val languageEditions: List<LanguageEdition>,
    @SerialName("original_workno")
    val originalWorkno: String? = null,
    @SerialName("other_language_editions_in_db")
    val otherLanguageEditionsInDb: List<OtherLanguageEdition>,
    @SerialName("translation_info")
    val translationInfo: TranslationInfo,
    @SerialName("work_attributes")
    val workAttributes: String,
    @SerialName("age_category_string")
    val ageCategoryString: String,
    @SerialName("duration")
    val duration: Int,
    @SerialName("source_type")
    val sourceType: String,
    @SerialName("source_id")
    val sourceId: String,
    @SerialName("source_url")
    val sourceUrl: String,
    @SerialName("userRating")
    val userRating: Int? = null,
    //@SerialName("playlistStatus")
    //val playlistStatus: PlaylistStatus,
    @SerialName("circle")
    val circle: Circle,
    @SerialName("samCoverUrl")
    val samCoverUrl: String,
    @SerialName("thumbnailCoverUrl")
    val thumbnailCoverUrl: String,
    @SerialName("mainCoverUrl")
    val mainCoverUrl: String
)

object LanguageEditionsDeserializer :
    JsonTransformingSerializer<List<LanguageEdition>>(ListSerializer(LanguageEdition.serializer())) {
    override fun transformDeserialize(element: JsonElement): JsonElement {
        if (element is JsonArray) {
            return element
        }
        if (element is JsonObject) {
            val array = buildJsonArray {
                element.entries.forEach { (_, value) ->
                    add(value)
                }
            }
            return array
        }
        return JsonArray(emptyList())
    }
}

@Serializable
data class Rank(
    @SerialName("term")
    val term: String,
    @SerialName("category")
    val category: String,
    @SerialName("rank")
    val rank: Int,
    @SerialName("rank_date")
    val rankDate: String // yyyy-MM-dd
)

@Serializable
data class RateCountDetail(
    @SerialName("review_point")
    val reviewPoint: Int,
    @SerialName("count")
    val count: Int,
    @SerialName("ratio")
    val ratio: Int
)

@Serializable
data class VA(
    @SerialName("id")
    val id: String,
    @SerialName("name")
    val name: String
)

@Serializable
data class Tag(
    @SerialName("id")
    val id: Int,
    @SerialName("i18n")
    val i18n: I18n,
    @SerialName("name")
    val name: String,
    @SerialName("upvote")
    val upvote: Int? = null,
    @SerialName("downvote")
    val downvote: Int? = null,
    @SerialName("voteRank")
    val voteRank: Int = 0,
    @SerialName("voteStatus")
    val voteStatus: Int = 0,
)

@Serializable
data class I18n(
    @SerialName("en-us")
    val enUs: Language,
    @SerialName("ja-jp")
    val jaJp: Language,
    @SerialName("zh-cn")
    val zhCn: Language
)

@Serializable
data class Language(
    @SerialName("name")
    val name: String? = null,
    @SerialName("history")
    val history: List<History> = emptyList()
)

@Serializable
data class History(
    @SerialName("name")
    val name: String,
    @SerialName("deprecatedAt")
    val deprecatedAt: Long
)

@Serializable
data class LanguageEdition(
    @SerialName("lang")
    val lang: String,
    @SerialName("label")
    val label: String,
    @SerialName("workno")
    val workno: String,
    @SerialName("edition_id")
    val editionId: Int,
    @SerialName("edition_type")
    val editionType: String,
    @SerialName("display_order")
    val displayOrder: Int
)

@Serializable
data class OtherLanguageEdition(
    @SerialName("id")
    val id: Int,
    @SerialName("lang")
    val lang: String,
    @SerialName("title")
    val title: String,
    @SerialName("source_id")
    val sourceId: String,
    @SerialName("is_original")
    val isOriginal: Boolean,
    @SerialName("source_type")
    val sourceType: String
)

@Serializable
data class TranslationInfo(
    @SerialName("lang")
    val lang: String? = null,
    //@SerialName("is_child")
    //val isChild: Boolean,
    //@SerialName("is_parent")
    //val isParent: Boolean,
    //@SerialName("is_original")
    //val isOriginal: Boolean,
    //@SerialName("is_volunteer")
    //val isVolunteer: Boolean,
    //@SerialName("child_worknos")
    //val childWorknos: List<Any> = emptyList(),
    //@SerialName("parent_workno")
    //val parentWorkno: String?,
    //@SerialName("original_workno")
    //val originalWorkno: String?,
    //@SerialName("is_translation_agree")
    //val isTranslationAgree: Boolean,
    //@SerialName("translation_bonus_langs")
    //val translationBonusLangs: List<Any> = emptyList(),
    //@SerialName("is_translation_bonus_child")
    //val isTranslationBonusChild: Boolean
)

//@Serializable
//data class PlaylistStatus(
// TODO: ??
// uuid to boolean
//ex: f87ed037-4e61-47aa-8c9c-ae9ddbc50341: false
//)

@Serializable
data class Circle(
    @SerialName("id")
    val id: Int,
    @SerialName("name")
    val name: String,
    @SerialName("source_id")
    val sourceId: String,
    @SerialName("source_type")
    val sourceType: String
)