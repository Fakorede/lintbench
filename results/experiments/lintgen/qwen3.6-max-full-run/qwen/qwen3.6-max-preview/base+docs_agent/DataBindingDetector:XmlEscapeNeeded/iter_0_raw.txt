package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Attr
import org.w3c.dom.Node
import org.w3c.dom.Text

class DataBindingDetector : Detector(), XmlScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "XmlEscapeNeeded",
            briefDescription = "Missing XML Escape",
            explanation = "When a string contains characters that have special usage in XML, you must escape the characters.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(DataBindingDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
        )

        private val SPECIAL_CHARS = setOf('<', '>', '&', '"', '\'')
    }

    override fun getApplicableElements(): Collection<String>? = null

    override fun visitText(context: XmlContext, text: Text) {
        checkValue(context, text, text.nodeValue)
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        checkValue(context, attribute, attribute.value)
    }

    private fun checkValue(context: XmlContext, node: Node, value: String?) {
        if (value.isNullOrEmpty()) return

        val unescapedChar = value.find { it in SPECIAL_CHARS }
        if (unescapedChar != null) {
            context.report(
                ISSUE,
                context.getLocation(node),
                "Missing XML escape for special character '$unescapedChar'"
            )
        }
    }
}