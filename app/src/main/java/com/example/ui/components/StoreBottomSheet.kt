package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.billing.ConsumableProduct
import com.example.data.billing.PurchaseUiState
import com.example.ui.theme.JetBrainsMonoFontFamily
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreBottomSheet(
  balance: Double,
  products: List<ConsumableProduct>,
  purchaseState: PurchaseUiState,
  noticeMessage: String?,
  onDismiss: () -> Unit,
  onBuyProduct: (String) -> Unit,
  onDismissStatus: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val isLoading = purchaseState is PurchaseUiState.Loading

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = Color(0xFF1C1C1E),
    contentColor = Color.White,
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .padding(horizontal = 20.dp)
          .padding(bottom = 24.dp)
          .navigationBarsPadding(),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      // Header + Live Balance
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Credit Store",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
          )
          Text(
            text = noticeMessage ?: "$0.10 per calculation after 5 free uses • No Ads",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFAEAEB2),
          )
        }

        Surface(
          shape = RoundedCornerShape(999.dp),
          color = Color(0xFFFF9F0A).copy(alpha = 0.18f),
          border = BorderStroke(1.dp, Color(0xFFFF9F0A)),
          modifier = Modifier.testTag("store_balance_pill"),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.AccountBalanceWallet,
              contentDescription = null,
              tint = Color(0xFFFF9F0A),
              modifier = Modifier.size(16.dp),
            )
            Text(
              text = "Balance: $${String.format(Locale.US, "%.2f", balance)}",
              style =
                MaterialTheme.typography.labelLarge.copy(
                  fontFamily = JetBrainsMonoFontFamily
                ),
              color = Color(0xFFFF9F0A),
            )
          }
        }
      }

      // Purchase feedback banner
      StorePurchaseStatusBanner(
        state = purchaseState,
        onDismiss = onDismissStatus,
      )

      // 3x4 Grid of 12 Consumable Credit Packs
      val rows = products.chunked(3)
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { rowProducts ->
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            rowProducts.forEach { product ->
              StoreCreditTile(
                product = product,
                enabled = !isLoading,
                onClick = { onBuyProduct(product.productId) },
                modifier = Modifier.weight(1f),
              )
            }
          }
        }
      }

      Text(
        text = "Credits never expire and work completely offline once purchased.",
        style = MaterialTheme.typography.labelSmall,
        color = Color(0xFF8E8E93),
      )
    }
  }
}

@Composable
private fun StoreCreditTile(
  product: ConsumableProduct,
  enabled: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    onClick = onClick,
    enabled = enabled,
    shape = RoundedCornerShape(16.dp),
    color = Color(0xFF2C2C2E),
    contentColor = Color.White,
    border = BorderStroke(1.dp, Color(0xFF3A3A3C)),
    modifier =
      modifier
        .height(66.dp)
        .minimumInteractiveComponentSize()
        .testTag("store_buy_${product.productId}"),
  ) {
    Box(contentAlignment = Alignment.Center) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
      ) {
        Text(
          text = product.displayLabel,
          style =
            MaterialTheme.typography.titleMedium.copy(
              fontFamily = JetBrainsMonoFontFamily,
              fontSize = 18.sp,
              fontWeight = FontWeight.Bold,
            ),
          color = Color(0xFFFF9F0A),
        )
        if (product.playFormattedPrice != null) {
          Text(
            text = product.playFormattedPrice,
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFFAEAEB2),
          )
        } else {
          Text(
            text = "Credit",
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF8E8E93),
          )
        }
      }
    }
  }
}

@Composable
private fun StorePurchaseStatusBanner(
  state: PurchaseUiState,
  onDismiss: () -> Unit,
) {
  AnimatedVisibility(
    visible = state !is PurchaseUiState.Idle,
    enter = expandVertically() + fadeIn(),
    exit = shrinkVertically() + fadeOut(),
  ) {
    val bg: Color
    val fg: Color
    val text: String
    val showSpinner = state is PurchaseUiState.Loading

    when (state) {
      is PurchaseUiState.Idle -> return@AnimatedVisibility
      is PurchaseUiState.Loading -> {
        bg = Color(0xFF2C2C2E)
        fg = Color.White
        text = state.message
      }
      is PurchaseUiState.Pending -> {
        bg = Color(0xFF3A2E14)
        fg = Color(0xFFFBBF24)
        text = "Purchase pending confirmation. Balance will update automatically."
      }
      is PurchaseUiState.Canceled -> {
        bg = Color(0xFF2C2C2E)
        fg = Color(0xFFAEAEB2)
        text = "Purchase canceled. No charge was made."
      }
      is PurchaseUiState.Credited -> {
        bg = Color(0xFF0E3A25)
        fg = Color(0xFF34D399)
        text = "Added $${String.format(Locale.US, "%.2f", state.amountAdded)} to your balance!"
      }
      is PurchaseUiState.Error -> {
        bg = Color(0xFF3B181A)
        fg = Color(0xFFFF6961)
        text = state.message
      }
    }

    Surface(
      shape = RoundedCornerShape(14.dp),
      color = bg,
      contentColor = fg,
      modifier = Modifier.fillMaxWidth().testTag("store_purchase_status"),
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        if (showSpinner) {
          CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
            color = fg,
          )
        } else {
          Icon(
            imageVector =
              when (state) {
                is PurchaseUiState.Credited -> Icons.Rounded.CheckCircle
                is PurchaseUiState.Pending -> Icons.Rounded.HourglassTop
                else -> Icons.Rounded.ErrorOutline
              },
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
          text = text,
          style = MaterialTheme.typography.bodyMedium,
          modifier = Modifier.weight(1f),
        )
        if (!showSpinner) {
          IconButton(onClick = onDismiss, modifier = Modifier.size(30.dp)) {
            Icon(
              imageVector = Icons.Rounded.Close,
              contentDescription = "Dismiss",
              modifier = Modifier.size(16.dp),
            )
          }
        }
      }
    }
  }
}
