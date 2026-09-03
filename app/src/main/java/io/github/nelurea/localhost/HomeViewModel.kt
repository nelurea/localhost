package io.github.nelurea.localhost

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.nelurea.localhost.data.DraftStore
import io.github.nelurea.localhost.data.ImageStore
import io.github.nelurea.localhost.data.PostEntity
import io.github.nelurea.localhost.data.PostRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeViewModel(
    private val repository: PostRepository,
    private val draftStore: DraftStore,
    private val imageStore: ImageStore
) : ViewModel() {
    val posts: StateFlow<List<PostEntity>> = repository.posts.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    private val _draft = MutableStateFlow("")
    val draft: StateFlow<String> = _draft.asStateFlow()

    private val _selectedImagePath =
        MutableStateFlow<String?>(null)

    val selectedImagePath: StateFlow<String?> =
        _selectedImagePath.asStateFlow()

    private var saveDraftJob: Job? = null
    private var draftChangedSinceInit = false

    init {
        viewModelScope.launch {
            val restoredDraft = try {
                withContext(Dispatchers.IO) {
                    draftStore.read()
                }
            } catch (_: Exception) {
                ""
            }

            if (!draftChangedSinceInit) {
                _draft.value = restoredDraft
            }
        }
    }

    fun onDraftChange(text: String) {
        draftChangedSinceInit = true
        _draft.value = text

        saveDraftJob?.cancel()
        saveDraftJob = viewModelScope.launch {
            delay(300)

            try {
                withContext(Dispatchers.IO) {
                    draftStore.write(text)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Keep the current in-memory draft if persistence fails.
            }
        }
    }

    fun selectImage(uri: Uri) {
        viewModelScope.launch {
            try {
                val importedPath = withContext(Dispatchers.IO) {
                    imageStore.importImage(uri)
                }

                val previousPath = _selectedImagePath.value
                _selectedImagePath.value = importedPath

                if (
                    previousPath != null &&
                    previousPath != importedPath
                ) {
                    withContext(Dispatchers.IO) {
                        imageStore.delete(previousPath)
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Keep the current draft and selected image unchanged.
            }
        }
    }

    fun addPost(
        text: String,
        onSaved: () -> Unit
    ) {
        val post = text.trim()
        val imagePath = _selectedImagePath.value

        if (post.isEmpty() && imagePath == null) return

        viewModelScope.launch {
            try {
                repository.addPost(
                    text = post,
                    imagePath = imagePath
                )

                if (_draft.value.trim() == post) {
                    saveDraftJob?.cancel()

                    try {
                        withContext(Dispatchers.IO) {
                            draftStore.clear()
                        }
                    } catch (_: Exception) {
                        // The post is already safely persisted.
                    }

                    _draft.value = ""
                }

                if (_selectedImagePath.value == imagePath) {
                    _selectedImagePath.value = null
                }

                onSaved()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Keep the draft intact if post persistence fails.
            }
        }
    }

    fun deletePost(postId: Long) {
        viewModelScope.launch {
            try {
                repository.deletePost(postId)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Keep the post visible if deletion fails.
            }
        }
    }

    fun restorePost(postId: Long) {
        viewModelScope.launch {
            try {
                repository.restorePost(postId)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Keep the current persisted state if restore fails.
            }
        }
    }

    class Factory(
        private val repository: PostRepository,
        private val draftStore: DraftStore,
        private val imageStore: ImageStore
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (
                modelClass.isAssignableFrom(
                    HomeViewModel::class.java
                )
            ) {
                return HomeViewModel(
                    repository,
                    draftStore,
                    imageStore
                ) as T
            }

            throw IllegalArgumentException(
                "Unknown ViewModel class: ${modelClass.name}"
            )
        }
    }
}

