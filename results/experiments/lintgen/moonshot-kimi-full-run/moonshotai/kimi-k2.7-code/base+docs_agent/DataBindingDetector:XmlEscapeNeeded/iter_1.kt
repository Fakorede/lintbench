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
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

class DataBindingDetector : Detector(), XmlScanner {

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return
        visitNode(context, root)
    }

    private fun visitNode(context: XmlContext, node: Node) {
        if (node !is Element) return

        val attributes = node.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as Attr
            val value = attr.value ?: continue
            if (value.needsXmlEscape()) {
                context.report(
                    ISSUE,
                    attr,
                    context.getLocation(attr),
                    "This attribute value contains XML special characters that must be escaped."
                )
            }
        }

        var child = node.firstChild
        while (child != null) {
            if (child.nodeType == Node.TEXT_NODE) {
                val text = child.textContent ?: ""
                if (text.needsXmlEscape()) {
                    context.report(
                        ISSUE,
                        child,
                        context.getLocation(child),
                        "This text contains XML special characters that must be escaped."
                    )
                }
            } else if (child is Element) {
                visitNode(context, child)
            }
            child = child.nextSibling
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
                When a string contains characters that have special usage in XML,
                you must escape the characters.
            """.trimIndent(),
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