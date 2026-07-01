package com.mediaviewer.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.mediaviewer.model.*
import com.mediaviewer.ui.theme.*
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.*

private val SWIPE_ANIM = tween<IntOffset>(200, easing = FastOutSlowInEasing)
private val FADE_ANIM  = tween<Float>(150)

// ─── Quick action enum ────────────────────────────────────────────────────────

private enum class QuickAction { TOP, RIGHT, BOTTOM, LEFT }

private fun getHoveredAction(fingerPos: Offset, center: Offset): QuickAction? {
    val dx = fingerPos.x - center.x; val dy = fingerPos.y - center.y
    if (sqrt(dx * dx + dy * dy) < 40f) return null
    val angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble()))
    return when {
        angle < -135 || angle > 135 -> QuickAction.LEFT
        angle < -45                 -> QuickAction.TOP
        angle < 45                  -> QuickAction.RIGHT
        else                        -> QuickAction.BOTTOM
    }
}

// ─── Root ─────────────────────────────────────────────────────────────────────

@Composable
fun MainFeedScreen(
    mediaItems: List<MediaItem>,
    currentIndex: Int,
    currentItem: MediaItem?,
    screenState: ScreenState,
    appMode: AppMode,
    navDirection: Int,
    reducedAnimations: Boolean,
    availableFeeds: List<BskyFeedInfo>,
    selectedFeedUri: String?,
    comments: List<CommentItem>,
    commentsLoading: Boolean,
    downloadOnLike: Boolean,
    downloadProgress: DownloadProgress?,
    e621SearchTags: String,
    isLoading: Boolean,
    bskyLoggedIn: Boolean,
    e621LoggedIn: Boolean,
    bskyHandle: String,
    e621Username: String,
    errorMessage: String?,
    onNavigateNext: () -> Unit,
    onNavigatePrev: () -> Unit,
    onNavigateTo: (Int) -> Unit,
    onSetScreen: (ScreenState) -> Unit,
    onToggleLike: () -> Unit,
    onToggleRepost: () -> Unit,
    onToggleBookmark: () -> Unit,
    onToggleFollow: () -> Unit,
    onE621Vote: (Int) -> Unit,
    onPostComment: (String) -> Unit,
    onLikeComment: (CommentItem) -> Unit,
    onVoteComment: (CommentItem, Int) -> Unit,
    onSelectFeed: (String?) -> Unit,
    onToggleDownloadOnLike: (Boolean) -> Unit,
    onDownloadAllLiked: () -> Unit,
    onCancelDownload: () -> Unit,
    onShowLikes: () -> Unit,
    onToggleReducedAnimations: (Boolean) -> Unit,
    onLoginBluesky: (String, String) -> Unit,
    onLogoutBluesky: () -> Unit,
    onSaveE621Credentials: (String, String) -> Unit,
    onLogoutE621: () -> Unit,
    onSearchE621: (String) -> Unit,
    onShowE621Favorites: () -> Unit,
    onSwipeToMode: (AppMode) -> Unit,
    onLoadMore: () -> Unit,
    onDownloadCurrent: () -> Unit,
    onRefresh: () -> Unit,
    onTapAuthor: (MediaItem) -> Unit,
    onTagClick: (String) -> Unit,
    onTagAdd: (String) -> Unit,
    onTagExclude: (String) -> Unit
) {
    val context = LocalContext.current

    Box(Modifier.fillMaxSize().background(OledBlack)) {
        AnimatedContent(
            targetState = screenState,
            transitionSpec = {
                if (reducedAnimations) EnterTransition.None togetherWith ExitTransition.None
                else when {
                    targetState == ScreenState.SETTINGS ->
                        slideInVertically(tween(220, easing = FastOutSlowInEasing)) { -it } togetherWith
                        slideOutVertically(tween(220, easing = FastOutSlowInEasing)) { it }
                    initialState == ScreenState.SETTINGS ->
                        slideInVertically(tween(220, easing = FastOutSlowInEasing)) { it } togetherWith
                        slideOutVertically(tween(220, easing = FastOutSlowInEasing)) { -it }
                    // Feed → Comments: media "shrinks up" feel via scale+fade rather than a full-screen slide
                    targetState == ScreenState.COMMENTS ->
                        (fadeIn(tween(180)) + scaleIn(tween(220, easing = FastOutSlowInEasing), initialScale = 0.92f, transformOrigin = TransformOrigin(0.5f, 0f))) togetherWith
                        (fadeOut(tween(140)) + scaleOut(tween(180, easing = FastOutSlowInEasing), targetScale = 0.85f, transformOrigin = TransformOrigin(0.5f, 0f)))
                    initialState == ScreenState.COMMENTS ->
                        (fadeIn(tween(180)) + scaleIn(tween(220, easing = FastOutSlowInEasing), initialScale = 0.92f)) togetherWith
                        (fadeOut(tween(140)) + scaleOut(tween(180, easing = FastOutSlowInEasing), targetScale = 0.92f, transformOrigin = TransformOrigin(0.5f, 0f)))
                    else -> fadeIn(FADE_ANIM) togetherWith fadeOut(FADE_ANIM)
                }
            },
            label = "screen"
        ) { state ->
            when (state) {
                ScreenState.FEED -> FeedView(
                    mediaItems        = mediaItems,
                    currentIndex      = currentIndex,
                    currentItem       = currentItem,
                    appMode           = appMode,
                    isLoading         = isLoading,
                    reducedAnimations = reducedAnimations,
                    onSwipeLeft       = onNavigateNext,
                    onSwipeRight      = onNavigatePrev,
                    onSwipeUp         = { onSetScreen(ScreenState.COMMENTS) },
                    onSwipeDown       = { onSetScreen(ScreenState.SETTINGS) },
                    onPinchToGrid     = { onSetScreen(ScreenState.GRID) },
                    onDoubleTap       = { haptic(context); if (appMode == AppMode.BLUESKY) onToggleLike() else onToggleBookmark() },
                    onToggleLike      = onToggleLike,
                    onToggleRepost    = onToggleRepost,
                    onToggleBookmark  = onToggleBookmark,
                    onToggleFollow    = onToggleFollow,
                    onE621Vote        = onE621Vote,
                    onDownload        = onDownloadCurrent,
                    onTapAuthor       = onTapAuthor
                )
                ScreenState.COMMENTS -> CommentsSheet(
                    currentItem     = currentItem,
                    comments        = comments,
                    commentsLoading = commentsLoading,
                    appMode         = appMode,
                    onPostComment   = onPostComment,
                    onLikeComment   = onLikeComment,
                    onVoteComment   = onVoteComment,
                    onSwipeDown     = { onSetScreen(ScreenState.FEED) },
                    onTagClick      = onTagClick,
                    onTagAdd        = onTagAdd,
                    onTagExclude    = onTagExclude
                )
                ScreenState.SETTINGS -> SettingsSheet(
                    appMode                   = appMode,
                    bskyLoggedIn              = bskyLoggedIn,
                    e621LoggedIn              = e621LoggedIn,
                    bskyHandle                = bskyHandle,
                    e621Username              = e621Username,
                    availableFeeds            = availableFeeds,
                    selectedFeedUri           = selectedFeedUri,
                    downloadOnLike            = downloadOnLike,
                    downloadProgress          = downloadProgress,
                    reducedAnimations         = reducedAnimations,
                    e621SearchTags            = e621SearchTags,
                    isLoading                 = isLoading,
                    onLoginBluesky            = onLoginBluesky,
                    onLogoutBluesky           = onLogoutBluesky,
                    onSaveE621Credentials     = onSaveE621Credentials,
                    onLogoutE621              = onLogoutE621,
                    onSelectFeed              = { uri -> onSelectFeed(uri); onSetScreen(ScreenState.FEED) },
                    onToggleDownloadOnLike    = onToggleDownloadOnLike,
                    onDownloadAllLiked        = onDownloadAllLiked,
                    onCancelDownload          = onCancelDownload,
                    onShowLikes               = { onShowLikes(); onSetScreen(ScreenState.FEED) },
                    onToggleReducedAnimations = onToggleReducedAnimations,
                    onSearchE621              = { tags -> onSearchE621(tags); onSetScreen(ScreenState.FEED) },
                    onShowE621Favorites       = { onShowE621Favorites(); onSetScreen(ScreenState.FEED) },
                    onSwitchMode              = onSwipeToMode,
                    onSwipeToFeed             = { onSetScreen(ScreenState.FEED) }
                )
                ScreenState.GRID -> GridScreen(
                    items           = mediaItems,
                    currentIndex    = currentIndex,
                    appMode         = appMode,
                    availableFeeds  = availableFeeds,
                    selectedFeedUri = selectedFeedUri,
                    e621SearchTags  = e621SearchTags,
                    onItemClick     = { idx -> onNavigateTo(idx) },
                    onLoadMore      = onLoadMore,
                    onSelectFeed    = onSelectFeed,
                    onSearchE621    = onSearchE621,
                    onRefresh       = onRefresh
                )
            }
        }

        if (errorMessage != null) {
            Snackbar(
                modifier       = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                containerColor = OffBlack,
                contentColor   = Color.White
            ) { Text(errorMessage, fontSize = 13.sp) }
        }
    }
}

// ─── Feed View ────────────────────────────────────────────────────────────────

@Composable
private fun FeedView(
    mediaItems: List<MediaItem>,
    currentIndex: Int,
    currentItem: MediaItem?,
    appMode: AppMode,
    isLoading: Boolean,
    reducedAnimations: Boolean,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    onSwipeUp: () -> Unit,
    onSwipeDown: () -> Unit,
    onPinchToGrid: () -> Unit,
    onDoubleTap: () -> Unit,
    onToggleLike: () -> Unit,
    onToggleRepost: () -> Unit,
    onToggleBookmark: () -> Unit,
    onToggleFollow: () -> Unit,
    onE621Vote: (Int) -> Unit,
    onDownload: () -> Unit,
    onTapAuthor: (MediaItem) -> Unit
) {
    val context     = LocalContext.current
    val imageLoader = remember { ImageLoader(context) }

    LaunchedEffect(currentIndex) {
        (1..3).mapNotNull { mediaItems.getOrNull(currentIndex + it) }.forEach { item ->
            if (!item.isVideo && item.mediaUrl.isNotBlank())
                imageLoader.enqueue(ImageRequest.Builder(context).data(item.mediaUrl).build())
            if (item.thumbUrl.isNotBlank())
                imageLoader.enqueue(ImageRequest.Builder(context).data(item.thumbUrl).build())
        }
    }

    Box(Modifier.fillMaxSize().background(OledBlack)) {
        if (isLoading && currentItem == null) {
            CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White, strokeWidth = 1.5.dp)
        } else {
            AnimatedContent(
                targetState = currentIndex,
                transitionSpec = {
                    if (reducedAnimations) EnterTransition.None togetherWith ExitTransition.None
                    else {
                        val dir = if (targetState > initialState) 1 else -1
                        (slideInHorizontally(SWIPE_ANIM) { it * dir } + fadeIn(FADE_ANIM)) togetherWith
                        (slideOutHorizontally(SWIPE_ANIM) { -it * dir } + fadeOut(FADE_ANIM))
                    }
                },
                label = "post"
            ) { idx ->
                val item = mediaItems.getOrNull(idx) ?: return@AnimatedContent
                PostContent(
                    item             = item,
                    appMode          = appMode,
                    onSwipeLeft      = onSwipeLeft,
                    onSwipeRight     = onSwipeRight,
                    onSwipeUp        = onSwipeUp,
                    onSwipeDown      = onSwipeDown,
                    onPinchToGrid    = onPinchToGrid,
                    onDoubleTap      = onDoubleTap,
                    onToggleLike     = onToggleLike,
                    onToggleRepost   = onToggleRepost,
                    onToggleBookmark = onToggleBookmark,
                    onToggleFollow   = onToggleFollow,
                    onE621Vote       = onE621Vote,
                    onDownload       = onDownload,
                    onTapAuthor      = { onTapAuthor(item) }
                )
            }
        }
    }
}

// ─── Post Content ─────────────────────────────────────────────────────────────

@Composable
private fun PostContent(
    item: MediaItem,
    appMode: AppMode,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    onSwipeUp: () -> Unit,
    onSwipeDown: () -> Unit,
    onPinchToGrid: () -> Unit,
    onDoubleTap: () -> Unit,
    onToggleLike: () -> Unit,
    onToggleRepost: () -> Unit,
    onToggleBookmark: () -> Unit,
    onToggleFollow: () -> Unit,
    onE621Vote: (Int) -> Unit,
    onDownload: () -> Unit,
    onTapAuthor: () -> Unit
) {
    val context = LocalContext.current

    var scale  by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    LaunchedEffect(item.id) { scale = 1f; offset = Offset.Zero }

    var menuCenter    by remember { mutableStateOf<Offset?>(null) }
    var hoveredAction by remember { mutableStateOf<QuickAction?>(null) }

    fun clampOffset(raw: Offset, s: Float): Offset {
        if (s <= 1.001f || containerSize == IntSize.Zero) return Offset.Zero
        val maxX = (containerSize.width  * (s - 1f) / 2f)
        val maxY = (containerSize.height * (s - 1f) / 2f)
        return Offset(raw.x.coerceIn(-maxX, maxX), raw.y.coerceIn(-maxY, maxY))
    }

    Box(Modifier.fillMaxSize()) {

        // Media area drawn FIRST (behind the bars) and fills the whole box so zoomed
        // content can visually overlap the author/action bars.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { containerSize = it }
                .pointerInput(item.id) {
                    var lastTapMs = 0L

                    awaitEachGesture {
                        val down     = awaitFirstDown(requireUnconsumed = false)
                        val downPos  = down.position
                        val downTime = System.currentTimeMillis()

                        if (downTime - lastTapMs < 280L && scale <= 1.05f) {
                            onDoubleTap(); down.consume(); lastTapMs = 0L; return@awaitEachGesture
                        }
                        lastTapMs = downTime

                        var dx = 0f; var dy = 0f
                        var menuOpen = false
                        var longPressFired = false
                        var prevPinchDist = -1f
                        var prevCentroid = downPos
                        var pointerCountEverTwo = false
                        // For "release then pinch again to enter grid": only allow grid-trigger
                        // if this gesture's pinch began while already at scale 1
                        var gridArmDist = -1f
                        var gridArmed = false

                        while (true) {
                            val elapsed = System.currentTimeMillis() - downTime
                            if (!menuOpen && !longPressFired && !pointerCountEverTwo &&
                                elapsed >= 450L && abs(dx) < 28f && abs(dy) < 28f && scale <= 1.05f) {
                                longPressFired = true
                                haptic(context)
                                menuCenter = downPos
                                menuOpen = true
                            }

                            val result = withTimeoutOrNull(16L) { awaitPointerEvent(PointerEventPass.Main) }
                            val event = result ?: continue
                            val pressed = event.changes.filter { it.pressed }

                            if (pressed.isEmpty()) {
                                if (menuOpen) {
                                    haptic(context)
                                    when (hoveredAction) {
                                        QuickAction.TOP    -> if (appMode == AppMode.BLUESKY) onToggleLike()     else onE621Vote(1)
                                        QuickAction.BOTTOM -> if (appMode == AppMode.BLUESKY) onDownload()       else onE621Vote(-1)
                                        QuickAction.LEFT   -> if (appMode == AppMode.BLUESKY) onToggleBookmark() else onDownload()
                                        QuickAction.RIGHT  -> if (appMode == AppMode.BLUESKY) onToggleRepost()   else onToggleBookmark()
                                        null -> {}
                                    }
                                    menuCenter = null; hoveredAction = null
                                } else if (scale <= 1.05f) {
                                    when {
                                        abs(dx) > 80f && abs(dx) > abs(dy) * 1.2f ->
                                            if (dx < 0) onSwipeLeft() else onSwipeRight()
                                        abs(dy) > 80f && abs(dy) > abs(dx) * 1.2f ->
                                            if (dy < 0) onSwipeUp() else onSwipeDown()
                                    }
                                }
                                // Snap fully back to identity once released at/near scale 1
                                if (scale <= 1.02f) { scale = 1f; offset = Offset.Zero }
                                break
                            }

                            if (pressed.size >= 2) {
                                pointerCountEverTwo = true
                                menuOpen = false; menuCenter = null; hoveredAction = null
                                val p1 = pressed[0].position; val p2 = pressed[1].position
                                val dist = (p1 - p2).getDistance(); val centroid = (p1 + p2) / 2f

                                // Arm grid-entry only if this pinch *starts* at scale==1 (i.e. user
                                // released after a previous zoom-out and is pinching in fresh)
                                if (gridArmDist < 0f) {
                                    gridArmDist = dist
                                    gridArmed = scale <= 1.01f
                                }

                                if (prevPinchDist > 0f) {
                                    val rawNewScale = scale * dist / prevPinchDist
                                    if (gridArmed && dist < gridArmDist * 0.7f) {
                                        scale = 1f; offset = Offset.Zero; onPinchToGrid()
                                        break
                                    }
                                    // Never go below 1 mid-gesture — zoom-out stops at "normal size"
                                    val newScale = rawNewScale.coerceIn(1f, 8f)
                                    scale = newScale
                                    offset = clampOffset(if (newScale > 1.02f) offset + (centroid - prevCentroid) else Offset.Zero, newScale)
                                }
                                prevPinchDist = dist; prevCentroid = centroid
                                pressed.forEach { it.consume() }
                            } else {
                                prevPinchDist = -1f
                                val change = pressed[0]
                                val delta = change.positionChange()

                                if (menuOpen) {
                                    hoveredAction = getHoveredAction(change.position, menuCenter!!)
                                    change.consume()
                                } else if (scale > 1.05f) {
                                    offset = clampOffset(offset + delta, scale)
                                    change.consume()
                                } else {
                                    dx += delta.x; dy += delta.y
                                    if (abs(dx) > viewConfiguration.touchSlop || abs(dy) > viewConfiguration.touchSlop) {
                                        longPressFired = true
                                    }
                                }
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            val mediaModifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y
            }
            if (item.isVideo && item.videoPlaylistUrl != null) {
                VideoPlayer(item.videoPlaylistUrl, mediaModifier)
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    AsyncImage(model = item.thumbUrl.ifBlank { item.mediaUrl },
                        contentDescription = null, contentScale = ContentScale.Fit, modifier = mediaModifier)
                    if (item.mediaUrl != item.thumbUrl && item.mediaUrl.isNotBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(item.mediaUrl).crossfade(true).build(),
                            contentDescription = item.altText.ifBlank { null },
                            contentScale = ContentScale.Fit, modifier = mediaModifier
                        )
                    }
                }
            }

            val mc = menuCenter
            if (mc != null) {
                QuickActionMenu(center = mc, hoveredAction = hoveredAction, appMode = appMode)
            }
        }

        // Author row & action row drawn AFTER media so they sit on top (z-order) and
        // remain tappable even when the media is zoomed in underneath them.
        AuthorRow(item, appMode, onToggleFollow, onTapAuthor,
            Modifier.fillMaxWidth().align(Alignment.TopCenter)
                .background(Color.Black.copy(0.55f))
                .windowInsetsPadding(WindowInsets.statusBars)
                .height(44.dp)
                .zIndex(2f)
        )

        ActionRow(item, appMode, onToggleLike, onToggleRepost, onToggleBookmark, onE621Vote,
            Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                .background(Color.Black.copy(0.55f))
                .windowInsetsPadding(WindowInsets.navigationBars)
                .height(52.dp)
                .zIndex(2f)
        )
    }
}

// ─── Quick Action Menu ────────────────────────────────────────────────────────

@Composable
private fun QuickActionMenu(center: Offset, hoveredAction: QuickAction?, appMode: AppMode) {
    val density = LocalDensity.current
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val menuScale by animateFloatAsState(
        targetValue   = if (visible) 1f else 0f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label = "menuScale"
    )

    val cx = with(density) { center.x.toDp() }
    val cy = with(density) { center.y.toDp() }
    val radius = 70.dp

    val actions: List<Triple<QuickAction, ImageVector, Color>> = if (appMode == AppMode.BLUESKY) listOf(
        Triple(QuickAction.TOP,    Icons.Filled.Favorite,     LikeRed),
        Triple(QuickAction.RIGHT,  Icons.Default.Repeat,      RepostGreen),
        Triple(QuickAction.BOTTOM, Icons.Default.Download,    Color.White),
        Triple(QuickAction.LEFT,   Icons.Filled.Bookmark,     BookmarkYellow)
    ) else listOf(
        Triple(QuickAction.TOP,    Icons.Default.ArrowUpward,   VoteGreen),
        Triple(QuickAction.RIGHT,  Icons.Filled.Star,           BookmarkYellow),
        Triple(QuickAction.BOTTOM, Icons.Default.ArrowDownward, VoteRed),
        Triple(QuickAction.LEFT,   Icons.Default.Download,      Color.White)
    )

    Box(Modifier.fillMaxSize().zIndex(3f)) {
        actions.forEach { (action, icon, tint) ->
            val (bx, by) = when (action) {
                QuickAction.TOP    -> Pair(cx - 24.dp, cy - radius - 24.dp)
                QuickAction.BOTTOM -> Pair(cx - 24.dp, cy + radius - 24.dp)
                QuickAction.LEFT   -> Pair(cx - radius - 24.dp, cy - 24.dp)
                QuickAction.RIGHT  -> Pair(cx + radius - 24.dp, cy - 24.dp)
            }
            val isHovered = hoveredAction == action
            val btnScale by animateFloatAsState(
                targetValue   = if (isHovered) 1.3f else 1f,
                animationSpec = spring(Spring.DampingRatioMediumBouncy),
                label         = "btn"
            )
            Box(
                modifier = Modifier
                    .offset(x = bx, y = by)
                    .scale(menuScale * btnScale)
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (isHovered) Color.White.copy(0.25f) else Color(0xFF1C1C1C)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
            }
        }
    }
}

// ─── Author Row ───────────────────────────────────────────────────────────────

@Composable
private fun AuthorRow(item: MediaItem, appMode: AppMode, onToggleFollow: () -> Unit, onTapAuthor: () -> Unit, modifier: Modifier) {
    val author = item.author
    Row(modifier = modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            modifier = Modifier.weight(1f, fill = false).clickable(onClick = onTapAuthor)
        ) {
            if (author.avatarUrl != null) {
                AsyncImage(model = author.avatarUrl, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.size(24.dp).clip(CircleShape))
            } else {
                Box(Modifier.size(24.dp).clip(CircleShape).background(Color.White.copy(0.12f)))
            }
            Text(author.displayName, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text("@${author.handle}", color = DimGray, fontSize = 12.sp, maxLines = 1)
        }
        Spacer(Modifier.weight(1f))
        if (appMode == AppMode.BLUESKY) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (author.isFollowing) Color.White.copy(0.07f) else Color.White.copy(0.14f))
                    .clickable(onClick = onToggleFollow)
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            ) {
                Text(if (author.isFollowing) "Following" else "Follow",
                    color = if (author.isFollowing) DimGray else Color.White,
                    fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

// ─── Action Row ───────────────────────────────────────────────────────────────

@Composable
private fun ActionRow(
    item: MediaItem, appMode: AppMode,
    onToggleLike: () -> Unit, onToggleRepost: () -> Unit,
    onToggleBookmark: () -> Unit, onE621Vote: (Int) -> Unit,
    modifier: Modifier
) {
    Row(modifier = modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (appMode == AppMode.BLUESKY) {
            ActionButton(if (item.isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                if (item.isLiked) LikeRed else Color.White,
                item.likeCount.takeIf { it > 0 }?.toString(), onToggleLike)
            ActionButton(Icons.Default.Repeat,
                if (item.isReposted) RepostGreen else Color.White,
                item.repostCount.takeIf { it > 0 }?.toString(), onToggleRepost)
            ActionButton(if (item.isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                if (item.isBookmarked) BookmarkYellow else Color.White, null, onToggleBookmark)
        } else {
            ActionButton(Icons.Default.ArrowUpward, if (item.e621UserVote == 1) VoteGreen else Color.White, null) { onE621Vote(1) }
            if (item.e621Score != 0)
                Text(if (item.e621Score > 0) "+${item.e621Score}" else "${item.e621Score}",
                    color = if (item.e621Score > 0) VoteGreen else VoteRed, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            ActionButton(Icons.Default.ArrowDownward, if (item.e621UserVote == -1) VoteRed else Color.White, null) { onE621Vote(-1) }
            Spacer(Modifier.width(8.dp))
            ActionButton(if (item.isBookmarked) Icons.Filled.Star else Icons.Outlined.StarBorder,
                if (item.isBookmarked) BookmarkYellow else Color.White, null, onToggleBookmark)
        }
    }
}

@Composable
private fun ActionButton(icon: ImageVector, tint: Color, label: String? = null, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 10.dp)) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(26.dp))
        if (label != null) Text(label, color = tint, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

// ─── Video Player ─────────────────────────────────────────────────────────────

@Composable
private fun VideoPlayer(url: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val player  = remember { ExoPlayer.Builder(context).build().apply { repeatMode = Player.REPEAT_MODE_ONE; volume = 1f } }
    LaunchedEffect(url) { player.setMediaItem(ExoMediaItem.fromUri(url)); player.prepare(); player.play() }
    DisposableEffect(Unit) { onDispose { player.release() } }
    AndroidView(
        factory = { ctx -> PlayerView(ctx).apply {
            this.player = player; useController = true
            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
            setBackgroundColor(android.graphics.Color.BLACK)
        }},
        modifier = modifier
    )
}

// ─── Haptic ───────────────────────────────────────────────────────────────────

private fun haptic(context: Context) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager)
                .defaultVibrator.vibrate(VibrationEffect.createOneShot(38, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            val v = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                v.vibrate(VibrationEffect.createOneShot(38, VibrationEffect.DEFAULT_AMPLITUDE))
            else @Suppress("DEPRECATION") v.vibrate(38)
        }
    } catch (_: Exception) {}
}
