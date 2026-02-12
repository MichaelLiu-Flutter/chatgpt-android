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
    return chain.proceed(builder.build())
  }

  private fun normalizeApiBaseUrl(baseUrl: String, fallback: String): okhttp3.HttpUrl {
    val candidate = baseUrl.trim().ifBlank { fallback }
    val candidateWithTrailingSlash = if (candidate.endsWith('/')) candidate else "$candidate/"
    val withApiVersion = if (candidateWithTrailingSlash.endsWith("v1/")) {
      candidateWithTrailingSlash
    } else {
      "${candidateWithTrailingSlash}v1/"
    }

    return withApiVersion.toHttpUrlOrNull()
      ?: DEFAULT_OPENAI_API_BASE_URL.toHttpUrl()
  }

  private companion object {
    private const val DEFAULT_OPENAI_BASE_URL = "https://api.openai.com/"
    private const val DEFAULT_OPENAI_API_BASE_URL = "https://api.openai.com/v1/"
  }
}
