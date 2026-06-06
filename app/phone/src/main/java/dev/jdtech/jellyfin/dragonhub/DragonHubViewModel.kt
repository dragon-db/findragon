package dev.jdtech.jellyfin.dragonhub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.data.BuildConfig as DataBuildConfig
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.dragonhub.api.DragonHubApiService
import dev.jdtech.jellyfin.dragonhub.api.dto.DragonHubNotificationDto
import dev.jdtech.jellyfin.dragonhub.api.dto.DragonHubVersionResponseDto
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import java.time.LocalDateTime
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class DragonHubViewModel
@Inject
constructor(
    private val apiService: DragonHubApiService,
    private val appPreferences: AppPreferences,
    private val database: ServerDatabaseDao,
) : ViewModel() {
    private val _state = MutableStateFlow(DragonHubState())
    val state = _state.asStateFlow()

    private var hasChecked = false

    fun check() {
        if (hasChecked || DataBuildConfig.DRAGON_HUB_URL.isBlank()) return
        hasChecked = true

        viewModelScope.launch {
            val update =
                apiService.getVersion()?.takeIf {
                    DragonHubUpdatePolicy.isUpdateAvailable(it.versionCode)
                }
            val notifications =
                currentUsername()?.let { username ->
                    apiService.getNotifications(username)?.notifications.orEmpty().latestFirst()
                } ?: emptyList()

            _state.update {
                it.copy(
                    update = update,
                    notifications = notifications,
                    showNotifications = notifications.isNotEmpty(),
                )
            }
        }
    }

    fun dismissUpdate() {
        _state.update { it.copy(update = null) }
    }

    fun dismissNotifications() {
        _state.update { it.copy(showNotifications = false) }
    }

    private suspend fun currentUsername(): String? =
        withContext(Dispatchers.IO) {
            val serverId =
                appPreferences.getValue(appPreferences.currentServer) ?: return@withContext null
            database.getServerWithAddressAndUser(serverId)?.user?.name
        }

    private fun List<DragonHubNotificationDto>.latestFirst(): List<DragonHubNotificationDto> {
        return sortedWith(
            compareByDescending<DragonHubNotificationDto> { parseCreatedAt(it.createdAt) }
                .thenByDescending { it.id }
        )
    }

    private fun parseCreatedAt(createdAt: String): LocalDateTime? {
        return runCatching { LocalDateTime.parse(createdAt) }.getOrNull()
    }
}

data class DragonHubState(
    val update: DragonHubVersionResponseDto? = null,
    val notifications: List<DragonHubNotificationDto> = emptyList(),
    val showNotifications: Boolean = false,
)
