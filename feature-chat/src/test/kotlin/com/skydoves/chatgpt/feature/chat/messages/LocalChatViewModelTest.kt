package com.skydoves.chatgpt.feature.chat.messages

import com.skydoves.chatgpt.core.data.repository.GPTMessageRepository
import com.skydoves.chatgpt.core.model.GPTMessage
import com.skydoves.chatgpt.core.model.local.GPTConfig
import com.skydoves.chatgpt.core.model.local.LocalChatMessage
import com.skydoves.chatgpt.core.model.local.LocalChatSessionSummary
import com.skydoves.chatgpt.core.model.network.GPTChatRequest
import com.skydoves.chatgpt.core.model.network.GPTChatResponse
import com.skydoves.chatgpt.core.model.network.GPTResponseOutput
import com.skydoves.chatgpt.feature.chat.BuildConfig
import com.skydoves.chatgpt.feature.chat.testing.MainDispatcherRule
import com.skydoves.sandwich.ApiResponse
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LocalChatViewModelTest {

  @get:Rule
  val mainDispatcherRule = MainDispatcherRule()

  @Test
  fun sendMessage_blank_doesNothing() = runTest(mainDispatcherRule.dispatcher) {
    val repository = QueueingRepository(
      responses = ArrayDeque(
        listOf(ApiResponse.Success(GPTChatResponse()))
      )
    )
    val viewModel = LocalChatViewModel(repository)

    viewModel.sendMessage("   ")
    runCurrent()

    assertTrue(repository.requests.isEmpty())
    assertTrue(viewModel.messages.value.isEmpty())
    assertFalse(viewModel.sending.value)
  }

  @Test
  fun sendMessage_trimsText_buildsRequestFromHistory_andAppendsAssistantMessage() =
    runTest(mainDispatcherRule.dispatcher) {
      val repository = QueueingRepository(
        responses = ArrayDeque(
          listOf(
            ApiResponse.Success(
              GPTChatResponse(
                output = listOf(
                  GPTResponseOutput(type = "message", role = "assistant", text = "Hi!")
                )
              )
            )
          )
        )
      )
      val viewModel = LocalChatViewModel(repository)

      viewModel.sendMessage("  hello  ")
      advanceUntilIdle()

      assertEquals(1, repository.requests.size)
      val request = repository.requests.single()
      assertEquals(BuildConfig.GPT_MODEL, request.model)
      assertEquals(1, request.input.size)
      assertEquals("user", request.input[0].role)
      assertEquals("input_text", request.input[0].content.single().type)
      assertEquals("hello", request.input[0].content.single().text)

      assertFalse(viewModel.sending.value)
      assertEquals(
        listOf(
          LocalChatMessage(role = "user", content = "hello"),
          LocalChatMessage(role = "assistant", content = "Hi!", reasoning = null, isError = false),
        ),
        viewModel.messages.value,
      )
    }

  @Test
  fun sendMessage_whenAlreadySending_doesNotQueueSecondMessageOrRequest() =
    runTest(mainDispatcherRule.dispatcher) {
      val repository = SuspendedRepository()
      val viewModel = LocalChatViewModel(repository)

      viewModel.sendMessage("one")
      runCurrent() // flips sending=true and enters repository.sendMessage(...)

      assertTrue(viewModel.sending.value)
      assertEquals(1, repository.requests.size)
      assertEquals(
        listOf(
          LocalChatMessage(role = "user", content = "one"),
          LocalChatMessage(role = "assistant", content = "", isStreaming = true),
        ),
        viewModel.messages.value
      )

      viewModel.sendMessage("two")
      runCurrent()

      assertEquals(1, repository.requests.size)
      assertEquals(
        listOf(
          LocalChatMessage(role = "user", content = "one"),
          LocalChatMessage(role = "assistant", content = "", isStreaming = true),
        ),
        viewModel.messages.value
      )

      repository.response.complete(
        ApiResponse.Success(
          GPTChatResponse(output = listOf(GPTResponseOutput(type = "message", role = "assistant", text = "ack")))
        )
      )
      advanceUntilIdle()

      assertFalse(viewModel.sending.value)
      assertEquals(2, viewModel.messages.value.size)
      assertEquals("ack", viewModel.messages.value.last().content)
    }

  @Test
  fun sendMessage_failure_appendsErrorMessage_andNextRequestExcludesErrorFromHistory() =
    runTest(mainDispatcherRule.dispatcher) {
      val repository = QueueingRepository(
        responses = ArrayDeque(
          listOf(
            ApiResponse.Failure.Exception(IllegalStateException("boom")),
            ApiResponse.Success(
              GPTChatResponse(
                output = listOf(GPTResponseOutput(type = "message", role = "assistant", text = "ok"))
              )
            ),
          )
        )
      )
      val viewModel = LocalChatViewModel(repository)

      viewModel.sendMessage("first")
      advanceUntilIdle()

      assertEquals(1, repository.requests.size)
      assertEquals(2, viewModel.messages.value.size)
      assertTrue(viewModel.messages.value[1].isError)
      assertEquals("Error: boom", viewModel.messages.value[1].content)

      viewModel.sendMessage("second")
      advanceUntilIdle()

      assertEquals(2, repository.requests.size)
      val request2 = repository.requests[1]
      assertEquals(
        listOf(
          GPTMessage(role = "user", content = "first"),
          GPTMessage(role = "user", content = "second"),
        ),
        request2.input.map { inputMessage ->
          GPTMessage(role = inputMessage.role, content = inputMessage.content.single().text)
        }
      )

      assertEquals("ok", viewModel.messages.value.last().content)
    }

  @Test
  fun sendMessage_nextRequestIncludesPriorAssistantAsOutputText() =
    runTest(mainDispatcherRule.dispatcher) {
      val repository = QueueingRepository(
        responses = ArrayDeque(
          listOf(
            ApiResponse.Success(
              GPTChatResponse(
                output = listOf(GPTResponseOutput(type = "message", role = "assistant", text = "a1"))
              )
            ),
            ApiResponse.Success(
              GPTChatResponse(
                output = listOf(GPTResponseOutput(type = "message", role = "assistant", text = "a2"))
              )
            ),
          )
        )
      )
      val viewModel = LocalChatViewModel(repository)

      viewModel.sendMessage("u1")
      advanceUntilIdle()
      viewModel.sendMessage("u2")
      advanceUntilIdle()

      assertEquals(2, repository.requests.size)
      val request2 = repository.requests[1]
      assertEquals(3, request2.input.size)
      assertEquals("user", request2.input[0].role)
      assertEquals("input_text", request2.input[0].content.single().type)
      assertEquals("assistant", request2.input[1].role)
      assertEquals("output_text", request2.input[1].content.single().type)
      assertEquals("user", request2.input[2].role)
      assertEquals("input_text", request2.input[2].content.single().type)
    }

  @Test
  fun sendMessage_success_includesReasoningOnlyWhenNotBlank() =
    runTest(mainDispatcherRule.dispatcher) {
      val repository = QueueingRepository(
        responses = ArrayDeque(
          listOf(
            ApiResponse.Success(
              GPTChatResponse(
                output = listOf(
                  GPTResponseOutput(type = "reasoning", text = "  because  "),
                  GPTResponseOutput(type = "message", role = "assistant", text = "answer"),
                )
              )
            ),
            ApiResponse.Success(
              GPTChatResponse(
                output = listOf(
                  GPTResponseOutput(type = "reasoning", text = "   "),
                  GPTResponseOutput(type = "message", role = "assistant", text = "answer2"),
                )
              )
            ),
          )
        )
      )
      val viewModel = LocalChatViewModel(repository)

      viewModel.sendMessage("q1")
      advanceUntilIdle()
      assertEquals("because", viewModel.messages.value.last().reasoning)

      viewModel.sendMessage("q2")
      advanceUntilIdle()
      assertNull(viewModel.messages.value.last().reasoning)
    }

  @Test
  fun sendMessage_onStreamResetCancel_retriesWithReasoningNone() =
    runTest(mainDispatcherRule.dispatcher) {
      val repository = QueueingRepository(
        responses = ArrayDeque(
          listOf(
            ApiResponse.Failure.Exception(IllegalStateException("stream was reset: CANCEL")),
            ApiResponse.Success(
              GPTChatResponse(
                output = listOf(GPTResponseOutput(type = "message", role = "assistant", text = "ok"))
              )
            ),
          )
        )
      )
      val viewModel = LocalChatViewModel(repository)

      viewModel.sendMessage("hello")
      advanceUntilIdle()

      assertEquals(2, repository.requests.size)
      assertEquals("high", repository.requests[0].reasoning.effort)
      assertEquals(1, repository.requests[0].tools.size)
      assertEquals("none", repository.requests[1].reasoning.effort)
      assertEquals(1, repository.requests[1].tools.size)
      assertEquals("ok", viewModel.messages.value.last().content)
      assertFalse(viewModel.sending.value)
    }

  @Test
  fun sendMessage_onRepeatedStreamResetCancel_retriesWithoutTools() =
    runTest(mainDispatcherRule.dispatcher) {
      val repository = QueueingRepository(
        responses = ArrayDeque(
          listOf(
            ApiResponse.Failure.Exception(IllegalStateException("stream was reset: CANCEL")),
            ApiResponse.Failure.Exception(IllegalStateException("http2 framing layer")),
            ApiResponse.Success(
              GPTChatResponse(
                output = listOf(GPTResponseOutput(type = "message", role = "assistant", text = "ok"))
              )
            ),
          )
        )
      )
      val viewModel = LocalChatViewModel(repository)

      viewModel.sendMessage("hello")
      advanceUntilIdle()

      assertEquals(3, repository.requests.size)
      assertEquals("none", repository.requests[1].reasoning.effort)
      assertEquals(1, repository.requests[1].tools.size)
      assertEquals("none", repository.requests[2].reasoning.effort)
      assertTrue(repository.requests[2].tools.isEmpty())
      assertEquals("ok", viewModel.messages.value.last().content)
      assertFalse(viewModel.sending.value)
    }

  @Test
  fun loadSession_doesNotGetOverriddenByInitMostRecentSelection() =
    runTest(mainDispatcherRule.dispatcher) {
      val initGate = CompletableDeferred<Unit>()
      val repository = object : GPTMessageRepository {
        private val defaultConfig = GPTConfig(
          id = "default",
          name = "Default",
          baseUrl = "",
          apiKey = "",
          isDefault = true
        )
        private var listCalls = 0

        override suspend fun sendMessage(gptChatRequest: GPTChatRequest): ApiResponse<GPTChatResponse> {
          throw AssertionError("sendMessage should not be called in this test")
        }

        override suspend fun listGptConfigs(): List<GPTConfig> = listOf(defaultConfig)

        override suspend fun getActiveGptConfig(): GPTConfig = defaultConfig

        override suspend fun setActiveGptConfig(configId: String) = Unit

        override suspend fun upsertGptConfig(config: GPTConfig) = Unit

        override suspend fun deleteGptConfig(configId: String) = Unit

        override suspend fun listLocalChatSessions(): List<LocalChatSessionSummary> {
          listCalls += 1
          // First call (from init) is delayed so a later loadSession() can finish first.
          if (listCalls == 1) initGate.await()
          return listOf(
            LocalChatSessionSummary(
              id = "newest",
              title = "Newest",
              preview = "",
              updatedAt = 2L,
              messageCount = 1
            ),
            LocalChatSessionSummary(
              id = "older",
              title = "Older",
              preview = "",
              updatedAt = 1L,
              messageCount = 1
            )
          )
        }

        override suspend fun deleteLocalChatSession(sessionId: String) = Unit

        override suspend fun createLocalChatSession(): LocalChatSessionSummary {
          throw AssertionError("createLocalChatSession should not be called in this test")
        }

        override suspend fun loadLocalChatSessionMessages(sessionId: String): List<LocalChatMessage> =
          when (sessionId) {
            "older" -> listOf(LocalChatMessage(role = "user", content = "older-msg"))
            "newest" -> listOf(LocalChatMessage(role = "user", content = "newest-msg"))
            else -> emptyList()
          }

        override suspend fun saveLocalChatSessionMessages(
          sessionId: String,
          messages: List<LocalChatMessage>
        ): LocalChatSessionSummary {
          throw AssertionError("saveLocalChatSessionMessages should not be called in this test")
        }

        override fun watchIsChannelMessageEmpty(cid: String): Flow<Boolean> = emptyFlow()
      }

      val viewModel = LocalChatViewModel(repository)

      // Let init start and get stuck at the first listLocalChatSessions() call.
      runCurrent()

      viewModel.loadSession("older")
      advanceUntilIdle()

      // Now allow init to continue; it should not override the user-selected session.
      initGate.complete(Unit)
      advanceUntilIdle()

      assertEquals("older", viewModel.activeSessionId.value)
      assertEquals(listOf(LocalChatMessage(role = "user", content = "older-msg")), viewModel.messages.value)
    }

  private class QueueingRepository(
    private val responses: ArrayDeque<ApiResponse<GPTChatResponse>>,
  ) : GPTMessageRepository {
    private var sessionMessages: List<LocalChatMessage> = emptyList()
    private val defaultConfig = GPTConfig(
      id = "default",
      name = "Default",
      baseUrl = "",
      apiKey = "",
      isDefault = true
    )
    val requests = mutableListOf<GPTChatRequest>()

    override suspend fun sendMessage(gptChatRequest: GPTChatRequest): ApiResponse<GPTChatResponse> {
      requests += gptChatRequest
      return responses.removeFirstOrNull()
        ?: throw AssertionError("No queued response left for sendMessage")
    }

    override suspend fun listGptConfigs(): List<GPTConfig> = listOf(defaultConfig)

    override suspend fun getActiveGptConfig(): GPTConfig = defaultConfig

    override suspend fun setActiveGptConfig(configId: String) = Unit

    override suspend fun upsertGptConfig(config: GPTConfig) = Unit

    override suspend fun deleteGptConfig(configId: String) = Unit

    override suspend fun listLocalChatSessions(): List<LocalChatSessionSummary> = listOf(
      LocalChatSessionSummary(
        id = SESSION_ID,
        title = "New Chat",
        preview = "",
        updatedAt = 0L,
        messageCount = sessionMessages.size
      )
    )

    override suspend fun deleteLocalChatSession(sessionId: String) {
      if (sessionId == SESSION_ID) {
        sessionMessages = emptyList()
      }
    }

    override suspend fun createLocalChatSession(): LocalChatSessionSummary = LocalChatSessionSummary(
      id = SESSION_ID,
      title = "New Chat",
      preview = "",
      updatedAt = 0L,
      messageCount = 0
    )

    override suspend fun loadLocalChatSessionMessages(sessionId: String): List<LocalChatMessage> =
      sessionMessages

    override suspend fun saveLocalChatSessionMessages(
      sessionId: String,
      messages: List<LocalChatMessage>
    ): LocalChatSessionSummary {
      sessionMessages = messages
      return LocalChatSessionSummary(
        id = sessionId,
        title = "New Chat",
        preview = messages.lastOrNull()?.content.orEmpty(),
        updatedAt = 0L,
        messageCount = messages.size
      )
    }

    override fun watchIsChannelMessageEmpty(cid: String): Flow<Boolean> = emptyFlow()
  }

  private class SuspendedRepository : GPTMessageRepository {
    private var sessionMessages: List<LocalChatMessage> = emptyList()
    private val defaultConfig = GPTConfig(
      id = "default",
      name = "Default",
      baseUrl = "",
      apiKey = "",
      isDefault = true
    )
    val requests = mutableListOf<GPTChatRequest>()
    val response = CompletableDeferred<ApiResponse<GPTChatResponse>>()

    override suspend fun sendMessage(gptChatRequest: GPTChatRequest): ApiResponse<GPTChatResponse> {
      requests += gptChatRequest
      return response.await()
    }

    override suspend fun listGptConfigs(): List<GPTConfig> = listOf(defaultConfig)

    override suspend fun getActiveGptConfig(): GPTConfig = defaultConfig

    override suspend fun setActiveGptConfig(configId: String) = Unit

    override suspend fun upsertGptConfig(config: GPTConfig) = Unit

    override suspend fun deleteGptConfig(configId: String) = Unit

    override suspend fun listLocalChatSessions(): List<LocalChatSessionSummary> = listOf(
      LocalChatSessionSummary(
        id = SESSION_ID,
        title = "New Chat",
        preview = "",
        updatedAt = 0L,
        messageCount = sessionMessages.size
      )
    )

    override suspend fun deleteLocalChatSession(sessionId: String) {
      if (sessionId == SESSION_ID) {
        sessionMessages = emptyList()
      }
    }

    override suspend fun createLocalChatSession(): LocalChatSessionSummary = LocalChatSessionSummary(
      id = SESSION_ID,
      title = "New Chat",
      preview = "",
      updatedAt = 0L,
      messageCount = 0
    )

    override suspend fun loadLocalChatSessionMessages(sessionId: String): List<LocalChatMessage> =
      sessionMessages

    override suspend fun saveLocalChatSessionMessages(
      sessionId: String,
      messages: List<LocalChatMessage>
    ): LocalChatSessionSummary {
      sessionMessages = messages
      return LocalChatSessionSummary(
        id = sessionId,
        title = "New Chat",
        preview = messages.lastOrNull()?.content.orEmpty(),
        updatedAt = 0L,
        messageCount = messages.size
      )
    }

    override fun watchIsChannelMessageEmpty(cid: String): Flow<Boolean> = emptyFlow()
  }

  private companion object {
    private const val SESSION_ID = "session-test"
  }
}
