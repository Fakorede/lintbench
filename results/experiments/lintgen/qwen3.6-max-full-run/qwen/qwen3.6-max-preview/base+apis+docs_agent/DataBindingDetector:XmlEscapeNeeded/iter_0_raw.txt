package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.w3c.dom.Attr
import org.w3c.dom.Node
import org.w3c.dom.Text

class DataBindingDetector : Detector(), XmlScanner {

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "XmlEscapeNeeded",
            briefDescription = "Missing XML Escape",
            explanation = "When a string contains characters that have special usage in XML, you must escape the characters.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(DataBindingDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
        )
    }

    override fun getApplicableElements(): Collection<String>? = ALL

    override fun visitText(context: XmlContext, text: Text) {
        checkForUnescapedCharacters(context, text, text.data)
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        checkForUnescapedCharacters(context, attribute, attribute.value)
    }

    private fun checkForUnescapedCharacters(context: XmlContext, node: Node, value: String) {
        if (value.isEmpty()) return

        if ('<' in value || '&' in value) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Missing XML escape for special character"
            )
        }
    }
}