package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.CalculationHistoryEntity
import com.example.ui.theme.JetBrainsMonoFontFamily
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryBottomSheet(
  history: List<CalculationHistoryEntity>,
  isPro: Boolean,
  freeLimit: Int,
  onDismiss: () -> Unit,
  onSelectExpression: (String) -> Unit,
  onDeleteItem: (Long) -> Unit,
  onClearAll: () -> Unit,
  onUpgradeClick: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface,
    contentColor = MaterialTheme.colorScheme.onSurface,
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .heightIn(min = 260.dp, max = 560.dp)
          .navigationBarsPadding()
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text(
              text = "History",
              style = MaterialTheme.typography.headlineMedium,
            )
            Surface(
              shape = RoundedCornerShape(999.dp),
              color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
              Text(
                text = if (isPro) "${history.size} • Unlimited" else "${history.size} / $freeLimit",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
              )
            }
          }
          if (!isPro) {
            Text(
              text = "Free tier keeps the latest $freeLimit calculations.",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        if (!isPro) {
          TextButton(onClick = onUpgradeClick) {
            Text("Unlimited", fontWeight = FontWeight.Bold)
          }
        }

        if (history.isNotEmpty()) {
          IconButton(
            onClick = onClearAll,
            modifier = Modifier.testTag("clear_history_button"),
          ) {
            Icon(
              imageVector = Icons.Rounded.DeleteSweep,
              contentDescription = "Clear all history",
              tint = MaterialTheme.colorScheme.error,
            )
          }
        }
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

      if (history.isEmpty()) {
        Box(
          modifier = Modifier.fillMaxWidth().padding(40.dp),
          contentAlignment = Alignment.Center,
        ) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.History,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
              modifier = Modifier.size(48.dp),
            )
            Text(
              text = "No calculations yet",
              style = MaterialTheme.typography.titleMedium,
            )
            Text(
              text = "Tap = on any expression to save it here for quick reuse.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      } else {
        LazyColumn(
          contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          items(history, key = { it.id }) { item ->
            HistoryEntryCard(
              entry = item,
              onUseExpression = { onSelectExpression(item.expression) },
              onUseResult = { onSelectExpression(item.result) },
              onDelete = { onDeleteItem(item.id) },
            )
          }
        }
      }
      Spacer(modifier = Modifier.height(12.dp))
    }
  }
}

@Composable
private fun HistoryEntryCard(
  entry: CalculationHistoryEntity,
  onUseExpression: () -> Unit,
  onUseResult: () -> Unit,
  onDelete: () -> Unit,
) {
  val timeText =
    SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(entry.timestamp))

  Surface(
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
    modifier = Modifier.fillMaxWidth().testTag("history_item_${entry.id}"),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text(
            text = timeText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          if (entry.isScientific) {
            Surface(
              shape = RoundedCornerShape(6.dp),
              color = MaterialTheme.colorScheme.primaryContainer,
            ) {
              Text(
                text = "SCI",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
              )
            }
          }
        }

        Text(
          text = entry.expression,
          style =
            MaterialTheme.typography.bodyLarge.copy(
              fontFamily = JetBrainsMonoFontFamily,
            ),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.clickable(onClick = onUseExpression),
        )

        Text(
          text = "= ${entry.result}",
          style =
            MaterialTheme.typography.titleLarge.copy(
              fontFamily = JetBrainsMonoFontFamily,
              fontWeight = FontWeight.Bold,
            ),
          color = MaterialTheme.colorScheme.primary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.clickable(onClick = onUseResult),
        )
      }

      IconButton(onClick = onDelete) {
        Icon(
          imageVector = Icons.Rounded.DeleteOutline,
          contentDescription = "Delete history entry",
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}
