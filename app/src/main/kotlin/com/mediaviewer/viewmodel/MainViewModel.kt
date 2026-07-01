package com.mediaviewer.viewmodel

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mediaviewer.model.*
import com.mediaviewer.repository.BlueskyRepository
import com.mediaviewer.repository.E621Repository
import com.mediaviewer.util.PreferencesManager
import com.mediaviewer.worker.DownloadWorker
import com.mediaviewer.worker.urlToDownloadInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs     = PreferencesManager(application)
    private val bskyRepo  = BlueskyRepository()
    private val e621Repo  = E621Repository()

    // ── Session ───────────────────────────────────────────────────────────────
    private val _bskyLoggedIn = MutableStateFlow(false)
    val bskyLoggedIn: StateFlow<Boolean> = _bskyLoggedIn

    private val _e621LoggedIn = MutableStateFlow(false)
    val e621LoggedIn: StateFlow<Boolean> = _e621LoggedIn

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private var bskyToken        = ""
    private var bskyRefreshToken = ""
    private val _bskyDid = MutableStateFlow("")
    val bskyDid: StateFlow<String> = _bskyDid
    var bskyHandle               = ""
    var e621Username             = ""
    var e621ApiKey               = ""

    // ── Settings ──────────────────────────────────────────────────────────────
    private val _reducedAnimations = MutableStateFlow(false)
    val reducedAnimations: StateFlow<Boolean> = _reducedAnimations

    private val _downloadOnLike = MutableStateFlow(false)
    val downloadOnLike: StateFlow<Boolean> = _downloadOnLike

    private val _downloadProgress = MutableStateFlow<DownloadProgress?>(null)
    val downloadProgress: StateFlow<DownloadProgress?> = _downloadProgress

    @Volatile private var cancelDownloadFlag = false

    // ── App Mode / Screen ─────────────────────────────────────────────────────
    private val _appMode     = MutableStateFlow(AppMode.BLUESKY)
    val appMode: StateFlow<AppMode> = _appMode

    private val _screenState = MutableStateFlow(ScreenState.SETTINGS)
    val screenState: StateFlow<ScreenState> = _screenState

    // Track swipe direction for animations (1=next/down, -1=prev/up, 0=other)
    private val _navDirection = MutableStateFlow(0)
    val navDirection: StateFlow<Int> = _navDirection

    // ── List picker (shown after following someone) ───────────────────────────
    private val _listPickerTargetDid = MutableStateFlow<String?>(null)
    val listPickerTargetDid: StateFlow<String?> = _listPickerTargetDid

    private val _userLists = MutableStateFlow<List<BskyList>>(emptyList())
    val userLists: StateFlow<List<BskyList>> = _userLists

    private val _userStarterPacks = MutableStateFlow<List<BskyStarterPackView>>(emptyList())
    val userStarterPacks: StateFlow<List<BskyStarterPackView>> = _userStarterPacks

    private val _userListsLoading = MutableStateFlow(false)
    val userListsLoading: StateFlow<Boolean> = _userListsLoading

    // ── Feed ──────────────────────────────────────────────────────────────────
    private val _mediaItems = MutableStateFlow<List<MediaItem>>(emptyList())
    val mediaItems: StateFlow<List<MediaItem>> = _mediaItems

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex

    private var feedCursor: String?  = null
    private var isLoadingMore        = false

    private val _availableFeeds = MutableStateFlow<List<BskyFeedInfo>>(emptyList())
    val availableFeeds: StateFlow<List<BskyFeedInfo>> = _availableFeeds

    private val _selectedFeedUri = MutableStateFlow<String?>(null)
    val selectedFeedUri: StateFlow<String?> = _selectedFeedUri

    // ── e621 ──────────────────────────────────────────────────────────────────
    private val _e621SearchTags = MutableStateFlow("order:hot")
    val e621SearchTags: StateFlow<String> = _e621SearchTags

    private var e621Page              = 1
    private var e621ShowingFavorites  = false

    // ── Comments ──────────────────────────────────────────────────────────────
    private val _comments = MutableStateFlow<List<CommentItem>>(emptyList())
    val comments: StateFlow<List<CommentItem>> = _comments

    private val _commentsLoading = MutableStateFlow(false)
    val commentsLoading: StateFlow<Boolean> = _commentsLoading

    // ── Derived ───────────────────────────────────────────────────────────────
    val currentItem: StateFlow<MediaItem?> = combine(_mediaItems, _currentIndex) { items, idx ->
        items.getOrNull(idx)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // ── Init ──────────────────────────────────────────────────────────────────
    init {
        viewModelScope.launch {
            prefs.reducedAnimations.collect { _reducedAnimations.value = it }
        }
        viewModelScope.launch {
            prefs.downloadOnLike.collect { _downloadOnLike.value = it }
        }
        viewModelScope.launch {
            // Restore session on launch
            val accessJwt   = prefs.bskyAccessJwt.first()
            val refreshJwt  = prefs.bskyRefreshJwt.first()
            val did         = prefs.bskyDid.first()
            val handle      = prefs.bskyHandle.first()
            val e621User    = prefs.e621Username.first()
            val e621Key     = prefs.e621ApiKey.first()
            val lastMode    = prefs.lastMode.first()
            val lastFeedUri = prefs.lastFeedUri.first()
            val lastE621Tags = prefs.lastE621Tags.first()

            if (!lastE621Tags.isNullOrBlank()) _e621SearchTags.value = lastE621Tags
            _selectedFeedUri.value = lastFeedUri

            // Restore e621 credentials
            if (!e621User.isNullOrBlank() && !e621Key.isNullOrBlank()) {
                e621Username = e621User
                e621ApiKey   = e621Key
                _e621LoggedIn.value = true
            }

            // Restore Bluesky session
            if (!accessJwt.isNullOrBlank() && did != null && handle != null) {
                bskyToken        = accessJwt
                bskyRefreshToken = refreshJwt ?: ""
                _bskyDid.value   = did
                bskyHandle       = handle
                _bskyLoggedIn.value = true
            }

            // Restore last mode and go to feed if logged in for that mode
            if (lastMode == "E621" && _e621LoggedIn.value) {
                _appMode.value = AppMode.E621
                _screenState.value = ScreenState.FEED
                loadE621Posts()
            } else if (_bskyLoggedIn.value) {
                _appMode.value = AppMode.BLUESKY
                _screenState.value = ScreenState.FEED
                loadFeed()
                loadAvailableFeeds()
            }
            // else: stay on SETTINGS
        }
    }

    // ── Auth ──────────────────────────────────────────────────────────────────

    fun loginBluesky(identifier: String, password: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            bskyRepo.login(identifier, password)
                .onSuccess { session ->
                    bskyToken        = session.accessJwt
                    bskyRefreshToken = session.refreshJwt
_bskyDid.value          = session.did
                    bskyHandle       = session.handle
                    prefs.saveBskySession(session.accessJwt, session.refreshJwt, session.did, session.handle)
                    _bskyLoggedIn.value = true
                    _appMode.value = AppMode.BLUESKY
                    prefs.setLastMode("BLUESKY")
                    _screenState.value = ScreenState.FEED
                    loadFeed()
                    loadAvailableFeeds()
                }
                .onFailure { _errorMessage.value = it.message ?: "Login failed" }
            _isLoading.value = false
        }
    }

    fun logoutBluesky() {
        viewModelScope.launch {
            prefs.clearBskySession()
            bskyToken = ""; bskyRefreshToken = ""; _bskyDid.value = ""; bskyHandle = ""
            _bskyLoggedIn.value = false
            if (_appMode.value == AppMode.BLUESKY) {
                _mediaItems.value = emptyList()
                _screenState.value = ScreenState.SETTINGS
            }
        }
    }

    fun saveE621Credentials(username: String, apiKey: String) {
        if (username.isBlank() || apiKey.isBlank()) return
        viewModelScope.launch {
            e621Username = username
            e621ApiKey   = apiKey
            prefs.saveE621Credentials(username, apiKey)
            _e621LoggedIn.value = true
            _appMode.value = AppMode.E621
            prefs.setLastMode("E621")
            _screenState.value = ScreenState.FEED
            loadE621Posts()
        }
    }

    fun logoutE621() {
        viewModelScope.launch {
            prefs.clearE621Credentials()
            e621Username = ""; e621ApiKey = ""
            _e621LoggedIn.value = false
            if (_appMode.value == AppMode.E621) {
                _mediaItems.value = emptyList()
                _screenState.value = ScreenState.SETTINGS
            }
        }
    }

    // ── Feed Loading ──────────────────────────────────────────────────────────

    /** Attempts to refresh the Bluesky access token. Returns true if successful. */
    private suspend fun refreshBskyTokenIfPossible(): Boolean {
        if (bskyRefreshToken.isBlank()) return false
        val result = bskyRepo.refreshToken(bskyRefreshToken)
        return result.fold(
            onSuccess = { refreshed ->
                bskyToken        = refreshed.accessJwt
                bskyRefreshToken = refreshed.refreshJwt
                _bskyDid.value   = refreshed.did
                bskyHandle       = refreshed.handle
                prefs.saveBskySession(refreshed.accessJwt, refreshed.refreshJwt, refreshed.did, refreshed.handle)
                true
            },
            onFailure = {
                // Refresh token itself is dead — force re-login
                prefs.clearBskySession()
                _bskyLoggedIn.value = false
                _screenState.value = ScreenState.SETTINGS
                false
            }
        )
    }

    private fun isAuthError(message: String?): Boolean {
        if (message == null) return false
        return message.contains("400") || message.contains("401") || message.contains("ExpiredToken", true) || message.contains("InvalidToken", true)
    }

    fun loadFeed(reset: Boolean = true) {
        if (_appMode.value == AppMode.E621) { loadE621Posts(reset); return }
        if (!_bskyLoggedIn.value) return
        viewModelScope.launch(Dispatchers.IO) {
            if (reset) { _isLoading.value = true; feedCursor = null; _currentIndex.value = 0 }
            if (isLoadingMore && !reset) return@launch
            isLoadingMore = true

            suspend fun attempt(): Result<Pair<List<MediaItem>, String?>> {
                val feedUri = _selectedFeedUri.value
                return if (feedUri == null) bskyRepo.getTimeline(bskyToken, feedCursor)
                       else bskyRepo.getFeed(bskyToken, feedUri, feedCursor)
            }

            var result = attempt()
            if (result.isFailure && isAuthError(result.exceptionOrNull()?.message)) {
                if (refreshBskyTokenIfPossible()) result = attempt()
            }

            result.onSuccess { (items, cursor) ->
                feedCursor = cursor
                _mediaItems.value = if (reset) items else _mediaItems.value + items
            }.onFailure { _errorMessage.value = it.message }
            _isLoading.value = false
            isLoadingMore = false
        }
    }

    fun loadMore() {
        if (feedCursor == null || isLoadingMore) return
        loadFeed(reset = false)
    }

    fun loadAvailableFeeds() {
        if (!_bskyLoggedIn.value) return
        viewModelScope.launch(Dispatchers.IO) {
            var result = bskyRepo.getSavedFeeds(bskyToken, _bskyDid.value)
            if (result.isFailure && isAuthError(result.exceptionOrNull()?.message)) {
                if (refreshBskyTokenIfPossible()) result = bskyRepo.getSavedFeeds(bskyToken, _bskyDid.value)
            }
            result.onSuccess { _availableFeeds.value = it }
                  .onFailure { _errorMessage.value = "Feeds: ${it.message}" }
        }
    }

    fun showAuthorFeed(did: String) {
        if (!_bskyLoggedIn.value) return
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _currentIndex.value = 0
            var result = bskyRepo.getAuthorFeed(bskyToken, did)
            if (result.isFailure && isAuthError(result.exceptionOrNull()?.message)) {
                if (refreshBskyTokenIfPossible()) result = bskyRepo.getAuthorFeed(bskyToken, did)
            }
            result.onSuccess { (items, _) ->
                _mediaItems.value = items
                _screenState.value = ScreenState.FEED
            }.onFailure { _errorMessage.value = it.message }
            _isLoading.value = false
        }
    }

    fun selectFeed(uri: String?) {
        _selectedFeedUri.value = uri
        viewModelScope.launch { prefs.setLastFeedUri(uri) }
        loadFeed(reset = true)
    }

    fun loadE621Posts(reset: Boolean = true) {
        if (!_e621LoggedIn.value) return
        viewModelScope.launch(Dispatchers.IO) {
            if (reset) { e621Page = 1; _isLoading.value = true; _currentIndex.value = 0 }
            val result = if (e621ShowingFavorites)
                e621Repo.getFavorites(e621Username, e621ApiKey, e621Page)
            else
                e621Repo.searchPosts(e621Username, e621ApiKey, _e621SearchTags.value, e621Page)
            result.onSuccess { items ->
                _mediaItems.value = if (reset) items else _mediaItems.value + items
                e621Page++
            }.onFailure { _errorMessage.value = it.message }
            _isLoading.value = false
        }
    }

    fun setE621SearchTags(tags: String) {
        _e621SearchTags.value = tags
        viewModelScope.launch { prefs.setLastE621Tags(tags) }
    }

    /** Replace search with a single tag and execute the search immediately (tag tap). */
    fun searchSingleTag(tag: String) {
        e621ShowingFavorites = false
        _e621SearchTags.value = tag
        viewModelScope.launch { prefs.setLastE621Tags(tag) }
        loadE621Posts(reset = true)
        _screenState.value = ScreenState.FEED
    }

    /** Append (or exclude with -) a tag to the current search without executing it. */
    fun addTagToSearch(tag: String, exclude: Boolean) {
        val token = if (exclude) "-$tag" else tag
        val current = _e621SearchTags.value.trim()
        val parts = current.split(Regex("\\s+")).filter { it.isNotBlank() }.toMutableList()
        // Remove any existing occurrence (with or without the opposite sign) before adding
        parts.removeAll { it == tag || it == "-$tag" }
        parts.add(token)
        _e621SearchTags.value = parts.joinToString(" ")
        viewModelScope.launch { prefs.setLastE621Tags(_e621SearchTags.value) }
    }

    fun searchE621() {
        e621ShowingFavorites = false
        loadE621Posts(reset = true)
    }

    fun showE621Favorites() {
        e621ShowingFavorites = true
        loadE621Posts(reset = true)
    }

    fun showBskyLikes() {
        if (!_bskyLoggedIn.value) return
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _currentIndex.value = 0
            bskyRepo.getActorLikes(bskyToken, _bskyDid.value)
                .onSuccess { (items, _) ->
                    _mediaItems.value = items
                    _screenState.value = ScreenState.FEED
                }
                .onFailure { _errorMessage.value = it.message }
            _isLoading.value = false
        }
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    fun setMode(mode: AppMode) {
        _appMode.value = mode
        viewModelScope.launch { prefs.setLastMode(mode.name) }
        if (mode == AppMode.E621) {
            if (_e621LoggedIn.value) loadE621Posts()
            else _screenState.value = ScreenState.SETTINGS
        } else {
            if (_bskyLoggedIn.value) loadFeed()
            else _screenState.value = ScreenState.SETTINGS
        }
    }

    fun setScreen(screen: ScreenState) {
        _navDirection.value = when {
            screen == ScreenState.COMMENTS -> 1
            screen == ScreenState.FEED && _screenState.value == ScreenState.COMMENTS -> -1
            screen == ScreenState.SETTINGS -> -1
            screen == ScreenState.FEED && _screenState.value == ScreenState.SETTINGS -> 1
            else -> 0
        }
        _screenState.value = screen
        if (screen == ScreenState.COMMENTS) loadComments()
    }

    fun navigateNext() {
        val next = _currentIndex.value + 1
        if (next < _mediaItems.value.size) {
            _navDirection.value = 1
            _currentIndex.value = next
            if (next >= _mediaItems.value.size - 5) loadMore()
        }
    }

    fun navigatePrev() {
        val prev = _currentIndex.value - 1
        if (prev >= 0) {
            _navDirection.value = -1
            _currentIndex.value = prev
        }
    }

    fun navigateTo(index: Int) {
        if (index in _mediaItems.value.indices) {
            _navDirection.value = if (index > _currentIndex.value) 1 else -1
            _currentIndex.value = index
            _screenState.value  = ScreenState.FEED
        }
    }

    // ── Social Actions (optimistic updates) ───────────────────────────────────

    fun toggleLike() {
        val item = currentItem.value ?: return
        if (_appMode.value == AppMode.BLUESKY) {
            if (item.isLiked) {
                // Optimistic unlike
                updateCurrentItem { it.copy(isLiked = false, likeUri = null, likeCount = (it.likeCount - 1).coerceAtLeast(0)) }
                viewModelScope.launch(Dispatchers.IO) {
                    bskyRepo.unlikePost(bskyToken, _bskyDid.value, item.likeUri ?: return@launch)
                        .onFailure { updateCurrentItem { it.copy(isLiked = true, likeUri = item.likeUri, likeCount = item.likeCount) } }
                }
            } else {
                // Optimistic like
                updateCurrentItem { it.copy(isLiked = true, likeCount = it.likeCount + 1) }
                viewModelScope.launch(Dispatchers.IO) {
                    bskyRepo.likePost(bskyToken, _bskyDid.value, item.postUri, item.postCid)
                        .onSuccess { uri ->
                            updateCurrentItem { it.copy(likeUri = uri) }
                            if (_downloadOnLike.value) enqueueDownload(item)
                        }
                        .onFailure { updateCurrentItem { it.copy(isLiked = false, likeCount = item.likeCount) } }
                }
            }
        }
    }

    fun toggleRepost() {
        val item = currentItem.value ?: return
        if (_appMode.value != AppMode.BLUESKY) return
        if (item.isReposted) {
            updateCurrentItem { it.copy(isReposted = false, repostUri = null, repostCount = (it.repostCount - 1).coerceAtLeast(0)) }
            viewModelScope.launch(Dispatchers.IO) {
                bskyRepo.unrepost(bskyToken, _bskyDid.value, item.repostUri ?: return@launch)
                    .onFailure { updateCurrentItem { it.copy(isReposted = true, repostUri = item.repostUri, repostCount = item.repostCount) } }
            }
        } else {
            updateCurrentItem { it.copy(isReposted = true, repostCount = it.repostCount + 1) }
            viewModelScope.launch(Dispatchers.IO) {
                bskyRepo.repostPost(bskyToken, _bskyDid.value, item.postUri, item.postCid)
                    .onSuccess { uri -> updateCurrentItem { it.copy(repostUri = uri) } }
                    .onFailure { updateCurrentItem { it.copy(isReposted = false, repostCount = item.repostCount) } }
            }
        }
    }

    fun toggleBookmark() {
        val item = currentItem.value ?: return
        if (_appMode.value == AppMode.E621) {
            val pid = item.e621PostId ?: return
            if (item.isBookmarked) {
                updateCurrentItem { it.copy(isBookmarked = false) }
                viewModelScope.launch(Dispatchers.IO) {
                    e621Repo.removeFavorite(e621Username, e621ApiKey, pid)
                        .onFailure { updateCurrentItem { it.copy(isBookmarked = true) } }
                }
            } else {
                updateCurrentItem { it.copy(isBookmarked = true) }
                viewModelScope.launch(Dispatchers.IO) {
                    e621Repo.addFavorite(e621Username, e621ApiKey, pid)
                        .onSuccess { if (_downloadOnLike.value) enqueueDownload(item) }
                        .onFailure { updateCurrentItem { it.copy(isBookmarked = false) } }
                }
            }
        } else {
            updateCurrentItem { it.copy(isBookmarked = !it.isBookmarked) }
        }
    }

    fun e621Vote(vote: Int) {
        val item = currentItem.value ?: return
        val pid  = item.e621PostId ?: return
        val newVote = if (item.e621UserVote == vote) 0 else vote
        updateCurrentItem { it.copy(e621UserVote = newVote) }
        viewModelScope.launch(Dispatchers.IO) {
            e621Repo.votePost(e621Username, e621ApiKey, pid, if (newVote == 0) (vote * -1) else newVote)
                .onFailure { updateCurrentItem { it.copy(e621UserVote = item.e621UserVote) } }
        }
    }

    fun toggleFollow() {
        val item   = currentItem.value ?: return
        val author = item.author
        if (_appMode.value != AppMode.BLUESKY) return
        if (author.isFollowing) {
            // Unfollow
            updateCurrentItemAuthor { it.copy(isFollowing = false, followingUri = null) }
            viewModelScope.launch(Dispatchers.IO) {
                bskyRepo.unfollowUser(bskyToken, _bskyDid.value, author.followingUri ?: return@launch)
                    .onFailure { updateCurrentItemAuthor { it.copy(isFollowing = true, followingUri = author.followingUri) } }
            }
        } else {
            // Follow — then show "add to list" picker
            updateCurrentItemAuthor { it.copy(isFollowing = true) }
            viewModelScope.launch(Dispatchers.IO) {
                bskyRepo.followUser(bskyToken, _bskyDid.value, author.did)
                    .onSuccess { uri ->
                        updateCurrentItemAuthor { it.copy(followingUri = uri) }
                        openListPicker(author.did)
                    }
                    .onFailure { updateCurrentItemAuthor { it.copy(isFollowing = false) } }
            }
        }
    }

    private fun openListPicker(targetDid: String) {
        _listPickerTargetDid.value = targetDid
        viewModelScope.launch(Dispatchers.IO) {
            _userListsLoading.value = true
            // Fetch both in parallel
            val listJob = launch {
                bskyRepo.getUserLists(bskyToken, _bskyDid.value)
                    .onSuccess { _userLists.value = it }
            }
            val packJob = launch {
                bskyRepo.getUserStarterPacks(bskyToken, _bskyDid.value)
                    .onSuccess { _userStarterPacks.value = it }
            }
            listJob.join(); packJob.join()
            _userListsLoading.value = false
        }
    }

    fun dismissListPicker() {
        _listPickerTargetDid.value = null
    }

    fun addAccountToList(listUri: String) {
        val targetDid = _listPickerTargetDid.value ?: return
        _listPickerTargetDid.value = null // close immediately
        viewModelScope.launch(Dispatchers.IO) {
            bskyRepo.addToList(bskyToken, _bskyDid.value, listUri, targetDid)
                .onSuccess { showToast("Added to list") }
                .onFailure { _errorMessage.value = "Add to list failed: ${it.message}" }
        }
    }

    fun downloadCurrentItem() {
        val item = currentItem.value ?: return
        enqueueDownload(item)
    }

    // ── Comments ──────────────────────────────────────────────────────────────

    private fun loadComments() {
        val item = currentItem.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _commentsLoading.value = true
            _comments.value = emptyList()
            if (_appMode.value == AppMode.BLUESKY)
                bskyRepo.getPostThread(bskyToken, item.postUri)
                    .onSuccess { _comments.value = it }
                    .onFailure { _errorMessage.value = it.message }
            else {
                val pid = item.e621PostId ?: return@launch
                e621Repo.getComments(e621Username, e621ApiKey, pid)
                    .onSuccess { _comments.value = it }
                    .onFailure { _errorMessage.value = it.message }
            }
            _commentsLoading.value = false
        }
    }

    fun postComment(text: String) {
        val item = currentItem.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            if (_appMode.value == AppMode.BLUESKY)
                bskyRepo.replyToPost(bskyToken, _bskyDid.value,
                    item.postUri, item.postCid, item.postUri, item.postCid, text)
                    .onSuccess { loadComments() }
                    .onFailure { _errorMessage.value = it.message }
            else {
                e621Repo.createComment(e621Username, e621ApiKey, item.e621PostId ?: return@launch, text)
                    .onSuccess { loadComments() }
                    .onFailure { _errorMessage.value = it.message }
            }
        }
    }

    fun likeComment(comment: CommentItem) {
        if (_appMode.value != AppMode.BLUESKY) return
        val newLiked = !comment.isLiked
        updateComment(comment.id) { it.copy(isLiked = newLiked, likeCount = if (newLiked) it.likeCount + 1 else (it.likeCount - 1).coerceAtLeast(0)) }
        viewModelScope.launch(Dispatchers.IO) {
            if (comment.isLiked) {
                bskyRepo.unlikeComment(bskyToken, _bskyDid.value, comment.likeUri ?: return@launch)
                    .onFailure { updateComment(comment.id) { it.copy(isLiked = comment.isLiked, likeCount = comment.likeCount) } }
            } else {
                bskyRepo.likeComment(bskyToken, _bskyDid.value, comment.uri, comment.cid)
                    .onSuccess { uri -> updateComment(comment.id) { it.copy(likeUri = uri) } }
                    .onFailure { updateComment(comment.id) { it.copy(isLiked = comment.isLiked, likeCount = comment.likeCount) } }
            }
        }
    }

    fun voteComment(comment: CommentItem, vote: Int) {
        if (_appMode.value != AppMode.E621) return
        val newVote = if (comment.e621UserVote == vote) 0 else vote
        updateComment(comment.id) { it.copy(e621UserVote = newVote) }
        viewModelScope.launch(Dispatchers.IO) {
            val id = comment.id.toIntOrNull() ?: return@launch
            e621Repo.voteComment(e621Username, e621ApiKey, id, if (newVote == 0) vote * -1 else newVote)
                .onFailure { updateComment(comment.id) { it.copy(e621UserVote = comment.e621UserVote) } }
        }
    }

    // ── Downloads ─────────────────────────────────────────────────────────────

    fun setDownloadOnLike(enabled: Boolean) {
        viewModelScope.launch { prefs.setDownloadOnLike(enabled) }
    }

    fun setReducedAnimations(enabled: Boolean) {
        viewModelScope.launch { prefs.setReducedAnimations(enabled) }
    }

    fun downloadAllLiked() {
        if (_downloadProgress.value?.isRunning == true) return
        cancelDownloadFlag = false
        if (_appMode.value == AppMode.BLUESKY) downloadAllBskyLiked()
        else downloadAllE621Favorites()
    }

    fun cancelDownloadAll() {
        cancelDownloadFlag = true
        _downloadProgress.value = _downloadProgress.value?.copy(isRunning = false)
    }

    private fun downloadAllBskyLiked() {
        viewModelScope.launch(Dispatchers.IO) {
            _downloadProgress.value = DownloadProgress(0, true)
            var cursor: String? = null
            var total = 0
            do {
                if (cancelDownloadFlag) break
                bskyRepo.getActorLikes(bskyToken, _bskyDid.value, cursor)
                    .onSuccess { (items, nextCursor) ->
                        items.forEach { if (!cancelDownloadFlag) { enqueueDownload(it); total++ } }
                        _downloadProgress.value = DownloadProgress(total, !cancelDownloadFlag)
                        cursor = nextCursor
                    }
                    .onFailure { cursor = null }
            } while (cursor != null && !cancelDownloadFlag)
            _downloadProgress.value = DownloadProgress(total, false)
        }
    }

    private fun downloadAllE621Favorites() {
        viewModelScope.launch(Dispatchers.IO) {
            _downloadProgress.value = DownloadProgress(0, true)
            var page  = 1
            var total = 0
            while (!cancelDownloadFlag) {
                val items = e621Repo.getFavorites(e621Username, e621ApiKey, page)
                    .getOrNull() ?: break
                if (items.isEmpty()) break
                items.forEach { if (!cancelDownloadFlag) { enqueueDownload(it); total++ } }
                _downloadProgress.value = DownloadProgress(total, !cancelDownloadFlag)
                page++
            }
            _downloadProgress.value = DownloadProgress(total, false)
        }
    }

    private fun enqueueDownload(item: MediaItem) {
        val (url, filename, mimeType) = urlToDownloadInfo(item.mediaUrl, item.id)
        DownloadWorker.enqueue(getApplication(), url, filename, mimeType, item.id)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun updateCurrentItem(transform: (MediaItem) -> MediaItem) {
        val idx  = _currentIndex.value
        val list = _mediaItems.value.toMutableList()
        val item = list.getOrNull(idx) ?: return
        list[idx] = transform(item)
        _mediaItems.value = list
    }

    private fun updateCurrentItemAuthor(transform: (AuthorInfo) -> AuthorInfo) {
        updateCurrentItem { it.copy(author = transform(it.author)) }
    }

    private fun updateComment(commentId: String, transform: (CommentItem) -> CommentItem) {
        _comments.value = _comments.value.map { if (it.id == commentId) transform(it) else it }
    }

    fun clearError() { _errorMessage.value = null }

    private fun showToast(msg: String) {
        viewModelScope.launch(Dispatchers.Main) {
            Toast.makeText(getApplication(), msg, Toast.LENGTH_SHORT).show()
        }
    }
}
