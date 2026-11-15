package com.example.domain

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

enum class AngleMode(val label: String) {
  DEG("DEG"),
  RAD("RAD"),
}

sealed interface CalculationResult {
  data class Success(val formatted: String, val value: Double) : CalculationResult

  data class Error(val message: String) : CalculationResult
}

object CalculatorEngine {

  private const val MAX_SIGNIFICANT_DIGITS = 12

  fun evaluate(
    rawExpression: String,
    angleMode: AngleMode = AngleMode.DEG,
  ): CalculationResult {
    val sanitized = normalizeExpression(rawExpression)
    if (sanitized.isEmpty()) {
      return CalculationResult.Error("Empty expression")
    }

    val balanced = autoCloseParentheses(sanitized)

    return try {
      val tokens = tokenize(balanced)
      if (tokens.isEmpty()) {
        return CalculationResult.Error("Invalid expression")
      }
      val parser = Parser(tokens, angleMode)
      val value = parser.parseExpression()
      if (!parser.isAtEnd()) {
        return CalculationResult.Error("Syntax error")
      }
      if (value.isNaN()) {
        return CalculationResult.Error("Undefined")
      }
      if (value.isInfinite()) {
        return CalculationResult.Error("Overflow")
      }
      CalculationResult.Success(formatNumber(value), value)
    } catch (e: IllegalArgumentException) {
      CalculationResult.Error(e.message ?: "Syntax error")
    } catch (e: ArithmeticException) {
      CalculationResult.Error(e.message ?: "Math error")
    }
  }

  fun evaluatePreview(
    rawExpression: String,
    angleMode: AngleMode = AngleMode.DEG,
  ): String? {
    val trimmed = rawExpression.trim()
    if (trimmed.isEmpty()) return null
    // Don't show live preview for a bare single number without operators/functions
    val hasOperatorOrFunc =
      trimmed.any { it in charArrayOf('+', '-', '−', '×', '*', '÷', '/', '%', '^', '!', '√', 'π', 'e') } ||
        trimmed.contains("sin(") ||
        trimmed.contains("cos(") ||
        trimmed.contains("tan(") ||
        trimmed.contains("log(") ||
        trimmed.contains("ln(")
    if (!hasOperatorOrFunc) return null

    // Strip trailing binary operator for smoother live preview while typing
    var candidate = normalizeExpression(trimmed)
    while (candidate.isNotEmpty() && candidate.last() in charArrayOf('+', '-', '×', '÷', '^', '(')) {
      candidate = candidate.dropLast(1)
    }
    if (candidate.isEmpty()) return null

    return when (val result = evaluate(candidate, angleMode)) {
      is CalculationResult.Success -> result.formatted
      is CalculationResult.Error -> null
    }
  }

  fun nextParenthesis(expression: String): String {
    val openCount = expression.count { it == '(' }
    val closeCount = expression.count { it == ')' }
    val lastChar = expression.lastOrNull()
    val canClose =
      openCount > closeCount &&
        lastChar != null &&
        (lastChar.isDigit() || lastChar == ')' || lastChar == 'π' || lastChar == 'e' || lastChar == '!' || lastChar == '%')
    return if (canClose) ")" else "("
  }

  fun formatNumber(value: Double): String {
    if (value.isNaN()) return "Error"
    if (value.isInfinite()) return "Overflow"
    val clean = if (abs(value) < 1e-13) 0.0 else value

    val absVal = abs(clean)
    if (absVal != 0.0 && (absVal >= 1e12 || absVal < 1e-9)) {
      val bd = BigDecimal.valueOf(clean).round(MathContext(9, RoundingMode.HALF_UP)).stripTrailingZeros()
      return bd.toString()
    }

    val bd =
      BigDecimal.valueOf(clean)
        .round(MathContext(MAX_SIGNIFICANT_DIGITS, RoundingMode.HALF_UP))
        .stripTrailingZeros()
    val plain = bd.toPlainString()
    return if (plain == "-0") "0" else plain
  }

  private fun normalizeExpression(raw: String): String {
    return raw
      .replace(" ", "")
      .replace("−", "-")
      .replace("×", "*")
      .replace("÷", "/")
      .replace("sqrt(", "√(")
      .replace("pi", "π")
  }

  private fun autoCloseParentheses(expr: String): String {
    val openCount = expr.count { it == '(' }
    val closeCount = expr.count { it == ')' }
    return if (openCount > closeCount) {
      expr + ")".repeat(openCount - closeCount)
    } else {
      expr
    }
  }

  private sealed interface Token {
    data class Number(val value: Double) : Token

    data class Operator(val symbol: Char) : Token

    data class Function(val name: String) : Token

    data object LeftParen : Token

    data object RightParen : Token

    data object Factorial : Token

    data object Percent : Token
  }

  private fun tokenize(expr: String): List<Token> {
    val tokens = mutableListOf<Token>()
    var i = 0

    fun needsImplicitMultiplication(): Boolean {
      val prev = tokens.lastOrNull() ?: return false
      return prev is Token.Number ||
        prev is Token.RightParen ||
        prev is Token.Factorial ||
        prev is Token.Percent
    }

    while (i < expr.length) {
      val ch = expr[i]
      when {
        ch.isDigit() || ch == '.' -> {
          if (needsImplicitMultiplication()) {
            tokens.add(Token.Operator('*'))
          }
          val start = i
          var dotCount = 0
          while (i < expr.length && (expr[i].isDigit() || expr[i] == '.')) {
            if (expr[i] == '.') dotCount++
            if (dotCount > 1) throw IllegalArgumentException("Invalid decimal")
            i++
          }
          // Handle scientific notation like 1.5E10 or 1.5e-5
          if (i < expr.length && expr[i] == 'E') {
            i++
            if (i < expr.length && (expr[i] == '+' || expr[i] == '-')) i++
            val expStart = i
            while (i < expr.length && expr[i].isDigit()) i++
            if (i == expStart) throw IllegalArgumentException("Invalid exponent")
          }
          val numStr = expr.substring(start, i)
          val num = numStr.toDoubleOrNull() ?: throw IllegalArgumentException("Invalid number")
          tokens.add(Token.Number(num))
        }

        ch == 'π' -> {
          if (needsImplicitMultiplication()) tokens.add(Token.Operator('*'))
          tokens.add(Token.Number(Math.PI))
          i++
        }

        ch == 'e' -> {
          if (needsImplicitMultiplication()) tokens.add(Token.Operator('*'))
          tokens.add(Token.Number(Math.E))
          i++
        }

        ch == '√' -> {
          if (needsImplicitMultiplication()) tokens.add(Token.Operator('*'))
          tokens.add(Token.Function("sqrt"))
          i++
        }

        ch.isLetter() -> {
          if (needsImplicitMultiplication()) tokens.add(Token.Operator('*'))
          val start = i
          while (i < expr.length && expr[i].isLetter()) i++
          val name = expr.substring(start, i)
          if (name !in setOf("sin", "cos", "tan", "log", "ln", "sqrt")) {
            throw IllegalArgumentException("Unknown function: $name")
          }
          tokens.add(Token.Function(name))
        }

        ch == '(' -> {
          if (needsImplicitMultiplication()) tokens.add(Token.Operator('*'))
          tokens.add(Token.LeftParen)
          i++
        }

        ch == ')' -> {
          tokens.add(Token.RightParen)
          i++
        }

        ch == '!' -> {
          tokens.add(Token.Factorial)
          i++
        }

        ch == '%' -> {
          tokens.add(Token.Percent)
          i++
        }

        ch in charArrayOf('+', '-', '*', '/', '^') -> {
          tokens.add(Token.Operator(ch))
          i++
        }

        else -> throw IllegalArgumentException("Unexpected character: $ch")
      }
    }
    return tokens
  }

  private class Parser(
    private val tokens: List<Token>,
    private val angleMode: AngleMode,
  ) {
    private var pos = 0

    fun isAtEnd(): Boolean = pos >= tokens.size

    private fun peek(): Token? = tokens.getOrNull(pos)

    private fun consume(): Token = tokens[pos++]

    fun parseExpression(): Double {
      var left = parseTerm()
      while (true) {
        val token = peek()
        if (token is Token.Operator && (token.symbol == '+' || token.symbol == '-')) {
          consume()
          val right = parseTerm()
          left = if (token.symbol == '+') left + right else left - right
        } else {
          break
        }
      }
      return left
    }

    private fun parseTerm(): Double {
      var left = parseUnary()
      while (true) {
        val token = peek()
        if (token is Token.Operator && (token.symbol == '*' || token.symbol == '/')) {
          consume()
          val right = parseUnary()
          if (token.symbol == '/') {
            if (right == 0.0) throw ArithmeticException("Cannot divide by zero")
            left /= right
          } else {
            left *= right
          }
        } else {
          break
        }
      }
      return left
    }

    private fun parseUnary(): Double {
      val token = peek()
      if (token is Token.Operator && (token.symbol == '+' || token.symbol == '-')) {
        consume()
        val operand = parseUnary()
        return if (token.symbol == '-') -operand else operand
      }
      return parsePower()
    }

    private fun parsePower(): Double {
      val base = parsePostfix()
      val token = peek()
      if (token is Token.Operator && token.symbol == '^') {
        consume()
        val exponent = parseUnary()
        return base.pow(exponent)
      }
      return base
    }

    private fun parsePostfix(): Double {
      var value = parsePrimary()
      while (true) {
        when (peek()) {
          is Token.Factorial -> {
            consume()
            value = factorial(value)
          }
          is Token.Percent -> {
            consume()
            value /= 100.0
          }
          else -> break
        }
      }
      return value
    }

    private fun parsePrimary(): Double {
      val token = peek() ?: throw IllegalArgumentException("Incomplete expression")
      return when (token) {
        is Token.Number -> {
          consume()
          token.value
        }

        is Token.LeftParen -> {
          consume()
          val value = parseExpression()
          if (peek() !is Token.RightParen) {
            throw IllegalArgumentException("Mismatched parentheses")
          }
          consume()
          value
        }

        is Token.Function -> {
          consume()
          val arg =
            if (peek() is Token.LeftParen) {
              consume()
              val inner = parseExpression()
              if (peek() !is Token.RightParen) {
                throw IllegalArgumentException("Mismatched parentheses")
              }
              consume()
              inner
            } else {
              parseUnary()
            }
          applyFunction(token.name, arg)
        }

        else -> throw IllegalArgumentException("Syntax error")
      }
    }

    private fun applyFunction(name: String, arg: Double): Double {
      return when (name) {
        "sin" -> {
          if (angleMode == AngleMode.DEG) {
            val norm = ((arg % 360.0) + 360.0) % 360.0
            if (abs(norm % 180.0) < 1e-12) 0.0 else sin(Math.toRadians(arg))
          } else {
            sin(arg)
          }
        }

        "cos" -> {
          if (angleMode == AngleMode.DEG) {
            val norm = ((arg % 360.0) + 360.0) % 360.0
            if (abs((norm - 90.0) % 180.0) < 1e-12) 0.0 else cos(Math.toRadians(arg))
          } else {
            cos(arg)
          }
        }

        "tan" -> {
          if (angleMode == AngleMode.DEG) {
            val norm = ((arg % 360.0) + 360.0) % 360.0
            if (abs((norm - 90.0) % 180.0) < 1e-12) {
              throw ArithmeticException("Undefined")
            }
            if (abs(norm % 180.0) < 1e-12) 0.0 else tan(Math.toRadians(arg))
          } else {
            val c = cos(arg)
            if (abs(c) < 1e-12) throw ArithmeticException("Undefined")
            tan(arg)
          }
        }

        "log" -> {
          if (arg <= 0.0) throw ArithmeticException("Domain error")
          log10(arg)
        }

        "ln" -> {
          if (arg <= 0.0) throw ArithmeticException("Domain error")
          ln(arg)
        }

        "sqrt" -> {
          if (arg < 0.0) throw ArithmeticException("Domain error")
          sqrt(arg)
        }

        else -> throw IllegalArgumentException("Unknown function: $name")
      }
    }

    private fun factorial(value: Double): Double {
      if (value < 0.0 || value != floor(value)) {
        throw ArithmeticException("Domain error")
      }
      val n = value.toInt()
      if (n > 170) throw ArithmeticException("Overflow")
      var acc = 1.0
      for (k in 2..n) {
        acc *= k
      }
      return acc
    }
  }
}
