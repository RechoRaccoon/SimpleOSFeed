package com.mediaviewer.repository

import com.mediaviewer.model.*
import com.mediaviewer.network.BlueskyApi
import com.mediaviewer.network.NetworkClient
import java.time.Instant

class BlueskyRepository {

    private var api: BlueskyApi = NetworkClient.buildBlueskyApi()
    private var baseUrl: String = "https://bsky.social/"

    fun updateServiceUrl(url: String) { baseUrl = url; api = NetworkClient.buildBlueskyApi(url) }

    // ── Auth ──────────────────────────────────────────────────────────────────

    suspend fun login(identifier: String, password: String): Result<BskySession> = runCatching {
        val resp = api.createSession(BskyCreateSessionRequest(identifier, password))
        resp.body() ?: error("Login failed: ${resp.code()} ${resp.message()}")
    }

    suspend fun refreshToken(refreshJwt: String): Result<BskyRefreshResponse> = runCatching {
        val resp = api.refreshSession("Bearer $refreshJwt")
        resp.body() ?: error("Refresh failed: ${resp.code()}")
    }

    // ── Feed ──────────────────────────────────────────────────────────────────

    suspend fun getTimeline(token: String, cursor: String? = null, limit: Int = 50)
        : Result<Pair<List<MediaItem>, String?>> = runCatching {
        val resp = api.getTimeline("Bearer $token", limit, cursor)
        val body = resp.body() ?: error("Timeline ${resp.code()}: ${resp.message()}")
        Pair(body.feed.flatMap { parseFeedItem(it) }, body.cursor)
    }

    suspend fun getFeed(token: String, feedUri: String, cursor: String? = null, limit: Int = 50)
        : Result<Pair<List<MediaItem>, String?>> = runCatching {
        val resp = api.getFeed("Bearer $token", feedUri, limit, cursor)
        val body = resp.body() ?: error("Feed ${resp.code()}: ${resp.message()}")
        Pair(body.feed.flatMap { parseFeedItem(it) }, body.cursor)
    }

    suspend fun getActorLikes(token: String, did: String, cursor: String? = null)
        : Result<Pair<List<MediaItem>, String?>> = runCatching {
        val resp = api.getActorLikes("Bearer $token", did, 100, cursor)
        val body = resp.body() ?: error("Likes ${resp.code()}")
        Pair(body.feed.flatMap { parseFeedItem(it) }, body.cursor)
    }

    // ── Saved Feeds — robust JSON parsing ────────────────────────────────────

    suspend fun getSavedFeeds(token: String, did: String): Result<List<BskyFeedInfo>> = runCatching {
        val feedUris = mutableListOf<String>()

        // Fetch the user's actual saved/pinned feed preferences.
        // Bluesky accounts use EITHER the V2 format OR the legacy V1 format — never both
        // meaningfully — so we use V2 if present, otherwise fall back to V1.
        var prefsError: String? = null
        runCatching {
            val resp = api.getPreferences("Bearer $token")
            if (!resp.isSuccessful) { prefsError = "Prefs HTTP ${resp.code()}"; return@runCatching }
            val body = resp.body() ?: run { prefsError = "Prefs: empty body"; return@runCatching }

            val v2 = body.preferences.firstOrNull {
                it.isJsonObject && it.asJsonObject.get("\$type")?.asString?.endsWith("savedFeedsPrefV2") == true
            }
            if (v2 != null) {
                val items = v2.asJsonObject.getAsJsonArray("items")
                items?.forEach { item ->
                    if (!item.isJsonObject) return@forEach
                    val itemObj  = item.asJsonObject
                    val itemType = itemObj.get("type")?.asString
                    if (itemType == "feed") {
                        itemObj.get("value")?.asString?.let { v -> if (v.startsWith("at://")) feedUris.add(v) }
                    }
                }
            } else {
                val v1 = body.preferences.firstOrNull {
                    it.isJsonObject && it.asJsonObject.get("\$type")?.asString?.endsWith("savedFeedsPref") == true
                }
                if (v1 != null) {
                    val obj = v1.asJsonObject
                    val pinned = obj.getAsJsonArray("pinned")?.mapNotNull { it.asString } ?: emptyList()
                    val saved  = obj.getAsJsonArray("saved")?.mapNotNull { it.asString }  ?: emptyList()
                    feedUris.addAll((pinned + saved).filter { it.startsWith("at://") }.distinct())
                }
            }
        }

        val allFeeds = mutableListOf<BskyFeedInfo>()
        if (feedUris.isNotEmpty()) {
            feedUris.distinct().chunked(25).forEach { batch ->
                val batchResult = runCatching { api.getFeedGenerators("Bearer $token", batch) }
                val batchBody = batchResult.getOrNull()?.takeIf { it.isSuccessful }?.body()
                if (batchBody != null) {
                    batchBody.feeds.forEach { allFeeds.add(BskyFeedInfo(it.uri, it.displayName, it.avatar)) }
                } else {
                    // One bad URI shouldn't sink the whole batch — retry individually
                    batch.forEach { uri ->
                        runCatching { api.getFeedGenerators("Bearer $token", listOf(uri)) }
                            .getOrNull()?.body()?.feeds?.firstOrNull()?.let {
                                allFeeds.add(BskyFeedInfo(it.uri, it.displayName, it.avatar))
                            }
                    }
                }
            }
        }

        // Fallback: feeds the user created themself (only if they have no saved feeds at all)
        if (allFeeds.isEmpty()) {
            runCatching { api.getActorFeeds("Bearer $token", did, 30) }
                .getOrNull()?.body()?.feeds?.forEach {
                    allFeeds.add(BskyFeedInfo(it.uri, it.displayName, it.avatar))
                }
        }

        if (allFeeds.isEmpty() && prefsError != null) error(prefsError!!)

        allFeeds
    }

    suspend fun getAuthorFeed(token: String, actorDid: String, cursor: String? = null)
        : Result<Pair<List<MediaItem>, String?>> = runCatching {
        val resp = api.getAuthorFeed("Bearer $token", actorDid, 50, cursor)
        val body = resp.body() ?: error("AuthorFeed ${resp.code()}")
        Pair(body.feed.flatMap { parseFeedItem(it) }, body.cursor)
    }

    // ── Thread / Comments ─────────────────────────────────────────────────────

    suspend fun getPostThread(token: String, uri: String): Result<List<CommentItem>> = runCatching {
        val resp = api.getPostThread("Bearer $token", uri, 10)
        val body = resp.body() ?: error("Thread ${resp.code()}")
        (body.thread.replies ?: emptyList()).mapNotNull { view ->
            val post = view.post ?: return@mapNotNull null
            CommentItem(
                id                = post.cid,
                uri               = post.uri,
                cid               = post.cid,
                authorHandle      = post.author.handle,
                authorDisplayName = post.author.displayName ?: post.author.handle,
                authorAvatarUrl   = post.author.avatar,
                body              = post.record.text ?: "",
                createdAt         = post.record.createdAt ?: "",
                likeCount         = post.likeCount ?: 0,
                isLiked           = post.viewer?.like != null,
                likeUri           = post.viewer?.like
            )
        }
    }

    // ── Social Actions ────────────────────────────────────────────────────────

    suspend fun likePost(token: String, did: String, postUri: String, postCid: String): Result<String> =
        createRecord(token, did, "app.bsky.feed.like", mapOf(
            "\$type" to "app.bsky.feed.like",
            "subject" to mapOf("uri" to postUri, "cid" to postCid),
            "createdAt" to Instant.now().toString()
        ))

    suspend fun unlikePost(token: String, did: String, likeUri: String): Result<Unit> =
        deleteRecord(token, did, "app.bsky.feed.like", likeUri.rkey())

    suspend fun repostPost(token: String, did: String, postUri: String, postCid: String): Result<String> =
        createRecord(token, did, "app.bsky.feed.repost", mapOf(
            "\$type" to "app.bsky.feed.repost",
            "subject" to mapOf("uri" to postUri, "cid" to postCid),
            "createdAt" to Instant.now().toString()
        ))

    suspend fun unrepost(token: String, did: String, repostUri: String): Result<Unit> =
        deleteRecord(token, did, "app.bsky.feed.repost", repostUri.rkey())

    suspend fun followUser(token: String, did: String, targetDid: String): Result<String> =
        createRecord(token, did, "app.bsky.graph.follow", mapOf(
            "\$type" to "app.bsky.graph.follow",
            "subject" to targetDid,
            "createdAt" to Instant.now().toString()
        ))

    suspend fun unfollowUser(token: String, did: String, followUri: String): Result<Unit> =
        deleteRecord(token, did, "app.bsky.graph.follow", followUri.rkey())

    suspend fun replyToPost(
        token: String, did: String,
        rootUri: String, rootCid: String,
        parentUri: String, parentCid: String,
        text: String
    ): Result<String> = createRecord(token, did, "app.bsky.feed.post", mapOf(
        "\$type" to "app.bsky.feed.post",
        "text" to text,
        "reply" to mapOf(
            "root"   to mapOf("uri" to rootUri,   "cid" to rootCid),
            "parent" to mapOf("uri" to parentUri, "cid" to parentCid)
        ),
        "createdAt" to Instant.now().toString()
    ))

    suspend fun getUserLists(token: String, did: String): Result<List<BskyList>> = runCatching {
        val resp = api.getLists("Bearer $token", did, 100)
        val body = resp.body() ?: error("Lists ${resp.code()}: ${resp.message()}")
        body.lists
    }

    /** Returns the user's starter packs. To add a member, call addToList() using
     *  starterPack.record.list as the listUri — that's the underlying list. */
    suspend fun getUserStarterPacks(token: String, did: String): Result<List<BskyStarterPackView>> = runCatching {
        val resp = api.getActorStarterPacks("Bearer $token", did, 100)
        val body = resp.body() ?: error("StarterPacks ${resp.code()}: ${resp.message()}")
        body.starterPacks
    }

    suspend fun addToList(token: String, repoDid: String, listUri: String, targetDid: String): Result<String> =
        createRecord(token, repoDid, "app.bsky.graph.listitem", mapOf(
            "\$type" to "app.bsky.graph.listitem",
            "subject" to targetDid,
            "list" to listUri,
            "createdAt" to Instant.now().toString()
        ))

    suspend fun likeComment(token: String, did: String, commentUri: String, commentCid: String): Result<String> =
        likePost(token, did, commentUri, commentCid)

    suspend fun unlikeComment(token: String, did: String, likeUri: String): Result<Unit> =
        unlikePost(token, did, likeUri)

    // ── Helpers ───────────────────────────────────────────────────────────────

    private suspend fun createRecord(token: String, did: String, collection: String, record: Map<String, Any>): Result<String> = runCatching {
        val resp = api.createRecord("Bearer $token", BskyCreateRecordRequest(did, collection, record))
        resp.body()?.uri ?: error("CreateRecord ${resp.code()}")
    }

    private suspend fun deleteRecord(token: String, did: String, collection: String, rkey: String): Result<Unit> = runCatching {
        val resp = api.deleteRecord("Bearer $token", BskyDeleteRecordRequest(did, collection, rkey))
        if (!resp.isSuccessful) error("DeleteRecord ${resp.code()}")
    }

    private fun String.rkey() = this.substringAfterLast('/')

    private fun parseFeedItem(item: BskyFeedItem): List<MediaItem> {
        val post   = item.post
        val author = AuthorInfo(
            did          = post.author.did,
            handle       = post.author.handle,
            displayName  = post.author.displayName ?: post.author.handle,
            avatarUrl    = post.author.avatar,
            followingUri = post.author.viewer?.following,
            isFollowing  = post.author.viewer?.following != null
        )
        return when (val embed = post.embed) {
            null -> emptyList()
            else -> when {
                embed.type.contains("images") -> (embed.images ?: emptyList()).map { img ->
                    MediaItem(
                        id = "${post.cid}_${img.fullsize.hashCode()}", mediaUrl = img.fullsize,
                        thumbUrl = img.thumb, isVideo = false, postUri = post.uri, postCid = post.cid,
                        author = author, likeUri = post.viewer?.like, repostUri = post.viewer?.repost,
                        isLiked = post.viewer?.like != null, isReposted = post.viewer?.repost != null,
                        likeCount = post.likeCount ?: 0, replyCount = post.replyCount ?: 0,
                        repostCount = post.repostCount ?: 0, altText = img.alt ?: ""
                    )
                }
                embed.type.contains("video") -> listOf(
                    MediaItem(
                        id = post.cid, mediaUrl = embed.thumbnail ?: "", thumbUrl = embed.thumbnail ?: "",
                        isVideo = true, videoPlaylistUrl = embed.playlist, postUri = post.uri, postCid = post.cid,
                        author = author, likeUri = post.viewer?.like, repostUri = post.viewer?.repost,
                        isLiked = post.viewer?.like != null, isReposted = post.viewer?.repost != null,
                        likeCount = post.likeCount ?: 0, replyCount = post.replyCount ?: 0,
                        repostCount = post.repostCount ?: 0
                    )
                )
                embed.type.contains("recordWithMedia") ->
                    embed.media?.let { parseFeedItem(item.copy(post = post.copy(embed = it))) } ?: emptyList()
                else -> emptyList()
            }
        }
    }
}
