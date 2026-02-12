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

import android.content.Intent
import android.net.Uri
import android.text.method.LinkMovementMethod
import android.util.TypedValue
import android.widget.TextView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.skydoves.chatgpt.feature.chat.R
import com.skydoves.chatgpt.feature.chat.theme.ChatGPTStreamTheme
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration

@Composable
fun LocalChatGPTMessages(
  onBackPressed: () -> Unit,
  viewModel: LocalChatViewModel = hiltViewModel()
) {
  val messages by viewModel.messages.collectAsStateWithLifecycle()
  val sending by viewModel.sending.collectAsStateWithLifecycle()
  val listState = rememberLazyListState()
  val emptyResponse = stringResource(id = R.string.local_mode_empty_response)

  var input by rememberSaveable { mutableStateOf("") }

  fun onSendMessage() {
    if (input.isBlank() || sending) return
    viewModel.sendMessage(input)
    input = ""
  }

  LaunchedEffect(messages.size) {
    if (messages.isNotEmpty()) {
      listState.animateScrollToItem(messages.lastIndex)
    }
  }

  ChatGPTStreamTheme {
    val backgroundBrush = Brush.verticalGradient(
      colors = listOf(
        MaterialTheme.colorScheme.surface,
        MaterialTheme.colorScheme.background,
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
      )
    )

    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(backgroundBrush)
    ) {
      Column(modifier = Modifier.fillMaxSize()) {
        LocalChatHeader(
          onBackPressed = onBackPressed,
          sending = sending,
          messageCount = messages.size
        )

        if (messages.isEmpty()) {
          LocalChatEmptyState(
            modifier = Modifier
              .weight(1f)
              .fillMaxWidth()
              .padding(horizontal = 20.dp)
          )
        } else {
          LazyColumn(
            state = listState,
            modifier = Modifier
              .weight(1f)
              .fillMaxWidth()
              .padding(horizontal = 12.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            itemsIndexed(
              items = messages,
              key = { index, message -> "$index-${message.role}-${message.content.hashCode()}" }
            ) { index, message ->
              LocalChatMessageBubble(
                index = index,
                message = message,
                emptyResponse = emptyResponse
              )
            }
          }
        }

        LocalInputSection(
          input = input,
          sending = sending,
          onInputChange = { input = it },
          onSendClick = ::onSendMessage
        )
      }
    }
  }
}

@Composable
private fun LocalChatHeader(
  onBackPressed: () -> Unit,
  sending: Boolean,
  messageCount: Int
) {
  Surface(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 12.dp, vertical = 10.dp),
    color = MaterialTheme.colorScheme.surfaceVariant,
    shape = RoundedCornerShape(16.dp),
    tonalElevation = 2.dp
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(2.dp)
      ) {
        Text(
          text = stringResource(id = R.string.local_mode_title),
          style = MaterialTheme.typography.titleMedium
        )
        Text(
          text = if (sending) {
            stringResource(id = R.string.local_mode_subtitle_sending)
          } else {
            stringResource(
              id = R.string.local_mode_subtitle_ready,
              messageCount
            )
          },
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
      TextButton(onClick = onBackPressed) {
        Text(text = stringResource(id = R.string.local_mode_back))
      }
    }
  }
}

@Composable
private fun LocalChatEmptyState(modifier: Modifier = Modifier) {
  Box(modifier = modifier, contentAlignment = Alignment.Center) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Text(
        text = stringResource(id = R.string.local_mode_empty_title),
        style = MaterialTheme.typography.titleMedium
      )
      Text(
        text = stringResource(id = R.string.local_mode_empty_description),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
      )
    }
  }
}

@Composable
private fun LocalChatMessageBubble(
  index: Int,
  message: LocalChatMessage,
  emptyResponse: String
) {
  val isUser = message.role == USER_ROLE
  val streamingPlaceholder = stringResource(id = R.string.local_mode_streaming_placeholder)
  val parsedMessage = remember(
    message.role,
    message.content,
    message.reasoning,
    message.toolEvents,
    message.isStreaming,
    emptyResponse,
    streamingPlaceholder
  ) {
    ParsedLocalMessage(
      answer = message.content.trim().ifBlank {
        if (message.isStreaming) {
          streamingPlaceholder
        } else {
          emptyResponse
        }
      },
      reasoning = message.reasoning?.trim()?.ifBlank { null },
      toolEvents = message.toolEvents
    )
  }

  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
  ) {
    Column(
      modifier = Modifier.widthIn(max = 320.dp),
      horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
      Text(
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        text = if (isUser) {
          stringResource(id = R.string.local_mode_user_label)
        } else {
          stringResource(id = R.string.local_mode_assistant_label)
        },
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      Surface(
        shape = RoundedCornerShape(
          topStart = if (isUser) 18.dp else 6.dp,
          topEnd = if (isUser) 6.dp else 18.dp,
          bottomStart = 18.dp,
          bottomEnd = 18.dp
        ),
        color = if (isUser) {
          MaterialTheme.colorScheme.primary
        } else {
          MaterialTheme.colorScheme.surface
        },
        border = if (isUser) {
          null
        } else {
          BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
          )
        },
        tonalElevation = if (isUser) 1.dp else 3.dp
      ) {
        Column(
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          if (isUser) {
            Text(
              text = parsedMessage.answer,
              style = MaterialTheme.typography.bodyMedium,
              color = Color.White
            )
          } else {
            MarkdownMessageText(
              markdown = parsedMessage.answer,
              textColor = MaterialTheme.colorScheme.onSurface,
              textSizeSp = MaterialTheme.typography.bodyMedium.fontSize.value
            )
            ReasoningSection(
              index = index,
              reasoning = parsedMessage.reasoning,
              toolEvents = parsedMessage.toolEvents,
              isStreaming = message.isStreaming
            )
          }
        }
      }
    }
  }
}

@Composable
private fun ReasoningSection(
  index: Int,
  reasoning: String?,
  toolEvents: List<LocalChatToolEvent>,
  isStreaming: Boolean
) {
  val shouldShowProcess = reasoning != null || toolEvents.isNotEmpty() || isStreaming
  if (!shouldShowProcess) return

  val reasoningPending = stringResource(id = R.string.local_mode_reasoning_pending)
  val toolsPending = stringResource(id = R.string.local_mode_tool_calls_pending)
  var expanded by rememberSaveable(
    index,
    reasoning,
    toolEvents.hashCode(),
    isStreaming
  ) { mutableStateOf(isStreaming) }
  val timelineItems = remember(reasoning, toolEvents, isStreaming, reasoningPending, toolsPending) {
    listOf(
      ProcessTimelineItem(
        titleRes = R.string.local_mode_reasoning_title,
        content = reasoning,
        emptyRes = if (isStreaming) {
          R.string.local_mode_reasoning_pending
        } else {
          R.string.local_mode_reasoning_empty
        }
      ),
      ProcessTimelineItem(
        titleRes = R.string.local_mode_tool_calls_title,
        content = toolEvents
          .map(LocalChatToolEvent::toCliText)
          .joinToString(separator = "\n")
          .ifBlank { null },
        emptyRes = if (isStreaming) {
          R.string.local_mode_tool_calls_pending
        } else {
          R.string.local_mode_tool_calls_empty
        }
      )
    )
  }

  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    TextButton(
      modifier = Modifier.align(Alignment.Start),
      contentPadding = PaddingValues(0.dp),
      onClick = { expanded = !expanded }
    ) {
      Text(
        text = if (expanded) {
          stringResource(id = R.string.local_mode_reasoning_hide)
        } else {
          stringResource(id = R.string.local_mode_reasoning_show)
        },
        style = MaterialTheme.typography.labelMedium
      )
    }

    AnimatedVisibility(visible = expanded) {
      Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(
          width = 1.dp,
          color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        )
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(10.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Text(
            text = stringResource(id = R.string.local_mode_process_title),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          timelineItems.forEachIndexed { timelineIndex, item ->
            ProcessTimelineRow(
              isLast = timelineIndex == timelineItems.lastIndex,
              title = stringResource(id = item.titleRes),
              content = item.content,
              emptyContent = stringResource(id = item.emptyRes)
            )
          }
        }
      }
    }
  }
}

@Composable
private fun ProcessTimelineRow(
  isLast: Boolean,
  title: String,
  content: String?,
  emptyContent: String
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.Top
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = Modifier.padding(top = 4.dp)
    ) {
      Box(
        modifier = Modifier
          .size(8.dp)
          .background(
            color = MaterialTheme.colorScheme.primary,
            shape = CircleShape
          )
      )
      if (!isLast) {
        Box(
          modifier = Modifier
            .padding(top = 4.dp)
            .width(2.dp)
            .height(26.dp)
            .background(
              color = MaterialTheme.colorScheme.outlineVariant,
              shape = RoundedCornerShape(4.dp)
            )
        )
      }
    }

    Column(
      modifier = Modifier.fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
      Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      if (content.isNullOrBlank()) {
        Text(
          text = emptyContent,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      } else {
        MarkdownMessageText(
          markdown = content,
          textColor = MaterialTheme.colorScheme.onSurfaceVariant,
          textSizeSp = MaterialTheme.typography.bodySmall.fontSize.value
        )
      }
    }
  }
}

@Composable
private fun MarkdownMessageText(
  markdown: String,
  textColor: Color,
  textSizeSp: Float,
  modifier: Modifier = Modifier
) {
  val markwon = rememberMarkwon()
  val linkColor = MaterialTheme.colorScheme.primary.toArgb()

  AndroidView(
    modifier = modifier.fillMaxWidth(),
    factory = { context ->
      TextView(context).apply {
        linksClickable = true
        movementMethod = LinkMovementMethod.getInstance()
        includeFontPadding = false
      }
    },
    update = { textView ->
      textView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
      textView.setTextColor(textColor.toArgb())
      textView.setLinkTextColor(linkColor)
      textView.highlightColor = android.graphics.Color.TRANSPARENT
      textView.setTextSize(
        TypedValue.COMPLEX_UNIT_SP,
        textSizeSp.takeIf { it > 0f } ?: DEFAULT_MARKDOWN_TEXT_SIZE
      )
      markwon.setMarkdown(textView, markdown)
    }
  )
}

@Composable
private fun rememberMarkwon(): Markwon {
  val context = LocalContext.current
  return remember(context) {
    Markwon.builder(context)
      .usePlugin(
        object : AbstractMarkwonPlugin() {
          override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
            builder.linkResolver { view, link ->
              runCatching {
                view.context.startActivity(
                  Intent(Intent.ACTION_VIEW, Uri.parse(link)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                  }
                )
              }
            }
          }
        }
      )
      .build()
  }
}

@Composable
private fun LocalInputSection(
  input: String,
  sending: Boolean,
  onInputChange: (String) -> Unit,
  onSendClick: () -> Unit
) {
  Surface(tonalElevation = 3.dp) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      OutlinedTextField(
        modifier = Modifier.weight(1f),
        value = input,
        onValueChange = onInputChange,
        enabled = !sending,
        maxLines = 4,
        placeholder = {
          Text(text = stringResource(id = R.string.local_mode_input_placeholder))
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(
          onSend = {
            if (input.isNotBlank() && !sending) {
              onSendClick()
            }
          }
        )
      )
      Button(
        enabled = input.isNotBlank() && !sending,
        onClick = onSendClick
      ) {
        if (sending) {
          CircularProgressIndicator(
            modifier = Modifier
              .size(18.dp)
              .padding(2.dp),
            strokeWidth = 2.dp
          )
        } else {
          Text(text = stringResource(id = R.string.local_mode_send))
        }
      }
    }
  }
}

private data class ParsedLocalMessage(
  val answer: String,
  val reasoning: String?,
  val toolEvents: List<LocalChatToolEvent>
)

private data class ProcessTimelineItem(
  val titleRes: Int,
  val content: String?,
  val emptyRes: Int
)

private fun LocalChatToolEvent.toCliText(): String {
  val statusText = status?.takeIf(String::isNotBlank)?.uppercase()?.let { "[$it] " }.orEmpty()
  val messageText = message?.takeIf(String::isNotBlank)?.let { " - $it" }.orEmpty()
  val queryText = query?.takeIf(String::isNotBlank)?.let { " query=\"$it\"" }.orEmpty()
  val itemTypeText = itemType?.takeIf(String::isNotBlank)?.let { " item=$it" }.orEmpty()
  return "$statusText$type$itemTypeText$queryText$messageText".trim()
}

private const val USER_ROLE = "user"
private const val DEFAULT_MARKDOWN_TEXT_SIZE = 16f
