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

import androidx.core.content.edit
import com.skydoves.chatgpt.core.model.local.GPTConfig
import com.skydoves.chatgpt.core.model.local.GPTConfigPreferencesKeys
import com.skydoves.chatgpt.core.model.local.LocalChatMessage
import com.skydoves.chatgpt.core.model.local.LocalChatSessionSummary
import com.skydoves.chatgpt.core.model.local.LocalChatToolEvent
import com.skydoves.chatgpt.core.model.network.GPTChatRequest
import com.skydoves.chatgpt.core.model.network.GPTChatResponse
import com.skydoves.chatgpt.core.model.network.GPTResponseStreamEvent
import com.skydoves.chatgpt.core.network.BuildConfig as NetworkBuildConfig
import com.skydoves.chatgpt.core.network.ChatGPTDispatchers
import com.skydoves.chatgpt.core.network.Dispatcher
import com.skydoves.chatgpt.core.network.service.ChatGPTService
import com.skydoves.chatgpt.core.preferences.Preferences
import com.skydoves.sandwich.ApiResponse
import io.getstream.chat.android.client.ChatClient
import io.getstream.result.onSuccessSuspend
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal class GPTMessageRepositoryImpl @Inject constructor(
  @Dispatcher(ChatGPTDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
  private val chatClient: ChatClient,
  private val chatGptService: ChatGPTService,
  private val preferences: Preferences
) : GPTMessageRepository {

  private val gptConfigLock = Any()
  private val localSessionLock = Any()

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
              if (dataBuilder.isNotEmpty()) dataBuilder.append("\n")
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

  override suspend fun listGptConfigs(): List<GPTConfig> = withContext(ioDispatcher) {
    synchronized(gptConfigLock) {
      loadGptConfigsLocked()
    }
  }

  override suspend fun getActiveGptConfig(): GPTConfig = withContext(ioDispatcher) {
    synchronized(gptConfigLock) {
      val configs = loadGptConfigsLocked()
      val activeConfig = resolveActiveGptConfigLocked(configs)
      persistActiveGptConfigLocked(activeConfig)
      activeConfig
    }
  }

  override suspend fun setActiveGptConfig(configId: String) = withContext(ioDispatcher) {
    synchronized(gptConfigLock) {
      val configs = loadGptConfigsLocked()
      val target = configs.firstOrNull { it.id == configId } ?: defaultGptConfig()
      persistActiveGptConfigLocked(target)
    }
  }

  override suspend fun upsertGptConfig(config: GPTConfig) = withContext(ioDispatcher) {
    synchronized(gptConfigLock) {
      if (config.id == GPTConfigPreferencesKeys.DEFAULT_CONFIG_ID || config.isDefault) {
        persistActiveGptConfigLocked(defaultGptConfig())
        return@withContext
      }

      val customConfig = config.normalizeCustomConfig()
      val current = loadGptConfigsLocked()
      val updated = buildList {
        addAll(current.filterNot { it.id == customConfig.id || it.isDefault })
        add(customConfig)
      }.sortedBy { it.name.lowercase() }

      saveCustomGptConfigsLocked(updated)

      val activeConfigId = preferences.sharedPreferences
        .getString(GPTConfigPreferencesKeys.KEY_ACTIVE_GPT_CONFIG_ID, null)
      if (activeConfigId == customConfig.id) {
        persistActiveGptConfigLocked(customConfig)
      } else if (activeConfigId.isNullOrBlank()) {
        persistActiveGptConfigLocked(defaultGptConfig())
      }
    }
  }

  override suspend fun deleteGptConfig(configId: String) = withContext(ioDispatcher) {
    synchronized(gptConfigLock) {
      if (configId.isBlank() || configId == GPTConfigPreferencesKeys.DEFAULT_CONFIG_ID) {
        return@withContext
      }

      val current = loadGptConfigsLocked()
      val updated = current.filterNot { it.id == configId || it.isDefault }
      saveCustomGptConfigsLocked(updated)

      val activeConfig = resolveActiveGptConfigLocked(loadGptConfigsLocked())
      persistActiveGptConfigLocked(activeConfig)
    }
  }

  override suspend fun listLocalChatSessions(): List<LocalChatSessionSummary> = withContext(ioDispatcher) {
    synchronized(localSessionLock) {
      loadLocalSessionSummariesLocked()
    }
  }

  override suspend fun deleteLocalChatSession(sessionId: String) = withContext(ioDispatcher) {
    synchronized(localSessionLock) {
      if (sessionId.isBlank()) return@withContext

      val updated = loadLocalSessionSummariesLocked().filterNot { it.id == sessionId }
      saveLocalSessionSummariesLocked(updated)
      preferences.sharedPreferences.edit {
        remove(localSessionMessagesKey(sessionId))
      }
    }
  }

  override suspend fun createLocalChatSession(): LocalChatSessionSummary = withContext(ioDispatcher) {
    synchronized(localSessionLock) {
      val now = System.currentTimeMillis()
      val session = LocalChatSessionSummary(
        id = UUID.randomUUID().toString(),
        title = DEFAULT_LOCAL_SESSION_TITLE,
        preview = "",
        updatedAt = now,
        messageCount = 0
      )

      val existing = loadLocalSessionSummariesLocked()
      val updated = buildList {
        add(session)
        addAll(existing.filterNot { it.id == session.id })
      }.sortedByDescending(LocalChatSessionSummary::updatedAt)

      saveLocalSessionSummariesLocked(updated)
      saveLocalSessionMessagesLocked(session.id, emptyList())
      session
    }
  }

  override suspend fun loadLocalChatSessionMessages(sessionId: String): List<LocalChatMessage> =
    withContext(ioDispatcher) {
      synchronized(localSessionLock) {
        loadLocalSessionMessagesLocked(sessionId)
      }
    }

  override suspend fun saveLocalChatSessionMessages(
    sessionId: String,
    messages: List<LocalChatMessage>
  ): LocalChatSessionSummary = withContext(ioDispatcher) {
    synchronized(localSessionLock) {
      val normalizedMessages = messages
        .map { message -> message.copy(isStreaming = false) }
        .filterNot(LocalChatMessage::isTransientStreamingPlaceholder)

      saveLocalSessionMessagesLocked(sessionId, normalizedMessages)

      val previousSummary = loadLocalSessionSummariesLocked().firstOrNull { it.id == sessionId }
      val summary = normalizedMessages.toSessionSummary(
        sessionId = sessionId,
        previousTitle = previousSummary?.title
      )

      val updated = buildList {
        add(summary)
        addAll(loadLocalSessionSummariesLocked().filterNot { it.id == sessionId })
      }.sortedByDescending(LocalChatSessionSummary::updatedAt)

      saveLocalSessionSummariesLocked(updated)
      summary
    }
  }

  override fun watchIsChannelMessageEmpty(cid: String): Flow<Boolean> = flow {
    chatClient.channel(cid).watch().await().onSuccessSuspend { channel ->
      emit(channel.messages.isEmpty())
    }
  }.flowOn(ioDispatcher)

  private fun loadGptConfigsLocked(): List<GPTConfig> {
    val defaultConfig = defaultGptConfig()
    val payload = preferences.sharedPreferences.getString(GPTConfigPreferencesKeys.KEY_GPT_CONFIGS, null)
    val presetConfigs = initialCustomGptConfigs()
    if (payload.isNullOrBlank() || payload == "[]") {
      saveCustomGptConfigsLocked(presetConfigs)
      markPresetConfigsSeededLocked()
      return listOf(defaultConfig) + presetConfigs
    }

    val array = runCatching { JSONArray(payload) }.getOrElse {
      saveCustomGptConfigsLocked(presetConfigs)
      markPresetConfigsSeededLocked()
      return listOf(defaultConfig) + presetConfigs
    }
    val storedCustomConfigs = buildList {
      repeat(array.length()) { index ->
        val item = array.optJSONObject(index) ?: return@repeat
        val id = item.optNullableString(JSON_KEY_ID) ?: return@repeat
        val name = item.optNullableString(JSON_KEY_NAME) ?: return@repeat
        add(
          GPTConfig(
            id = id,
            name = name,
            baseUrl = item.optRawString(JSON_KEY_BASE_URL).orEmpty(),
            apiKey = item.optRawString(JSON_KEY_API_KEY).orEmpty(),
            isDefault = false
          )
        )
      }
    }.distinctBy { it.id }.sortedBy { it.name.lowercase() }

    val missingPresetConfigs = presetConfigs.filterNot { preset ->
      storedCustomConfigs.any { it.id == preset.id }
    }
    if (missingPresetConfigs.isEmpty() && isPresetConfigsSeededLocked()) {
      return listOf(defaultConfig) + storedCustomConfigs
    }

    val mergedCustomConfigs = buildList {
      addAll(storedCustomConfigs)
      addAll(missingPresetConfigs)
    }.distinctBy { it.id }.sortedBy { it.name.lowercase() }
    saveCustomGptConfigsLocked(mergedCustomConfigs)
    markPresetConfigsSeededLocked()
    return listOf(defaultConfig) + mergedCustomConfigs
  }

  private fun saveCustomGptConfigsLocked(configs: List<GPTConfig>) {
    val array = JSONArray()
    configs
      .filterNot { it.isDefault || it.id == GPTConfigPreferencesKeys.DEFAULT_CONFIG_ID }
      .forEach { config ->
        array.put(
          JSONObject()
            .put(JSON_KEY_ID, config.id)
            .put(JSON_KEY_NAME, config.name)
            .put(JSON_KEY_BASE_URL, config.baseUrl)
            .put(JSON_KEY_API_KEY, config.apiKey)
        )
      }

    preferences.sharedPreferences.edit {
      putString(GPTConfigPreferencesKeys.KEY_GPT_CONFIGS, array.toString())
    }
  }

  private fun resolveActiveGptConfigLocked(configs: List<GPTConfig>): GPTConfig {
    val activeConfigId = preferences.sharedPreferences
      .getString(GPTConfigPreferencesKeys.KEY_ACTIVE_GPT_CONFIG_ID, null)
      .orEmpty()

    return configs.firstOrNull { it.id == activeConfigId } ?: defaultGptConfig()
  }

  private fun persistActiveGptConfigLocked(config: GPTConfig) {
    preferences.sharedPreferences.edit {
      putString(GPTConfigPreferencesKeys.KEY_ACTIVE_GPT_CONFIG_ID, config.id)
      putString(GPTConfigPreferencesKeys.KEY_ACTIVE_GPT_BASE_URL, config.baseUrl)
      putString(GPTConfigPreferencesKeys.KEY_ACTIVE_GPT_API_KEY, config.apiKey)
    }
  }

  private fun defaultGptConfig(): GPTConfig = GPTConfig(
    id = GPTConfigPreferencesKeys.DEFAULT_CONFIG_ID,
    name = GPTConfigPreferencesKeys.DEFAULT_CONFIG_NAME,
    baseUrl = NetworkBuildConfig.GPT_BASE_URL,
    apiKey = NetworkBuildConfig.GPT_API_KEY,
    isDefault = true
  )

  private fun initialCustomGptConfigs(): List<GPTConfig> = listOfNotNull(
    createPresetConfig(
      id = PRESET_CONFIG_ID_CODE2,
      fallbackName = "YLSAGI",
      name = NetworkBuildConfig.GPT_PRESET_1_NAME,
      baseUrl = NetworkBuildConfig.GPT_PRESET_1_BASE_URL,
      apiKey = NetworkBuildConfig.GPT_PRESET_1_API_KEY
    ),
    createPresetConfig(
      id = PRESET_CONFIG_ID_88CODE,
      fallbackName = "88Code",
      name = NetworkBuildConfig.GPT_PRESET_2_NAME,
      baseUrl = NetworkBuildConfig.GPT_PRESET_2_BASE_URL,
      apiKey = NetworkBuildConfig.GPT_PRESET_2_API_KEY
    ),
    createPresetConfig(
      id = PRESET_CONFIG_ID_PACKY,
      fallbackName = "Packy API",
      name = NetworkBuildConfig.GPT_PRESET_3_NAME,
      baseUrl = NetworkBuildConfig.GPT_PRESET_3_BASE_URL,
      apiKey = NetworkBuildConfig.GPT_PRESET_3_API_KEY
    )
  ).sortedBy { it.name.lowercase() }

  private fun createPresetConfig(
    id: String,
    fallbackName: String,
    name: String,
    baseUrl: String,
    apiKey: String
  ): GPTConfig? {
    if (baseUrl.isBlank() || apiKey.isBlank()) return null
    return GPTConfig(
      id = id,
      name = name.ifBlank { fallbackName },
      baseUrl = baseUrl,
      apiKey = apiKey,
      isDefault = false
    ).normalizeCustomConfig()
  }

  private fun isPresetConfigsSeededLocked(): Boolean = preferences.sharedPreferences
    .getBoolean(KEY_GPT_PRESET_CONFIGS_SEEDED, false)

  private fun markPresetConfigsSeededLocked() {
    preferences.sharedPreferences.edit {
      putBoolean(KEY_GPT_PRESET_CONFIGS_SEEDED, true)
    }
  }

  private fun GPTConfig.normalizeCustomConfig(): GPTConfig = copy(
    id = id.trim().ifBlank { UUID.randomUUID().toString() },
    name = name.trim().ifBlank { DEFAULT_CUSTOM_CONFIG_NAME },
    baseUrl = baseUrl.trim(),
    apiKey = apiKey.trim(),
    isDefault = false
  )

  private fun loadLocalSessionSummariesLocked(): List<LocalChatSessionSummary> {
    val payload = preferences.sharedPreferences.getString(KEY_LOCAL_CHAT_SESSIONS, null).orEmpty()
    if (payload.isBlank()) return emptyList()

    val sessionArray = runCatching { JSONArray(payload) }.getOrElse { return emptyList() }
    return buildList {
      repeat(sessionArray.length()) { index ->
        val item = sessionArray.optJSONObject(index) ?: return@repeat
        val id = item.optNullableString(JSON_KEY_ID) ?: return@repeat
        add(
          LocalChatSessionSummary(
            id = id,
            title = item.optNullableString(JSON_KEY_TITLE) ?: DEFAULT_LOCAL_SESSION_TITLE,
            preview = item.optRawString(JSON_KEY_PREVIEW).orEmpty(),
            updatedAt = item.optLong(JSON_KEY_UPDATED_AT, 0L),
            messageCount = item.optInt(JSON_KEY_MESSAGE_COUNT, 0)
          )
        )
      }
    }.sortedByDescending(LocalChatSessionSummary::updatedAt)
  }

  private fun saveLocalSessionSummariesLocked(summaries: List<LocalChatSessionSummary>) {
    val jsonArray = JSONArray()
    summaries
      .sortedByDescending(LocalChatSessionSummary::updatedAt)
      .forEach { summary ->
        jsonArray.put(
          JSONObject()
            .put(JSON_KEY_ID, summary.id)
            .put(JSON_KEY_TITLE, summary.title)
            .put(JSON_KEY_PREVIEW, summary.preview)
            .put(JSON_KEY_UPDATED_AT, summary.updatedAt)
            .put(JSON_KEY_MESSAGE_COUNT, summary.messageCount)
        )
      }

    preferences.sharedPreferences.edit {
      putString(KEY_LOCAL_CHAT_SESSIONS, jsonArray.toString())
    }
  }

  private fun loadLocalSessionMessagesLocked(sessionId: String): List<LocalChatMessage> {
    if (sessionId.isBlank()) return emptyList()

    val payload = preferences.sharedPreferences
      .getString(localSessionMessagesKey(sessionId), null)
      .orEmpty()
    if (payload.isBlank()) return emptyList()

    val messagesArray = runCatching { JSONArray(payload) }.getOrElse { return emptyList() }
    return buildList {
      repeat(messagesArray.length()) { index ->
        val item = messagesArray.optJSONObject(index) ?: return@repeat
        val role = item.optNullableString(JSON_KEY_ROLE) ?: return@repeat
        val content = item.optRawString(JSON_KEY_CONTENT) ?: return@repeat
        val reasoning = item.optRawString(JSON_KEY_REASONING)
        val toolEvents = item.optJSONArray(JSON_KEY_TOOL_EVENTS).toLocalToolEvents()

        add(
          LocalChatMessage(
            role = role,
            content = content,
            reasoning = reasoning,
            toolEvents = toolEvents,
            isStreaming = false,
            isError = item.optBoolean(JSON_KEY_IS_ERROR, false)
          )
        )
      }
    }
  }

  private fun saveLocalSessionMessagesLocked(sessionId: String, messages: List<LocalChatMessage>) {
    if (sessionId.isBlank()) return

    val jsonArray = JSONArray()
    messages.forEach { message ->
      val messageJson = JSONObject()
        .put(JSON_KEY_ROLE, message.role)
        .put(JSON_KEY_CONTENT, message.content)
        .put(JSON_KEY_IS_ERROR, message.isError)

      message.reasoning
        ?.takeIf(String::isNotBlank)
        ?.let { messageJson.put(JSON_KEY_REASONING, it) }

      if (message.toolEvents.isNotEmpty()) {
        val events = JSONArray()
        message.toolEvents.forEach { event ->
          events.put(
            JSONObject().put(JSON_KEY_TYPE, event.type).apply {
              event.status?.let { put(JSON_KEY_STATUS, it) }
              event.itemType?.let { put(JSON_KEY_ITEM_TYPE, it) }
              event.query?.let { put(JSON_KEY_QUERY, it) }
              event.message?.let { put(JSON_KEY_MESSAGE, it) }
            }
          )
        }
        messageJson.put(JSON_KEY_TOOL_EVENTS, events)
      }

      jsonArray.put(messageJson)
    }

    preferences.sharedPreferences.edit {
      putString(localSessionMessagesKey(sessionId), jsonArray.toString())
    }
  }

  private fun List<LocalChatMessage>.toSessionSummary(
    sessionId: String,
    previousTitle: String?
  ): LocalChatSessionSummary {
    val titleCandidate = firstOrNull { it.role == USER_ROLE }
      ?.content
      ?.trim()
      ?.take(MAX_SESSION_TITLE_LENGTH)
      .orEmpty()

    val title = titleCandidate.ifBlank {
      previousTitle?.takeIf(String::isNotBlank) ?: DEFAULT_LOCAL_SESSION_TITLE
    }

    val preview = lastOrNull { it.content.isNotBlank() }
      ?.content
      ?.replace("\n", " ")
      ?.trim()
      ?.take(MAX_PREVIEW_LENGTH)
      .orEmpty()

    return LocalChatSessionSummary(
      id = sessionId,
      title = title,
      preview = preview,
      updatedAt = System.currentTimeMillis(),
      messageCount = size
    )
  }

  private fun localSessionMessagesKey(sessionId: String): String =
    "$KEY_LOCAL_CHAT_MESSAGES_PREFIX$sessionId"

  private companion object {
    private const val KEY_LOCAL_CHAT_SESSIONS = "key_local_chat_sessions"
    private const val KEY_LOCAL_CHAT_MESSAGES_PREFIX = "key_local_chat_messages_"
    private const val KEY_GPT_PRESET_CONFIGS_SEEDED = "key_gpt_preset_configs_seeded"

    private const val JSON_KEY_ID = "id"
    private const val JSON_KEY_NAME = "name"
    private const val JSON_KEY_BASE_URL = "baseUrl"
    private const val JSON_KEY_API_KEY = "apiKey"
    private const val JSON_KEY_TITLE = "title"
    private const val JSON_KEY_PREVIEW = "preview"
    private const val JSON_KEY_UPDATED_AT = "updatedAt"
    private const val JSON_KEY_MESSAGE_COUNT = "messageCount"

    private const val JSON_KEY_ROLE = "role"
    private const val JSON_KEY_CONTENT = "content"
    private const val JSON_KEY_REASONING = "reasoning"
    private const val JSON_KEY_TOOL_EVENTS = "toolEvents"
    private const val JSON_KEY_IS_ERROR = "isError"

    private const val JSON_KEY_TYPE = "type"
    private const val JSON_KEY_STATUS = "status"
    private const val JSON_KEY_ITEM_TYPE = "itemType"
    private const val JSON_KEY_QUERY = "query"
    private const val JSON_KEY_MESSAGE = "message"

    private const val USER_ROLE = "user"

    private const val DEFAULT_LOCAL_SESSION_TITLE = "New Chat"
    private const val DEFAULT_CUSTOM_CONFIG_NAME = "Custom"
    private const val MAX_SESSION_TITLE_LENGTH = 60
    private const val MAX_PREVIEW_LENGTH = 80

    private const val PRESET_CONFIG_ID_CODE2 = "preset_code2_ylsagi"
    private const val PRESET_CONFIG_ID_88CODE = "preset_88code"
    private const val PRESET_CONFIG_ID_PACKY = "preset_packyapi"
  }
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
  if (isBlank()) return null
  val payload = trim()

  val directJsonMessage = runCatching {
    val json = JSONObject(payload)
    val error = json.optJSONObject("error")
    error.optNullableString("message") ?: json.optNullableString("message")
  }.getOrNull()
  if (!directJsonMessage.isNullOrBlank()) {
    return directJsonMessage.extractEmbeddedDetailIfPresent()
  }

  // Some gateways return SSE-formatted errors inside HTTP error bodies.
  val sseMessage = payload
    .lineSequence()
    .map(String::trim)
    .firstOrNull { it.startsWith("data:") }
    ?.removePrefix("data:")
    ?.trim()
    ?.takeIf(String::isNotEmpty)
    ?.let { dataLine ->
      runCatching {
        val json = JSONObject(dataLine)
        val error = json.optJSONObject("error")
        error.optNullableString("message") ?: json.optNullableString("message")
      }.getOrNull()
    }
  if (!sseMessage.isNullOrBlank()) {
    return sseMessage.extractEmbeddedDetailIfPresent()
  }

  return payload.take(256)
}

private fun String.extractEmbeddedDetailIfPresent(): String {
  val detailPattern = Regex("""\\"detail\\":\\"([^\\"]+)\\"""")
  val detail = detailPattern.find(this)?.groupValues?.getOrNull(1)
  return detail ?: this
}

private fun JSONObject?.optNullableString(name: String): String? {
  if (this == null || !has(name) || isNull(name)) return null
  return opt(name)?.toString()?.trim()?.takeIf(String::isNotEmpty)
}

private fun JSONObject?.optRawString(name: String): String? {
  if (this == null || !has(name) || isNull(name)) return null
  return opt(name)?.toString()
}

private fun JSONArray?.toLocalToolEvents(): List<LocalChatToolEvent> {
  if (this == null) return emptyList()

  return buildList {
    repeat(length()) { index ->
      val item = optJSONObject(index) ?: return@repeat
      val type = item.optNullableString("type") ?: return@repeat
      add(
        LocalChatToolEvent(
          type = type,
          status = item.optNullableString("status"),
          itemType = item.optNullableString("itemType"),
          query = item.optRawString("query"),
          message = item.optRawString("message")
        )
      )
    }
  }
}

private fun LocalChatMessage.isTransientStreamingPlaceholder(): Boolean =
  role == "assistant" &&
    content.isBlank() &&
    reasoning.isNullOrBlank() &&
    toolEvents.isEmpty() &&
    !isError
