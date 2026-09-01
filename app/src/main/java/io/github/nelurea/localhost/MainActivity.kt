package io.github.nelurea.localhost

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nelurea.localhost.data.DraftStore
import io.github.nelurea.localhost.data.LocalhostDatabase
import io.github.nelurea.localhost.data.PostEntity
import io.github.nelurea.localhost.data.PostRepository
import io.github.nelurea.localhost.ui.theme.LocalhostTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    private val homeViewModel: HomeViewModel by viewModels {
        val database = LocalhostDatabase.getInstance(applicationContext)

        HomeViewModel.Factory(
            repository = PostRepository(database.postDao()),
            draftStore = DraftStore(applicationContext)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            LocalhostTheme {
                val posts by homeViewModel.posts.collectAsStateWithLifecycle()
                val draft by homeViewModel.draft.collectAsStateWithLifecycle()

                HomeScreen(
                    posts = posts,
                    draft = draft,
                    onDraftChange = homeViewModel::onDraftChange,
                    onPost = { text, onSaved ->
                        homeViewModel.addPost(text, onSaved)
                    }
                )
            }
        }
    }
}

@Composable
fun HomeScreen(
    posts: List<PostEntity>,
    draft: String,
    onDraftChange: (String) -> Unit,
    onPost: (String, () -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = localhostPalette()

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
            val groupedPosts = posts.groupBy { postDate(it.createdAt) }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(
                top = 10.dp,
                bottom = 12.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(
                items = groupedPosts.entries.toList(),
                key = { it.key.toEpochDay() }
            ) { (date, dayPosts) ->
                DayGroup(
                    date = date,
                    posts = dayPosts
                )
            }
        }

            Composer(
                text = draft,
                onTextChange = onDraftChange,
                onPost = {
                    val post = draft.trim()

                    if (post.isNotEmpty()) {
                        onPost(post) {}
                    }
                },
                palette = palette,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            )
        }
    }
}

@Composable
private fun DayGroup(
    date: LocalDate,
    posts: List<PostEntity>,
    modifier: Modifier = Modifier
) {
    val palette = localhostPalette()

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .border(
                width = 1.dp,
                color = palette.groupBorder,
                shape = RoundedCornerShape(18.dp)
            ),
        shape = RoundedCornerShape(18.dp),
        color = palette.groupGlass,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = 8.dp,
                vertical = 8.dp
            ),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 8.dp,
                        end = 8.dp,
                        top = 3.dp,
                        bottom = 1.dp
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(7.dp),
                    shape = androidx.compose.foundation.shape.CircleShape,
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

            posts.forEach { post ->
                TimelinePost(post = post)
            }
        }
    }
}

@Composable
private fun TimelinePost(
    post: PostEntity,
    modifier: Modifier = Modifier
) {
    val palette = localhostPalette()

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

private data class LocalhostPalette(
    val canvasTop: Color,
    val canvasBottom: Color,
    val groupGlass: Color,
    val groupBorder: Color,
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
    onTextChange: (String) -> Unit,
    onPost: () -> Unit,
    palette: LocalhostPalette,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = palette.composerGlass,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = 10.dp,
                vertical = 8.dp
            ),
            verticalAlignment = Alignment.Bottom
        ) {
            FutureActionButton(
                drawableRes = R.drawable.ic_add_soft,
                contentDescription = "Attach file",
                palette = palette
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
                enabled = text.isNotBlank(),
                modifier = Modifier
                    .size(48.dp)
                    .semantics { contentDescription = "Post" },
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.accent,
                    disabledContainerColor = palette.accent.copy(alpha = 0.35f)
                ),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
            ) {
                androidx.compose.foundation.Image(
                    painter = painterResource(R.drawable.ic_send_soft),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
            }
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
                this.contentDescription = "$contentDescription, coming soon"
            },
        shape = CircleShape,
        color = palette.accent.copy(alpha = 0.72f),
        tonalElevation = 0.dp
    ) {
        androidx.compose.foundation.layout.Box(
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.foundation.Image(
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
            onDraftChange = {},
            onPost = { _, onSaved ->
                onSaved()
            }
        )
    }
}
