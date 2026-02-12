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

import com.skydoves.chatgpt.core.model.GPTChoice
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GPTChatResponse(
  @field:Json(name = "id") val id: String = "",
  @field:Json(name = "object") val `object`: String = "",
  @field:Json(name = "created") val created: Long? = null,
  @field:Json(name = "created_at") val createdAt: Long? = null,
  @field:Json(name = "model") val model: String = "",
  @field:Json(name = "system_fingerprint") val systemFingerprint: String? = null,
  @field:Json(name = "choices") val choices: List<GPTChoice> = emptyList(),
  @field:Json(name = "output") val output: List<GPTResponseOutput> = emptyList()
) {

  fun extractAssistantText(): String {
    val assistantOutput = output
      .asSequence()
      .filter { item ->
        item.role == ASSISTANT_ROLE || (item.role == null && item.type == MESSAGE_TYPE)
      }
      .flatMap { item ->
        buildList {
          item.content.orEmpty().mapNotNullTo(this) { content -> content.text }
          item.text?.let(::add)
        }.asSequence()
      }
      .map(String::trim)
      .filter(String::isNotEmpty)
      .toList()

    if (assistantOutput.isNotEmpty()) {
      return assistantOutput.joinToString(separator = "\n")
    }

    return choices.firstOrNull()?.message?.content.orEmpty()
  }

  private companion object {
    private const val ASSISTANT_ROLE = "assistant"
    private const val MESSAGE_TYPE = "message"
  }
}

@JsonClass(generateAdapter = true)
data class GPTResponseOutput(
  @field:Json(name = "type") val type: String? = null,
  @field:Json(name = "role") val role: String? = null,
  @field:Json(name = "text") val text: String? = null,
  @field:Json(name = "content") val content: List<GPTResponseOutputContent>? = null
)

@JsonClass(generateAdapter = true)
data class GPTResponseOutputContent(
  @field:Json(name = "type") val type: String? = null,
  @field:Json(name = "text") val text: String? = null
)
