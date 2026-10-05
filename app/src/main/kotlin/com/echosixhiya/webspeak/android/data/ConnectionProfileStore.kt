package com.echosixhiya.webspeak.android.data

import android.content.Context
import androidx.core.content.edit
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.Base64

/** Non-secret defaults only; passwords and invitation tokens are deliberately never persisted. */
class ConnectionProfileStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun gatewayUrl(): String = preferences.getString(KEY_GATEWAY, "").orEmpty()
    fun nickname(): String = preferences.getString(KEY_NICKNAME, "").orEmpty()
    fun target(): String = preferences.getString(KEY_TARGET, "").orEmpty()

    fun save(gatewayUrl: String, nickname: String, target: String) {
        preferences.edit {
            putString(KEY_GATEWAY, gatewayUrl.take(512))
            putString(KEY_NICKNAME, nickname.take(30))
            putString(KEY_TARGET, target.take(255))
        }
    }

    fun favorites(): List<FavoriteServerProfile> = preferences.getString(KEY_FAVORITES, "")
        .orEmpty()
        .lineSequence()
        .mapNotNull(FavoriteServerProfileCodec::decode)
        .toList()
        .let(FavoriteServerProfiles::unique)

    fun addFavorite(profile: FavoriteServerProfile): List<FavoriteServerProfile> =
        FavoriteServerProfiles.upsert(favorites(), profile).also(::persistFavorites)

    fun removeFavorite(profile: FavoriteServerProfile): List<FavoriteServerProfile> =
        FavoriteServerProfiles.remove(favorites(), profile).also(::persistFavorites)

    private fun persistFavorites(profiles: List<FavoriteServerProfile>) {
        preferences.edit {
            putString(KEY_FAVORITES, profiles.joinToString("\n", transform = FavoriteServerProfileCodec::encode))
        }
    }

    companion object {
        private const val PREFERENCES = "webspeak_connection_profile"
        private const val KEY_GATEWAY = "gateway"
        private const val KEY_NICKNAME = "nickname"
        private const val KEY_TARGET = "target"
        private const val KEY_FAVORITES = "favorites"
    }
}

/** A favorite deliberately contains no password, invitation token, or TeamSpeak identity. */
data class FavoriteServerProfile(
    val gatewayUrl: String,
    val nickname: String,
    val target: String,
)

internal object FavoriteServerProfiles {
    const val MAX_FAVORITES = 20

    fun isValid(profile: FavoriteServerProfile): Boolean = FavoriteServerProfileCodec.sanitize(profile) != null

    fun sameDestination(first: FavoriteServerProfile, second: FavoriteServerProfile): Boolean =
        normalizeGateway(first.gatewayUrl) == normalizeGateway(second.gatewayUrl) &&
            first.target.trim().equals(second.target.trim(), ignoreCase = true)

    fun upsert(
        profiles: List<FavoriteServerProfile>,
        candidate: FavoriteServerProfile,
    ): List<FavoriteServerProfile> {
        val safeCandidate = FavoriteServerProfileCodec.sanitize(candidate) ?: return unique(profiles)
        return (listOf(safeCandidate) + profiles.filterNot { sameDestination(it, safeCandidate) })
            .take(MAX_FAVORITES)
    }

    fun remove(
        profiles: List<FavoriteServerProfile>,
        candidate: FavoriteServerProfile,
    ): List<FavoriteServerProfile> =
        profiles.filterNot { sameDestination(it, candidate) }.take(MAX_FAVORITES)

    fun unique(profiles: List<FavoriteServerProfile>): List<FavoriteServerProfile> {
        val result = mutableListOf<FavoriteServerProfile>()
        profiles.forEach { profile ->
            if (result.none { sameDestination(it, profile) } && result.size < MAX_FAVORITES) result += profile
        }
        return result
    }

    private fun normalizeGateway(value: String): String = value.trim().trimEnd('/').lowercase()
}

internal object FavoriteServerProfileCodec {
    private const val MAX_ENCODED_LENGTH = 2_048
    private val gatewayApi = GatewayApi()

    fun encode(profile: FavoriteServerProfile): String {
        val safeProfile = requireNotNull(sanitize(profile)) { "Favorite profile is invalid" }
        val bytes = ByteArrayOutputStream().use { output ->
            DataOutputStream(output).use { data ->
                data.writeUTF(safeProfile.gatewayUrl)
                data.writeUTF(safeProfile.nickname)
                data.writeUTF(safeProfile.target)
            }
            output.toByteArray()
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun decode(encoded: String): FavoriteServerProfile? {
        if (encoded.isBlank() || encoded.length > MAX_ENCODED_LENGTH) return null
        return runCatching {
            val bytes = Base64.getUrlDecoder().decode(encoded)
            DataInputStream(ByteArrayInputStream(bytes)).use { data ->
                val profile = sanitize(
                    FavoriteServerProfile(
                        gatewayUrl = data.readUTF(),
                        nickname = data.readUTF(),
                        target = data.readUTF(),
                    ),
                ) ?: return null
                if (data.available() != 0) return null
                profile
            }
        }.getOrNull()
    }

    fun sanitize(profile: FavoriteServerProfile): FavoriteServerProfile? {
        val safeGatewayUrl = runCatching { gatewayApi.parseGatewayUrl(profile.gatewayUrl).toString() }.getOrNull()
            ?: return null
        val safe = FavoriteServerProfile(
            gatewayUrl = safeGatewayUrl.take(512),
            nickname = profile.nickname.trim().take(30),
            target = profile.target.trim().take(255),
        )
        return safe.takeIf { it.gatewayUrl.isNotBlank() && it.nickname.isNotBlank() }
    }
}
