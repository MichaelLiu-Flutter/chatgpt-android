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
import com.skydoves.chatgpt.core.model.network.GPTResponseStreamEvent
import com.skydoves.chatgpt.feature.chat.BuildConfig
import com.skydoves.sandwich.ApiResponse
import com.skydoves.sandwich.getOrThrow
import com.skydoves.sandwich.isSuccess
import com.skydoves.sandwich.messageOrNull
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
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
      current + LocalChatMessage(role = USER_ROLE, content = trimmedText)
    }

    viewModelScope.launch {
      _sending.value = true
      var assistantPlaceholderAdded = false
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

        appendStreamingAssistantPlaceholder()
        assistantPlaceholderAdded = true

        val streamResult = sendMessageStreamWithFallback(request)
        when {
          streamResult.completed -> {
            completeStreamingAssistant()
          }

          streamResult.hasVisibleOutput -> {
            failStreamingAssistant(streamResult.errorMessage ?: DEFAULT_STREAM_ERROR_MESSAGE)
          }

          else -> {
            val response = sendMessageWithFallback(request)
            handleFallbackResponse(response, streamResult.errorMessage)
          }
        }
      } catch (cancellationException: CancellationException) {
        throw cancellationException
      } catch (throwable: Throwable) {
        val errorText = throwable.message ?: DEFAULT_REQUEST_ERROR_MESSAGE
        if (assistantPlaceholderAdded) {
          failStreamingAssistant(errorText)
        } else {
          appendAssistantError(errorText)
        }
      } finally {
        _sending.value = false
      }
    }
  }

  private suspend fun handleFallbackResponse(
    response: ApiResponse<GPTChatResponse>,
    streamErrorMessage: String?
  ) {
    if (response.isSuccess) {
      val chatResponse = response.getOrThrow()
      val assistantText = chatResponse.extractAssistantText()
      val reasoningText = chatResponse.extractReasoningText()
      replaceStreamingAssistant(
        content = assistantText,
        reasoning = reasoningText.takeIf { it.isNotBlank() },
        toolEvents = emptyList(),
        isStreaming = false,
        isError = false
      )
      return
    }

    val fallbackErrorMessage = response.messageOrNull ?: DEFAULT_REQUEST_ERROR_MESSAGE
    val readableError = buildReadableStreamError(
      streamErrorMessage = streamErrorMessage,
      fallbackErrorMessage = fallbackErrorMessage
    )
    failStreamingAssistant(readableError)
  }

  private suspend fun sendMessageStreamWithFallback(
    request: GPTChatRequest
  ): StreamAttemptResult {
    val primary = collectStreamAttempt(request)
    if (!primary.shouldRetryWithLowerReasoning()) return primary

    resetStreamingAssistant()

    val lowerReasoningRequest = request.copy(reasoning = GPTReasoning(effort = "none"))
    val retryWithoutReasoning = collectStreamAttempt(lowerReasoningRequest)
    if (!retryWithoutReasoning.shouldRetryWithLowerReasoning() || request.tools.isEmpty()) {
      return retryWithoutReasoning
    }

    resetStreamingAssistant()
    return collectStreamAttempt(lowerReasoningRequest.copy(tools = emptyList()))
  }

  private suspend fun collectStreamAttempt(request: GPTChatRequest): StreamAttemptResult {
    var completed = false
    var errorMessage: String? = null
    var hasVisibleOutput = false

    repository.sendMessageStream(request.copy(stream = true)).collect { event ->
      when (event) {
        is GPTResponseStreamEvent.OutputTextDelta -> {
          if (event.delta.isNotEmpty()) {
            appendAssistantContent(event.delta)
            hasVisibleOutput = true
          }
        }

        is GPTResponseStreamEvent.ReasoningSummaryTextDelta -> {
          if (event.delta.isNotEmpty()) {
            appendAssistantReasoning(event.delta)
            hasVisibleOutput = true
          }
        }

        is GPTResponseStreamEvent.OutputItemAdded -> {
          appendToolEvent(event.toLocalToolEvent(kind = TOOL_EVENT_OUTPUT_ITEM_ADDED))
          hasVisibleOutput = true
        }

        is GPTResponseStreamEvent.OutputItemDone -> {
          appendToolEvent(event.toLocalToolEvent(kind = TOOL_EVENT_OUTPUT_ITEM_DONE))
          hasVisibleOutput = true
        }

        is GPTResponseStreamEvent.WebSearchCall -> {
          appendToolEvent(event.toLocalToolEvent())
          hasVisibleOutput = true
        }

        is GPTResponseStreamEvent.Failed -> {
          errorMessage = event.message
        }

        is GPTResponseStreamEvent.Completed -> {
          completed = true
        }

        is GPTResponseStreamEvent.Unknown -> Unit
      }
    }

    return StreamAttemptResult(
      completed = completed,
      errorMessage = errorMessage,
      hasVisibleOutput = hasVisibleOutput
    )
  }

  private suspend fun sendMessageWithFallback(
    request: GPTChatRequest
  ): ApiResponse<GPTChatResponse> {
    val nonStreamRequest = request.copy(stream = false)

    val primary = repository.sendMessage(nonStreamRequest)
    if (!primary.isStreamResetCancel()) return primary

    val lowerReasoningRequest = nonStreamRequest.copy(reasoning = GPTReasoning(effort = "none"))
    val retryWithoutReasoning = repository.sendMessage(lowerReasoningRequest)
    if (!retryWithoutReasoning.isStreamResetCancel() || nonStreamRequest.tools.isEmpty()) {
      return retryWithoutReasoning
    }

    return repository.sendMessage(lowerReasoningRequest.copy(tools = emptyList()))
  }

  private fun appendStreamingAssistantPlaceholder() {
    _messages.update { current ->
      current + LocalChatMessage(
        role = ASSISTANT_ROLE,
        content = "",
        isStreaming = true
      )
    }
  }

  private fun completeStreamingAssistant() {
    updateLastAssistantMessage { message ->
      message.copy(
        isStreaming = false,
        reasoning = message.reasoning?.trim()?.ifBlank { null }
      )
    }
  }

  private fun resetStreamingAssistant() {
    replaceStreamingAssistant(
      content = "",
      reasoning = null,
      toolEvents = emptyList(),
      isStreaming = true,
      isError = false
    )
  }

  private fun failStreamingAssistant(errorText: String) {
    updateLastAssistantMessage { message ->
      val resolvedContent = if (message.content.isBlank()) {
        "Error: $errorText"
      } else {
        "${message.content}\n\nError: $errorText"
      }
      message.copy(
        content = resolvedContent,
        isStreaming = false,
        isError = true
      )
    }
  }

  private fun appendAssistantError(errorText: String) {
    _messages.update { current ->
      current + LocalChatMessage(
        role = ASSISTANT_ROLE,
        content = "Error: $errorText",
        isError = true
      )
    }
  }

  private fun replaceStreamingAssistant(
    content: String,
    reasoning: String?,
    toolEvents: List<LocalChatToolEvent>,
    isStreaming: Boolean,
    isError: Boolean
  ) {
    updateLastAssistantMessage { message ->
      message.copy(
        content = content,
        reasoning = reasoning,
        toolEvents = toolEvents,
        isStreaming = isStreaming,
        isError = isError
      )
    }
  }

  private fun appendAssistantContent(delta: String) {
    updateLastAssistantMessage { message ->
      message.copy(content = message.content + delta)
    }
  }

  private fun appendAssistantReasoning(delta: String) {
    updateLastAssistantMessage { message ->
      val updatedReasoning = (message.reasoning ?: "") + delta
      message.copy(reasoning = updatedReasoning)
    }
  }

  private fun appendToolEvent(event: LocalChatToolEvent) {
    updateLastAssistantMessage { message ->
      message.copy(toolEvents = message.toolEvents + event)
    }
  }

  private inline fun updateLastAssistantMessage(
    transform: (LocalChatMessage) -> LocalChatMessage
  ) {
    _messages.update { current ->
      val lastIndex = current.lastIndex
      if (lastIndex < 0) return@update current

      val lastMessage = current[lastIndex]
      if (lastMessage.role != ASSISTANT_ROLE) return@update current

      current.toMutableList().apply {
        this[lastIndex] = transform(lastMessage)
      }
    }
  }

  private fun StreamAttemptResult.shouldRetryWithLowerReasoning(): Boolean =
    !completed && errorMessage.isStreamResetCancel()

  private fun ApiResponse<GPTChatResponse>.isStreamResetCancel(): Boolean =
    messageOrNull.isStreamResetCancel()

  private fun String?.isStreamResetCancel(): Boolean {
    val message = this.orEmpty().lowercase()
    return "stream was reset: cancel" in message || "http2 framing layer" in message
  }

  private fun buildReadableStreamError(
    streamErrorMessage: String?,
    fallbackErrorMessage: String?
  ): String {
    val stream = streamErrorMessage?.takeIf(String::isNotBlank)
    val fallback = fallbackErrorMessage?.takeIf(String::isNotBlank)
    return when {
      stream != null && fallback != null ->
        "Streaming failed: $stream. Fallback failed: $fallback"

      stream != null -> "Streaming failed: $stream"
      fallback != null -> fallback
      else -> DEFAULT_REQUEST_ERROR_MESSAGE
    }
  }

  private companion object {
    private const val USER_ROLE = "user"
    private const val ASSISTANT_ROLE = "assistant"

    private const val TOOL_EVENT_OUTPUT_ITEM_ADDED = "response.output_item.added"
    private const val TOOL_EVENT_OUTPUT_ITEM_DONE = "response.output_item.done"

    private const val DEFAULT_STREAM_ERROR_MESSAGE = "stream request failed"
    private const val DEFAULT_REQUEST_ERROR_MESSAGE = "request failed"
  }
}

private fun GPTResponseStreamEvent.OutputItemAdded.toLocalToolEvent(kind: String): LocalChatToolEvent =
  LocalChatToolEvent(
    type = kind,
    status = status,
    itemType = itemType,
    query = query
  )

private fun GPTResponseStreamEvent.OutputItemDone.toLocalToolEvent(kind: String): LocalChatToolEvent =
  LocalChatToolEvent(
    type = kind,
    status = status,
    itemType = itemType,
    query = query
  )

private fun GPTResponseStreamEvent.WebSearchCall.toLocalToolEvent(): LocalChatToolEvent =
  LocalChatToolEvent(
    type = eventType,
    status = status,
    query = query,
    message = message
  )

private data class StreamAttemptResult(
  val completed: Boolean,
  val errorMessage: String?,
  val hasVisibleOutput: Boolean
)

data class LocalChatMessage(
  val role: String,
  val content: String,
  val reasoning: String? = null,
  val toolEvents: List<LocalChatToolEvent> = emptyList(),
  val isStreaming: Boolean = false,
  val isError: Boolean = false
)

data class LocalChatToolEvent(
  val type: String,
  val status: String? = null,
  val itemType: String? = null,
  val query: String? = null,
  val message: String? = null
)
