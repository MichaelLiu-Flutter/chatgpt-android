package com.skydoves.chatgpt.feature.chat.e2e

import com.skydoves.chatgpt.core.model.GPTMessage
import com.skydoves.chatgpt.core.model.network.GPTChatRequest
import com.skydoves.chatgpt.core.network.GPTInterceptor
import com.skydoves.chatgpt.core.network.BuildConfig as NetworkBuildConfig
import com.skydoves.chatgpt.core.network.service.ChatGPTService
import com.skydoves.chatgpt.feature.chat.BuildConfig as FeatureBuildConfig
import com.skydoves.sandwich.getOrThrow
import com.skydoves.sandwich.isSuccess
import com.skydoves.sandwich.messageOrNull
import com.skydoves.sandwich.retrofit.adapters.ApiResponseCallAdapterFactory
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assume.assumeTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.create
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertTrue

/**
 * Live (real-network) unit test.
 *
 * Disabled by default to keep unit tests hermetic and CI-friendly.
 *
 * Enable with:
 *   RUN_LIVE_GPT_TESTS=1 ./gradlew :feature-chat:testDebugUnitTest
 *
 * Requires a real `GPT_API_KEY` in your local `secrets.properties`.
 */
class LiveWebSearchChatTest {

  @Test
  fun gpt_canAnswerAWebSearchPrompt_overNetwork() = runBlocking {
    assumeTrue(System.getenv("RUN_LIVE_GPT_TESTS") == "1")
    assumeTrue(NetworkBuildConfig.GPT_API_KEY.isNotBlank())
    assumeTrue(NetworkBuildConfig.GPT_API_KEY != "aaaaaaaaaa")

    val baseUrl = NetworkBuildConfig.GPT_BASE_URL
      .ifBlank { "https://api.openai.com/" }
      .removeSuffix("/v1")
      .removeSuffix("/v1/")
      .let { if (it.endsWith('/')) it else "$it/" }

    // Avoid HTTP logging in tests to prevent leaking secrets to stdout.
    val okHttpClient = OkHttpClient.Builder()
      .addInterceptor(GPTInterceptor())
      .connectTimeout(30, TimeUnit.SECONDS)
      .readTimeout(90, TimeUnit.SECONDS)
      .writeTimeout(90, TimeUnit.SECONDS)
      .callTimeout(120, TimeUnit.SECONDS)
      .build()

    val service: ChatGPTService = Retrofit.Builder()
      .baseUrl(baseUrl)
      .client(okHttpClient)
      .addConverterFactory(MoshiConverterFactory.create())
      .addCallAdapterFactory(ApiResponseCallAdapterFactory.create())
      .build()
      .create()

    val request = GPTChatRequest(
      model = FeatureBuildConfig.GPT_MODEL,
      messages = listOf(
        GPTMessage(
          role = "user",
          // Stable assertion target; should be answerable even if "web_search" isn't executed.
          content = "Use web search if available. What is the official Android Developers domain? Reply with just the domain."
        )
      )
    )

    val response = service.sendMessage(request)
    if (!response.isSuccess) {
      throw AssertionError("Live request failed: ${response.messageOrNull}")
    }

    val text = response.getOrThrow().extractAssistantText().trim()
    assertTrue("Expected android developers domain in response. Got: `$text`", text.contains("developer.android.com"))
  }
}

