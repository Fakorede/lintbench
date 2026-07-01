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
                When a string contains characters that have special usage in XML, \
                you must escape the characters. For example, if you have a data binding \
                expression that uses the `<` or `>` operators, you must escape them as \
                `&lt;` and `&gt;` respectively. Similarly, the `&` character must be \
                escaped as `&amp;`.
            """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )

        /** Characters that require XML escaping and their expected escape sequences */
        private val UNESCAPED_CHARS = listOf('<', '>')

        /**
         * Checks whether a data binding expression contains unescaped XML characters.
         * Data binding expressions are enclosed in @{...} or @={...}.
         */
        private fun containsUnescapedXmlChars(value: String): Boolean {
            // Find data binding expressions: @{...} or @={...}
            var i = 0
            while (i < value.length) {
                // Look for @ followed by optional = and {
                if (value[i] == '@') {
                    val start = i
                    i++
                    if (i < value.length && value[i] == '=') {
                        i++
                    }
                    if (i < value.length && value[i] == '{') {
                        // Found a data binding expression, scan until matching }
                        i++
                        var depth = 1
                        while (i < value.length && depth > 0) {
                            when (value[i]) {
                                '{' -> depth++
                                '}' -> depth--
                                '<', '>' -> {
                                    // Check it's not already escaped
                                    // Raw < or > inside a data binding expression is a problem
                                    if (depth > 0) {
                                        return true
                                    }
                                }
                            }
                            i++
                        }
                        continue
                    } else {
                        i = start + 1
                    }
                }
                i++
            }
            return false
        }

        /**
         * Returns true if the attribute value looks like it contains a data binding expression.
         */
        private fun isDataBindingExpression(value: String): Boolean {
            return value.contains("@{") || value.contains("@={")
        }
    }

    override fun getApplicableAttributes(): Collection<String>? {
        // Return null to check all attributes
        return ALL
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val value = attribute.value ?: return

        // Only process attributes that contain data binding expressions
        if (!isDataBindingExpression(value)) {
            return
        }

        // Check for unescaped XML characters within data binding expressions
        if (containsUnescapedXmlChars(value)) {
            context.report(
                issue = ISSUE,
                location = context.getValueLocation(attribute),
                message = "Missing XML escape: data binding expression contains unescaped " +
                    "XML characters (`<` or `>`). Use `&lt;` and `&gt;` instead.",
            )
        }
    }
}