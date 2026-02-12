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

package com.skydoves.chatgpt.core.model.network

import com.skydoves.chatgpt.core.model.GPTMessage
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GPTChatRequest(
  @field:Json(name = "model") val model: String,
  @field:Json(name = "input") val input: List<GPTInputMessage>,
  @field:Json(name = "instructions") val instructions: String = DEFAULT_INSTRUCTIONS,
  @field:Json(name = "reasoning") val reasoning: GPTReasoning = GPTReasoning(),
  @field:Json(name = "tools") val tools: List<GPTTool> = listOf(GPTTool()),
  @field:Json(name = "stream") val stream: Boolean = false
) {

  constructor(model: String, messages: List<GPTMessage>) : this(
    model = model,
    input = messages.map { message ->
      GPTInputMessage(
        role = message.role,
        content = listOf(
          GPTInputContent(
            type = message.toInputContentType(),
            text = message.content
          )
        )
      )
    }
  )

  private companion object {
    private const val DEFAULT_INSTRUCTIONS = "You are a helpful assistant."
  }
}

@JsonClass(generateAdapter = true)
data class GPTInputMessage(
  @field:Json(name = "role") val role: String,
  @field:Json(name = "content") val content: List<GPTInputContent>
)

@JsonClass(generateAdapter = true)
data class GPTInputContent(
  @field:Json(name = "type") val type: String = "input_text",
  @field:Json(name = "text") val text: String
)

@JsonClass(generateAdapter = true)
data class GPTReasoning(
  @field:Json(name = "effort") val effort: String = "high"
)

@JsonClass(generateAdapter = true)
data class GPTTool(
  @field:Json(name = "type") val type: String = "web_search"
)

private fun GPTMessage.toInputContentType(): String = when (role) {
  "assistant" -> "output_text"
  else -> "input_text"
}
