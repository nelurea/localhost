package io.github.nelurea.localhost

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nelurea.localhost.data.DraftStore
import io.github.nelurea.localhost.data.LocalhostDatabase
import io.github.nelurea.localhost.data.PostEntity
import io.github.nelurea.localhost.data.PostRepository
import io.github.nelurea.localhost.ui.theme.LocalhostTheme

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
    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
    ) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(
                items = posts,
                key = { it.id }
            ) { post ->
                Text(
                    text = post.text,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
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
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        )
    }
}

@Composable
private fun Composer(
    text: String,
    onTextChange: (String) -> Unit,
    onPost: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = 12.dp,
                vertical = 10.dp
            ),
            verticalAlignment = Alignment.Bottom
        ) {
            TextField(
                value = text,
                onValueChange = onTextChange,
                placeholder = {
                    Text("Write something...")
                },
                modifier = Modifier.weight(1f),
                minLines = 1,
                maxLines = 5,
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(
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
                contentPadding = ButtonDefaults.ContentPadding
            ) {
                Text("↑")
            }
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
