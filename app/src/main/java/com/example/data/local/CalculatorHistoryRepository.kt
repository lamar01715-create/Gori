package com.example.data.local

import kotlinx.coroutines.flow.Flow

class CalculatorHistoryRepository(private val dao: CalculationHistoryDao) {

  companion object {
    const val FREE_HISTORY_LIMIT = 50
  }

  val historyItems: Flow<List<CalculationHistoryEntity>> = dao.getAllHistory()
  val historyCount: Flow<Int> = dao.getHistoryCount()

  suspend fun recordCalculation(
    expression: String,
    result: String,
    isScientific: Boolean,
    isPro: Boolean,
  ) {
    dao.insert(
      CalculationHistoryEntity(
        expression = expression,
        result = result,
        isScientific = isScientific,
      )
    )
    if (!isPro) {
      dao.trimToLimit(FREE_HISTORY_LIMIT)
    }
  }

  suspend fun deleteItem(id: Long) {
    dao.deleteById(id)
  }

  suspend fun clearAll() {
    dao.clearAll()
  }
}
