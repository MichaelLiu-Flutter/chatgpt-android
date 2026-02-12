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

package com.skydoves.chatgpt.core.model.local

data class LocalChatSessionSummary(
  val id: String,
  val title: String,
  val preview: String,
  val updatedAt: Long,
  val messageCount: Int
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
