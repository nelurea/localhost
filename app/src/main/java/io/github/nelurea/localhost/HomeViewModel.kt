package io.github.nelurea.localhost

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.nelurea.localhost.data.PostEntity
import io.github.nelurea.localhost.data.PostRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(
    private val repository: PostRepository
) : ViewModel() {
    val posts: StateFlow<List<PostEntity>> = repository.posts.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    fun addPost(
        text: String,
        onSaved: () -> Unit
    ) {
        val post = text.trim()
        if (post.isEmpty()) return

        viewModelScope.launch {
            try {
                repository.addPost(post)
                onSaved()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Keep the draft intact if persistence fails.
            }
        }
    }

    class Factory(
        private val repository: PostRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
                return HomeViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
