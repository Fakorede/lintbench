package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.w3c.dom.Attr
import org.w3c.dom.Node
import org.w3c.dom.Text

class DataBindingDetector : Detector(), XmlScanner {

    companion object {
        val ISSUE = Issue.create(
            id = "XmlEscapeNeeded",
            briefDescription = "Missing XML Escape",
            explanation = "When a string contains characters that have special usage in XML, you must escape the characters. " +
                    "For example, `<` should be written as `&lt;` and `&` should be written as `&amp;`.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                DataBindingDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )

        private val UNESCAPED_AMP_REGEX = Regex("&(?!amp;|lt;|gt;|quot;|apos;|#\\d+;|#x[\\da-fA-F]+;)")
        private val UNESCAPED_LT_REGEX = Regex("<")
    }

    override fun visitText(context: XmlContext, text: Text) {
        checkContent(context, text, text.nodeValue)
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        checkContent(context, attribute, attribute.value)
    }

    private fun checkContent(context: XmlContext, node: Node, content: String?) {
        if (content.isNullOrEmpty()) return
        if (node.nodeType == Node.CDATA_SECTION_NODE) return

        if (UNESCAPED_AMP_REGEX.containsMatchIn(content)) {
            context.report(
                ISSUE,
                context.getLocation(node),
                "Unescaped '&' character. Use `&amp;` instead."
            )
        } else if (UNESCAPED_LT_REGEX.containsMatchIn(content)) {
            context.report(
                ISSUE,
                context.getLocation(node),
                "Unescaped '<' character. Use `&lt;` instead."
            )
        }
    }
}