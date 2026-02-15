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

@file:OptIn(ExperimentalComposeUiApi::class)

package com.skydoves.chatgpt.feature.chat.channels

import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.skydoves.balloon.compose.Balloon
import com.skydoves.chatgpt.core.designsystem.component.ChatGPTLoadingIndicator
import com.skydoves.chatgpt.core.designsystem.composition.LocalOnFinishDispatcher
import com.skydoves.chatgpt.core.designsystem.theme.STREAM_PRIMARY
import com.skydoves.chatgpt.core.model.local.LocalChatSessionSummary
import com.skydoves.chatgpt.core.navigation.AppComposeNavigator
import com.skydoves.chatgpt.core.navigation.ChatGPTScreens
import com.skydoves.chatgpt.feature.chat.BuildConfig
import com.skydoves.chatgpt.feature.chat.R
import com.skydoves.chatgpt.feature.chat.messages.LocalChatViewModel
import com.skydoves.chatgpt.feature.chat.theme.ChatGPTStreamTheme
import io.getstream.chat.android.compose.ui.channels.ChannelsScreen

@Composable
fun ChatGPTChannels(
  modifier: Modifier,
  composeNavigator: AppComposeNavigator,
  viewModel: ChatGPTChannelsViewModel? = null,
  onFinishDispatcher: (() -> Unit)? = LocalOnFinishDispatcher.current
) {
  val isStreamEnabled = BuildConfig.STREAM_API_KEY.isNotBlank() &&
    BuildConfig.STREAM_API_KEY != "aaaaaaaaaa"

  if (!isStreamEnabled) {
    val localChatViewModel: LocalChatViewModel = hiltViewModel()
    val localSessions by localChatViewModel.sessions.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    var pendingNewSessionNavigation by remember { mutableStateOf(false) }
    var previousTopSessionId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
      localChatViewModel.refreshSessions()
    }

    DisposableEffect(lifecycleOwner, localChatViewModel) {
      val observer = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_RESUME) {
          localChatViewModel.refreshSessions()
        }
      }
      lifecycleOwner.lifecycle.addObserver(observer)
      onDispose {
        lifecycleOwner.lifecycle.removeObserver(observer)
      }
    }

    LaunchedEffect(localSessions, pendingNewSessionNavigation, previousTopSessionId) {
      val newTopSessionId = localSessions.firstOrNull()?.id
      if (pendingNewSessionNavigation &&
        !newTopSessionId.isNullOrBlank() &&
        newTopSessionId != previousTopSessionId
      ) {
        pendingNewSessionNavigation = false
        composeNavigator.navigate(
          ChatGPTScreens.Messages.createRoute(
            ChatGPTScreens.createLocalSessionRoute(newTopSessionId)
          )
        )
      }
    }

    BackHandler(enabled = true) { onFinishDispatcher?.invoke() }

    ChatGPTStreamTheme {
      Box(
        modifier = modifier
          .fillMaxSize()
          .background(MaterialTheme.colorScheme.background)
          .semantics { testTagsAsResourceId = true }
      ) {
        Column(
          modifier = Modifier.fillMaxSize()
        ) {
          // Header
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = stringResource(id = R.string.local_mode_history_title),
              style = MaterialTheme.typography.headlineMedium,
              color = MaterialTheme.colorScheme.onBackground
            )
          }

          if (localSessions.isEmpty()) {
            Box(
              modifier = Modifier.fillMaxSize(),
              contentAlignment = Alignment.Center
            ) {
              Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
              ) {
                Icon(
                  imageVector = Icons.Filled.AddComment,
                  contentDescription = null,
                  modifier = Modifier.size(64.dp),
                  tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Text(
                  text = stringResource(id = R.string.local_mode_history_description),
                  style = MaterialTheme.typography.bodyLarge,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  textAlign = TextAlign.Center
                )
              }
            }
          } else {
            LazyColumn(
              modifier = Modifier.fillMaxSize(),
              contentPadding = PaddingValues(bottom = 80.dp) // Space for FAB
            ) {
              items(
                items = localSessions,
                key = LocalChatSessionSummary::id
              ) { session ->
                DismissibleLocalSessionItem(
                  session = session,
                  onClick = {
                    composeNavigator.navigate(
                      ChatGPTScreens.Messages.createRoute(
                        ChatGPTScreens.createLocalSessionRoute(session.id)
                      )
                    )
                  },
                  onDelete = {
                    localChatViewModel.deleteSession(session.id)
                  }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
              }
            }
          }
        }

        // FAB for New Chat
        FloatingActionButton(
          modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(24.dp),
          onClick = {
            pendingNewSessionNavigation = true
            previousTopSessionId = localSessions.firstOrNull()?.id
            localChatViewModel.createSessionAndEnter()
          },
          containerColor = MaterialTheme.colorScheme.primary,
          contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
          Icon(
            imageVector = Icons.Filled.AddComment,
            contentDescription = stringResource(id = R.string.local_mode_new_chat)
          )
        }
      }
    }
    return
  }

  val channelsViewModel: ChatGPTChannelsViewModel = viewModel ?: hiltViewModel()
  val uiState by channelsViewModel.channelUiState.collectAsStateWithLifecycle()

  HandleGPTChannelsUiState(uiState = uiState)

  ChatGPTStreamTheme {
    Box(
      modifier = modifier
        .fillMaxSize()
        .semantics { testTagsAsResourceId = true }
    ) {
      ChannelsScreen(
        isShowingHeader = false,
        onChannelClick = { channel ->
          composeNavigator.navigate(ChatGPTScreens.Messages.createRoute(channel.cid))
        },
        onBackPressed = { onFinishDispatcher?.invoke() }
      )

      val isBalloonDisplayed by channelsViewModel
        .isBalloonDisplayedState
        .collectAsStateWithLifecycle()

      Balloon(
        modifier = Modifier
          .align(Alignment.BottomEnd)
          .padding(16.dp)
          .size(58.dp),
        builder = rememberFloatingBalloon(),
        balloonContent = {
          Text(
            modifier = Modifier
              .padding(12.dp)
              .fillMaxWidth(),
            text = "You can add your ChatGPT channel!",
            textAlign = TextAlign.Center,
            color = Color.White
          )
        }
      ) { balloonWindow ->

        LaunchedEffect(key1 = Unit) {
          if (!isBalloonDisplayed) {
            balloonWindow.showAlignTop()
          }

          balloonWindow.setOnBalloonDismissListener {
            channelsViewModel.balloonChannelDisplayed()
            balloonWindow.dismiss()
          }
        }

        FloatingActionButton(
          modifier = Modifier.matchParentSize(),
          containerColor = STREAM_PRIMARY,
          shape = CircleShape,
          onClick = { channelsViewModel.handleEvents(GPTChannelEvent.CreateChannel) }
        ) {
          Icon(
            imageVector = Icons.Filled.AddComment,
            contentDescription = null,
            tint = Color.White
          )
        }
      }

      if (uiState == GPTChannelUiState.Loading) {
        ChatGPTLoadingIndicator()
      }
    }
  }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun DismissibleLocalSessionItem(
  session: LocalChatSessionSummary,
  onClick: () -> Unit,
  onDelete: () -> Unit
) {
  val dismissState = rememberSwipeToDismissBoxState(
    confirmValueChange = { value ->
      if (value == SwipeToDismissBoxValue.EndToStart) {
        onDelete()
        true
      } else {
        false
      }
    }
  )

  SwipeToDismissBox(
    state = dismissState,
    enableDismissFromStartToEnd = false,
    enableDismissFromEndToStart = true,
    backgroundContent = {
      val color = if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) {
        MaterialTheme.colorScheme.errorContainer
      } else {
        MaterialTheme.colorScheme.background
      }
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(color)
          .padding(end = 24.dp),
        contentAlignment = Alignment.CenterEnd
      ) {
        Icon(
          imageVector = Icons.Filled.Delete,
          contentDescription = stringResource(id = R.string.local_mode_delete_chat),
          tint = MaterialTheme.colorScheme.onErrorContainer
        )
      }
    }
  ) {
    LocalSessionItem(
      session = session,
      onClick = onClick
    )
  }
}

@Composable
private fun LocalSessionItem(
  session: LocalChatSessionSummary,
  onClick: () -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .background(MaterialTheme.colorScheme.background)
      .padding(horizontal = 20.dp, vertical = 16.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    // Avatar / Icon
    Surface(
      modifier = Modifier.size(48.dp),
      shape = CircleShape,
      color = MaterialTheme.colorScheme.primaryContainer
    ) {
      Box(contentAlignment = Alignment.Center) {
        Text(
          text = session.title.firstOrNull()?.toString()?.uppercase() ?: "?",
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onPrimaryContainer
        )
      }
    }

    // Content
    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = session.title,
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          modifier = Modifier.weight(1f)
        )
        Text(
          text = if ((System.currentTimeMillis() - session.updatedAt) < 60_000) {
            stringResource(id = R.string.local_mode_just_now)
          } else {
            DateUtils.getRelativeTimeSpanString(session.updatedAt).toString()
          },
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(start = 8.dp)
        )
      }
      
      if (session.preview.isNotBlank()) {
        Text(
          text = session.preview,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis
        )
      }
    }
  }
}

@Composable
private fun HandleGPTChannelsUiState(
  uiState: GPTChannelUiState
) {
  val context = LocalContext.current
  LaunchedEffect(key1 = uiState) {
    when (uiState) {
      is GPTChannelUiState.Success -> Toast.makeText(
        context,
        R.string.toast_success_create_channel,
        Toast.LENGTH_SHORT
      ).show()

      is GPTChannelUiState.Error -> Toast.makeText(
        context,
        R.string.toast_error,
        Toast.LENGTH_SHORT
      ).show()

      else -> Unit
    }
  }
}
