package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

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
            explanation = "In Android XML documents, only specify the namespace on the root/document element. Namespace declarations elsewhere in the document are typically accidental leftovers from copy/pasting XML from other files or documentation.",
            category = Category.PERFORMANCE,
            priority = 1,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return
        checkChildren(context, root)
    }

    private fun checkChildren(context: XmlContext, parent: Element) {
        val childNodes = parent.childNodes
        for (i in 0 until childNodes.length) {
            val node = childNodes.item(i)
            if (node.nodeType == Node.ELEMENT_NODE) {
                val element = node as Element
                checkElement(context, element)
                checkChildren(context, element)
            }
        }
    }

    private fun checkElement(context: XmlContext, element: Element) {
        val attributes = element.attributes ?: return
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i)
            if (attr.nodeName.startsWith("xmlns")) {
                context.report(
                    ISSUE,
                    context.getLocation(attr),
                    "Redundant namespace declaration"
                )
            }
        }
    }
}