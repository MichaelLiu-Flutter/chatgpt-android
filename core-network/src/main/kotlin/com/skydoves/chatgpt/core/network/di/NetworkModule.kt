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

package com.skydoves.chatgpt.core.network.di

import com.skydoves.chatgpt.core.network.BuildConfig
import com.skydoves.chatgpt.core.network.GPTInterceptor
import com.skydoves.chatgpt.core.network.service.ChatGPTService
import com.skydoves.sandwich.retrofit.adapters.ApiResponseCallAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.create

@Module
@InstallIn(SingletonComponent::class)
internal object NetworkModule {

  private const val DEFAULT_OPENAI_BASE_URL = "https://api.openai.com/"
  private const val CONNECT_TIMEOUT_SECONDS = 1200L

  @Provides
  @Singleton
  fun provideOkHttpClient(): OkHttpClient {
    return OkHttpClient.Builder()
      .addInterceptor(GPTInterceptor())
      .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
      // 0 means no timeout limit for read/write/call.
      .readTimeout(0, TimeUnit.SECONDS)
      .writeTimeout(0, TimeUnit.SECONDS)
      .callTimeout(0, TimeUnit.SECONDS)
      .apply {
        if (BuildConfig.DEBUG) {
          val logger = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
            redactHeader("Authorization")
          }
          // BODY logging buffers the whole stream and blocks SSE updates.
          this.addInterceptor { chain ->
            val accept = chain.request().header("Accept").orEmpty()
            if (accept.contains("text/event-stream", ignoreCase = true)) {
              chain.proceed(chain.request())
            } else {
              logger.intercept(chain)
            }
          }
        }
      }
      .build()
  }

  @Provides
  @Singleton
  fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
    val normalizedBaseUrl = BuildConfig.GPT_BASE_URL
      .ifBlank { DEFAULT_OPENAI_BASE_URL }
      .removeSuffix("/v1")
      .removeSuffix("/v1/")
      .let { if (it.endsWith('/')) it else "$it/" }

    return Retrofit.Builder()
      .client(okHttpClient)
      .baseUrl(normalizedBaseUrl)
      .addConverterFactory(MoshiConverterFactory.create())
      .addCallAdapterFactory(ApiResponseCallAdapterFactory.create())
      .build()
  }

  @Provides
  @Singleton
  fun provideChatGPTService(retrofit: Retrofit): ChatGPTService = retrofit.create()
}
