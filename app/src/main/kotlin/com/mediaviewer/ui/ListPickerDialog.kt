package com.mediaviewer.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.mediaviewer.model.BskyList
import com.mediaviewer.model.BskyStarterPackView
import com.mediaviewer.ui.theme.*

private enum class PickerTab { LISTS, STARTER_PACKS }

@Composable
fun ListPickerDialog(
    lists: List<BskyList>,
    starterPacks: List<BskyStarterPackView>,
    listsLoading: Boolean,
    onSelectList: (listUri: String) -> Unit,
    onDismiss: () -> Unit
) {
    var activeTab by remember { mutableStateOf(PickerTab.LISTS) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnClickOutside = true,
            dismissOnBackPress    = true,
            usePlatformDefaultWidth = false
        )
    ) {
        // Full-screen scrim — tapping it dismisses
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            // Card — absorbs all touches so nothing behind fires accidentally
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.88f)
                    .heightIn(min = 140.dp, max = 480.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(OffBlack)
                    // Absorb touches inside the card — clickable(false) propagates to parent
                    // which would dismiss, so we use a pointerInput that consumes instead.
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                event.changes.forEach { it.consume() }
                            }
                        }
                    }
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {

                    // ── Title ─────────────────────────────────────────────────
                    Text(
                        text       = "Add to…",
                        color      = Color.White,
                        fontSize   = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier   = Modifier.padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 12.dp)
                    )

                    // ── Tab row with swipe support ────────────────────────────
                    var swipeDx by remember { mutableFloatStateOf(0f) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .pointerInput(Unit) {
                                detectHorizontalDragGestures(
                                    onDragEnd = {
                                        if (swipeDx < -60f) activeTab = PickerTab.STARTER_PACKS
                                        else if (swipeDx > 60f) activeTab = PickerTab.LISTS
                                        swipeDx = 0f
                                    },
                                    onDragCancel = { swipeDx = 0f }
                                ) { _, dragAmount -> swipeDx += dragAmount }
                            },
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PickerTab.entries.forEach { tab ->
                            val selected = activeTab == tab
                            Text(
                                text       = if (tab == PickerTab.LISTS) "My Lists" else "Starter Packs",
                                color      = if (selected) Color.White else DimGray,
                                fontSize   = 13.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                modifier   = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (selected) Color.White.copy(0.1f) else Color.Transparent)
                                    .clickable { activeTab = tab }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                    // ── Animated content pane ─────────────────────────────────
                    AnimatedContent(
                        targetState = activeTab,
                        transitionSpec = {
                            val dir = if (targetState == PickerTab.STARTER_PACKS) 1 else -1
                            (slideInHorizontally(tween(180)) { it * dir } + fadeIn(tween(150))) togetherWith
                            (slideOutHorizontally(tween(180)) { -it * dir } + fadeOut(tween(120)))
                        },
                        label = "tab"
                    ) { tab ->
                        when (tab) {
                            PickerTab.LISTS -> PickerBody(
                                loading  = listsLoading,
                                empty    = "You have no lists yet.",
                                content  = {
                                    items(lists, key = { it.uri }) { list ->
                                        ListRow(
                                            name       = list.name,
                                            subtitle   = list.itemCount?.let { "$it members" },
                                            avatarUrl  = list.avatar,
                                            isPackIcon = false,
                                            onClick    = { onSelectList(list.uri) }
                                        )
                                    }
                                }
                            )
                            PickerTab.STARTER_PACKS -> PickerBody(
                                loading  = listsLoading,
                                empty    = "You have no Starter Packs yet.",
                                content  = {
                                    items(starterPacks, key = { it.uri }) { pack ->
                                        // Adding to a starter pack = adding to its underlying list
                                        val listUri = pack.record?.list ?: return@items
                                        ListRow(
                                            name       = pack.record.name,
                                            subtitle   = pack.listItemCount?.let { "$it members" }
                                                         ?: pack.joinedAllTimeCount?.let { "$it joined" },
                                            avatarUrl  = null,
                                            isPackIcon = true,
                                            onClick    = { onSelectList(listUri) }
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerBody(
    loading: Boolean,
    empty: String,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    when {
        loading -> Box(
            Modifier.fillMaxWidth().height(120.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 1.5.dp, modifier = Modifier.size(28.dp))
        }
        else -> LazyColumn(
            modifier       = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            content()
            // Show empty message as a list item if needed
        }
    }
}

@Composable
private fun ListRow(
    name: String,
    subtitle: String?,
    avatarUrl: String?,
    isPackIcon: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (avatarUrl != null) {
            AsyncImage(
                model              = avatarUrl,
                contentDescription = null,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier.size(36.dp).clip(CircleShape)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector        = if (isPackIcon) Icons.Default.Groups else Icons.Default.FormatListBulleted,
                    contentDescription = null,
                    tint               = DimGray,
                    modifier           = Modifier.size(20.dp)
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            if (subtitle != null) {
                Text(subtitle, color = DimGray, fontSize = 12.sp)
            }
        }
    }
}
