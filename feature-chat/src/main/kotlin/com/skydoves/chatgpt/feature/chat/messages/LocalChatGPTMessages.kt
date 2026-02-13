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

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.text.method.LinkMovementMethod
import android.util.TypedValue
import android.widget.TextView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.skydoves.chatgpt.core.model.local.GPTConfig
import com.skydoves.chatgpt.core.model.local.LocalChatMessage
import com.skydoves.chatgpt.feature.chat.R
import com.skydoves.chatgpt.feature.chat.theme.ChatGPTStreamTheme
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import kotlinx.coroutines.launch

@Composable
fun LocalChatGPTMessages(
  onBackPressed: () -> Unit,
  sessionId: String? = null,
  viewModel: LocalChatViewModel = hiltViewModel()
) {
  val messages by viewModel.messages.collectAsStateWithLifecycle()
  val sending by viewModel.sending.collectAsStateWithLifecycle()
  val gptConfigs by viewModel.gptConfigs.collectAsStateWithLifecycle()
  val activeGptConfigId by viewModel.activeGptConfigId.collectAsStateWithLifecycle()
  val listState = rememberLazyListState()
  val emptyResponse = stringResource(id = R.string.local_mode_empty_response)

  var input by rememberSaveable { mutableStateOf("") }
  var showConfigSheet by rememberSaveable { mutableStateOf(false) }

  fun onSendMessage() {
    if (input.isBlank() || sending) return
    viewModel.sendMessage(input)
    input = ""
  }

  LaunchedEffect(viewModel, sessionId) {
    sessionId?.let(viewModel::loadSession)
  }

  LaunchedEffect(messages.size) {
    if (messages.isNotEmpty()) {
      listState.animateScrollToItem(messages.lastIndex)
    }
  }

  ChatGPTStreamTheme {
    val coroutineScope = rememberCoroutineScope()

    val showScrollToBottom by remember(messages.size, listState) {
      derivedStateOf {
        if (messages.isEmpty()) return@derivedStateOf false
        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        lastVisible < (messages.lastIndex - 1)
      }
    }

    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(Color.White)
    ) {
      Column(modifier = Modifier.fillMaxSize()) {
        LocalChatHeader(
          onBackPressed = onBackPressed,
          onManageConfigClick = { showConfigSheet = true }
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
          onSendClick = ::onSendMessage,
          onPauseClick = viewModel::pauseGenerating
        )
      }

      AnimatedVisibility(
        modifier = Modifier
          .align(Alignment.BottomEnd)
          .padding(end = 16.dp, bottom = 88.dp),
        visible = showScrollToBottom
      ) {
        FloatingActionButton(
          onClick = {
            if (messages.isNotEmpty()) {
              coroutineScope.launch {
                listState.animateScrollToItem(messages.lastIndex)
              }
            }
          },
          containerColor = MaterialTheme.colorScheme.surface
        ) {
          Icon(
            imageVector = Icons.Filled.KeyboardArrowDown,
            contentDescription = stringResource(id = R.string.local_mode_scroll_to_bottom),
            tint = MaterialTheme.colorScheme.onSurface
          )
        }
      }
    }

    if (showConfigSheet) {
      GPTConfigManagementBottomSheet(
        configs = gptConfigs,
        activeConfigId = activeGptConfigId,
        onDismissRequest = { showConfigSheet = false },
        onSelectConfig = viewModel::setActiveGptConfig,
        onDeleteConfig = viewModel::deleteGptConfig,
        onAddConfig = { name, baseUrl, apiKey ->
          viewModel.addGptConfig(
            name = name,
            baseUrl = baseUrl,
            apiKey = apiKey
          )
        }
      )
    }
  }
}

@Composable
private fun LocalChatHeader(
  onBackPressed: () -> Unit,
  onManageConfigClick: () -> Unit
) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    color = Color.White,
    tonalElevation = 0.dp
  ) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        IconButton(
          modifier = Modifier.size(34.dp),
          onClick = onBackPressed
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(id = R.string.local_mode_back)
          )
        }
        Text(
          modifier = Modifier
            .weight(1f)
            .padding(start = 6.dp),
          text = stringResource(id = R.string.local_mode_title),
          style = MaterialTheme.typography.titleSmall
        )
        IconButton(
          modifier = Modifier.size(34.dp),
          onClick = onManageConfigClick
        ) {
          Icon(
            imageVector = Icons.Filled.Settings,
            contentDescription = stringResource(id = R.string.local_mode_manage_configs)
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GPTConfigManagementBottomSheet(
  configs: List<GPTConfig>,
  activeConfigId: String?,
  onDismissRequest: () -> Unit,
  onSelectConfig: (String) -> Unit,
  onDeleteConfig: (String) -> Unit,
  onAddConfig: (String, String, String) -> Unit
) {
  var name by rememberSaveable { mutableStateOf("") }
  var baseUrl by rememberSaveable { mutableStateOf("") }
  var apiKey by rememberSaveable { mutableStateOf("") }

  val canAdd = name.isNotBlank() && baseUrl.isNotBlank() && apiKey.isNotBlank()

  ModalBottomSheet(onDismissRequest = onDismissRequest) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Text(
        text = stringResource(id = R.string.local_mode_config_sheet_title),
        style = MaterialTheme.typography.titleMedium
      )

      if (configs.isEmpty()) {
        Text(
          text = stringResource(id = R.string.local_mode_config_empty),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      } else {
        configs.forEachIndexed { index, config ->
          GPTConfigItem(
            config = config,
            isActive = config.id == activeConfigId,
            onSelect = { onSelectConfig(config.id) },
            onDelete = {
              if (!config.isDefault) {
                onDeleteConfig(config.id)
              }
            }
          )
          if (index != configs.lastIndex) {
            HorizontalDivider()
          }
        }
      }

      HorizontalDivider()

      Text(
        text = stringResource(id = R.string.local_mode_config_add_title),
        style = MaterialTheme.typography.titleSmall
      )

      OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = name,
        onValueChange = { name = it },
        singleLine = true,
        label = { Text(text = stringResource(id = R.string.local_mode_config_name)) }
      )

      OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = baseUrl,
        onValueChange = { baseUrl = it },
        singleLine = true,
        label = { Text(text = stringResource(id = R.string.local_mode_config_base_url)) }
      )

      OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = apiKey,
        onValueChange = { apiKey = it },
        singleLine = true,
        label = { Text(text = stringResource(id = R.string.local_mode_config_api_key)) }
      )

      Button(
        modifier = Modifier.fillMaxWidth(),
        enabled = canAdd,
        onClick = {
          onAddConfig(name.trim(), baseUrl.trim(), apiKey.trim())
          name = ""
          baseUrl = ""
          apiKey = ""
        }
      ) {
        Text(text = stringResource(id = R.string.local_mode_config_add_action))
      }

      Box(modifier = Modifier.size(8.dp))
    }
  }
}

@Composable
private fun GPTConfigItem(
  config: GPTConfig,
  isActive: Boolean,
  onSelect: () -> Unit,
  onDelete: () -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onSelect)
      .padding(vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    RadioButton(
      selected = isActive,
      onClick = onSelect
    )
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = config.name,
        style = MaterialTheme.typography.bodyMedium
      )
      Text(
        text = config.baseUrl,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
      if (isActive) {
        Text(
          text = stringResource(id = R.string.local_mode_config_active),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.primary
        )
      }
    }
    if (!config.isDefault) {
      IconButton(onClick = onDelete) {
        Icon(
          imageVector = Icons.Filled.Delete,
          contentDescription = stringResource(id = R.string.local_mode_config_delete)
        )
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
  val context = LocalContext.current
  val isTyping = !isUser && message.isStreaming && message.content.isBlank()
  val parsedMessage = remember(
    message.role,
    message.content,
    message.reasoning,
    message.isStreaming,
    emptyResponse
  ) {
    ParsedLocalMessage(
      answer = message.content.trim().ifBlank { if (message.isStreaming) "" else emptyResponse },
      reasoning = message.reasoning?.trim()?.ifBlank { null }
    )
  }

  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
  ) {
    if (!isUser) {
      AgentAvatar(
        modifier = Modifier.padding(top = 22.dp, end = 10.dp),
        name = message.agentName
          ?.takeIf(String::isNotBlank)
          ?: stringResource(id = R.string.local_mode_assistant_label)
      )
    }
    Column(
      modifier = Modifier.widthIn(max = 320.dp),
      horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
      if (isUser) {
        Text(
          modifier = Modifier
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .align(Alignment.End),
          text = stringResource(id = R.string.local_mode_user_label),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      } else {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Text(
            text = message.agentName
              ?.takeIf(String::isNotBlank)
              ?: stringResource(id = R.string.local_mode_assistant_label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )

          // Keep the copy affordance visually subtle without forcing 48.dp min touch targets.
          CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
            IconButton(
              modifier = Modifier.size(28.dp),
              enabled = !isTyping,
              onClick = {
                val clip = ClipData.newPlainText(
                  context.getString(R.string.local_mode_copy_answer),
                  parsedMessage.answer
                )
                val clipboard = context.getSystemService(ClipboardManager::class.java)
                clipboard?.setPrimaryClip(clip)
                android.widget.Toast.makeText(
                  context,
                  context.getString(R.string.local_mode_copied_to_clipboard),
                  android.widget.Toast.LENGTH_SHORT
                ).show()
              }
            ) {
              Icon(
                modifier = Modifier.size(16.dp),
                imageVector = Icons.Filled.ContentCopy,
                contentDescription = stringResource(id = R.string.local_mode_copy_answer),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isTyping) 0.4f else 1f)
              )
            }
          }
        }
      }
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
            if (isTyping) {
              TypingDots(
                textStyle = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            } else {
              MarkdownMessageText(
                markdown = parsedMessage.answer,
                textColor = MaterialTheme.colorScheme.onSurface,
                textSizeSp = MaterialTheme.typography.bodyMedium.fontSize.value
              )
            }
            ReasoningSection(
              index = index,
              reasoning = parsedMessage.reasoning,
              isStreaming = message.isStreaming
            )
          }
        }
      }
    }
  }
}

@Composable
private fun AgentAvatar(
  name: String,
  modifier: Modifier = Modifier
) {
  val normalized = remember(name) { name.trim().ifBlank { "Assistant" } }
  val seed = remember(normalized) { normalized.lowercase().hashCode() }
  val hue = remember(seed) { ((seed % 360) + 360) % 360 }
  val base = Color.hsl(hue.toFloat(), 0.55f, 0.42f)
  val background = lerp(base, MaterialTheme.colorScheme.surface, 0.25f)
  val initial = remember(normalized) { normalized.firstOrNull()?.uppercase() ?: "A" }

  Surface(
    modifier = modifier.size(32.dp),
    shape = RoundedCornerShape(16.dp),
    color = background
  ) {
    Box(contentAlignment = Alignment.Center) {
      Text(
        text = initial,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurface
      )
    }
  }
}

@Composable
private fun TypingDots(
  textStyle: androidx.compose.ui.text.TextStyle,
  color: Color
) {
  val transition = rememberInfiniteTransition(label = "typing")
  val alpha by transition.animateFloat(
    initialValue = 0.35f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 650),
      repeatMode = RepeatMode.Reverse
    ),
    label = "typingAlpha"
  )

  Text(
    text = "…",
    style = textStyle,
    color = color.copy(alpha = alpha)
  )
}

@Composable
private fun ReasoningSection(
  index: Int,
  reasoning: String?,
  isStreaming: Boolean
) {
  val shouldShowProcess = reasoning != null || isStreaming
  if (!shouldShowProcess) return

  var expanded by rememberSaveable(index, reasoning, isStreaming) {
    mutableStateOf(isStreaming)
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
          verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Text(
            text = stringResource(id = R.string.local_mode_reasoning_title),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )

          val content = reasoning?.takeIf(String::isNotBlank)
          if (content == null) {
            Text(
              text = if (isStreaming) {
                stringResource(id = R.string.local_mode_reasoning_pending)
              } else {
                stringResource(id = R.string.local_mode_reasoning_empty)
              },
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
        // Make assistant answers selectable so users can long-press and copy.
        setTextIsSelectable(true)
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
  onSendClick: () -> Unit,
  onPauseClick: () -> Unit
) {
  Surface(
    color = Color.White,
    tonalElevation = 2.dp
  ) {
    if (sending) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
      ) {
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
          FilledIconButton(
            modifier = Modifier.size(32.dp),
            onClick = onPauseClick
          ) {
            Icon(
              modifier = Modifier.size(14.dp),
              imageVector = Icons.Filled.Stop,
              contentDescription = stringResource(id = R.string.local_mode_pause)
            )
          }
        }
      }
    } else {
      Surface(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 10.dp, vertical = 2.dp),
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        border = BorderStroke(
          width = 1.dp,
          color = Color(0xFFE5E7EB)
        )
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(start = 2.dp, end = 4.dp, top = 1.dp, bottom = 1.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
            Box(
              modifier = Modifier
                .weight(1f)
                .heightIn(min = 28.dp)
                .padding(horizontal = 8.dp, vertical = 2.dp),
              contentAlignment = Alignment.CenterStart
            ) {
              if (input.isBlank()) {
                Text(
                  text = stringResource(id = R.string.local_mode_input_placeholder),
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
              BasicTextField(
                modifier = Modifier.fillMaxWidth(),
                value = input,
                onValueChange = onInputChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                  color = MaterialTheme.colorScheme.onSurface
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                  onSend = {
                    if (input.isNotBlank()) {
                      onSendClick()
                    }
                  }
                )
              )
            }
          }
          CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
            FilledIconButton(
              modifier = Modifier.size(32.dp),
              enabled = input.isNotBlank(),
              onClick = onSendClick
            ) {
              Icon(
                modifier = Modifier.size(16.dp),
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = stringResource(id = R.string.local_mode_send)
              )
            }
          }
        }
      }
    }
  }
}

private data class ParsedLocalMessage(
  val answer: String,
  val reasoning: String?
)

private const val USER_ROLE = "user"
private const val DEFAULT_MARKDOWN_TEXT_SIZE = 16f
