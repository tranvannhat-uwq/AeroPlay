package com.aeroplay.app.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.imageLoader
import com.aeroplay.app.core.model.AppSettings
import com.aeroplay.app.core.model.ContentSourceType
import com.aeroplay.app.core.model.ThemeMode
import com.aeroplay.app.data.repository.PersonalizedRecommendations
import com.aeroplay.app.data.repository.SettingsRepository
import com.aeroplay.app.data.youtube.YouTubePolicyFeatureFlags
import com.aeroplay.app.updates.AppUpdateManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val personalizedRecommendations: PersonalizedRecommendations,
    val appUpdates: AppUpdateManager
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    private val _statusToastMessage = MutableStateFlow<String?>(null)
    val statusToastMessage: StateFlow<String?> = _statusToastMessage.asStateFlow()

    fun clearStatusMessage() {
        _statusToastMessage.value = null
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            settingsRepository.setThemeMode(mode)
        }
    }

    fun setSourceType(sourceType: ContentSourceType) {
        viewModelScope.launch {
            settingsRepository.setActiveSourceType(sourceType, warningAccepted = true)
            _statusToastMessage.value = "Đã chuyển sang: ${sourceType.displayName}"
        }
    }

    fun setDefaultQuality(qualityLabel: String) {
        viewModelScope.launch {
            settingsRepository.setDefaultQuality(qualityLabel)
        }
    }

    fun setDataSaver(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setDataSaverEnabled(enabled)
        }
    }

    fun setDefaultSpeed(speed: Float) {
        viewModelScope.launch {
            settingsRepository.setDefaultPlaybackSpeed(speed)
        }
    }

    fun setAutoAudioOnly(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAutoAudioOnlyWhenBackground(enabled)
        }
    }

    fun setPauseOnHeadphoneDisconnect(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setPauseOnHeadphoneDisconnect(enabled)
        }
    }

    fun setPipEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setPipEnabled(enabled)
        }
    }

    fun setBackgroundPlayback(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setBackgroundPlaybackEnabled(enabled)
        }
    }

    fun setAutoPlaySimilarMusic(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAutoPlaySimilarMusic(enabled)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            personalizedRecommendations.resetPersonalization()
            _statusToastMessage.value = "Đã xóa lịch sử xem, tìm kiếm và dữ liệu cá nhân hóa. Yêu thích và danh sách phát vẫn được giữ."
        }
    }

    @OptIn(coil.annotation.ExperimentalCoilApi::class)
    fun clearAppCache() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    context.imageLoader.memoryCache?.clear()
                    context.imageLoader.diskCache?.clear()
                    context.cacheDir.listFiles()?.forEach { file ->
                        if (file.name != "aeroplay_settings") {
                            file.deleteRecursively()
                        }
                    }
                }
            }
            _statusToastMessage.value = "Đã dọn sạch bộ nhớ đệm hình ảnh và mạng."
        }
    }
}
