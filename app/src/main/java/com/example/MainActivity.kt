package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.CalculatorViewModel
import com.example.ui.components.StoreBottomSheet
import com.example.ui.screens.CalculatorScreen
import com.example.ui.theme.CalculatorProTheme

class MainActivity : ComponentActivity() {

  private val viewModel: CalculatorViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK),
      navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK),
    )
    setContent {
      val uiState by viewModel.uiState.collectAsStateWithLifecycle()

      CalculatorProTheme {
        CalculatorScreen(
          uiState = uiState,
          onDigit = viewModel::onDigit,
          onOperator = viewModel::onOperator,
          onToggleSign = viewModel::onToggleSign,
          onPercent = viewModel::onPercent,
          onBackspace = viewModel::onBackspace,
          onClear = viewModel::onClear,
          onEquals = viewModel::onEquals,
          onOpenStore = { viewModel.openStoreSheet() },
          onLoadHistoryExpression = viewModel::onLoadHistoryExpression,
          onDeleteHistoryItem = viewModel::onDeleteHistoryItem,
          onClearHistory = viewModel::onClearHistory,
        )

        if (uiState.showStoreSheet) {
          StoreBottomSheet(
            balance = uiState.balance,
            products = uiState.products,
            purchaseState = uiState.purchaseState,
            noticeMessage = uiState.storeNotice,
            onDismiss = viewModel::dismissStoreSheet,
            onBuyProduct = { productId -> viewModel.buyProduct(this@MainActivity, productId) },
            onDismissStatus = viewModel::dismissPurchaseStatus,
          )
        }
      }
    }
  }

  override fun onResume() {
    super.onResume()
    viewModel.refreshBilling()
  }
}
