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
import org.w3c.dom.Element

class DataBindingDetector : Detector(), XmlScanner {

    override fun getApplicableElements(): Collection<String>? = listOf(ALL)
    override fun getApplicableAttributes(): Collection<String>? = listOf(ALL)

    override fun visitElement(context: XmlContext, element: Element) {
        val text = element.textContent ?: return
        if (text.needsXmlEscape()) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "This text contains XML special characters that must be escaped."
            )
        }
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val value = attribute.value ?: return
        if (value.needsXmlEscape()) {
            context.report(
                ISSUE,
                attribute,
                context.getLocation(attribute),
                "This attribute value contains XML special characters that must be escaped."
            )
        }
    }

    private fun String.needsXmlEscape(): Boolean {
        return contains('<') || contains('>') || contains('&') || contains('"') || contains('\'')
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "XmlEscapeNeeded",
            briefDescription = "Missing XML Escape",
            explanation = """
                When a string contains characters that have special usage in XML, \
                you must escape the characters.
            """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                DataBindingDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}