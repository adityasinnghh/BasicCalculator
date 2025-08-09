package com.example.calculator

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import java.util.ArrayDeque

class MainActivity : AppCompatActivity() {

    private lateinit var tvInput: TextView
    private var expression = ""      // what's shown / typed
    private var lastWasEqual = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvInput = findViewById(R.id.tvInput)

        val numberButtons = listOf(
            R.id.btn0, R.id.btn1, R.id.btn2, R.id.btn3,
            R.id.btn4, R.id.btn5, R.id.btn6, R.id.btn7,
            R.id.btn8, R.id.btn9
        )

        // Numbers
        numberButtons.forEach { id ->
            findViewById<Button>(id).setOnClickListener {
                if (lastWasEqual) { expression = ""; lastWasEqual = false }
                expression += (it as Button).text
                tvInput.text = expression
            }
        }

        // Dot
        findViewById<Button>(R.id.btnDot).setOnClickListener {
            if (lastWasEqual) { expression = ""; lastWasEqual = false }
            expression += "."
            tvInput.text = expression
        }

        // Parentheses & operators
        findViewById<Button>(R.id.btnOpen).setOnClickListener { appendOperator("(") }
        findViewById<Button>(R.id.btnClose).setOnClickListener { appendOperator(")") }
        findViewById<Button>(R.id.btnPlus).setOnClickListener { appendOperator("+") }
        findViewById<Button>(R.id.btnMinus).setOnClickListener { appendOperator("-") }
        findViewById<Button>(R.id.btnMultiply).setOnClickListener { appendOperator("*") }
        findViewById<Button>(R.id.btnDivide).setOnClickListener { appendOperator("/") }

        // Clear
        findViewById<Button>(R.id.btnClear).setOnClickListener {
            expression = ""
            tvInput.text = "0"
            lastWasEqual = false
        }

        // Backspace
        findViewById<Button>(R.id.btnBack).setOnClickListener {
            if (expression.isNotEmpty()) {
                expression = expression.dropLast(1)
                tvInput.text = if (expression.isEmpty()) "0" else expression
            }
        }

        // Equal (evaluate)
        findViewById<Button>(R.id.btnEqual).setOnClickListener {
            if (expression.isEmpty()) return@setOnClickListener

            // trim trailing operators and '('
            var exprToEval = expression
            while (exprToEval.isNotEmpty() && (isOperator(exprToEval.last()) || exprToEval.last() == '(')) {
                exprToEval = exprToEval.dropLast(1)
            }
            if (exprToEval.isEmpty()) return@setOnClickListener

            try {
                val result = evaluateExpression(exprToEval)
                val out = formatResult(result)
                tvInput.text = "$exprToEval=$out"     // show typed expression + =result
                expression = out                       // next typing continues from result
                lastWasEqual = true
            } catch (e: ArithmeticException) {
                tvInput.text = "Math Error"
                expression = ""
                lastWasEqual = false
            } catch (e: Exception) {
                tvInput.text = "Error"
                expression = ""
                lastWasEqual = false
            }
        }
    }

    // append operator or parentheses; handle replacement of consecutive operators
    private fun appendOperator(op: String) {
        if (lastWasEqual) lastWasEqual = false

        if (expression.isEmpty()) {
            // allow unary minus or leading '('
            if (op == "-") {
                expression += "-"
                tvInput.text = expression
            } else if (op == "(") {
                expression += "("
                tvInput.text = expression
            }
            return
        }

        val last = expression.last()
        if (op == "(") {
            // if previous char is digit or ')', insert multiplication implicitly
            if (last.isDigit() || last == ')') expression += "*("
            else expression += "("
            tvInput.text = expression
            return
        }

        if (op == ")") {
            expression += ")"
            tvInput.text = expression
            return
        }

        // op is + - * /
        if (isOperator(last)) {
            // replace last operator with new one (so pressing + then - toggles)
            expression = expression.dropLast(1) + op
        } else {
            expression += op
        }
        tvInput.text = expression
    }

    private fun isOperator(c: Char) = c == '+' || c == '-' || c == '*' || c == '/'

    // ---------- Parsing & evaluation (shunting-yard -> postfix -> eval) ----------

    // Tokenize with support for unary minus (e.g. -5 or 3 * -2)
    private fun tokenize(expr: String): List<String> {
        val tokens = mutableListOf<String>()
        var i = 0
        val n = expr.length
        while (i < n) {
            val c = expr[i]
            if (c == '(' || c == ')') {
                tokens.add(c.toString())
                i++
            } else if (isOperator(c)) {
                // detect unary minus
                if (c == '-' && (i == 0 || expr[i - 1] == '(' || isOperator(expr[i - 1]))) {
                    // parse number starting with '-'
                    var j = i + 1
                    val sb = StringBuilder("-")
                    while (j < n && (expr[j].isDigit() || expr[j] == '.')) {
                        sb.append(expr[j]); j++
                    }
                    if (sb.length == 1) {
                        // lonely '-' (no number) -> treat as operator
                        tokens.add("-"); i++
                    } else {
                        tokens.add(sb.toString()); i = j
                    }
                } else {
                    tokens.add(c.toString()); i++
                }
            } else if (c.isDigit() || c == '.') {
                var j = i
                val sb = StringBuilder()
                while (j < n && (expr[j].isDigit() || expr[j] == '.')) {
                    sb.append(expr[j]); j++
                }
                tokens.add(sb.toString()); i = j
            } else if (c.isWhitespace()) {
                i++
            } else {
                throw IllegalArgumentException("Invalid character: $c")
            }
        }
        return tokens
    }

    private fun toPostfix(tokens: List<String>): List<String> {
        val output = mutableListOf<String>()
        val ops = ArrayDeque<String>()
        val prec = mapOf("+" to 1, "-" to 1, "*" to 2, "/" to 2)

        for (t in tokens) {
            when {
                t.toDoubleOrNull() != null -> output.add(t)
                t == "(" -> ops.addFirst(t)
                t == ")" -> {
                    while (ops.isNotEmpty() && ops.first() != "(") {
                        output.add(ops.removeFirst())
                    }
                    if (ops.isEmpty() || ops.first() != "(") throw IllegalArgumentException("Mismatched parentheses")
                    ops.removeFirst()
                }
                t in prec -> {
                    while (ops.isNotEmpty() && ops.first() != "(" && prec.getValue(ops.first()) >= prec.getValue(t)) {
                        output.add(ops.removeFirst())
                    }
                    ops.addFirst(t)
                }
                else -> throw IllegalArgumentException("Unknown token: $t")
            }
        }
        while (ops.isNotEmpty()) {
            val o = ops.removeFirst()
            if (o == "(" || o == ")") throw IllegalArgumentException("Mismatched parentheses")
            output.add(o)
        }
        return output
    }

    private fun evalPostfix(postfix: List<String>): Double {
        val st = ArrayDeque<Double>()
        for (t in postfix) {
            val num = t.toDoubleOrNull()
            if (num != null) {
                st.addFirst(num)
            } else {
                if (st.size < 2) throw IllegalArgumentException("Invalid expression")
                val b = st.removeFirst()
                val a = st.removeFirst()
                val res = when (t) {
                    "+" -> a + b
                    "-" -> a - b
                    "*" -> a * b
                    "/" -> {
                        if (b == 0.0) throw ArithmeticException("Division by zero")
                        a / b
                    }
                    else -> throw IllegalArgumentException("Unknown operator $t")
                }
                st.addFirst(res)
            }
        }
        if (st.size != 1) throw IllegalArgumentException("Invalid expression")
        return st.first()
    }

    private fun evaluateExpression(expr: String): Double {
        val tokens = tokenize(expr)
        val postfix = toPostfix(tokens)
        return evalPostfix(postfix)
    }

    private fun formatResult(v: Double): String {
        return if (v % 1.0 == 0.0) v.toLong().toString()
        else v.toString().trimEnd('0').trimEnd('.')
    }
}
