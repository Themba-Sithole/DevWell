package za.co.devwell.viewmodel

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import za.co.devwell.data.AppContainer
import za.co.devwell.data.avatar.AvatarImage
import za.co.devwell.data.avatar.AvatarRepository

data class AvatarStatus(val busy: Boolean = false, val error: String? = null)

class AvatarViewModel(
    private val repository: AvatarRepository = AppContainer.avatar,
) : ViewModel() {
    val photo: StateFlow<ImageBitmap?> = repository.photo
        .map { bytes -> bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _status = MutableStateFlow(AvatarStatus())
    val status: StateFlow<AvatarStatus> = _status.asStateFlow()

    fun onPhotoPicked(context: Context, uri: Uri) = change {
        val jpeg = withContext(Dispatchers.IO) { AvatarImage.prepare(context.applicationContext, uri) }
            ?: throw IllegalArgumentException("That image couldn't be read. Try a different photo.")
        repository.upload(jpeg)
    }

    fun removePhoto() = change { repository.remove() }

    fun clearError() {
        _status.update { it.copy(error = null) }
    }

    private fun change(block: suspend () -> Unit) {
        if (_status.value.busy) return
        _status.value = AvatarStatus(busy = true)
        viewModelScope.launch {
            try {
                block()
                _status.value = AvatarStatus()
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalArgumentException) {
                _status.value = AvatarStatus(error = e.message)
            } catch (_: Exception) {
                _status.value = AvatarStatus(
                    error = "Couldn't update your photo. Check your connection and try again.",
                )
            }
        }
    }
}