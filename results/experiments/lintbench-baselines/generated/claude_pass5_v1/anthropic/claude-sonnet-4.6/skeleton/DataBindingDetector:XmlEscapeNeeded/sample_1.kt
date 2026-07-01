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

        // Characters that require XML escaping and their expected escape sequences
        private val UNESCAPED_PATTERNS = listOf(
            Regex("&(?!(?:amp|lt|gt|apos|quot|#\\d+|#x[0-9a-fA-F]+);)") to "&amp;",
            Regex("<") to "&lt;",
            Regex(">") to "&gt;"
        )
    }

    override fun getApplicableAttributes(): Collection<String>? {
        // Return null to check all attributes
        return ALL
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val value = attribute.value ?: return

        // Only check attributes that look like data binding expressions
        // Data binding expressions are wrapped in @{ } or @={ }
        if (!value.contains("@{") && !value.contains("@={")) {
            return
        }

        // Extract the data binding expression content
        val expressionStart = value.indexOf("@{").let {
            if (it >= 0) it else value.indexOf("@={")
        }

        if (expressionStart < 0) return

        // Check for unescaped special XML characters in the attribute value
        checkForUnescapedCharacters(context, attribute, value)
    }

    private fun checkForUnescapedCharacters(context: XmlContext, attribute: Attr, value: String) {
        // Check for unescaped '<' character
        if (value.contains('<')) {
            reportIssue(
                context,
                attribute,
                "The character `<` must be escaped as `&lt;` in XML attributes"
            )
            return
        }

        // Check for unescaped '>' character (while technically allowed in attributes,
        // it's best practice to escape it)
        if (value.contains('>')) {
            reportIssue(
                context,
                attribute,
                "The character `>` must be escaped as `&gt;` in XML attributes"
            )
            return
        }

        // Check for unescaped '&' character that is not already an entity reference
        val ampersandRegex = Regex("&(?!(?:amp|lt|gt|apos|quot|#\\d+|#x[0-9a-fA-F]+);)")
        if (ampersandRegex.containsMatchIn(value)) {
            reportIssue(
                context,
                attribute,
                "The character `&` must be escaped as `&amp;` in XML attributes"
            )
            return
        }
    }

    private fun reportIssue(context: XmlContext, attribute: Attr, message: String) {
        context.report(
            issue = ISSUE,
            location = context.getValueLocation(attribute),
            message = message
        )
    }
}