package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.CalculatorUiState
import com.example.ui.components.HistoryBottomSheet
import com.example.ui.theme.JetBrainsMonoFontFamily
import java.util.Locale

private val IosDarkBg = Color(0xFF000000)
private val IosFunctionKey = Color(0xFFA5A5A5)
private val IosNumberKey = Color(0xFF333333)
private val IosOperatorKey = Color(0xFFFF9F0A)

@Composable
fun CalculatorScreen(
  uiState: CalculatorUiState,
  onDigit: (String) -> Unit,
  onOperator: (String) -> Unit,
  onToggleSign: () -> Unit,
  onPercent: () -> Unit,
  onBackspace: () -> Unit,
  onClear: () -> Unit,
  onEquals: () -> Unit,
  onOpenStore: () -> Unit,
  onLoadHistoryExpression: (String) -> Unit,
  onDeleteHistoryItem: (Long) -> Unit,
  onClearHistory: () -> Unit,
) {
  val haptic = LocalHapticFeedback.current
  var showHistorySheet by remember { mutableStateOf(false) }

  fun tapHaptic(heavy: Boolean = false) {
    haptic.performHapticFeedback(
      if (heavy) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove
    )
  }

  Scaffold(
    containerColor = IosDarkBg,
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
  ) { innerPadding ->
    Box(
      modifier =
        Modifier.fillMaxSize()
          .padding(innerPadding)
          .statusBarsPadding()
          .navigationBarsPadding(),
      contentAlignment = Alignment.TopCenter,
    ) {
      BoxWithConstraints(
        modifier = Modifier.fillMaxHeight().widthIn(max = 460.dp).fillMaxWidth()
      ) {
        val compactHeight = maxHeight < 680.dp
        val keySpacing = if (compactHeight) 10.dp else 14.dp

        Column(
          modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp),
          verticalArrangement = Arrangement.SpaceBetween,
        ) {
          // Top Header: Balance: $X.XX + Free Counter + History
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Surface(
              onClick = {
                tapHaptic()
                onOpenStore()
              },
              shape = RoundedCornerShape(999.dp),
              color = Color(0xFF1C1C1E),
              border = BorderStroke(1.dp, IosOperatorKey.copy(alpha = 0.65f)),
              modifier = Modifier.minimumInteractiveComponentSize().testTag("balance_header_pill"),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Icon(
                  imageVector = Icons.Rounded.AccountBalanceWallet,
                  contentDescription = "Credit balance",
                  tint = IosOperatorKey,
                  modifier = Modifier.size(18.dp),
                )
                Text(
                  text = "Balance: $${String.format(Locale.US, "%.2f", uiState.balance)}",
                  style =
                    MaterialTheme.typography.titleMedium.copy(
                      fontFamily = JetBrainsMonoFontFamily,
                      fontWeight = FontWeight.Bold,
                    ),
                  color = Color.White,
                )
                Icon(
                  imageVector = Icons.Rounded.Add,
                  contentDescription = "Top up credits",
                  tint = IosOperatorKey,
                  modifier = Modifier.size(18.dp),
                )
              }
            }

            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              val remainingFree = (uiState.freeLimit - uiState.freeCount).coerceAtLeast(0)
              Surface(
                shape = RoundedCornerShape(999.dp),
                color = Color(0xFF1C1C1E),
                modifier = Modifier.testTag("free_count_pill"),
              ) {
                Text(
                  text = if (remainingFree > 0) "Free: $remainingFree/${uiState.freeLimit}" else "$0.10/calc",
                  style = MaterialTheme.typography.labelMedium,
                  color = if (remainingFree > 0) Color(0xFF34D399) else IosOperatorKey,
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
              }

              IconButton(
                onClick = {
                  tapHaptic()
                  showHistorySheet = true
                },
                modifier = Modifier.minimumInteractiveComponentSize().testTag("history_button"),
              ) {
                Icon(
                  imageVector = Icons.Rounded.History,
                  contentDescription = "Calculation history",
                  tint = Color.White,
                )
              }
            }
          }

          // Display Area (iPhone dark style)
          IosDisplayArea(
            expression = uiState.expression,
            livePreview = uiState.livePreview,
            errorMessage = uiState.errorMessage,
            modifier = Modifier.fillMaxWidth().weight(1f),
          )

          Spacer(modifier = Modifier.height(if (compactHeight) 8.dp else 14.dp))

          // 5x4 iPhone-Style Keypad
          Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(keySpacing),
          ) {
            // Row 1: ⌫, AC, %, ÷
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(keySpacing),
            ) {
              IosIconKey(
                icon = Icons.AutoMirrored.Rounded.Backspace,
                contentDescription = "Backspace",
                containerColor = IosFunctionKey,
                contentColor = Color.Black,
                testTag = "key_backspace",
                onClick = {
                  tapHaptic()
                  onBackspace()
                },
              )
              IosCircleKey(
                label = "AC",
                containerColor = IosFunctionKey,
                contentColor = Color.Black,
                testTag = "key_ac",
                onClick = {
                  tapHaptic(heavy = true)
                  onClear()
                },
              )
              IosCircleKey(
                label = "%",
                containerColor = IosFunctionKey,
                contentColor = Color.Black,
                testTag = "key_percent",
                onClick = {
                  tapHaptic()
                  onPercent()
                },
              )
              IosCircleKey(
                label = "÷",
                containerColor = IosOperatorKey,
                contentColor = Color.White,
                testTag = "key_div",
                onClick = {
                  tapHaptic()
                  onOperator("÷")
                },
              )
            }

            // Row 2: 7, 8, 9, ×
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(keySpacing),
            ) {
              IosCircleKey("7", IosNumberKey, Color.White, "key_7") {
                tapHaptic()
                onDigit("7")
              }
              IosCircleKey("8", IosNumberKey, Color.White, "key_8") {
                tapHaptic()
                onDigit("8")
              }
              IosCircleKey("9", IosNumberKey, Color.White, "key_9") {
                tapHaptic()
                onDigit("9")
              }
              IosCircleKey("×", IosOperatorKey, Color.White, "key_mul") {
                tapHaptic()
                onOperator("×")
              }
            }

            // Row 3: 4, 5, 6, −
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(keySpacing),
            ) {
              IosCircleKey("4", IosNumberKey, Color.White, "key_4") {
                tapHaptic()
                onDigit("4")
              }
              IosCircleKey("5", IosNumberKey, Color.White, "key_5") {
                tapHaptic()
                onDigit("5")
              }
              IosCircleKey("6", IosNumberKey, Color.White, "key_6") {
                tapHaptic()
                onDigit("6")
              }
              IosCircleKey("−", IosOperatorKey, Color.White, "key_sub") {
                tapHaptic()
                onOperator("-")
              }
            }

            // Row 4: 1, 2, 3, +
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(keySpacing),
            ) {
              IosCircleKey("1", IosNumberKey, Color.White, "key_1") {
                tapHaptic()
                onDigit("1")
              }
              IosCircleKey("2", IosNumberKey, Color.White, "key_2") {
                tapHaptic()
                onDigit("2")
              }
              IosCircleKey("3", IosNumberKey, Color.White, "key_3") {
                tapHaptic()
                onDigit("3")
              }
              IosCircleKey("+", IosOperatorKey, Color.White, "key_add") {
                tapHaptic()
                onOperator("+")
              }
            }

            // Row 5: +/−, 0, ., =
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(keySpacing),
            ) {
              IosCircleKey("+/−", IosNumberKey, Color.White, "key_sign") {
                tapHaptic()
                onToggleSign()
              }
              IosCircleKey("0", IosNumberKey, Color.White, "key_0") {
                tapHaptic()
                onDigit("0")
              }
              IosCircleKey(".", IosNumberKey, Color.White, "key_dot") {
                tapHaptic()
                onDigit(".")
              }
              IosCircleKey("=", IosOperatorKey, Color.White, "key_equals") {
                tapHaptic(heavy = true)
                onEquals()
              }
            }
          }
        }
      }
    }
  }

  if (showHistorySheet) {
    HistoryBottomSheet(
      history = uiState.history,
      isPro = true,
      freeLimit = 50,
      onDismiss = { showHistorySheet = false },
      onSelectExpression = { expr ->
        onLoadHistoryExpression(expr)
        showHistorySheet = false
      },
      onDeleteItem = onDeleteHistoryItem,
      onClearAll = onClearHistory,
      onUpgradeClick = { showHistorySheet = false },
    )
  }
}

@Composable
private fun IosDisplayArea(
  expression: String,
  livePreview: String?,
  errorMessage: String?,
  modifier: Modifier = Modifier,
) {
  val scrollState = rememberScrollState()
  LaunchedEffect(expression) { scrollState.animateScrollTo(scrollState.maxValue) }

  val fontSize =
    when {
      expression.length > 18 -> 34.sp
      expression.length > 12 -> 46.sp
      expression.length > 8 -> 58.sp
      else -> 72.sp
    }

  Box(
    modifier = modifier.padding(horizontal = 6.dp, vertical = 8.dp),
    contentAlignment = Alignment.BottomEnd,
  ) {
    Column(
      modifier = Modifier.fillMaxWidth(),
      horizontalAlignment = Alignment.End,
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      AnimatedContent(
        targetState = errorMessage ?: livePreview.orEmpty(),
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "iosPreviewTransition",
      ) { previewText ->
        if (previewText.isNotEmpty()) {
          Text(
            text = if (errorMessage != null) previewText else previewText,
            style =
              MaterialTheme.typography.headlineMedium.copy(
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = 24.sp,
                fontWeight = FontWeight.Normal,
              ),
            color = if (errorMessage != null) Color(0xFFFF453A) else Color(0xFF8E8E93),
            maxLines = 1,
            modifier = Modifier.testTag("calculator_display_preview"),
          )
        } else {
          Spacer(modifier = Modifier.height(30.dp))
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.End,
      ) {
        Text(
          text = expression.ifEmpty { "0" },
          style =
            MaterialTheme.typography.displayLarge.copy(
              fontFamily = JetBrainsMonoFontFamily,
              fontSize = fontSize,
              lineHeight = fontSize * 1.1f,
              fontWeight = FontWeight.Normal,
            ),
          color = Color.White,
          maxLines = 1,
          textAlign = TextAlign.End,
          modifier = Modifier.testTag("calculator_display_expression"),
        )
      }
    }
  }
}

@Composable
private fun RowScope.IosCircleKey(
  label: String,
  containerColor: Color,
  contentColor: Color,
  testTag: String,
  onClick: () -> Unit,
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isPressed by interactionSource.collectIsPressedAsState()
  val scale by
    animateFloatAsState(
      targetValue = if (isPressed) 0.92f else 1f,
      animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
      label = "iosKeyScale",
    )

  Surface(
    onClick = onClick,
    interactionSource = interactionSource,
    shape = CircleShape,
    color = containerColor,
    contentColor = contentColor,
    modifier =
      Modifier.weight(1f)
        .aspectRatio(1f)
        .minimumInteractiveComponentSize()
        .scale(scale)
        .testTag(testTag),
  ) {
    Box(contentAlignment = Alignment.Center) {
      Text(
        text = label,
        style =
          MaterialTheme.typography.headlineMedium.copy(
            fontFamily = JetBrainsMonoFontFamily,
            fontSize =
              when {
                label.length >= 3 -> 22.sp
                label.length == 2 -> 24.sp
                else -> 30.sp
              },
            fontWeight = FontWeight.Medium,
          ),
      )
    }
  }
}

@Composable
private fun RowScope.IosIconKey(
  icon: ImageVector,
  contentDescription: String,
  containerColor: Color,
  contentColor: Color,
  testTag: String,
  onClick: () -> Unit,
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isPressed by interactionSource.collectIsPressedAsState()
  val scale by
    animateFloatAsState(
      targetValue = if (isPressed) 0.92f else 1f,
      animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
      label = "iosIconScale",
    )

  Surface(
    onClick = onClick,
    interactionSource = interactionSource,
    shape = CircleShape,
    color = containerColor,
    contentColor = contentColor,
    modifier =
      Modifier.weight(1f)
        .aspectRatio(1f)
        .minimumInteractiveComponentSize()
        .scale(scale)
        .testTag(testTag),
  ) {
    Box(contentAlignment = Alignment.Center) {
      Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        modifier = Modifier.size(26.dp),
      )
    }
  }
}
