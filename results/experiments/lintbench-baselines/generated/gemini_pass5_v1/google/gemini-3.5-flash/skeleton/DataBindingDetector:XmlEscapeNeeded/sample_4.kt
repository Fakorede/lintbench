package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Attr

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
                When a string contains characters that have special usage in XML, you must escape the characters. \
                For example, characters like `<` and `&` inside Data Binding expressions must be escaped as `&lt;` and `&amp;`.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableAttributes(): Collection<String>? = null

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val value = attribute.value ?: return
        if (!isDataBindingExpression(value)) return

        val start = context.getValueStart(attribute)
        val end = context.getValueEnd(attribute)
        if (start < 0 || end < 0 || start > end) return

        val contents = context.getContents() ?: return
        if (end > contents.length) return

        val rawValue = contents.substring(start, end)

        if (hasUnescapedCharacters(rawValue)) {
            context.report(
                ISSUE,
                attribute,
                context.getValueLocation(attribute),
                "Characters such as '<' and '&' must be escaped in XML attributes"
            )
        }
    }

    private fun isDataBindingExpression(value: String): Boolean {
        val trimmed = value.trim()
        return (trimmed.startsWith("@{") || trimmed.startsWith("@={")) && trimmed.endsWith("}")
    }

    private fun hasUnescapedCharacters(rawValue: String): Boolean {
        if (rawValue.contains("<")) {
            return true
        }

        val entityRegex = Regex("&(?:lt|gt|amp|quot|apos|#[0-9]+|#x[0-9a-fA-F]+);")
        var index = rawValue.indexOf('&')
        while (index != -1) {
            val entityMatch = entityRegex.find(rawValue, index)
            if (entityMatch == null || entityMatch.range.first != index) {
                return true
            }
            index = rawValue.indexOf('&', index + 1)
        }

        return false
    }
}