package com.example

import com.example.domain.AngleMode
import com.example.domain.CalculationResult
import com.example.domain.CalculatorEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculatorEngineTest {

  @Test
  fun basicArithmetic_respectsOrderOfOperations() {
    val result = CalculatorEngine.evaluate("2+3×4-10÷2")
    assertEquals(CalculationResult.Success("9", 9.0), result)
  }

  @Test
  fun parenthesesAndPercent_evaluateCorrectly() {
    val result = CalculatorEngine.evaluate("(200+50)×20%")
    assertEquals(CalculationResult.Success("50", 50.0), result)
  }

  @Test
  fun floatingPointPrecision_avoidsArtifacts() {
    val result = CalculatorEngine.evaluate("0.1+0.2")
    assertEquals(CalculationResult.Success("0.3", 0.30000000000000004), result)
  }

  @Test
  fun scientificTrigDeg_evaluatesExactValues() {
    assertEquals("0.5", (CalculatorEngine.evaluate("sin(30)", AngleMode.DEG) as CalculationResult.Success).formatted)
    assertEquals("0.5", (CalculatorEngine.evaluate("cos(60)", AngleMode.DEG) as CalculationResult.Success).formatted)
    assertEquals("0", (CalculatorEngine.evaluate("cos(90)", AngleMode.DEG) as CalculationResult.Success).formatted)
    assertEquals("1", (CalculatorEngine.evaluate("tan(45)", AngleMode.DEG) as CalculationResult.Success).formatted)
    assertTrue(CalculatorEngine.evaluate("tan(90)", AngleMode.DEG) is CalculationResult.Error)
  }

  @Test
  fun scientificLogSqrtPowerFactorialPi_evaluateCorrectly() {
    assertEquals("2", (CalculatorEngine.evaluate("log(100)") as CalculationResult.Success).formatted)
    assertEquals("1", (CalculatorEngine.evaluate("ln(e)") as CalculationResult.Success).formatted)
    assertEquals("5", (CalculatorEngine.evaluate("√(25)") as CalculationResult.Success).formatted)
    assertEquals("8", (CalculatorEngine.evaluate("2^3") as CalculationResult.Success).formatted)
    assertEquals("120", (CalculatorEngine.evaluate("5!") as CalculationResult.Success).formatted)
    assertEquals("1", (CalculatorEngine.evaluate("0!") as CalculationResult.Success).formatted)
    assertEquals("-1", (CalculatorEngine.evaluate("cos(π)", AngleMode.RAD) as CalculationResult.Success).formatted)
  }

  @Test
  fun implicitMultiplication_worksCleanly() {
    val result = CalculatorEngine.evaluate("2(3+4)")
    assertEquals(CalculationResult.Success("14", 14.0), result)
  }

  @Test
  fun divisionByZero_returnsError() {
    val result = CalculatorEngine.evaluate("10÷0")
    assertTrue(result is CalculationResult.Error)
  }

  @Test
  fun smartParenthesis_insertsOpenOrCloseContextually() {
    assertEquals("(", CalculatorEngine.nextParenthesis(""))
    assertEquals("(", CalculatorEngine.nextParenthesis("2+"))
    assertEquals(")", CalculatorEngine.nextParenthesis("(2+3"))
    assertEquals("(", CalculatorEngine.nextParenthesis("(2+3)"))
  }
}
