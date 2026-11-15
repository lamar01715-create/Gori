package com.example.data.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

data class ConsumableProduct(
  val productId: String,
  val creditAmount: Double,
  val displayLabel: String,
  val playFormattedPrice: String? = null,
)

sealed interface PurchaseUiState {
  data object Idle : PurchaseUiState

  data class Loading(val message: String) : PurchaseUiState

  data object Pending : PurchaseUiState

  data object Canceled : PurchaseUiState

  data class Credited(val amountAdded: Double) : PurchaseUiState

  data class Error(val message: String) : PurchaseUiState
}

class BalanceService(context: Context) {

  companion object {
    private const val PREFS_NAME = "calculator_credit_prefs"
    private const val KEY_BALANCE = "balance"
    private const val KEY_FREE_COUNT = "freeCount"
    const val FREE_CALCULATION_LIMIT = 5
    const val CALCULATION_COST = 0.10

    val PRICE_MAP: Map<String, Double> =
      linkedMapOf(
        "dollar_10" to 0.10,
        "dollar_20" to 0.20,
        "dollar_30" to 0.30,
        "dollar_50" to 0.50,
        "dollar_1" to 1.0,
        "dollar_2" to 2.0,
        "dollar_3" to 3.0,
        "dollar_4" to 4.0,
        "dollar_5" to 5.0,
        "dollar_6" to 6.0,
        "dollar_7" to 7.0,
        "dollar_8" to 8.0,
      )

    private fun defaultLabel(amount: Double): String {
      return if (amount >= 1.0 && amount % 1.0 == 0.0) {
        "$${amount.toInt()}"
      } else {
        "$${String.format(java.util.Locale.US, "%.2f", amount)}"
      }
    }
  }

  private val prefs =
    context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

  private val _balance = MutableStateFlow(loadBalance())
  val balance: StateFlow<Double> = _balance.asStateFlow()

  private val _freeCount = MutableStateFlow(prefs.getInt(KEY_FREE_COUNT, 0))
  val freeCount: StateFlow<Int> = _freeCount.asStateFlow()

  private val _products =
    MutableStateFlow(
      PRICE_MAP.entries.map { (id, price) ->
        ConsumableProduct(
          productId = id,
          creditAmount = price,
          displayLabel = defaultLabel(price),
        )
      }
    )
  val products: StateFlow<List<ConsumableProduct>> = _products.asStateFlow()

  private val _purchaseState = MutableStateFlow<PurchaseUiState>(PurchaseUiState.Idle)
  val purchaseState: StateFlow<PurchaseUiState> = _purchaseState.asStateFlow()

  private val productDetailsCache = mutableMapOf<String, ProductDetails>()
  private val processedTokens = mutableSetOf<String>()

  private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
    when (billingResult.responseCode) {
      BillingClient.BillingResponseCode.OK -> {
        if (!purchases.isNullOrEmpty()) {
          scope.launch { handlePurchases(purchases) }
        }
      }

      BillingClient.BillingResponseCode.USER_CANCELED -> {
        _purchaseState.value = PurchaseUiState.Canceled
      }

      BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> {
        _purchaseState.value =
          PurchaseUiState.Error("Google Play Billing is unavailable on this device.")
      }

      BillingClient.BillingResponseCode.ITEM_UNAVAILABLE -> {
        _purchaseState.value =
          PurchaseUiState.Error("Product is not configured in Google Play Console yet.")
      }

      BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
      BillingClient.BillingResponseCode.NETWORK_ERROR -> {
        _purchaseState.value =
          PurchaseUiState.Error("Network error. Please check your connection and try again.")
      }

      else -> {
        _purchaseState.value =
          PurchaseUiState.Error(
            billingResult.debugMessage.ifBlank { "Purchase could not be completed." }
          )
      }
    }
  }

  private val billingClient: BillingClient =
    BillingClient.newBuilder(context.applicationContext)
      .setListener(purchasesUpdatedListener)
      .enablePendingPurchases(
        PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
      )
      .build()

  init {
    scope.launch {
      if (ensureConnected()) {
        queryAllProductDetails()
        checkPendingUnconsumedPurchases()
      }
    }
  }

  fun refreshPurchases() {
    scope.launch {
      if (ensureConnected()) {
        if (productDetailsCache.isEmpty()) {
          queryAllProductDetails()
        }
        checkPendingUnconsumedPurchases()
      }
    }
  }

  fun clearPurchaseState() {
    _purchaseState.value = PurchaseUiState.Idle
  }

  fun consumeFreeCalculationIfAvailable(): Boolean {
    val current = _freeCount.value
    if (current < FREE_CALCULATION_LIMIT) {
      val next = current + 1
      prefs.edit().putInt(KEY_FREE_COUNT, next).apply()
      _freeCount.value = next
      return true
    }
    return false
  }

  fun spend(amount: Double): Boolean {
    val currentBd = BigDecimal.valueOf(_balance.value).setScale(2, RoundingMode.HALF_UP)
    val amountBd = BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP)
    if (currentBd < amountBd) {
      return false
    }
    val next = currentBd.subtract(amountBd).setScale(2, RoundingMode.HALF_UP).toDouble()
    saveBalance(next)
    return true
  }

  fun buy(activity: Activity, productId: String) {
    scope.launch {
      _purchaseState.value = PurchaseUiState.Loading("Connecting to Google Play...")
      if (!ensureConnected()) {
        _purchaseState.value =
          PurchaseUiState.Error(
            "Unable to connect to Google Play Store on this device."
          )
        return@launch
      }

      if (!productDetailsCache.containsKey(productId)) {
        queryAllProductDetails()
      }

      val details = productDetailsCache[productId]
      if (details == null) {
        _purchaseState.value =
          PurchaseUiState.Error(
            "Product '$productId' not found in Google Play Console."
          )
        return@launch
      }

      val productDetailsParams =
        BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(details).build()
      val flowParams =
        BillingFlowParams.newBuilder()
          .setProductDetailsParamsList(listOf(productDetailsParams))
          .build()

      val result = billingClient.launchBillingFlow(activity, flowParams)
      if (result.responseCode != BillingClient.BillingResponseCode.OK) {
        _purchaseState.value =
          PurchaseUiState.Error(
            result.debugMessage.ifBlank { "Could not open Google Play checkout." }
          )
      }
    }
  }

  private suspend fun handlePurchases(purchases: List<Purchase>) {
    for (purchase in purchases) {
      val productId = purchase.products.firstOrNull { PRICE_MAP.containsKey(it) } ?: continue
      when (purchase.purchaseState) {
        Purchase.PurchaseState.PURCHASED -> {
          if (processedTokens.contains(purchase.purchaseToken)) continue
          val consumeResult = consumePurchaseSuspend(purchase.purchaseToken)
          if (consumeResult.responseCode == BillingClient.BillingResponseCode.OK) {
            processedTokens.add(purchase.purchaseToken)
            val credit = PRICE_MAP[productId] ?: 0.0
            addBalance(credit)
            _purchaseState.value = PurchaseUiState.Credited(credit)
          } else {
            _purchaseState.value =
              PurchaseUiState.Error(
                "Purchase received but consumption failed (${consumeResult.debugMessage})."
              )
          }
        }

        Purchase.PurchaseState.PENDING -> {
          _purchaseState.value = PurchaseUiState.Pending
        }

        else -> Unit
      }
    }
  }

  private suspend fun checkPendingUnconsumedPurchases() {
    val params =
      QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
    val (result, purchases) = queryPurchasesSuspend(params)
    if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases.isNotEmpty()) {
      handlePurchases(purchases)
    }
  }

  private suspend fun queryAllProductDetails() {
    val productList =
      PRICE_MAP.keys.map { id ->
        QueryProductDetailsParams.Product.newBuilder()
          .setProductId(id)
          .setProductType(BillingClient.ProductType.INAPP)
          .build()
      }
    val params = QueryProductDetailsParams.newBuilder().setProductList(productList).build()
    val (result, list) = queryProductDetailsSuspend(params)
    if (result.responseCode == BillingClient.BillingResponseCode.OK) {
      list.forEach { details -> productDetailsCache[details.productId] = details }
      _products.value =
        PRICE_MAP.entries.map { (id, price) ->
          val playPrice =
            productDetailsCache[id]?.oneTimePurchaseOfferDetails?.formattedPrice
          ConsumableProduct(
            productId = id,
            creditAmount = price,
            displayLabel = defaultLabel(price),
            playFormattedPrice = playPrice,
          )
        }
    }
  }

  private fun addBalance(amount: Double) {
    val currentBd = BigDecimal.valueOf(_balance.value).setScale(2, RoundingMode.HALF_UP)
    val addBd = BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP)
    val next = currentBd.add(addBd).setScale(2, RoundingMode.HALF_UP).toDouble()
    saveBalance(next)
  }

  private fun loadBalance(): Double {
    val raw = prefs.all[KEY_BALANCE]
    return when (raw) {
      is String -> raw.toDoubleOrNull() ?: 0.0
      is Float -> raw.toDouble()
      is Double -> raw
      else -> 0.0
    }.let {
      BigDecimal.valueOf(it).setScale(2, RoundingMode.HALF_UP).toDouble()
    }
  }

  private fun saveBalance(newBalance: Double) {
    prefs.edit().putString(KEY_BALANCE, String.format(java.util.Locale.US, "%.2f", newBalance)).apply()
    _balance.value = newBalance
  }

  private suspend fun ensureConnected(): Boolean {
    if (billingClient.isReady) return true
    return suspendCancellableCoroutine { continuation ->
      billingClient.startConnection(
        object : BillingClientStateListener {
          override fun onBillingSetupFinished(billingResult: BillingResult) {
            if (continuation.isActive) {
              continuation.resume(
                billingResult.responseCode == BillingClient.BillingResponseCode.OK
              )
            }
          }

          override fun onBillingServiceDisconnected() {
            if (continuation.isActive) {
              continuation.resume(false)
            }
          }
        }
      )
    }
  }

  private suspend fun queryProductDetailsSuspend(
    params: QueryProductDetailsParams
  ): Pair<BillingResult, List<ProductDetails>> =
    suspendCancellableCoroutine { continuation ->
      billingClient.queryProductDetailsAsync(params) { result, list ->
        if (continuation.isActive) continuation.resume(result to list)
      }
    }

  private suspend fun queryPurchasesSuspend(
    params: QueryPurchasesParams
  ): Pair<BillingResult, List<Purchase>> =
    suspendCancellableCoroutine { continuation ->
      billingClient.queryPurchasesAsync(params) { result, list ->
        if (continuation.isActive) continuation.resume(result to list)
      }
    }

  private suspend fun consumePurchaseSuspend(purchaseToken: String): BillingResult =
    suspendCancellableCoroutine { continuation ->
      val params = ConsumeParams.newBuilder().setPurchaseToken(purchaseToken).build()
      billingClient.consumeAsync(params) { billingResult, _ ->
        if (continuation.isActive) continuation.resume(billingResult)
      }
    }

  fun destroy() {
    if (billingClient.isReady) {
      billingClient.endConnection()
    }
  }
}
