package com.tardfyou.paperlens.core.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tardfyou.paperlens.core.designsystem.PaperLensTheme
import com.tardfyou.paperlens.core.model.ReaderBlock

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PLReaderParagraph(
    block: ReaderBlock,
    modifier: Modifier = Modifier,
    onLongPress: () -> Unit = {},
) {
    val backgroundColor = if (block.isHighlighted) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = {}, onLongClick = onLongPress),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Text(
            text = block.content,
            modifier = Modifier.padding(PaperLensTheme.spacing.lg),
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = if (block.isHighlighted) FontWeight.SemiBold else FontWeight.Normal,
            ),
        )
    }
}

@Composable
fun PLBottomPlayerBar(
    isSpeaking: Boolean,
    modifier: Modifier = Modifier,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = PaperLensTheme.spacing.md,
                    vertical = PaperLensTheme.spacing.sm,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            IconButton(onClick = onPrevious) {
                Icon(
                    imageVector = Icons.Rounded.SkipPrevious,
                    contentDescription = "\u4e0a\u4e00\u6bb5",
                )
            }
            IconButton(onClick = onPlayPause) {
                Icon(
                    imageVector = if (isSpeaking) {
                        Icons.Rounded.PauseCircle
                    } else {
                        Icons.Rounded.PlayCircle
                    },
                    contentDescription = if (isSpeaking) {
                        "\u6682\u505c\u6717\u8bfb"
                    } else {
                        "\u5f00\u59cb\u6717\u8bfb"
                    },
                )
            }
            IconButton(onClick = onNext) {
                Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = "\u4e0b\u4e00\u6bb5",
                )
            }
        }
    }
}
