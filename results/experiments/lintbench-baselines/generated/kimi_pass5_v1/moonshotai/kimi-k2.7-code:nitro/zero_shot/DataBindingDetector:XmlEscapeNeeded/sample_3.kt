package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.evaluateString

class DataBindingDetector : Detector(), Detector.UastScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>>? {
        return listOf(ULiteralExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitLiteralExpression(node: ULiteralExpression) {
                if (!node.isString) return
                val value = node.evaluateString() ?: return
                if (hasUnescapedXmlCharacter(value)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "This string contains characters that must be escaped in XML"
                    )
                }
            }
        }
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "XmlEscapeNeeded",
            briefDescription = "Missing XML Escape",
            explanation = """
                When a string contains characters that have special usage in XML
                (such as <, >, &, ", and '), you must escape them using the
                corresponding XML entities (for example, &lt;, &gt;, &amp;,
                &quot;, and &apos;).
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                DataBindingDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )

        private val XML_SPECIAL_CHARS = charArrayOf('<', '>', '&', '"', '\'')
        private val XML_ENTITY_REGEX =
            Regex("&(?:#[0-9]+|#x[0-9a-fA-F]+|[A-Za-z][A-Za-z0-9]*);")

        private fun hasUnescapedXmlCharacter(s: String): Boolean {
            val entityRanges = XML_ENTITY_REGEX.findAll(s).map { it.range }.toList()
            return s.withIndex().any { (index, char) ->
                char in XML_SPECIAL_CHARS && entityRanges.none { index in it }
            }
        }
    }
}