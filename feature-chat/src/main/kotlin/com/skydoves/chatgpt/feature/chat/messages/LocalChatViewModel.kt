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

package com.skydoves.chatgpt.feature.chat.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skydoves.chatgpt.core.data.repository.GPTMessageRepository
import com.skydoves.chatgpt.core.model.GPTMessage
import com.skydoves.chatgpt.core.model.network.GPTChatRequest
import com.skydoves.chatgpt.core.model.network.GPTChatResponse
import com.skydoves.chatgpt.core.model.network.GPTReasoning
import com.skydoves.chatgpt.feature.chat.BuildConfig
import com.skydoves.sandwich.ApiResponse
import com.skydoves.sandwich.getOrThrow
import com.skydoves.sandwich.isSuccess
import com.skydoves.sandwich.messageOrNull
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class LocalChatViewModel @Inject constructor(
  private val repository: GPTMessageRepository
) : ViewModel() {

  private val _messages = MutableStateFlow<List<LocalChatMessage>>(emptyList())
  val messages: StateFlow<List<LocalChatMessage>> = _messages.asStateFlow()

  private val _sending = MutableStateFlow(false)
  val sending: StateFlow<Boolean> = _sending.asStateFlow()

  fun sendMessage(text: String) {
    val trimmedText = text.trim()
    if (trimmedText.isEmpty() || _sending.value) return

    _messages.update { current ->
      current + LocalChatMessage(role = "user", content = trimmedText)
    }

    viewModelScope.launch {
      _sending.value = true
      try {
        val history = _messages.value
          .asSequence()
          .filterNot(LocalChatMessage::isError)
          .map { message -> GPTMessage(role = message.role, content = message.content) }
          .toList()
        val request = GPTChatRequest(
          model = BuildConfig.GPT_MODEL,
          messages = history
        )

        val response = sendMessageWithFallback(request)
        if (response.isSuccess) {
          val chatResponse = response.getOrThrow()
          val assistantText = chatResponse.extractAssistantText()
          val reasoningText = chatResponse.extractReasoningText()
          _messages.update { current ->
            current + LocalChatMessage(
              role = "assistant",
              content = assistantText,
              reasoning = reasoningText.takeIf { it.isNotBlank() }
            )
          }
        } else {
          val errorText = response.messageOrNull ?: "request failed"
          _messages.update { current ->
            current + LocalChatMessage(
              role = "assistant",
              content = "Error: $errorText",
              isError = true
            )
          }
        }
      } catch (cancellationException: CancellationException) {
        throw cancellationException
      } catch (throwable: Throwable) {
        val errorText = throwable.message ?: "request failed"
        _messages.update { current ->
          current + LocalChatMessage(
            role = "assistant",
            content = "Error: $errorText",
            isError = true
          )
        }
      } finally {
        _sending.value = false
      }
    }
  }

  private suspend fun sendMessageWithFallback(
    request: GPTChatRequest
  ): ApiResponse<GPTChatResponse> {
    val primary = repository.sendMessage(request)
    if (!primary.isStreamResetCancel()) return primary

    val lowerReasoningRequest = request.copy(reasoning = GPTReasoning(effort = "none"))
    val retryWithoutReasoning = repository.sendMessage(lowerReasoningRequest)
    if (!retryWithoutReasoning.isStreamResetCancel() || request.tools.isEmpty()) {
      return retryWithoutReasoning
    }

    return repository.sendMessage(lowerReasoningRequest.copy(tools = emptyList()))
  }

  private fun ApiResponse<GPTChatResponse>.isStreamResetCancel(): Boolean {
    val message = messageOrNull.orEmpty().lowercase()
    return "stream was reset: cancel" in message || "http2 framing layer" in message
  }
}

data class LocalChatMessage(
  val role: String,
  val content: String,
  val reasoning: String? = null,
  val isError: Boolean = false
)
