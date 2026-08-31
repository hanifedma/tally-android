package com.hanifedma.tally.core

/**
 * The small arithmetic the amount field allows: + − × ÷ and parentheses.
 *
 * It exists because splitting a bill is the single most common thing anyone
 * does with a calculator while entering an expense, and making them leave the
 * app to do it is absurd.
 *
 * A hand-written recursive-descent parser, character for character the same
 * grammar as evalExpression in money.js — including what it refuses, so that
 * a sum typed on a phone and the same sum typed on a laptop can never
 * disagree about what it means.
 */
object Calc {

    private val ALLOWED = Regex("^[0-9.()+\\-*/]+$")

    /**
     * @return the value, or null when the expression is incomplete or
     *         malformed — the caller shows the field as unfinished, not wrong.
     */
    /**
     * True when the input is a sum rather than a plain number — something
     * whose answer is worth showing before it is saved.
     *
     * A leading minus does not count: "-500" is a number someone typed, not
     * arithmetic they are part-way through. Mirrors isExpression in money.js.
     */
    fun isExpression(input: String?): Boolean {
        val src = (input ?: "")
            .replace(Regex("""[,\s]"""), "")
            .replace(Regex("""^[-−–—+]"""), "")
        return Regex("""[+\-−–—*/×÷xX()]""").containsMatchIn(src)
    }

    fun eval(input: String?): Double? {
        val src = (input ?: "")
            .replace('×', '*').replace('x', '*').replace('X', '*')
            .replace('÷', '/')
            .replace('−', '-').replace('–', '-').replace('—', '-')
            .filterNot { it == ',' || it.isWhitespace() }
        if (src.isEmpty()) return null
        if (!ALLOWED.matches(src)) return null

        val p = Parser(src)
        val value = p.expr() ?: return null
        if (!p.atEnd()) return null
        return if (value.isFinite()) value else null
    }

    private class Parser(private val s: String) {
        private var i = 0

        fun atEnd() = i >= s.length

        private fun eat(c: Char): Boolean {
            if (i < s.length && s[i] == c) { i++; return true }
            return false
        }

        fun expr(): Double? {
            var v = term() ?: return null
            while (true) {
                v = when {
                    eat('+') -> v + (term() ?: return null)
                    eat('-') -> v - (term() ?: return null)
                    else -> return v
                }
            }
        }

        private fun term(): Double? {
            var v = unary() ?: return null
            while (true) {
                v = when {
                    eat('*') -> v * (unary() ?: return null)
                    eat('/') -> {
                        val r = unary() ?: return null
                        // Division by zero is a question with no answer, not
                        // an infinity to store in someone's ledger.
                        if (r == 0.0) return null
                        v / r
                    }
                    else -> return v
                }
            }
        }

        private fun unary(): Double? {
            if (eat('-')) return unary()?.let { -it }
            if (eat('+')) return unary()
            return atom()
        }

        private fun atom(): Double? {
            if (eat('(')) {
                val v = expr() ?: return null
                if (!eat(')')) return null
                return v
            }
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
            if (i == start) return null
            val text = s.substring(start, i)
            // "1.2.3" would parse as 1.2 under a lenient reader; refuse it
            // rather than guess which number was meant.
            if (text.count { it == '.' } > 1) return null
            return text.toDoubleOrNull()
        }
    }
}
