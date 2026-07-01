package com.mediaviewer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.mediaviewer.model.AppMode
import com.mediaviewer.model.BskyFeedInfo
import com.mediaviewer.model.MediaItem
import com.mediaviewer.ui.theme.*

@Composable
fun GridScreen(
    items: List<MediaItem>,
    currentIndex: Int,
    appMode: AppMode,
    availableFeeds: List<BskyFeedInfo>,
    selectedFeedUri: String?,
    e621SearchTags: String,
    onItemClick: (Int) -> Unit,
    onLoadMore: () -> Unit,
    onSelectFeed: (String?) -> Unit,
    onSearchE621: (String) -> Unit,
    onRefresh: () -> Unit
) {
    val gridState  = rememberLazyGridState()
    var localTags  by remember(e621SearchTags) { mutableStateOf(e621SearchTags) }
    var refreshing by remember { mutableStateOf(false) }

    LaunchedEffect(currentIndex) {
        if (currentIndex > 0) gridState.scrollToItem(maxOf(0, currentIndex - 3))
    }

    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= items.size - 12
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && items.isNotEmpty()) onLoadMore()
    }

    // Pull-to-refresh: detect when grid is at top and user drags down
    val topReached by remember {
        derivedStateOf { !gridState.canScrollBackward }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OledBlack)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        // ── Feeds bar / search bar at very top ────────────────────────────────
        if (appMode == AppMode.BLUESKY) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(OledBlack)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GridFeedChip("Home", null, selectedFeedUri == null) { onSelectFeed(null) }
                availableFeeds.forEach { feed ->
                    GridFeedChip(feed.displayName, feed.avatarUrl, selectedFeedUri == feed.uri) {
                        onSelectFeed(feed.uri)
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(OledBlack)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value         = localTags,
                    onValueChange = { localTags = it },
                    placeholder   = { Text("Search tags…", color = DimGray, fontSize = 13.sp) },
                    singleLine    = true,
                    textStyle     = LocalTextStyle.current.copy(fontSize = 13.sp, color = Color.White),
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor      = Color.White.copy(0.3f),
                        unfocusedBorderColor    = Color.White.copy(0.1f),
                        cursorColor             = Color.White,
                        focusedContainerColor   = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedTextColor        = Color.White,
                        unfocusedTextColor      = Color.White
                    ),
                    modifier = Modifier.weight(1f).height(54.dp)
                )
                Button(
                    onClick  = { onSearchE621(localTags) },
                    colors   = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(0.1f),
                        contentColor   = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    modifier = Modifier.height(54.dp)
                ) { Text("Go", fontSize = 13.sp) }
            }
        }

        HorizontalDivider(color = Color.White.copy(alpha = 0.07f), thickness = 0.5.dp)

        // Pull-to-refresh indicator
        if (refreshing) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color    = Color.White,
                trackColor = OledBlack
            )
        }

        // ── 3-column grid ─────────────────────────────────────────────────────
        LazyVerticalGrid(
            columns               = GridCells.Fixed(3),
            state                 = gridState,
            modifier              = Modifier.fillMaxSize(),
            contentPadding        = PaddingValues(0.dp),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            verticalArrangement   = Arrangement.spacedBy(0.dp),
            // Pull-to-refresh via overscroll when at top
            userScrollEnabled     = true
        ) {
            itemsIndexed(items) { index, item ->
                GridCell(item, index == currentIndex) { onItemClick(index) }
            }
            // Trigger refresh if swiped past top (handled via LaunchedEffect)
        }
    }

    // Pull to refresh: watch for overscroll at top via a side effect
    // Simple approach: button-free pull to refresh using scroll position monitoring
    LaunchedEffect(topReached) {
        // Only used as signal; actual refresh triggered by swipe gesture in parent
    }
}

// Expose a pull-to-refresh trigger via the parent gesture in MainFeedScreen/ScreenState logic
// For now, the grid refresh is triggered by selecting a feed or searching

@Composable
private fun GridCell(item: MediaItem, isActive: Boolean, onClick: () -> Unit) {
    BoxWithConstraints(
        modifier = Modifier
            .aspectRatio(1f)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(item.thumbUrl.ifBlank { item.mediaUrl })
                .crossfade(false)
                .size(maxWidth.value.toInt())
                .build(),
            contentDescription = null,
            contentScale       = ContentScale.Crop,
            modifier           = Modifier.fillMaxSize()
        )
        if (isActive) {
            Box(Modifier.fillMaxSize().background(Color.White.copy(0.15f)))
        }
        if (item.isVideo) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = "Video",
                tint     = Color.White.copy(0.85f),
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(16.dp)
            )
        }
    }
}

@Composable
private fun GridFeedChip(name: String, avatarUrl: String?, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) Color.White.copy(0.15f) else Color.White.copy(0.06f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        if (avatarUrl != null) {
            AsyncImage(
                model              = avatarUrl,
                contentDescription = null,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier.size(14.dp).clip(CircleShape)
            )
        }
        Text(
            name,
            color      = if (isSelected) Color.White else DimGray,
            fontSize   = 11.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}
