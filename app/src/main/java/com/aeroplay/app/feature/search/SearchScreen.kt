package com.aeroplay.app.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aeroplay.app.core.designsystem.LocalFloatingPlayerInset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.aeroplay.app.core.common.FormatUtils
import com.aeroplay.app.core.designsystem.AddToPlaylistDialog
import com.aeroplay.app.core.designsystem.ErrorStateCard
import com.aeroplay.app.core.designsystem.LocalAeroPlayStatusColors
import com.aeroplay.app.core.designsystem.SkeletonVideoCard
import com.aeroplay.app.core.designsystem.VideoFeedCard
import com.aeroplay.app.core.model.ChannelItem
import com.aeroplay.app.core.model.PlaylistItem
import com.aeroplay.app.core.model.VideoItem

@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val history by viewModel.searchHistory.collectAsStateWithLifecycle()
    val favoriteIds by viewModel.favoriteIds.collectAsStateWithLifecycle()
    val playlists by viewModel.userPlaylists.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    var playlistTargetVideo by remember { mutableStateOf<VideoItem?>(null) }

    if (playlistTargetVideo != null) {
        AddToPlaylistDialog(
            video = playlistTargetVideo!!,
            playlists = playlists,
            onDismiss = { playlistTargetVideo = null },
            onSelectPlaylist = { plId -> viewModel.addVideoToPlaylist(plId, playlistTargetVideo!!) },
            onCreateAndAdd = { name -> viewModel.createPlaylistAndAdd(name, playlistTargetVideo!!) }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = 12.dp)
    ) {
        // Search Input Bar
        OutlinedTextField(
            value = uiState.query,
            onValueChange = { viewModel.onQueryChange(it) },
            placeholder = {
                Text(if (uiState.source.isExperimental) "Nhập từ khóa video, bài hát, kênh..." else "Tìm trong danh mục Chuẩn Mở")
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            trailingIcon = {
                if (uiState.query.isNotEmpty()) {
                    IconButton(onClick = { viewModel.onQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Filled.Clear,
                            contentDescription = "Xóa từ khóa"
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(20.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    focusManager.clearFocus()
                    viewModel.onSubmitSearch()
                }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Filter Chips
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(SearchFilterTab.entries) { tab ->
                FilterChip(
                    selected = uiState.activeFilter == tab,
                    onClick = { viewModel.selectFilter(tab) },
                    label = { Text(tab.label) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Content Area
        SearchResultsList(
            isRefreshing = uiState.isRefreshing,
            canRefresh = uiState.query.isNotBlank() && !uiState.isLoading,
            onRefresh = viewModel::refresh
        ) {
            // Local Search History Suggestions when query is empty
            if (uiState.query.trim().isEmpty()) {
                if (history.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Lịch sử tìm kiếm",
                                style = MaterialTheme.typography.titleMedium
                            )
                            TextButton(onClick = { viewModel.clearAllSearchHistory() }) {
                                Icon(
                                    imageVector = Icons.Filled.DeleteSweep,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Xóa tất cả")
                            }
                        }
                    }

                    items(history, key = { it }) { queryItem ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    focusManager.clearFocus()
                                    viewModel.onSubmitSearch(queryItem)
                                },
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.History,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = queryItem,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { viewModel.deleteHistoryQuery(queryItem) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "Xóa mục này",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Nhập từ khóa để tìm kiếm",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Tự động tìm kiếm sau 400ms khi bạn gõ",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                            )
                        }
                    }
                }
            } else {
                when {
                    uiState.isLoading -> {
                        items(3) {
                            SkeletonVideoCard()
                        }
                    }
                    uiState.error != null -> {
                        item {
                            ErrorStateCard(
                                title = "Không thể thực hiện tìm kiếm",
                                message = uiState.error!!.message,
                                technicalDetail = uiState.error!!.technicalDetail,
                                onRetry = { viewModel.onSubmitSearch() }
                            )
                        }
                    }
                    else -> {
                        val showChannels = uiState.activeFilter == SearchFilterTab.ALL ||
                            uiState.activeFilter == SearchFilterTab.CHANNELS
                        val showPlaylists = uiState.activeFilter == SearchFilterTab.ALL ||
                            uiState.activeFilter == SearchFilterTab.PLAYLISTS
                        val showVideos = uiState.activeFilter == SearchFilterTab.ALL ||
                            uiState.activeFilter == SearchFilterTab.VIDEOS

                        val hasVisibleResults = (showVideos && uiState.videos.isNotEmpty()) ||
                            (showChannels && uiState.channels.isNotEmpty()) ||
                            (showPlaylists && uiState.playlists.isNotEmpty())

                        if (!hasVisibleResults && !uiState.isRefreshing) {
                            item {
                                Column(Modifier.fillMaxWidth().padding(vertical = 24.dp)) {
                                    Text("Không có kết quả", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        if (uiState.source.isExperimental) "Hãy thử từ khóa khác hoặc chọn bộ lọc Tất cả."
                                        else "Chuẩn Mở chỉ tìm trong danh mục có sẵn. Hãy thử Sintel, Blender hoặc chọn bộ lọc Tất cả.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        if (showChannels && uiState.channels.isNotEmpty()) {
                            items(uiState.channels, key = { "ch_${it.id}" }) { channel ->
                                ChannelResultRow(channel = channel)
                            }
                        }

                        if (showPlaylists && uiState.playlists.isNotEmpty()) {
                            items(uiState.playlists, key = { "pl_${it.id}" }) { playlist ->
                                PlaylistResultRow(playlist = playlist)
                            }
                        }

                        if (showVideos) {
                            items(uiState.videos, key = { "vid_${it.id}" }) { video ->
                                VideoFeedCard(
                                    video = video,
                                    isFavorite = video.id in favoriteIds,
                                    onClick = { viewModel.playVideo(video) },
                                    onToggleFavorite = { viewModel.toggleFavorite(video) },
                                    onAddToPlaylist = { playlistTargetVideo = video }
                                )
                            }
                        }

                        if (uiState.nextPageToken != null && showVideos) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (uiState.isLoadingMore) {
                                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                                    } else {
                                        Button(onClick = { viewModel.loadMoreResults() }) {
                                            Text("Tải thêm kết quả")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(96.dp + LocalFloatingPlayerInset.current))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchResultsList(
    isRefreshing: Boolean,
    canRefresh: Boolean,
    onRefresh: () -> Unit,
    content: LazyListScope.() -> Unit
) {
    val refreshState = rememberPullToRefreshState()
    Box(
        Modifier.fillMaxSize().pullToRefresh(
            isRefreshing = isRefreshing,
            state = refreshState,
            enabled = canRefresh,
            onRefresh = onRefresh
        )
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content
        )
        if (isRefreshing || refreshState.distanceFraction > 0f) {
            PullToRefreshDefaults.Indicator(
                state = refreshState,
                isRefreshing = isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter).testTag("PullToRefreshIndicator")
            )
        }
    }
}

@Composable
private fun ChannelResultRow(channel: ChannelItem) {
    val statusColors = LocalAeroPlayStatusColors.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = channel.avatarUrl,
                contentDescription = channel.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = channel.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (channel.isVerified) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Filled.Verified,
                            contentDescription = "Đã xác minh",
                            tint = statusColors.verifiedBadge,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                val subs = FormatUtils.formatSubscribers(channel.subscriberCount)
                if (subs.isNotBlank()) {
                    Text(
                        text = subs,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistResultRow(playlist: PlaylistItem) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(width = 96.dp, height = 56.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                AsyncImage(
                    model = playlist.thumbnailUrl,
                    contentDescription = playlist.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${playlist.uploaderName} • ${playlist.streamCount} video",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
