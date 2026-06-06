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
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

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

    private val hasChecked = AtomicBoolean(false)

    fun check() {
        if (DataBuildConfig.DRAGON_HUB_URL.isBlank() || !hasChecked.compareAndSet(false, true)) {
            return
        }

        viewModelScope.launch {
            try {
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
                        error = null,
                    )
                }
            } catch (e: Exception) {
                Timber.w(e, "Dragon Hub check failed.")
                hasChecked.set(false)
                _state.update {
                    it.copy(
                        update = null,
                        notifications = emptyList(),
                        showNotifications = false,
                        error = "Dragon Hub check failed.",
                    )
                }
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
        return sortedWith { left, right ->
            val leftCreatedAt = parseCreatedAt(left.createdAt)
            val rightCreatedAt = parseCreatedAt(right.createdAt)

            when {
                leftCreatedAt != null && rightCreatedAt != null -> {
                    val dateComparison = rightCreatedAt.compareTo(leftCreatedAt)
                    if (dateComparison != 0) dateComparison else right.id.compareTo(left.id)
                }
                leftCreatedAt != null -> -1
                rightCreatedAt != null -> 1
                else -> right.id.compareTo(left.id)
            }
        }
    }

    private fun parseCreatedAt(createdAt: String): LocalDateTime? {
        return runCatching { LocalDateTime.parse(createdAt) }.getOrNull()
    }
}

data class DragonHubState(
    val update: DragonHubVersionResponseDto? = null,
    val notifications: List<DragonHubNotificationDto> = emptyList(),
    val showNotifications: Boolean = false,
    val error: String? = null,
)
