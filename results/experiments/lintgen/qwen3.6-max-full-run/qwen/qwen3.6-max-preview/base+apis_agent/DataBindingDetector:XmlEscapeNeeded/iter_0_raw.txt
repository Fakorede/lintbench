package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.w3c.dom.Attr
import org.w3c.dom.Text

class DataBindingDetector : Detector(), XmlScanner {

    companion object {
        val ISSUE = Issue.create(
            id = "XmlEscapeNeeded",
            briefDescription = "Missing XML Escape",
            explanation = "When a string contains characters that have special usage in XML, you must escape the characters.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                DataBindingDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }

    override fun getApplicableElements(): Collection<String>? = ALL

    override fun visitText(context: XmlContext, text: Text) {
        checkValue(context, text, text.data)
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        checkValue(context, attribute, attribute.value)
    }

    private fun checkValue(context: XmlContext, node: org.w3c.dom.Node, value: String?) {
        if (value.isNullOrEmpty()) return

        val specialChars = listOf('<', '>', '&', '"', '\'')
        for (char in specialChars) {
            if (value.contains(char)) {
                context.report(
                    ISSUE,
                    context.getLocation(node),
                    "Character '$char' must be escaped in XML"
                )
                return
            }
        }
    }
}