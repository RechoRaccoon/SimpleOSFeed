package com.mediaviewer.model

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

enum class AppMode { BLUESKY, E621 }
enum class ScreenState { FEED, COMMENTS, SETTINGS, GRID }

data class DownloadProgress(val count: Int, val isRunning: Boolean)

data class MediaItem(
    val id: String,
    val mediaUrl: String,
    val thumbUrl: String,
    val isVideo: Boolean,
    val videoPlaylistUrl: String? = null,
    val postUri: String = "",
    val postCid: String = "",
    val author: AuthorInfo,
    val likeUri: String? = null,
    val repostUri: String? = null,
    val bookmarkUri: String? = null,
    val isLiked: Boolean = false,
    val isReposted: Boolean = false,
    val isBookmarked: Boolean = false,
    val likeCount: Int = 0,
    val replyCount: Int = 0,
    val repostCount: Int = 0,
    val altText: String = "",
    val e621PostId: Int? = null,
    val e621Score: Int = 0,
    val e621UserVote: Int = 0,
    val tags: String = ""
)

data class AuthorInfo(
    val did: String,
    val handle: String,
    val displayName: String,
    val avatarUrl: String?,
    val followingUri: String? = null,
    val isFollowing: Boolean = false
)

data class CommentItem(
    val id: String,
    val uri: String = "",
    val cid: String = "",
    val authorHandle: String,
    val authorDisplayName: String,
    val authorAvatarUrl: String?,
    val body: String,
    val createdAt: String,
    val likeCount: Int = 0,
    val isLiked: Boolean = false,
    val likeUri: String? = null,
    val e621UserVote: Int = 0
)

// ── Bluesky ──────────────────────────────────────────────────────────────────

data class BskyCreateSessionRequest(val identifier: String, val password: String)

data class BskySession(
    val accessJwt: String,
    val refreshJwt: String,
    val handle: String,
    val did: String,
    val email: String? = null
)

data class BskyRefreshResponse(
    val accessJwt: String,
    val refreshJwt: String,
    val did: String,
    val handle: String
)

data class BskyTimelineResponse(val feed: List<BskyFeedItem>, val cursor: String? = null)

data class BskyFeedItem(
    val post: BskyPost,
    val reply: BskyReply? = null,
    val reason: BskyReason? = null
)

data class BskyPost(
    val uri: String,
    val cid: String,
    val author: BskyProfile,
    val record: BskyRecord,
    val embed: BskyEmbed? = null,
    val likeCount: Int? = 0,
    val repostCount: Int? = 0,
    val replyCount: Int? = 0,
    val viewer: BskyPostViewer? = null
)

data class BskyPostViewer(
    val like: String? = null,
    val repost: String? = null,
    val threadMuted: Boolean? = null
)

data class BskyProfile(
    val did: String,
    val handle: String,
    val displayName: String? = null,
    val avatar: String? = null,
    val viewer: BskyActorViewer? = null
)

data class BskyActorViewer(
    val following: String? = null,
    val followedBy: String? = null,
    val muted: Boolean? = null
)

data class BskyRecord(
    @SerializedName("\$type") val type: String = "",
    val text: String? = null,
    val createdAt: String? = null,
    val reply: BskyReplyRef? = null
)

data class BskyReplyRef(val root: BskyRef, val parent: BskyRef)
data class BskyRef(val uri: String, val cid: String)
data class BskyReply(val root: BskyPost? = null, val parent: BskyPost? = null)

data class BskyReason(
    @SerializedName("\$type") val type: String = "",
    val by: BskyProfile? = null
)

data class BskyEmbed(
    @SerializedName("\$type") val type: String = "",
    val images: List<BskyImageView>? = null,
    val playlist: String? = null,
    val thumbnail: String? = null,
    val aspectRatio: BskyAspectRatio? = null,
    val cid: String? = null,
    val external: BskyExternalView? = null,
    val media: BskyEmbed? = null,
    val record: BskyEmbedRecord? = null
)

data class BskyImageView(
    val thumb: String,
    val fullsize: String,
    val alt: String? = null,
    val aspectRatio: BskyAspectRatio? = null
)

data class BskyAspectRatio(val width: Int, val height: Int)
data class BskyExternalView(val uri: String, val title: String? = null, val thumb: String? = null)
data class BskyEmbedRecord(@SerializedName("\$type") val type: String = "")

data class BskyActorLikesResponse(val feed: List<BskyFeedItem>, val cursor: String? = null)

data class BskyCreateRecordRequest(
    val repo: String,
    val collection: String,
    val record: Map<String, Any>
)

data class BskyCreateRecordResponse(val uri: String, val cid: String)

data class BskyDeleteRecordRequest(
    val repo: String,
    val collection: String,
    val rkey: String
)

data class BskyThreadResponse(val thread: BskyThreadView)

data class BskyThreadView(
    @SerializedName("\$type") val type: String = "",
    val post: BskyPost? = null,
    val parent: BskyThreadView? = null,
    val replies: List<BskyThreadView>? = null,
    val notFound: Boolean? = null,
    val blocked: Boolean? = null
)

data class BskyPreferencesResponse(val preferences: List<JsonElement>)

data class BskyPreference(
    @SerializedName("\$type") val type: String = "",
    val pinned: List<String>? = null,
    val saved: List<String>? = null,
    val items: List<BskySavedFeedItem>? = null
)

data class BskySavedFeedItem(
    val type: String = "",
    val value: String = "",
    val pinned: Boolean = false,
    val id: String = ""
)

data class BskyFeedGeneratorView(
    val uri: String,
    val cid: String,
    val did: String,
    val displayName: String,
    val description: String? = null,
    val avatar: String? = null
)

data class BskyGetFeedGeneratorsResponse(val feeds: List<BskyFeedGeneratorView>)

data class BskyFeedInfo(
    val uri: String,
    val displayName: String,
    val avatarUrl: String? = null
)

// ── e621 ─────────────────────────────────────────────────────────────────────

data class E621PostsResponse(val posts: List<E621Post>)

data class E621Post(
    val id: Int,
    val file: E621File,
    val preview: E621Preview,
    val sample: E621Sample? = null,
    val score: E621Score,
    val tags: E621Tags,
    val fav_count: Int,
    val is_favorited: Boolean,
    val description: String,
    val created_at: String,
    val updated_at: String,
    val rating: String,
    val comment_count: Int
)

data class E621File(val width: Int, val height: Int, val ext: String, val url: String? = null, val md5: String)
data class E621Preview(val width: Int, val height: Int, val url: String? = null)
data class E621Sample(val has: Boolean, val width: Int, val height: Int, val url: String? = null)
data class E621Score(val up: Int, val down: Int, val total: Int)
data class E621Tags(
    val general: List<String>,
    val species: List<String>,
    val character: List<String>,
    val artist: List<String>,
    val meta: List<String>
)

data class E621Comment(
    val id: Int,
    val post_id: Int,
    val creator_id: Int?,
    val creator_name: String,
    val body: String,
    val created_at: String,
    val score: Int,
    val is_hidden: Boolean
)

// ── Author feed / discovery ───────────────────────────────────────────────────

data class BskyActorFeedsResponse(val feeds: List<BskyFeedGeneratorView>, val cursor: String? = null)

// ── Bluesky Lists ─────────────────────────────────────────────────────────────

data class BskyGetListsResponse(
    val lists: List<BskyList>,
    val cursor: String? = null
)

data class BskyList(
    val uri: String,
    val cid: String,
    val name: String,
    val purpose: String = "",
    val description: String? = null,
    val avatar: String? = null,
    val itemCount: Int? = null
)

// ── Bluesky Starter Packs ─────────────────────────────────────────────────────

data class BskyGetStarterPacksResponse(
    val starterPacks: List<BskyStarterPackView>,
    val cursor: String? = null
)

data class BskyStarterPackView(
    val uri: String,
    val cid: String,
    val record: BskyStarterPackRecord? = null,
    val creator: BskyProfile? = null,
    val listItemCount: Int? = null,
    val joinedAllTimeCount: Int? = null
)

data class BskyStarterPackRecord(
    @com.google.gson.annotations.SerializedName("\$type") val type: String = "",
    val name: String = "",
    val description: String? = null,
    val list: String = "",   // AT-URI of the underlying list — use this to add members
    val createdAt: String = ""
)
