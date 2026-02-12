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

import android.os.Build
import com.skydoves.chatgpt.core.model.local.GPTConfigPreferencesKeys
import com.skydoves.chatgpt.core.preferences.Preferences
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
    val configuredOriginator = normalizeOptionalHeaderValue(BuildConfig.GPT_CLIENT_ORIGINATOR)
      .ifBlank { DEFAULT_CODEX_ORIGINATOR }
    val configuredVersion = normalizeOptionalHeaderValue(BuildConfig.GPT_CLIENT_VERSION)
      .ifBlank { DEFAULT_CLIENT_VERSION }
    val configuredUserAgent = normalizeOptionalHeaderValue(BuildConfig.GPT_CLIENT_USER_AGENT)
      .ifBlank {
        buildCodexLikeUserAgent(
          originator = configuredOriginator,
          version = configuredVersion
        )
      }
    val configuredReferer = normalizeOptionalHeaderValue(BuildConfig.GPT_CLIENT_REFERER)
    val configuredAppName = normalizeOptionalHeaderValue(BuildConfig.GPT_CLIENT_APP_NAME)

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

  private fun buildCodexLikeUserAgent(originator: String, version: String): String {
    val release = Build.VERSION.RELEASE?.trim().orEmpty().ifBlank { "unknown" }
    val model = Build.MODEL?.trim().orEmpty().ifBlank { "android" }
    val candidate = "$originator/$version (Android $release; $model)"
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
    private const val DEFAULT_CODEX_ORIGINATOR = "codex_cli_rs"
    private const val DEFAULT_CLIENT_VERSION = "android"
  }
}
