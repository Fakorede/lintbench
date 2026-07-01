package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext

class DataBindingDetector : LayoutDetector() {

    companion object {
        private val IMPLEMENTATION = Implementation(
            DataBindingDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "XmlEscapeNeeded",
            briefDescription = "Missing XML Escape",
            explanation = """
                When a string contains characters that have special usage in XML, you must escape the characters.
                In data binding expressions the characters `<`, `>` and `&` must be written as `&lt;`, `&gt;`
                and `&amp;` respectively.
            """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableAttributes(): Collection<String>? = listOf(Detector.ALL)

    override fun visitAttribute(context: XmlContext, attribute: org.w3c.dom.Attr) {
        val rawValue = getRawAttributeValue(context, attribute) ?: return
        if (!rawValue.startsWith("@")) {
            return
        }

        val open = rawValue.indexOf('{')
        val close = rawValue.indexOf('}', open + 1)
        if (open == -1 || close == -1) {
            return
        }

        val expression = rawValue.substring(open + 1, close)
        for (i in expression.indices) {
            val c = expression[i]
            if (c == '&' && looksLikeEntity(expression, i)) {
                continue
            }
            val escape = ESCAPE_SEQUENCES[c] ?: continue
            context.report(
                ISSUE,
                attribute,
                context.getValueLocation(attribute),
                "The character `$c` must be escaped in XML as `$escape`",
            )
        }
    }

    private fun getRawAttributeValue(context: XmlContext, attribute: org.w3c.dom.Attr): String? {
        val source = context.client.readFile(context.file)
        if (source.isEmpty()) {
            return null
        }
        val location = context.getValueLocation(attribute)
        val start = location.start?.offset ?: return null
        val end = location.end?.offset ?: return null
        if (start < 0 || end > source.length || end <= start) {
            return null
        }
        return source.substring(start, end).trim { it == '"' || it == '\'' }
    }

    private fun looksLikeEntity(value: String, ampIndex: Int): Boolean {
        if (ampIndex >= value.length - 1) {
            return false
        }
        val next = value[ampIndex + 1]
        if (next == '#') {
            val semi = value.indexOf(';', ampIndex + 2)
            return semi != -1 && semi > ampIndex + 2
        }
        val semi = value.indexOf(';', ampIndex + 1)
        if (semi == -1) {
            return false
        }
        return (ampIndex + 1 until semi).all { value[it].isLetterOrDigit() }
    }

    private val ESCAPE_SEQUENCES = mapOf(
        '<' to "&lt;",
        '>' to "&gt;",
        '&' to "&amp;",
    )
}