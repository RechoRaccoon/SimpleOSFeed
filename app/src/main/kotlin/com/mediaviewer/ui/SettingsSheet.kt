package com.mediaviewer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mediaviewer.model.AppMode
import com.mediaviewer.model.BskyFeedInfo
import com.mediaviewer.model.DownloadProgress
import com.mediaviewer.ui.theme.*

@Composable
fun SettingsSheet(
    appMode: AppMode,
    bskyLoggedIn: Boolean,
    e621LoggedIn: Boolean,
    bskyHandle: String,
    e621Username: String,
    availableFeeds: List<BskyFeedInfo>,
    selectedFeedUri: String?,
    downloadOnLike: Boolean,
    downloadProgress: DownloadProgress?,
    reducedAnimations: Boolean,
    e621SearchTags: String,
    isLoading: Boolean,
    onLoginBluesky: (String, String) -> Unit,
    onLogoutBluesky: () -> Unit,
    onSaveE621Credentials: (String, String) -> Unit,
    onLogoutE621: () -> Unit,
    onSelectFeed: (String?) -> Unit,
    onToggleDownloadOnLike: (Boolean) -> Unit,
    onDownloadAllLiked: () -> Unit,
    onCancelDownload: () -> Unit,
    onShowLikes: () -> Unit,
    onToggleReducedAnimations: (Boolean) -> Unit,
    onSearchE621: (String) -> Unit,
    onShowE621Favorites: () -> Unit,
    onSwitchMode: (AppMode) -> Unit,
    onSwipeToFeed: () -> Unit
) {
    var bskyIdentifier by remember { mutableStateOf("") }
    var bskyPassword   by remember { mutableStateOf("") }
    var e621User       by remember { mutableStateOf("") }
    var e621Key        by remember { mutableStateOf("") }
    var localE621Tags  by remember(e621SearchTags) { mutableStateOf(e621SearchTags) }

    val isLoggedIn = if (appMode == AppMode.BLUESKY) bskyLoggedIn else e621LoggedIn

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OledBlack)
            .pointerInput(appMode) {
                var totalX = 0f
                var totalY = 0f
                detectDragGestures(
                    onDragStart = { totalX = 0f; totalY = 0f },
                    onDragEnd = {
                        if (abs(totalY) > 80f && abs(totalY) > abs(totalX) * 1.2f && totalY < 0) {
                            onSwipeToFeed()
                        } else if (abs(totalX) > 80f && abs(totalX) > abs(totalY) * 1.2f) {
                            if (totalX < 0) onSwitchMode(AppMode.E621) else onSwitchMode(AppMode.BLUESKY)
                        }
                    },
                    onDragCancel = { }
                ) { change, dragAmount ->
                    totalX += dragAmount.x
                    totalY += dragAmount.y
                }
            }
    ) {
        Column(
            modifier            = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(20.dp))

            // ── Mode header ───────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(36.dp)
            ) {
                Text(
                    "AT Protocol",
                    color      = if (appMode == AppMode.BLUESKY) Color.White else DimGray,
                    fontSize   = 13.sp,
                    fontWeight = if (appMode == AppMode.BLUESKY) FontWeight.SemiBold else FontWeight.Normal,
                    modifier   = Modifier
                        .align(Alignment.CenterStart)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (appMode == AppMode.BLUESKY) Color.White.copy(0.1f) else Color.Transparent)
                        .clickable { onSwitchMode(AppMode.BLUESKY) }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                )
                Text(
                    if (appMode == AppMode.BLUESKY) "AT Protocol" else "e621",
                    color         = Color.White,
                    fontSize      = 20.sp,
                    fontWeight    = FontWeight.Light,
                    letterSpacing = 2.sp,
                    modifier      = Modifier.align(Alignment.Center)
                )
                Text(
                    "e621",
                    color      = if (appMode == AppMode.E621) Color.White else DimGray,
                    fontSize   = 13.sp,
                    fontWeight = if (appMode == AppMode.E621) FontWeight.SemiBold else FontWeight.Normal,
                    modifier   = Modifier
                        .align(Alignment.CenterEnd)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (appMode == AppMode.E621) Color.White.copy(0.1f) else Color.Transparent)
                        .clickable { onSwitchMode(AppMode.E621) }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.07f))
            Spacer(Modifier.height(12.dp))

            if (!isLoggedIn) {
                // ── Login form ────────────────────────────────────────────────
                Spacer(Modifier.weight(1f))
                Column(
                    modifier            = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 36.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (appMode == AppMode.BLUESKY) {
                        OutlinedTextField(
                            value         = bskyIdentifier,
                            onValueChange = { bskyIdentifier = it },
                            placeholder   = { Text("handle or email", color = DimGray) },
                            singleLine    = true,
                            colors        = settingsFieldColors(),
                            modifier      = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value                = bskyPassword,
                            onValueChange        = { bskyPassword = it },
                            placeholder          = { Text("app password", color = DimGray) },
                            singleLine           = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions      = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction    = ImeAction.Done
                            ),
                            colors   = settingsFieldColors(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick  = { onLoginBluesky(bskyIdentifier.trim(), bskyPassword) },
                            enabled  = bskyIdentifier.isNotBlank() && bskyPassword.isNotBlank() && !isLoading,
                            colors   = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                            modifier = Modifier.fillMaxWidth().height(46.dp)
                        ) {
                            if (isLoading) CircularProgressIndicator(Modifier.size(18.dp), color = Color.Black, strokeWidth = 2.dp)
                            else Text("Sign in to Bluesky", fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        OutlinedTextField(
                            value         = e621User,
                            onValueChange = { e621User = it },
                            placeholder   = { Text("Username", color = DimGray) },
                            singleLine    = true,
                            colors        = settingsFieldColors(),
                            modifier      = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value                = e621Key,
                            onValueChange        = { e621Key = it },
                            placeholder          = { Text("API Key", color = DimGray) },
                            singleLine           = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions      = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction    = ImeAction.Done
                            ),
                            colors   = settingsFieldColors(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick  = { onSaveE621Credentials(e621User, e621Key) },
                            enabled  = e621User.isNotBlank() && e621Key.isNotBlank(),
                            colors   = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                            modifier = Modifier.fillMaxWidth().height(46.dp)
                        ) { Text("Sign in to e621", fontWeight = FontWeight.SemiBold) }
                    }
                }
                Spacer(Modifier.weight(1f))
            } else {
                // ── Feeds / Search ────────────────────────────────────────────
                if (appMode == AppMode.BLUESKY) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FeedChip("Home", null, selectedFeedUri == null) { onSelectFeed(null) }
                        availableFeeds.forEach { feed ->
                            FeedChip(feed.displayName, feed.avatarUrl, selectedFeedUri == feed.uri) {
                                onSelectFeed(feed.uri)
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value         = localE621Tags,
                            onValueChange = { localE621Tags = it },
                            placeholder   = { Text("Search tags…", color = DimGray, fontSize = 13.sp) },
                            singleLine    = true,
                            colors        = settingsFieldColors(),
                            modifier      = Modifier.weight(1f).height(56.dp)
                        )
                        Button(
                            onClick  = { onSearchE621(localE621Tags) },
                            colors   = ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(alpha = 0.1f),
                                contentColor   = Color.White
                            ),
                            modifier = Modifier.height(56.dp)
                        ) { Text("Search") }
                    }
                }

                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.07f))
                Spacer(Modifier.height(12.dp))

                Column(
                    modifier            = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Download When Liked/Favorited
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            if (appMode == AppMode.BLUESKY) "Download When Liked" else "Download When Favorited",
                            color = Color.White, fontSize = 14.sp
                        )
                        Switch(
                            checked         = downloadOnLike,
                            onCheckedChange = onToggleDownloadOnLike,
                            colors          = SwitchDefaults.colors(
                                checkedThumbColor   = Color.White,
                                checkedTrackColor   = VoteGreen,
                                uncheckedThumbColor = DimGray,
                                uncheckedTrackColor = Color.White.copy(0.1f)
                            )
                        )
                    }

                    // Reduced Animations
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Reduced Animations", color = Color.White, fontSize = 14.sp)
                        Switch(
                            checked         = reducedAnimations,
                            onCheckedChange = onToggleReducedAnimations,
                            colors          = SwitchDefaults.colors(
                                checkedThumbColor   = Color.White,
                                checkedTrackColor   = VoteGreen,
                                uncheckedThumbColor = DimGray,
                                uncheckedTrackColor = Color.White.copy(0.1f)
                            )
                        )
                    }

                    // Download All button with progress + cancel
                    val prog = downloadProgress
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(0.08f))
                            .clickable { if (prog?.isRunning != true) onDownloadAllLiked() },
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            when {
                                prog?.isRunning == true        -> "Downloading… ${prog.count} queued"
                                prog != null && prog.count > 0 -> "Done — ${prog.count} queued"
                                appMode == AppMode.BLUESKY     -> "Download All Liked Media"
                                else                           -> "Download All Saved Media"
                            },
                            color    = Color.White,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                        if (prog?.isRunning == true) {
                            IconButton(
                                onClick  = onCancelDownload,
                                modifier = Modifier.align(Alignment.CenterEnd).size(40.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Cancel", tint = DimGray, modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    // My Likes / My Favorites
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(0.08f))
                            .clickable { if (appMode == AppMode.BLUESKY) onShowLikes() else onShowE621Favorites() },
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector        = if (appMode == AppMode.BLUESKY) Icons.Default.Favorite else Icons.Default.Star,
                            contentDescription = null,
                            tint               = if (appMode == AppMode.BLUESKY) LikeRed else BookmarkYellow,
                            modifier           = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (appMode == AppMode.BLUESKY) "My Likes" else "My Favorites",
                            color = Color.White, fontSize = 13.sp
                        )
                    }

                    // Logged in as + Logout
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(0.05f))
                            .padding(horizontal = 14.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Logged in as @${if (appMode == AppMode.BLUESKY) bskyHandle else e621Username}",
                            color    = DimGray,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "Logout",
                            color      = Color(0xFFEF5350),
                            fontSize   = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier   = Modifier
                                .clickable { if (appMode == AppMode.BLUESKY) onLogoutBluesky() else onLogoutE621() }
                                .padding(start = 12.dp, top = 6.dp, bottom = 6.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // Footer
            Text(
                buildAnnotatedString {
                    append("Created by ")
                    withStyle(SpanStyle(color = Color(0xFF00FF07))) { append("Recho Raccoon") }
                },
                color     = DimGray,
                fontSize  = 11.sp,
                textAlign = TextAlign.Center,
                modifier  = Modifier.padding(bottom = 20.dp)
            )
        }
    }
}

@Composable
private fun FeedChip(name: String, avatarUrl: String?, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (isSelected) Color.White.copy(0.15f) else Color.White.copy(0.06f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (avatarUrl != null) {
            AsyncImage(
                model              = avatarUrl,
                contentDescription = null,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier.size(16.dp).clip(CircleShape)
            )
        }
        Text(
            name,
            color      = if (isSelected) Color.White else DimGray,
            fontSize   = 12.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun settingsFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor        = Color.White,
    unfocusedTextColor      = Color.White,
    focusedBorderColor      = Color.White.copy(0.3f),
    unfocusedBorderColor    = Color.White.copy(0.1f),
    cursorColor             = Color.White,
    focusedContainerColor   = Color.Transparent,
    unfocusedContainerColor = Color.Transparent
)
