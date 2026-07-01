package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Document

class NamespaceDetector : ResourceXmlDetector() {

    companion object {
        private val IMPLEMENTATION = Implementation(
            NamespaceDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "RedundantNamespace",
            briefDescription = "Redundant namespace",
            explanation = """
                In Android XML documents, only specify the namespace on the root/document \
                element. Namespace declarations elsewhere in the document are typically \
                accidental leftovers from copy/pasting XML from other files or documentation.
                """.trimIndent(),
            category = Category.PERFORMANCE,
            priority = 1,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return
        val childNodes = root.childNodes ?: return
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child != null && child.nodeType == org.w3c.dom.Node.ELEMENT_NODE) {
                checkElement(context, child as org.w3c.dom.Element)
            }
        }
    }

    private fun checkElement(context: XmlContext, element: org.w3c.dom.Element) {
        val attributes = element.attributes
        if (attributes != null) {
            for (i in 0 until attributes.length) {
                val attr = attributes.item(i) as? org.w3c.dom.Attr ?: continue
                val name = attr.name ?: continue
                if (name == "xmlns" || name.startsWith("xmlns:")) {
                    context.report(
                        issue = ISSUE,
                        scope = attr,
                        location = context.getLocation(attr),
                        message = "Redundant namespace declaration, can be omitted"
                    )
                }
            }
        }

        val childNodes = element.childNodes ?: return
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child != null && child.nodeType == org.w3c.dom.Node.ELEMENT_NODE) {
                checkElement(context, child as org.w3c.dom.Element)
            }
        }
    }
}