package dev.jdtech.jellyfin.account.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class JfaGoTokenDto(val token: String)

@Serializable
data class JfaGoMyDetailsDto(
    val username: String,
    val expiry: Long = 0,
    val email: JfaGoContactMethodDto? = null,
)

@Serializable
data class JfaGoContactMethodDto(
    val value: String = "",
    @SerialName("enabled") val contactEnabled: Boolean = false,
)
