/*
 * Designed and developed by 2024 skydoves (Jaewoong Eum)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.skydoves.chatgpt.core.network

import com.skydoves.chatgpt.core.model.local.GPTConfigPreferencesKeys
import com.skydoves.chatgpt.core.preferences.Preferences
import java.util.UUID
import javax.inject.Inject
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class GPTInterceptor @Inject constructor(
  private val preferences: Preferences
) : Interceptor {

  override fun intercept(chain: Interceptor.Chain): Response {
    val request = chain.request()
    val sharedPreferences = preferences.sharedPreferences

    val rawBaseUrl = sharedPreferences
      .getString(GPTConfigPreferencesKeys.KEY_ACTIVE_GPT_BASE_URL, null)
      .orEmpty()
    val rawApiKey = sharedPreferences
      .getString(GPTConfigPreferencesKeys.KEY_ACTIVE_GPT_API_KEY, null)
      .orEmpty()

    val activeBaseUrl = normalizeApiBaseUrl(
      baseUrl = rawBaseUrl,
      fallback = BuildConfig.GPT_BASE_URL.ifBlank { DEFAULT_OPENAI_BASE_URL }
    )
    val activeApiKey = rawApiKey.ifBlank { BuildConfig.GPT_API_KEY }.trim()

    // code2.ylsagi.io actively enforces a Codex-like client shape (observed 403 "Client not allowed"
    // unless we mimic Codex CLI's macOS UA format). Hardcode the successful Codex-like defaults for
    // this host so it works out-of-the-box even if local secrets override these fields incorrectly.
    val isCode2Gateway = activeBaseUrl.host.equals(CODE2_HOST, ignoreCase = true)

    val configuredOriginator = if (isCode2Gateway) {
      DEFAULT_CODEX_ORIGINATOR
    } else {
      normalizeOptionalHeaderValue(BuildConfig.GPT_CLIENT_ORIGINATOR).ifBlank { DEFAULT_CODEX_ORIGINATOR }
    }
    val configuredVersion = if (isCode2Gateway) {
      DEFAULT_CODEX_VERSION
    } else {
      normalizeOptionalHeaderValue(BuildConfig.GPT_CLIENT_VERSION).ifBlank { DEFAULT_CODEX_VERSION }
    }
    val configuredOsType = if (isCode2Gateway) {
      DEFAULT_CODEX_OS_TYPE
    } else {
      normalizeOptionalHeaderValue(BuildConfig.GPT_CLIENT_OS_TYPE).ifBlank { DEFAULT_CODEX_OS_TYPE }
    }
    val configuredOsVersion = if (isCode2Gateway) {
      DEFAULT_CODEX_OS_VERSION
    } else {
      normalizeOptionalHeaderValue(BuildConfig.GPT_CLIENT_OS_VERSION).ifBlank { DEFAULT_CODEX_OS_VERSION }
    }
    val configuredArch = if (isCode2Gateway) {
      DEFAULT_CODEX_ARCH
    } else {
      normalizeOptionalHeaderValue(BuildConfig.GPT_CLIENT_ARCH).ifBlank { DEFAULT_CODEX_ARCH }
    }
    val configuredTerminal = if (isCode2Gateway) {
      DEFAULT_CODEX_TERMINAL
    } else {
      normalizeOptionalHeaderValue(BuildConfig.GPT_CLIENT_TERMINAL).ifBlank { DEFAULT_CODEX_TERMINAL }
    }

    val configuredUserAgent = if (isCode2Gateway) {
      buildCodexLikeUserAgent(
        originator = DEFAULT_CODEX_ORIGINATOR,
        version = DEFAULT_CODEX_VERSION,
        osType = DEFAULT_CODEX_OS_TYPE,
        osVersion = DEFAULT_CODEX_OS_VERSION,
        arch = DEFAULT_CODEX_ARCH,
        terminal = DEFAULT_CODEX_TERMINAL
      )
    } else {
      normalizeOptionalHeaderValue(BuildConfig.GPT_CLIENT_USER_AGENT)
        .ifBlank {
          buildCodexLikeUserAgent(
            originator = configuredOriginator,
            version = configuredVersion,
            osType = configuredOsType,
            osVersion = configuredOsVersion,
            arch = configuredArch,
            terminal = configuredTerminal
          )
        }
    }
    val configuredReferer = normalizeOptionalHeaderValue(BuildConfig.GPT_CLIENT_REFERER)
    val configuredAppName = normalizeOptionalHeaderValue(BuildConfig.GPT_CLIENT_APP_NAME)

    val sessionId = getOrCreateSessionId(sharedPreferences)

    val routedUrl = request.url.newBuilder()
      .scheme(activeBaseUrl.scheme)
      .host(activeBaseUrl.host)
      .port(activeBaseUrl.port)
      .encodedPath(activeBaseUrl.resolve("responses")?.encodedPath ?: request.url.encodedPath)
      .build()

    val builder = request.newBuilder().url(routedUrl)
    if (activeApiKey.isNotEmpty()) {
      builder.header("Authorization", "Bearer $activeApiKey")
    }
    if (sessionId.isNotEmpty()) {
      builder.header("session_id", sessionId)
    }
    if (configuredOriginator.isNotEmpty()) {
      builder.header("originator", configuredOriginator)
    }
    if (configuredVersion.isNotEmpty()) {
      builder.header("version", configuredVersion)
    }
    if (configuredUserAgent.isNotEmpty()) {
      builder.header("User-Agent", configuredUserAgent)
    }
    if (configuredReferer.isNotEmpty()) {
      builder.header("HTTP-Referer", configuredReferer)
    }
    if (configuredAppName.isNotEmpty()) {
      builder.header("X-Title", configuredAppName)
    }
    return chain.proceed(builder.build())
  }

  private fun normalizeApiBaseUrl(baseUrl: String, fallback: String): okhttp3.HttpUrl {
    val candidate = baseUrl.trim().ifBlank { fallback }
    val candidateWithTrailingSlash = if (candidate.endsWith('/')) candidate else "$candidate/"
    return candidateWithTrailingSlash.toHttpUrlOrNull()
      ?: DEFAULT_OPENAI_API_BASE_URL.toHttpUrl()
  }

  private fun normalizeOptionalHeaderValue(raw: String): String {
    val normalized = raw.trim()
    return if (normalized.equals("unset", ignoreCase = true) || normalized == "-") {
      ""
    } else {
      normalized
    }
  }

  private fun getOrCreateSessionId(sharedPreferences: android.content.SharedPreferences): String {
    val existing = sharedPreferences.getString(KEY_GPT_SESSION_ID, null).orEmpty().trim()
    if (existing.isNotEmpty()) return existing

    val created = UUID.randomUUID().toString()
    sharedPreferences.edit().putString(KEY_GPT_SESSION_ID, created).apply()
    return created
  }

  private fun buildCodexLikeUserAgent(
    originator: String,
    version: String,
    osType: String,
    osVersion: String,
    arch: String,
    terminal: String
  ): String {
    // Follow Codex CLI format: "{originator}/{version} ({osType} {osVersion}; {arch}) {terminal}"
    // This is intentionally configurable via secrets.properties so gateways that key off Codex UA
    // can be matched without hardcoding values in code.
    val candidate = "$originator/$version ($osType $osVersion; $arch) $terminal"
    return sanitizeHeaderValue(candidate)
  }

  private fun sanitizeHeaderValue(candidate: String): String {
    if (candidate.isBlank()) return ""
    return buildString(candidate.length) {
      candidate.forEach { ch ->
        append(if (ch in ' '..'~') ch else '_')
      }
    }.trim()
  }

  private companion object {
    private const val DEFAULT_OPENAI_BASE_URL = "https://api.openai.com/"
    private const val DEFAULT_OPENAI_API_BASE_URL = "https://api.openai.com/v1/"

    // Prefer matching Codex CLI's header/UA shape by default.
    private const val DEFAULT_CODEX_ORIGINATOR = "codex_cli_rs"
    private const val DEFAULT_CODEX_VERSION = "0.1.0"
    private const val DEFAULT_CODEX_OS_TYPE = "Mac OS"
    private const val DEFAULT_CODEX_OS_VERSION = "14.6.1"
    private const val DEFAULT_CODEX_ARCH = "arm64"
    private const val DEFAULT_CODEX_TERMINAL = "Apple_Terminal"

    private const val KEY_GPT_SESSION_ID = "key_gpt_session_id"

    private const val CODE2_HOST = "code2.ylsagi.io"
  }
}
