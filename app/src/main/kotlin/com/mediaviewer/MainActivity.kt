package com.mediaviewer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.mediaviewer.ui.ListPickerDialog
import com.mediaviewer.ui.MainFeedScreen
import com.mediaviewer.ui.theme.MediaViewerTheme
import com.mediaviewer.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MediaViewerTheme {
                AppRoot(viewModel)
            }
        }
    }
}

@Composable
private fun AppRoot(viewModel: MainViewModel) {
    val mediaItems        by viewModel.mediaItems.collectAsState()
    val currentIndex      by viewModel.currentIndex.collectAsState()
    val currentItem       by viewModel.currentItem.collectAsState()
    val screenState       by viewModel.screenState.collectAsState()
    val appMode           by viewModel.appMode.collectAsState()
    val navDirection      by viewModel.navDirection.collectAsState()
    val reducedAnimations by viewModel.reducedAnimations.collectAsState()
    val availableFeeds    by viewModel.availableFeeds.collectAsState()
    val selectedFeed      by viewModel.selectedFeedUri.collectAsState()
    val comments          by viewModel.comments.collectAsState()
    val commentsLoad      by viewModel.commentsLoading.collectAsState()
    val downloadOnLike    by viewModel.downloadOnLike.collectAsState()
    val downloadProgress  by viewModel.downloadProgress.collectAsState()
    val e621Tags          by viewModel.e621SearchTags.collectAsState()
    val isLoading         by viewModel.isLoading.collectAsState()
    val bskyLoggedIn      by viewModel.bskyLoggedIn.collectAsState()
    val e621LoggedIn      by viewModel.e621LoggedIn.collectAsState()
    val errorMessage      by viewModel.errorMessage.collectAsState()
    val listPickerDid     by viewModel.listPickerTargetDid.collectAsState()
    val userLists         by viewModel.userLists.collectAsState()
    val userStarterPacks  by viewModel.userStarterPacks.collectAsState()
    val userListsLoading  by viewModel.userListsLoading.collectAsState()

    Box(Modifier.fillMaxSize()) {
        MainFeedScreen(
        mediaItems               = mediaItems,
        currentIndex             = currentIndex,
        currentItem              = currentItem,
        screenState              = screenState,
        appMode                  = appMode,
        navDirection             = navDirection,
        reducedAnimations        = reducedAnimations,
        availableFeeds           = availableFeeds,
        selectedFeedUri          = selectedFeed,
        comments                 = comments,
        commentsLoading          = commentsLoad,
        downloadOnLike           = downloadOnLike,
        downloadProgress         = downloadProgress,
        e621SearchTags           = e621Tags,
        isLoading                = isLoading,
        bskyLoggedIn             = bskyLoggedIn,
        e621LoggedIn             = e621LoggedIn,
        bskyHandle               = viewModel.bskyHandle,
        e621Username             = viewModel.e621Username,
        errorMessage             = errorMessage,
        onNavigateNext           = viewModel::navigateNext,
        onNavigatePrev           = viewModel::navigatePrev,
        onNavigateTo             = viewModel::navigateTo,
        onSetScreen              = viewModel::setScreen,
        onToggleLike             = viewModel::toggleLike,
        onToggleRepost           = viewModel::toggleRepost,
        onToggleBookmark         = viewModel::toggleBookmark,
        onToggleFollow           = viewModel::toggleFollow,
        onE621Vote               = viewModel::e621Vote,
        onPostComment            = viewModel::postComment,
        onLikeComment            = viewModel::likeComment,
        onVoteComment            = viewModel::voteComment,
        onSelectFeed             = viewModel::selectFeed,
        onToggleDownloadOnLike   = viewModel::setDownloadOnLike,
        onDownloadAllLiked       = viewModel::downloadAllLiked,
        onCancelDownload         = viewModel::cancelDownloadAll,
        onShowLikes              = viewModel::showBskyLikes,
        onToggleReducedAnimations = viewModel::setReducedAnimations,
        onLoginBluesky           = viewModel::loginBluesky,
        onLogoutBluesky          = viewModel::logoutBluesky,
        onSaveE621Credentials    = viewModel::saveE621Credentials,
        onLogoutE621             = viewModel::logoutE621,
        onSearchE621             = { tags -> viewModel.setE621SearchTags(tags); viewModel.searchE621() },
        onShowE621Favorites      = viewModel::showE621Favorites,
        onSwipeToMode            = viewModel::setMode,
        onLoadMore               = viewModel::loadMore,
        onDownloadCurrent        = viewModel::downloadCurrentItem,
        onRefresh                = { viewModel.loadFeed(reset = true) },
        onTapAuthor              = { item ->
            if (appMode == com.mediaviewer.model.AppMode.BLUESKY) viewModel.showAuthorFeed(item.author.did)
            else viewModel.searchSingleTag(item.author.handle)
        },
        onTagClick   = { tag -> viewModel.searchSingleTag(tag) },
        onTagAdd     = { tag -> viewModel.addTagToSearch(tag, exclude = false) },
        onTagExclude = { tag -> viewModel.addTagToSearch(tag, exclude = true) }
    )

        // List picker dialog — shown after the user taps Follow.
        // Lives outside MainFeedScreen so it sits above every screen state
        // and its Dialog window truly blocks all touches behind it.
        if (listPickerDid != null) {
            ListPickerDialog(
                lists         = userLists,
                starterPacks  = userStarterPacks,
                listsLoading  = userListsLoading,
                onSelectList  = { listUri -> viewModel.addAccountToList(listUri) },
                onDismiss     = { viewModel.dismissListPicker() }
            )
        }
    }   // end Box

    // Auto-clear errors after display
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            kotlinx.coroutines.delay(3000)
            viewModel.clearError()
        }
    }
}
