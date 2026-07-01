package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScannerConstants
import org.w3c.dom.Attr

class DataBindingDetector : Detector(), Detector.XmlScanner {

    override fun getApplicableAttributes(): Collection<String>? = XmlScannerConstants.ALL

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val rawValue = getRawAttributeValue(context, attribute) ?: return
        if (!rawValue.containsDataBindingExpression()) return
        checkForUnescaped(context, attribute, rawValue)
    }

    private fun getRawAttributeValue(context: XmlContext, attribute: Attr): String? {
        val location = context.getValueLocation(attribute)
        val start = location.start?.offset ?: return null
        val end = location.end?.offset ?: return null
        val contents = context.getContents() ?: return null
        if (start < 0 || end > contents.length) return null
        return contents.subSequence(start, end).toString()
    }

    private fun String.containsDataBindingExpression(): Boolean =
        DATA_BINDING_REGEX.find(this) != null

    private fun checkForUnescaped(context: XmlContext, attribute: Attr, rawValue: String) {
        val quote = getQuoteChar(rawValue)
        var match = DATA_BINDING_REGEX.find(rawValue)
        while (match != null) {
            val exprStart = match.range.last + 1
            val exprEnd = findExpressionEnd(rawValue, exprStart)
            if (exprEnd != -1) {
                scanExpression(context, attribute, rawValue, exprStart, exprEnd, quote)
            }
            match = match.next()
        }
    }

    private fun getQuoteChar(rawValue: String): Char? {
        val index = rawValue.indexOfAny(charArrayOf('"', '\''))
        return if (index != -1) rawValue[index] else null
    }

    private fun findExpressionEnd(value: String, start: Int): Int {
        var depth = 1
        var i = start
        while (i < value.length) {
            when (value[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
            i++
        }
        return -1
    }

    private fun scanExpression(
        context: XmlContext,
        attribute: Attr,
        rawValue: String,
        exprStart: Int,
        exprEnd: Int,
        quote: Char?
    ) {
        val expr = rawValue.substring(exprStart, exprEnd)
        var i = 0
        while (i < expr.length) {
            val c = expr[i]
            when {
                c == '&' -> {
                    val end = expr.indexOf(';', i)
                    if (end == -1 || !isValidEntityReference(expr.substring(i, end + 1))) {
                        report(context, attribute, exprStart + i, c)
                        i++
                    } else {
                        i = end + 1
                    }
                }
                c == '<' || c == '>' || c == quote -> {
                    report(context, attribute, exprStart + i, c)
                    i++
                }
                else -> i++
            }
        }
    }

    private fun isValidEntityReference(ref: String): Boolean {
        if (!ref.startsWith('&') || !ref.endsWith(';') || ref.length < 3) return false
        val body = ref.substring(1, ref.length - 1)
        return body.matches(Regex("[a-zA-Z][a-zA-Z0-9]*|#[0-9]+|#x[0-9a-fA-F]+"))
    }

    private fun report(context: XmlContext, attribute: Attr, offsetInRawValue: Int, char: Char) {
        val valueLocation = context.getValueLocation(attribute)
        val startOffset = valueLocation.start?.offset ?: return
        val source = context.getContents()?.toString() ?: return
        val charOffset = startOffset + offsetInRawValue
        if (charOffset < 0 || charOffset + 1 > source.length) return
        val location = Location.create(context.file, source, charOffset, charOffset + 1)
        context.report(
            ISSUE,
            location,
            "The character '$char' must be escaped in XML (use ${escapeSuggestion(char)})"
        )
    }

    private fun escapeSuggestion(char: Char): String = when (char) {
        '<' -> "&lt;"
        '>' -> "&gt;"
        '&' -> "&amp;"
        '"' -> "&quot;"
        '\'' -> "&apos;"
        else -> "a character reference"
    }

    companion object {
        private val DATA_BINDING_REGEX = Regex("@=?\\{")

        val ISSUE = Issue.create(
            id = "XmlEscapeNeeded",
            briefDescription = "Missing XML escape",
            explanation = """
                When a string contains characters that have special usage in XML, such as
                '<', '>', '&', and the attribute quote character, you must escape them using
                XML entities (for example &lt;, &gt;, &amp;, &quot;, or &apos;).
                This is especially important inside data binding expressions.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                DataBindingDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}