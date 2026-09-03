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
import kotlinx.coroutines.flow.map
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

    val postImagesByPostId: StateFlow<Map<Long, List<String>>> =
        repository.postsWithImages
            .map { relations ->
                relations.associate { relation ->
                    relation.post.id to
                        relation.images
                            .sortedBy { it.position }
                            .map { it.imagePath }
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyMap()
            )

    private val _draft = MutableStateFlow("")
    val draft: StateFlow<String> = _draft.asStateFlow()

    private val _selectedImagePaths =
        MutableStateFlow<List<String>>(emptyList())

    val selectedImagePaths: StateFlow<List<String>> =
        _selectedImagePaths.asStateFlow()

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

    fun selectImages(uris: List<Uri>) {
        if (uris.isEmpty()) return

        val remainingSlots =
            (MAX_SELECTED_IMAGES - _selectedImagePaths.value.size)
                .coerceAtLeast(0)

        if (remainingSlots == 0) return

        val selectedUris = uris.take(remainingSlots)

        viewModelScope.launch {
            val importedPaths = mutableListOf<String>()

            try {
                withContext(Dispatchers.IO) {
                    selectedUris.forEach { uri ->
                        importedPaths += imageStore.importImage(uri)
                    }
                }

                _selectedImagePaths.value =
                    _selectedImagePaths.value + importedPaths
            } catch (error: CancellationException) {
                withContext(Dispatchers.IO) {
                    importedPaths.forEach(imageStore::delete)
                }
                throw error
            } catch (_: Exception) {
                withContext(Dispatchers.IO) {
                    importedPaths.forEach(imageStore::delete)
                }
                // Keep the current draft and previous selections unchanged.
            }
        }
    }

    fun removeSelectedImage(imagePath: String) {
        if (imagePath !in _selectedImagePaths.value) return

        _selectedImagePaths.value =
            _selectedImagePaths.value.filterNot { it == imagePath }

        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    imageStore.delete(imagePath)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // The composer state is already updated.
            }
        }
    }

    fun addPost(
        text: String,
        onSaved: () -> Unit
    ) {
        val post = text.trim()
        val imagePaths = _selectedImagePaths.value

        if (post.isEmpty() && imagePaths.isEmpty()) return

        viewModelScope.launch {
            try {
                repository.addPost(
                    text = post,
                    imagePaths = imagePaths
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

                if (_selectedImagePaths.value == imagePaths) {
                    _selectedImagePaths.value = emptyList()
                }

                onSaved()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Keep draft and image selections if persistence fails.
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

    private companion object {
        const val MAX_SELECTED_IMAGES = 10
    }
}
