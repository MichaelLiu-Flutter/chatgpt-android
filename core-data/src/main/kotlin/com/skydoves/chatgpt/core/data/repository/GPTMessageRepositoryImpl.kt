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

package com.skydoves.chatgpt.core.data.repository

import com.skydoves.chatgpt.core.model.network.GPTChatRequest
import com.skydoves.chatgpt.core.model.network.GPTChatResponse
import com.skydoves.chatgpt.core.model.network.GPTResponseStreamEvent
import com.skydoves.chatgpt.core.network.ChatGPTDispatchers
import com.skydoves.chatgpt.core.network.Dispatcher
import com.skydoves.chatgpt.core.network.service.ChatGPTService
import com.skydoves.sandwich.ApiResponse
import io.getstream.chat.android.client.ChatClient
import io.getstream.result.onSuccessSuspend
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.json.JSONObject

internal class GPTMessageRepositoryImpl @Inject constructor(
  @Dispatcher(ChatGPTDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
  private val chatClient: ChatClient,
  private val chatGptService: ChatGPTService
) : GPTMessageRepository {

  override suspend fun sendMessage(gptChatRequest: GPTChatRequest): ApiResponse<GPTChatResponse> =
    chatGptService.sendMessage(gptChatRequest)

  override fun sendMessageStream(gptChatRequest: GPTChatRequest): Flow<GPTResponseStreamEvent> = flow {
    try {
      val streamRequest = if (gptChatRequest.stream) gptChatRequest else gptChatRequest.copy(stream = true)
      val streamResponse = chatGptService.sendMessageStream(streamRequest)
      if (!streamResponse.isSuccessful) {
        emit(
          GPTResponseStreamEvent.Failed(
            message = streamResponse.toReadableErrorMessage()
          )
        )
        return@flow
      }

      val responseBody = streamResponse.body()
      if (responseBody == null) {
        emit(GPTResponseStreamEvent.Failed(message = "Stream response body is empty."))
        return@flow
      }

      responseBody.use { body ->
        val reader = body.byteStream().bufferedReader()
        var eventName: String? = null
        val dataBuilder = StringBuilder()
        var terminalEventReceived = false

        suspend fun emitBufferedEvent() {
          if (dataBuilder.isEmpty() || terminalEventReceived) {
            dataBuilder.setLength(0)
            eventName = null
            return
          }
          val parsedEvent = GPTResponseStreamEvent.fromServerSentEvent(
            event = eventName,
            data = dataBuilder.toString()
          )
          dataBuilder.setLength(0)
          eventName = null
          if (parsedEvent != null) {
            emit(parsedEvent)
            if (
              parsedEvent is GPTResponseStreamEvent.Completed ||
              parsedEvent is GPTResponseStreamEvent.Failed
            ) {
              terminalEventReceived = true
            }
          }
        }

        while (!terminalEventReceived) {
          val line = reader.readLine() ?: break
          if (line.isBlank()) {
            emitBufferedEvent()
            continue
          }
          when {
            line.startsWith("event:") -> eventName = line.removePrefix("event:").trim()
            line.startsWith("data:") -> {
              val data = line.removePrefix("data:").trimStart()
              if (dataBuilder.isNotEmpty()) dataBuilder.append('\n')
              dataBuilder.append(data)
            }
          }
        }

        emitBufferedEvent()
        if (!terminalEventReceived) {
          emit(GPTResponseStreamEvent.Failed(message = "The stream ended before completion."))
        }
      }
    } catch (cancellationException: CancellationException) {
      throw cancellationException
    } catch (throwable: Throwable) {
      val errorMessage = throwable.message?.takeIf(String::isNotBlank)
        ?: "Stream request failed."
      emit(GPTResponseStreamEvent.Failed(message = errorMessage))
    }
  }.flowOn(ioDispatcher)

  override fun watchIsChannelMessageEmpty(cid: String): Flow<Boolean> = flow {
    chatClient.channel(cid).watch().await().onSuccessSuspend { channel ->
      emit(channel.messages.isEmpty())
    }
  }.flowOn(ioDispatcher)
}

private fun retrofit2.Response<*>.toReadableErrorMessage(): String {
  val payload = errorBody()?.string().orEmpty().trim()
  val bodyMessage = payload.toReadableErrorMessage()
  if (bodyMessage != null) return bodyMessage

  val fallbackMessage = message().trim()
  return if (fallbackMessage.isNotEmpty()) {
    "HTTP ${code()}: $fallbackMessage"
  } else {
    "HTTP ${code()} request failed."
  }
}

private fun String.toReadableErrorMessage(): String? {
  if (isEmpty()) return null

  return runCatching {
    val json = JSONObject(this)
    val error = json.optJSONObject("error")
    error.optNullableString("message") ?: json.optNullableString("message")
  }.getOrNull() ?: take(256)
}

private fun JSONObject?.optNullableString(name: String): String? {
  if (this == null || !has(name) || isNull(name)) return null
  return opt(name)?.toString()?.trim()?.takeIf(String::isNotEmpty)
}
