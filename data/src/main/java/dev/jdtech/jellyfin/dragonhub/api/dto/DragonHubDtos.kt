package dev.jdtech.jellyfin.dragonhub.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DragonHubVersionResponseDto(
    @SerialName("latest_version") val latestVersion: String,
    @SerialName("version_code") val versionCode: Int,
    val changelog: String = "",
    val notes: String = "",
    @SerialName("apk_url") val apkUrl: String = "",
    @SerialName("force_update") val forceUpdate: Boolean = false,
)

@Serializable
data class DragonHubNotificationsResponseDto(
    val notifications: List<DragonHubNotificationDto> = emptyList()
)

@Serializable
data class DragonHubNotificationDto(
    val id: Int,
    val message: String,
    @SerialName("created_at") val createdAt: String = "",
)
