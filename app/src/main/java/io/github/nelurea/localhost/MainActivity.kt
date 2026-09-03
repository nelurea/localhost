package io.github.nelurea.localhost

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.expandVertically
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nelurea.localhost.data.DraftStore
import io.github.nelurea.localhost.data.ImageStore
import io.github.nelurea.localhost.data.LocalhostDatabase
import io.github.nelurea.localhost.data.PostEntity
import io.github.nelurea.localhost.data.PostRepository
import io.github.nelurea.localhost.ui.theme.LocalhostTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val homeViewModel: HomeViewModel by viewModels {
        val database = LocalhostDatabase.getInstance(applicationContext)

        HomeViewModel.Factory(
            repository = PostRepository(
                database = database,
                postDao = database.postDao(),
                postImageDao = database.postImageDao()
            ),
            draftStore = DraftStore(applicationContext),
            imageStore = ImageStore(applicationContext)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            LocalhostTheme {
                val posts by homeViewModel.posts.collectAsStateWithLifecycle()
                val draft by homeViewModel.draft.collectAsStateWithLifecycle()
                val selectedImagePaths by
                    homeViewModel.selectedImagePaths.collectAsStateWithLifecycle()
                val postImagesByPostId by
                    homeViewModel.postImagesByPostId.collectAsStateWithLifecycle()

                HomeScreen(
                    posts = posts,
                    draft = draft,
                    selectedImagePaths = selectedImagePaths,
                                        postImagesByPostId = postImagesByPostId,
onDraftChange = homeViewModel::onDraftChange,
                    onSelectImages = homeViewModel::selectImages,
                    onRemoveSelectedImage =
                        homeViewModel::removeSelectedImage,
                    onPost = { text, onSaved ->
                        homeViewModel.addPost(text, onSaved)
                    },
                    onDeletePost = homeViewModel::deletePost,
                    onRestorePost = homeViewModel::restorePost
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    posts: List<PostEntity>,
    draft: String,
    selectedImagePaths: List<String>,
        postImagesByPostId: Map<Long, List<String>>,
onDraftChange: (String) -> Unit,
    onSelectImages: (List<Uri>) -> Unit,
    onRemoveSelectedImage: (String) -> Unit,
    onPost: (String, () -> Unit) -> Unit,
    onDeletePost: (Long) -> Unit,
    onRestorePost: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = localhostPalette()

    var viewerImagePaths by remember {
        mutableStateOf<List<String>?>(null)
    }
val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(
            maxItems = 10
        )
    ) { uris ->
        if (uris.isNotEmpty()) {
            onSelectImages(uris)
        }
    }

    // Only the most recently swiped post gets the three-second
    // cancellation window.
    var pendingDeletedPost by remember {
        mutableStateOf<PostEntity?>(null)
    }

    var animateNextPost by remember {
        mutableStateOf(false)
    }

    var entrancePostId by remember {
        mutableStateOf<Long?>(null)
    }

    LaunchedEffect(animateNextPost, posts.firstOrNull()?.id) {
        if (animateNextPost) {
            posts.firstOrNull()?.let { newestPost ->
                entrancePostId = newestPost.id
                animateNextPost = false
            }
        }
    }

    // A post can remain in the Room Flow for a brief moment after
    // deletePost() is called. Keep committed IDs hidden during that gap
    // so deleted cards never flash back into the timeline.
    val committingDeletedPostIds = remember {
        mutableStateSetOf<Long>()
    }

    LaunchedEffect(posts) {
        val visibleIds = posts
            .mapTo(mutableSetOf()) { it.id }

        committingDeletedPostIds
            .filter { it !in visibleIds }
            .forEach { postId ->
                committingDeletedPostIds.remove(postId)
            }
    }

    // Start the actual delete only after the three-second grace period.
    LaunchedEffect(pendingDeletedPost?.id) {
        val pendingPost =
            pendingDeletedPost ?: return@LaunchedEffect

        delay(3_000)

        if (pendingDeletedPost?.id == pendingPost.id) {
            committingDeletedPostIds.add(pendingPost.id)
            pendingDeletedPost = null
            onDeletePost(pendingPost.id)
        }
    }

    val timelinePosts = buildList {
        posts
            .filterNot { it.id in committingDeletedPostIds }
            .forEach { add(it) }

        pendingDeletedPost?.let { pendingPost ->
            if (none { it.id == pendingPost.id }) {
                add(pendingPost)
            }
        }
    }.sortedWith(
        compareByDescending<PostEntity> { it.createdAt }
            .thenByDescending { it.id }
    )

    val deletePost: (PostEntity) -> Unit = { post ->
        pendingDeletedPost?.let { previousPending ->
            if (previousPending.id != post.id) {
                // A new delete replaces the previous Undo candidate.
                // Commit the older delete immediately.
                committingDeletedPostIds.add(previousPending.id)
                onDeletePost(previousPending.id)
            }
        }

        pendingDeletedPost = post
    }

    val cancelPendingDelete: () -> Unit = {
        // Nothing has been deleted from Room yet.
        pendingDeletedPost = null
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        palette.canvasTop,
                        palette.canvasBottom
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
        ) {
            val groupedPosts = timelinePosts.groupBy {
                postDate(it.createdAt)
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(
                    top = 10.dp,
                    bottom = 12.dp
                ),
                verticalArrangement = Arrangement.Top
            ) {
                groupedPosts.entries.forEachIndexed { dayIndex, entry ->
                    val date = entry.key
                    val dayPosts = entry.value
                    val dayTone = if (dayIndex % 2 == 0) {
                        DayTone.Primary
                    } else {
                        DayTone.Secondary
                    }

                    stickyHeader(
                        key = "date-${date.toEpochDay()}"
                    ) {
                        StickyDateHeader(
                            date = date,
                            palette = palette,
                            dayTone = dayTone
                        )
                    }

                    dayPosts.forEachIndexed { postIndex, post ->
                        item(
                            key = post.id
                        ) {
                            TimelinePostContainer(
                                post = post,
                                imagePaths =
                                    postImagesByPostId[post.id]
                                        .orEmpty()
                                        .ifEmpty {
                                            post.imagePath
                                                ?.let(::listOf)
                                                .orEmpty()
                                        },
                                onOpenImages = { imagePaths ->
                                    viewerImagePaths = imagePaths
                                },
                                pendingDelete =
                                    post.id == pendingDeletedPost?.id,
                                onDelete = {
                                    deletePost(post)
                                },
                                onRestore = cancelPendingDelete,
                                dayTone = dayTone,
                                firstInDay = postIndex == 0,
                                lastInDay =
                                    postIndex == dayPosts.lastIndex,
                                animateEntrance =
                                    post.id == entrancePostId
                            )
                        }
                    }
                }
            }

            Composer(
                text = draft,
                selectedImagePaths = selectedImagePaths,
                onTextChange = onDraftChange,
                onRemoveImage = onRemoveSelectedImage,
                onAttachImage = {
                    imagePicker.launch(
                        PickVisualMediaRequest(
                            ActivityResultContracts
                                .PickVisualMedia
                                .ImageOnly
                        )
                    )
                },
                onPost = {
                    val post = draft.trim()

                    if (
                        post.isNotEmpty() ||
                        selectedImagePaths.isNotEmpty()
                    ) {
                        onPost(post) {
                            animateNextPost = true
                        }
                    }
                },
                palette = palette,
                modifier = Modifier.fillMaxWidth()
            )

            BottomNavigationBar(
                palette = palette,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            )
        }


        viewerImagePaths?.let { imagePaths ->
            PostedImageViewer(
                imagePaths = imagePaths,
                onDismiss = {
                    viewerImagePaths = null
                }
            )
        }
    }
}
private enum class DayTone {
    Primary,
    Secondary
}

@Composable
private fun StickyDateHeader(
    date: LocalDate,
    palette: LocalhostPalette,
    dayTone: DayTone,
    modifier: Modifier = Modifier
) {
    val dayBackground = when (dayTone) {
        DayTone.Primary -> palette.dayGroupPrimary
        DayTone.Secondary -> palette.dayGroupSecondary
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = 10.dp,
                top = 10.dp,
                end = 10.dp
            ),
        shape = RoundedCornerShape(
            topStart = 18.dp,
            topEnd = 18.dp,
            bottomStart = 0.dp,
            bottomEnd = 0.dp
        ),
        color = dayBackground,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(
                start = 14.dp,
                top = 10.dp,
                end = 14.dp,
                bottom = 7.dp
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(7.dp),
                shape = CircleShape,
                color = palette.warmAccent
            ) {}

            Spacer(Modifier.width(8.dp))

            Text(
                text = formatDateLabel(date),
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = palette.metaStrong
            )
        }
    }
}
@Composable
private fun TimelinePostContainer(
    post: PostEntity,
    imagePaths: List<String>,
    onOpenImages: (List<String>) -> Unit,
    pendingDelete: Boolean,
    onDelete: () -> Unit,
    onRestore: () -> Unit,
    dayTone: DayTone,
    firstInDay: Boolean,
    lastInDay: Boolean,
    animateEntrance: Boolean,
    modifier: Modifier = Modifier
) {
    val palette = localhostPalette()

    val dayBackground = when (dayTone) {
        DayTone.Primary -> palette.dayGroupPrimary
        DayTone.Secondary -> palette.dayGroupSecondary
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = 10.dp,
                end = 10.dp,
                bottom = if (lastInDay) 10.dp else 0.dp
            )
            .background(
                color = dayBackground,
                shape = RoundedCornerShape(
                    topStart = 0.dp,
                    topEnd = 0.dp,
                    bottomStart = if (lastInDay) 18.dp else 0.dp,
                    bottomEnd = if (lastInDay) 18.dp else 0.dp
                )
            )
            .padding(
                start = 8.dp,
                end = 8.dp,
                bottom = if (lastInDay) 8.dp else 7.dp
            )
    ) {
        if (animateEntrance) {
            val visibilityState = remember(post.id) {
                MutableTransitionState(false).apply {
                    targetState = true
                }
            }

            AnimatedVisibility(
                visibleState = visibilityState,
                enter =
                    expandVertically(
                        expandFrom = Alignment.Top,
                        animationSpec = tween(
                            durationMillis = 280,
                            easing = FastOutSlowInEasing
                        ),
                        clip = true
                    ) +
                    fadeIn(
                        animationSpec = tween(
                            durationMillis = 180,
                            delayMillis = 35
                        )
                    )
            ) {
                TimelinePost(
                    post = post,
                    imagePaths = imagePaths,
                    onOpenImages = onOpenImages,
                    pendingDelete = pendingDelete,
            onDelete = onDelete,
                    onRestore = onRestore
                )
            }
        } else {
            TimelinePost(
                    post = post,
                    imagePaths = imagePaths,
                    onOpenImages = onOpenImages,
                    pendingDelete = pendingDelete,
                onDelete = onDelete,
                onRestore = onRestore
            )
        }
    }
}
@Composable
private fun TimelinePost(
    post: PostEntity,
    imagePaths: List<String>,
    onOpenImages: (List<String>) -> Unit,
    pendingDelete: Boolean,
    onDelete: () -> Unit,
    onRestore: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (pendingDelete) {
        PendingDeletePost(
            post = post,
            onRestore = onRestore,
            modifier = modifier
        )
    } else {
        ActiveTimelinePost(
            post = post,
            imagePaths = imagePaths,
            onOpenImages = onOpenImages,
            onDelete = onDelete,
            modifier = modifier
        )
    }
}

@Composable
private fun ActiveTimelinePost(
    post: PostEntity,
    imagePaths: List<String>,
    onOpenImages: (List<String>) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = localhostPalette()

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
            }

            false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(
                        label = "Delete post",
                        action = {
                            onDelete()
                            true
                        }
                    )
                )
            },
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            when (dismissState.dismissDirection) {
                SwipeToDismissBoxValue.EndToStart -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                color = MaterialTheme.colorScheme.error,
                                shape = RoundedCornerShape(13.dp)
                            )
                            .padding(horizontal = 20.dp),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Icon(
                            painter = painterResource(
                                R.drawable.ic_delete_soft
                            ),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onError,
                            modifier = Modifier.size(25.dp)
                        )
                    }
                }

                else -> Unit
            }
        }
    ) {
        TimelinePostSurface(
            post = post,
            imagePaths = imagePaths,
            onOpenImages = onOpenImages,palette = palette
        )
    }
}

@Composable
private fun PendingDeletePost(
    post: PostEntity,
    onRestore: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = localhostPalette()

    var dotCount by remember(post.id) {
        mutableStateOf(1)
    }

    LaunchedEffect(post.id) {
        while (true) {
            delay(500)

            dotCount =
                if (dotCount >= 3) {
                    1
                } else {
                    dotCount + 1
                }
        }
    }

    val restoreState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.StartToEnd) {
                onRestore()
            }

            false
        }
    )

    SwipeToDismissBox(
        state = restoreState,
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(
                        label = "Cancel deletion",
                        action = {
                            onRestore()
                            true
                        }
                    )
                )
            },
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = false,
        backgroundContent = {
            when (restoreState.dismissDirection) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                color = palette.accent.copy(
                                    alpha = 0.32f
                                ),
                                shape = RoundedCornerShape(13.dp)
                            )
                            .padding(horizontal = 20.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Icon(
                            painter = painterResource(
                                R.drawable.ic_restore_soft
                            ),
                            contentDescription = null,
                            tint = palette.metaStrong,
                            modifier = Modifier.size(25.dp)
                        )
                    }
                }

                else -> Unit
            }
        }
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.error.copy(
                        alpha = 0.38f
                    ),
                    shape = RoundedCornerShape(13.dp)
                ),
            shape = RoundedCornerShape(13.dp),
            color = MaterialTheme.colorScheme.errorContainer.copy(
                alpha = 0.22f
            ),
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier.padding(
                    horizontal = 16.dp,
                    vertical = 12.dp
                )
            ) {
                Text(
                    text = "Deleting" + ".".repeat(dotCount),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.error
                )

                Text(
                    text = post.text,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        lineHeight = 24.sp
                    ),
                    color = palette.meta,
                    modifier = Modifier
                        .fillMaxWidth(0.90f)
                        .padding(top = 5.dp)
                )
            }
        }
    }
}
@Composable
private fun TimelinePostSurface(
    post: PostEntity,
    imagePaths: List<String>,
    onOpenImages: (List<String>) -> Unit,
    palette: LocalhostPalette,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = palette.postBorder,
                shape = RoundedCornerShape(13.dp)
            ),
        shape = RoundedCornerShape(13.dp),
        color = palette.postPaper,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = 16.dp,
                vertical = 12.dp
            )
        ) {
            Text(
                text = formatPostTime(post.createdAt),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = palette.meta
            )

            if (post.text.isNotEmpty()) {
                Text(
                    text = post.text,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        lineHeight = 24.sp
                    ),
                    color = palette.ink,
                    modifier = Modifier
                        .fillMaxWidth(0.90f)
                        .padding(top = 5.dp)
                )
            }

            imagePaths.firstOrNull()?.let { imagePath ->
                TimelineImage(
                    imagePath = imagePath,
                    imageCount = imagePaths.size,
                    onClick = {
                        onOpenImages(imagePaths)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            top = if (post.text.isEmpty()) {
                                6.dp
                            } else {
                                10.dp
                            }
                        )
                )
            }
        }
    }
}
@Composable
private fun TimelineImage(
    imagePath: String,
    imageCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var bitmap by remember(imagePath) {
        mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(
            null
        )
    }

    LaunchedEffect(imagePath) {
        bitmap = withContext(Dispatchers.IO) {
            BitmapFactory
                .decodeFile(imagePath)
                ?.asImageBitmap()
        }
    }

    bitmap?.let { image ->
        Box(
            modifier = modifier
                .clickable(onClick = onClick)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                tonalElevation = 0.dp
            ) {
                Image(
                    bitmap = image,
                    contentDescription = "Attached image",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentScale = ContentScale.Crop
                )
            }

            if (imageCount > 1) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = Color.Black.copy(alpha = 0.38f),
                    tonalElevation = 0.dp
                ) {
                    Text(
                        text = "1/$imageCount",
                        color = Color.White.copy(alpha = 0.92f),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        modifier = Modifier.padding(
                            horizontal = 8.dp,
                            vertical = 4.dp
                        )
                    )
                }
            }
        }
    }
}
@Composable
private fun PostedImageViewer(
    imagePaths: List<String>,
    onDismiss: () -> Unit
) {
    val pagerState = rememberPagerState(
        pageCount = { imagePaths.size }
    )

    BackHandler(onBack = onDismiss)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.94f))
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            ViewerImage(
                imagePath = imagePaths[page],
                modifier = Modifier.fillMaxSize()
            )
        }

        if (imagePaths.size > 1) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 10.dp, end = 12.dp),
                shape = RoundedCornerShape(10.dp),
                color = Color.Black.copy(alpha = 0.38f),
                tonalElevation = 0.dp
            ) {
                Text(
                    text = "${pagerState.currentPage + 1}/${imagePaths.size}",
                    color = Color.White.copy(alpha = 0.92f),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    modifier = Modifier.padding(
                        horizontal = 8.dp,
                        vertical = 4.dp
                    )
                )
            }
        }
    }
}

@Composable
private fun ViewerImage(
    imagePath: String,
    modifier: Modifier = Modifier
) {
    var bitmap by remember(imagePath) {
        mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)
    }

    LaunchedEffect(imagePath) {
        bitmap = withContext(Dispatchers.IO) {
            BitmapFactory
                .decodeFile(imagePath)
                ?.asImageBitmap()
        }
    }

    bitmap?.let { image ->
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = image,
                contentDescription = "Posted image",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }
    }
}

private fun postDate(
    createdAt: Long,
    zoneId: ZoneId = ZoneId.systemDefault()
): LocalDate {
    return Instant
        .ofEpochMilli(createdAt)
        .atZone(zoneId)
        .toLocalDate()
}

private fun formatPostTime(
    createdAt: Long,
    zoneId: ZoneId = ZoneId.systemDefault()
): String {
    return Instant
        .ofEpochMilli(createdAt)
        .atZone(zoneId)
        .format(DateTimeFormatter.ofPattern("HH:mm"))
}

private fun formatDateLabel(
    date: LocalDate,
    today: LocalDate = LocalDate.now()
): String {
    return when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> {
            val pattern = if (date.year == today.year) {
                "M/d"
            } else {
                "yyyy/M/d"
            }

            date.format(DateTimeFormatter.ofPattern(pattern))
        }
    }
}

@Composable
private fun BottomNavigationBar(
    palette: LocalhostPalette,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.padding(
            start = 10.dp,
            end = 10.dp,
            bottom = 6.dp
        ),
        shape = RoundedCornerShape(22.dp),
        color = palette.composerGlass,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            BottomNavigationItem(
                drawableRes = R.drawable.ic_home_soft,
                contentDescription = "Home",
                selected = true,
                palette = palette,
                modifier = Modifier.weight(1f)
            )

            BottomNavigationItem(
                drawableRes = R.drawable.ic_search_soft,
                contentDescription = "Search",
                selected = false,
                palette = palette,
                modifier = Modifier.weight(1f)
            )

            BottomNavigationItem(
                drawableRes = R.drawable.ic_settings_soft,
                contentDescription = "Settings",
                selected = false,
                palette = palette,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun BottomNavigationItem(
    drawableRes: Int,
    contentDescription: String,
    selected: Boolean,
    palette: LocalhostPalette,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(17.dp),
        color = if (selected) {
            palette.accent.copy(alpha = 0.18f)
        } else {
            Color.Transparent
        },
        tonalElevation = 0.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp)
                .semantics {
                    this.contentDescription = contentDescription
                },
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.foundation.Image(
                painter = painterResource(drawableRes),
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

private data class LocalhostPalette(
    val canvasTop: Color,
    val canvasBottom: Color,
    val groupGlass: Color,
    val groupBorder: Color,
    val dayGroupPrimary: Color,
    val dayGroupSecondary: Color,
    val postPaper: Color,
    val postBorder: Color,
    val composerGlass: Color,
    val inputFill: Color,
    val accent: Color,
    val ink: Color,
    val meta: Color,
    val metaStrong: Color,
    val warmAccent: Color
)

@Composable
private fun localhostPalette(): LocalhostPalette {
    return if (isSystemInDarkTheme()) {
        LocalhostPalette(
            canvasTop = Color(0xFF2C2B33),
            canvasBottom = Color(0xFF333540),
            groupGlass = Color(0xE63B3C48),
            groupBorder = Color(0x665C5D70),
            dayGroupPrimary = Color(0xFF343A48),
            dayGroupSecondary = Color(0xFF443A48),
            postPaper = Color(0xF2464652),
            postBorder = Color(0x665F6070),
            composerGlass = Color(0xF2383843),
            inputFill = Color(0xFF454651),
            accent = Color(0xFF9699C8),
            ink = Color(0xFFF0ECF3),
            meta = Color(0xFFD3CCD9),
            metaStrong = Color(0xFFE2DCE8),
            warmAccent = Color(0xFFD6A098)
        )
    } else {
        LocalhostPalette(
            canvasTop = Color(0xFFF2F1F6),
            canvasBottom = Color(0xFFE8EEF2),
            groupGlass = Color(0xCCDEE5EE),
            groupBorder = Color(0x99CDD5DF),
            dayGroupPrimary = Color(0xFFDCE8F2),
            dayGroupSecondary = Color(0xFFEADDEA),
            postPaper = Color(0xF7FAF9FB),
            postBorder = Color(0x99D8D7E0),
            composerGlass = Color(0xF2F0EFF4),
            inputFill = Color(0xFFF7F6F9),
            accent = Color(0xFF7D82AF),
            ink = Color(0xFF4E4A55),
            meta = Color(0xFF6E6878),
            metaStrong = Color(0xFF5C5667),
            warmAccent = Color(0xFFC98F87)
        )
    }
}

@Composable
private fun Composer(
    text: String,
    selectedImagePaths: List<String>,
    onTextChange: (String) -> Unit,
    onRemoveImage: (String) -> Unit,
    onAttachImage: () -> Unit,
    onPost: () -> Unit,
    palette: LocalhostPalette,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = palette.composerGlass,
        tonalElevation = 0.dp
    ) {
        Column {
            if (selectedImagePaths.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(
                        start = 10.dp,
                        top = 8.dp,
                        end = 10.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = selectedImagePaths,
                        key = { it }
                    ) { imagePath ->
                        ComposerImagePreview(
                            imagePath = imagePath,
                            onRemove = {
                                onRemoveImage(imagePath)
                            },
                            palette = palette,
                            modifier = Modifier.size(132.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.padding(
                    horizontal = 10.dp,
                    vertical = 8.dp
                ),
                verticalAlignment = Alignment.Bottom
            ) {
                ComposerActionButton(
                    drawableRes = R.drawable.ic_add_soft,
                    contentDescription = "Attach image",
                    palette = palette,
                    onClick = onAttachImage
                )

                Spacer(Modifier.width(6.dp))

                FutureActionButton(
                    drawableRes = R.drawable.ic_emoji_soft,
                    contentDescription = "Emoji palette",
                    palette = palette
                )

                Spacer(Modifier.width(8.dp))

                TextField(
                    value = text,
                    onValueChange = onTextChange,
                    placeholder = {
                        Text(
                            text = "Write something…",
                            style = MaterialTheme.typography.bodyLarge,
                            color = palette.meta
                        )
                    },
                    modifier = Modifier.weight(1f),
                    minLines = 1,
                    maxLines = 5,
                    shape = RoundedCornerShape(18.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = palette.ink
                    ),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = palette.inputFill,
                        unfocusedContainerColor = palette.inputFill,
                        focusedTextColor = palette.ink,
                        unfocusedTextColor = palette.ink,
                        cursorColor = palette.accent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                        errorIndicatorColor = Color.Transparent
                    )
                )

                Spacer(Modifier.width(8.dp))

                Button(
                    onClick = onPost,
                    enabled =
                        text.isNotBlank() ||
                        selectedImagePaths.isNotEmpty(),
                    modifier = Modifier
                        .size(48.dp)
                        .semantics {
                            contentDescription = "Post"
                        },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.accent,
                        disabledContainerColor =
                            palette.accent.copy(alpha = 0.35f)
                    ),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Image(
                        painter = painterResource(
                            R.drawable.ic_send_soft
                        ),
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ComposerImagePreview(
    imagePath: String,
    onRemove: () -> Unit,
    palette: LocalhostPalette,
    modifier: Modifier = Modifier
) {
    var bitmap by remember(imagePath) {
        mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(
            null
        )
    }

    LaunchedEffect(imagePath) {
        bitmap = withContext(Dispatchers.IO) {
            BitmapFactory
                .decodeFile(imagePath)
                ?.asImageBitmap()
        }
    }

    bitmap?.let { image ->
        Box(
            modifier = modifier
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(14.dp),
                tonalElevation = 0.dp
            ) {
                Image(
                    bitmap = image,
                    contentDescription = "Selected image",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            Surface(
                onClick = onRemove,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(48.dp)
                    .semantics {
                        contentDescription = "Remove selected image"
                    },
                shape = CircleShape,
                color = palette.composerGlass.copy(alpha = 0.92f),
                tonalElevation = 0.dp
            ) {
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(
                            R.drawable.ic_delete_soft
                        ),
                        contentDescription = null,
                        tint = palette.metaStrong,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ComposerActionButton(
    drawableRes: Int,
    contentDescription: String,
    palette: LocalhostPalette,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .semantics {
                this.contentDescription =
                    contentDescription
            },
        shape = CircleShape,
        color = palette.accent.copy(alpha = 0.72f),
        tonalElevation = 0.dp
    ) {
        Box(
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(drawableRes),
                contentDescription = null,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun FutureActionButton(
    drawableRes: Int,
    contentDescription: String,
    palette: LocalhostPalette
) {
    Surface(
        modifier = Modifier
            .size(48.dp)
            .semantics {
                this.contentDescription =
                    "$contentDescription, coming soon"
            },
        shape = CircleShape,
        color = palette.accent.copy(alpha = 0.72f),
        tonalElevation = 0.dp
    ) {
        Box(
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(drawableRes),
                contentDescription = null,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    LocalhostTheme {
        HomeScreen(
            posts = listOf(
                PostEntity(
                    id = 1,
                    createdAt = 0,
                    text = "A saved thought"
                )
            ),
            draft = "",
            selectedImagePaths = emptyList(),
                        postImagesByPostId = emptyMap(),
onDraftChange = {},
            onSelectImages = {},
            onRemoveSelectedImage = {},
            onPost = { _, onSaved ->
                onSaved()
            },
            onDeletePost = {},
            onRestorePost = {}
        )
    }
}
