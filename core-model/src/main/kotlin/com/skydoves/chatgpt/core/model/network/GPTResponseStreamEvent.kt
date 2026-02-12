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

import org.json.JSONObject

sealed interface GPTResponseStreamEvent {

  data class OutputTextDelta(val delta: String) : GPTResponseStreamEvent

  data class ReasoningSummaryTextDelta(val delta: String) : GPTResponseStreamEvent

  data class OutputItemAdded(
    val itemId: String?,
    val itemType: String?,
    val status: String?,
    val query: String?
  ) : GPTResponseStreamEvent

  data class OutputItemDone(
    val itemId: String?,
    val itemType: String?,
    val status: String?,
    val query: String?
  ) : GPTResponseStreamEvent

  data class WebSearchCall(
    val eventType: String,
    val itemId: String?,
    val status: String?,
    val query: String?,
    val message: String?
  ) : GPTResponseStreamEvent

  data class Failed(
    val message: String,
    val code: String? = null,
    val eventType: String? = null
  ) : GPTResponseStreamEvent

  data class Completed(val responseId: String? = null) : GPTResponseStreamEvent

  data class Unknown(
    val eventType: String,
    val payload: String
  ) : GPTResponseStreamEvent

  companion object {
    fun fromServerSentEvent(event: String?, data: String): GPTResponseStreamEvent? {
      val payload = data.trim()
      if (payload.isEmpty()) return null
      if (payload == "[DONE]") return Completed()

      val json = runCatching { JSONObject(payload) }.getOrElse {
        return Failed(
          message = "Failed to parse stream payload.",
          eventType = event
        )
      }
      val type = (event?.takeIf { it.isNotBlank() } ?: json.optNullableString("type")).orEmpty()

      return when {
        type == "response.output_text.delta" -> {
          val delta = json.optRawString("delta")
          if (delta != null) OutputTextDelta(delta) else null
        }

        type == "response.reasoning_summary_text.delta" -> {
          val delta = json.optRawString("delta")
          if (delta != null) ReasoningSummaryTextDelta(delta) else null
        }

        type == "response.output_item.added" -> {
          val item = json.optJSONObject("item")
          OutputItemAdded(
            itemId = json.optNullableString("item_id") ?: item.optNullableString("id"),
            itemType = item.optNullableString("type"),
            status = json.optNullableString("status") ?: item.optNullableString("status"),
            query = json.optQuery() ?: item.optQuery()
          )
        }

        type == "response.output_item.done" -> {
          val item = json.optJSONObject("item")
          OutputItemDone(
            itemId = json.optNullableString("item_id") ?: item.optNullableString("id"),
            itemType = item.optNullableString("type"),
            status = json.optNullableString("status") ?: item.optNullableString("status"),
            query = json.optQuery() ?: item.optQuery()
          )
        }

        type.startsWith("response.web_search_call.") -> {
          val item = json.optJSONObject("item")
          WebSearchCall(
            eventType = type,
            itemId = json.optNullableString("item_id") ?: item.optNullableString("id"),
            status = json.optNullableString("status") ?: item.optNullableString("status"),
            query = json.optQuery() ?: item.optQuery(),
            message = json.optNullableString("message") ?: item.optNullableString("message")
          )
        }

        type == "response.completed" -> {
          Completed(
            responseId = json.optNullableString("response_id")
              ?: json.optJSONObject("response").optNullableString("id")
          )
        }

        type == "response.error" || type == "response.failed" || type == "error" -> {
          val error = json.optJSONObject("error")
            ?: json.optJSONObject("response")?.optJSONObject("error")
          Failed(
            message = error.optNullableString("message")
              ?: json.optNullableString("message")
              ?: "Stream response failed.",
            code = error.optNullableString("code"),
            eventType = type
          )
        }

        else -> Unknown(
          eventType = type.ifEmpty { "unknown" },
          payload = payload
        )
      }
    }
  }
}

private fun JSONObject?.optNullableString(name: String): String? {
  if (this == null || !has(name) || isNull(name)) return null
  return opt(name)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
}

private fun JSONObject?.optRawString(name: String): String? {
  if (this == null || !has(name) || isNull(name)) return null
  return opt(name)?.toString()
}

private fun JSONObject?.optQuery(): String? = this.optNullableString("query")
  ?: this?.optJSONObject("action").optNullableString("query")
  ?: this?.optJSONObject("search").optNullableString("query")
