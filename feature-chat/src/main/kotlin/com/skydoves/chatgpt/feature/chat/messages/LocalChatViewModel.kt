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
import com.skydoves.chatgpt.feature.chat.BuildConfig
import com.skydoves.sandwich.getOrThrow
import com.skydoves.sandwich.isSuccess
import com.skydoves.sandwich.messageOrNull
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
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
      val history = _messages.value.map {
        GPTMessage(role = it.role, content = it.content)
      }
      val request = GPTChatRequest(
        model = BuildConfig.GPT_MODEL,
        messages = history
      )

      val response = repository.sendMessage(request)
      if (response.isSuccess) {
        val assistantText = response.getOrThrow().choices.firstOrNull()?.message?.content.orEmpty()
        _messages.update { current ->
          current + LocalChatMessage(role = "assistant", content = assistantText)
        }
      } else {
        val errorText = response.messageOrNull ?: "request failed"
        _messages.update { current ->
          current + LocalChatMessage(role = "assistant", content = "Error: $errorText")
        }
      }
      _sending.value = false
    }
  }
}

data class LocalChatMessage(
  val role: String,
  val content: String
)
