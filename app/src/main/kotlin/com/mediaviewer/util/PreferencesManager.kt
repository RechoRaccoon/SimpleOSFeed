package com.mediaviewer.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "media_viewer_prefs")

object PrefKeys {
    val BSKY_ACCESS_JWT      = stringPreferencesKey("bsky_access_jwt")
    val BSKY_REFRESH_JWT     = stringPreferencesKey("bsky_refresh_jwt")
    val BSKY_DID             = stringPreferencesKey("bsky_did")
    val BSKY_HANDLE          = stringPreferencesKey("bsky_handle")
    val BSKY_SERVICE_URL     = stringPreferencesKey("bsky_service_url")
    val E621_USERNAME        = stringPreferencesKey("e621_username")
    val E621_API_KEY         = stringPreferencesKey("e621_api_key")
    val DOWNLOAD_ON_LIKE     = booleanPreferencesKey("download_on_like")
    val LAST_MODE            = stringPreferencesKey("last_mode")
    val REDUCED_ANIMATIONS   = booleanPreferencesKey("reduced_animations")
    val LAST_FEED_URI        = stringPreferencesKey("last_feed_uri")
    val LAST_E621_TAGS       = stringPreferencesKey("last_e621_tags")
}

class PreferencesManager(private val context: Context) {

    val bskyAccessJwt: Flow<String?>  = context.dataStore.data.map { it[PrefKeys.BSKY_ACCESS_JWT] }
    val bskyRefreshJwt: Flow<String?> = context.dataStore.data.map { it[PrefKeys.BSKY_REFRESH_JWT] }
    val bskyDid: Flow<String?>        = context.dataStore.data.map { it[PrefKeys.BSKY_DID] }
    val bskyHandle: Flow<String?>     = context.dataStore.data.map { it[PrefKeys.BSKY_HANDLE] }
    val bskyServiceUrl: Flow<String>  = context.dataStore.data.map { it[PrefKeys.BSKY_SERVICE_URL] ?: "https://bsky.social/" }
    val e621Username: Flow<String?>   = context.dataStore.data.map { it[PrefKeys.E621_USERNAME] }
    val e621ApiKey: Flow<String?>     = context.dataStore.data.map { it[PrefKeys.E621_API_KEY] }
    val downloadOnLike: Flow<Boolean> = context.dataStore.data.map { it[PrefKeys.DOWNLOAD_ON_LIKE] ?: false }
    val lastMode: Flow<String>        = context.dataStore.data.map { it[PrefKeys.LAST_MODE] ?: "BLUESKY" }
    val reducedAnimations: Flow<Boolean> = context.dataStore.data.map { it[PrefKeys.REDUCED_ANIMATIONS] ?: false }
    val lastFeedUri: Flow<String?>    = context.dataStore.data.map { it[PrefKeys.LAST_FEED_URI] }
    val lastE621Tags: Flow<String?>   = context.dataStore.data.map { it[PrefKeys.LAST_E621_TAGS] }

    suspend fun setLastFeedUri(uri: String?) {
        context.dataStore.edit { prefs ->
            if (uri == null) prefs.remove(PrefKeys.LAST_FEED_URI) else prefs[PrefKeys.LAST_FEED_URI] = uri
        }
    }

    suspend fun setLastE621Tags(tags: String) {
        context.dataStore.edit { prefs -> prefs[PrefKeys.LAST_E621_TAGS] = tags }
    }

    suspend fun saveBskySession(accessJwt: String, refreshJwt: String, did: String, handle: String) {
        context.dataStore.edit { prefs ->
            prefs[PrefKeys.BSKY_ACCESS_JWT]  = accessJwt
            prefs[PrefKeys.BSKY_REFRESH_JWT] = refreshJwt
            prefs[PrefKeys.BSKY_DID]         = did
            prefs[PrefKeys.BSKY_HANDLE]      = handle
        }
    }

    suspend fun clearBskySession() {
        context.dataStore.edit { prefs ->
            prefs.remove(PrefKeys.BSKY_ACCESS_JWT)
            prefs.remove(PrefKeys.BSKY_REFRESH_JWT)
            prefs.remove(PrefKeys.BSKY_DID)
            prefs.remove(PrefKeys.BSKY_HANDLE)
        }
    }

    suspend fun saveE621Credentials(username: String, apiKey: String) {
        context.dataStore.edit { prefs ->
            prefs[PrefKeys.E621_USERNAME] = username
            prefs[PrefKeys.E621_API_KEY]  = apiKey
        }
    }

    suspend fun clearE621Credentials() {
        context.dataStore.edit { prefs ->
            prefs.remove(PrefKeys.E621_USERNAME)
            prefs.remove(PrefKeys.E621_API_KEY)
        }
    }

    suspend fun setDownloadOnLike(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[PrefKeys.DOWNLOAD_ON_LIKE] = enabled }
    }

    suspend fun setLastMode(mode: String) {
        context.dataStore.edit { prefs -> prefs[PrefKeys.LAST_MODE] = mode }
    }

    suspend fun setReducedAnimations(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[PrefKeys.REDUCED_ANIMATIONS] = enabled }
    }
}
