package com.example.ui

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.billing.BalanceService
import com.example.data.billing.ConsumableProduct
import com.example.data.billing.PurchaseUiState
import com.example.data.local.AppDatabase
import com.example.data.local.CalculationHistoryEntity
import com.example.data.local.CalculatorHistoryRepository
import com.example.domain.AngleMode
import com.example.domain.CalculationResult
import com.example.domain.CalculatorEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CalculatorUiState(
  val expression: String = "",
  val livePreview: String? = null,
  val errorMessage: String? = null,
  val justEvaluated: Boolean = false,
  val balance: Double = 0.0,
  val freeCount: Int = 0,
  val freeLimit: Int = BalanceService.FREE_CALCULATION_LIMIT,
  val products: List<ConsumableProduct> = emptyList(),
  val purchaseState: PurchaseUiState = PurchaseUiState.Idle,
  val showStoreSheet: Boolean = false,
  val storeNotice: String? = null,
  val history: List<CalculationHistoryEntity> = emptyList(),
)

class CalculatorViewModel(application: Application) : AndroidViewModel(application) {

  private val balanceService = BalanceService(application)
  private val historyRepository =
    CalculatorHistoryRepository(AppDatabase.getInstance(application).calculationHistoryDao())

  private data class DisplayInputState(
    val expression: String = "",
    val livePreview: String? = null,
    val errorMessage: String? = null,
    val justEvaluated: Boolean = false,
  )

  private val displayInput = MutableStateFlow(DisplayInputState())
  private val storeSheetState = MutableStateFlow<Pair<Boolean, String?>>(false to null)

  val uiState: StateFlow<CalculatorUiState> =
    combine(
        displayInput,
        balanceService.balance,
        balanceService.freeCount,
        balanceService.products,
        balanceService.purchaseState,
        storeSheetState,
        historyRepository.historyItems,
      ) { values ->
        val input = values[0] as DisplayInputState
        val balance = values[1] as Double
        val freeCount = values[2] as Int
        @Suppress("UNCHECKED_CAST")
        val products = values[3] as List<ConsumableProduct>
        val purchaseState = values[4] as PurchaseUiState
        @Suppress("UNCHECKED_CAST")
        val sheet = values[5] as Pair<Boolean, String?>
        @Suppress("UNCHECKED_CAST")
        val history = values[6] as List<CalculationHistoryEntity>

        CalculatorUiState(
          expression = input.expression,
          livePreview = input.livePreview,
          errorMessage = input.errorMessage,
          justEvaluated = input.justEvaluated,
          balance = balance,
          freeCount = freeCount,
          products = products,
          purchaseState = purchaseState,
          showStoreSheet = sheet.first,
          storeNotice = sheet.second,
          history = history,
        )
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CalculatorUiState(),
      )

  fun refreshBilling() {
    balanceService.refreshPurchases()
  }

  fun onDigit(digit: String) {
    updateExpression { current, justEvaluated ->
      val base = if (justEvaluated) "" else current
      if (digit == ".") {
        val lastToken = base.takeLastWhile { it.isDigit() || it == '.' }
        if (lastToken.contains('.')) return@updateExpression base
        val lastChar = base.lastOrNull()
        if (base.isEmpty() || (lastChar != null && !lastChar.isDigit())) {
          return@updateExpression "${base}0."
        }
      }
      base + digit
    }
  }

  fun onOperator(op: String) {
    updateExpression { current, _ ->
      if (current.isEmpty()) {
        return@updateExpression if (op == "-") "-" else ""
      }
      val last = current.last()
      val operators = setOf('+', '-', '×', '÷')
      when {
        last in operators -> {
          if (op == "-" && last in setOf('×', '÷')) {
            current + op
          } else if (current.length >= 2 && last == '-' && current[current.length - 2] in setOf('×', '÷')) {
            current.dropLast(2) + op
          } else {
            current.dropLast(1) + op
          }
        }
        last == '(' && op != "-" -> current
        else -> current + op
      }
    }
  }

  fun onToggleSign() {
    updateExpression { current, _ ->
      if (current.isEmpty()) return@updateExpression "-"
      if (current.startsWith("-") && current.drop(1).all { it.isDigit() || it == '.' }) {
        current.drop(1)
      } else if (current.all { it.isDigit() || it == '.' }) {
        "-$current"
      } else if (current.endsWith("(-")) {
        current.dropLast(2)
      } else {
        val idx = current.indexOfLast { it in charArrayOf('+', '-', '×', '÷', '(') }
        if (idx == -1) {
          "-$current"
        } else {
          val prefix = current.substring(0, idx + 1)
          val tail = current.substring(idx + 1)
          if (tail.isEmpty()) "${current}(-" else "$prefix(-$tail)"
        }
      }
    }
  }

  fun onPercent() {
    updateExpression { current, _ ->
      val last = current.lastOrNull() ?: return@updateExpression current
      if (last.isDigit() || last == ')') "$current%" else current
    }
  }

  fun onBackspace() {
    displayInput.update { state ->
      if (state.expression.isEmpty()) {
        state.copy(errorMessage = null, livePreview = null, justEvaluated = false)
      } else {
        val next = state.expression.dropLast(1)
        DisplayInputState(
          expression = next,
          livePreview = CalculatorEngine.evaluatePreview(next, AngleMode.DEG),
          errorMessage = null,
          justEvaluated = false,
        )
      }
    }
  }

  fun onClear() {
    displayInput.value = DisplayInputState()
  }

  fun onEquals() {
    val current = displayInput.value.expression
    if (current.isBlank()) return

    when (val result = CalculatorEngine.evaluate(current, AngleMode.DEG)) {
      is CalculationResult.Error -> {
        displayInput.update { it.copy(errorMessage = result.message, livePreview = null) }
      }

      is CalculationResult.Success -> {
        // Expense Logic:
        // if freeCount < 5: do calculation, freeCount++
        // else: try spend(0.10), if true do calculation, if false open Store BottomSheet
        val canCalculate =
          balanceService.consumeFreeCalculationIfAvailable() ||
            balanceService.spend(BalanceService.CALCULATION_COST)

        if (!canCalculate) {
          openStoreSheet(
            "Free uses finished (5/5). Add credits ($0.10 per calculation) to continue."
          )
          return
        }

        displayInput.value =
          DisplayInputState(
            expression = result.formatted,
            livePreview = null,
            errorMessage = null,
            justEvaluated = true,
          )

        if (current != result.formatted) {
          viewModelScope.launch {
            historyRepository.recordCalculation(
              expression = current,
              result = result.formatted,
              isScientific = false,
              isPro = true,
            )
          }
        }
      }
    }
  }

  fun onLoadHistoryExpression(expression: String) {
    displayInput.value =
      DisplayInputState(
        expression = expression,
        livePreview = CalculatorEngine.evaluatePreview(expression, AngleMode.DEG),
        errorMessage = null,
        justEvaluated = false,
      )
  }

  fun onDeleteHistoryItem(id: Long) {
    viewModelScope.launch { historyRepository.deleteItem(id) }
  }

  fun onClearHistory() {
    viewModelScope.launch { historyRepository.clearAll() }
  }

  fun openStoreSheet(notice: String? = null) {
    balanceService.clearPurchaseState()
    storeSheetState.value = true to notice
  }

  fun dismissStoreSheet() {
    storeSheetState.value = false to null
  }

  fun buyProduct(activity: Activity, productId: String) {
    balanceService.buy(activity, productId)
  }

  fun dismissPurchaseStatus() {
    balanceService.clearPurchaseState()
  }

  private fun updateExpression(transform: (String, Boolean) -> String) {
    displayInput.update { state ->
      val nextExpr = transform(state.expression, state.justEvaluated)
      DisplayInputState(
        expression = nextExpr,
        livePreview = CalculatorEngine.evaluatePreview(nextExpr, AngleMode.DEG),
        errorMessage = null,
        justEvaluated = false,
      )
    }
  }

  override fun onCleared() {
    super.onCleared()
    balanceService.destroy()
  }
}
