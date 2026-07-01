package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.w3c.dom.*

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
        checkElement(context, root, root)
    }

    private fun checkElement(context: XmlContext, root: Element, element: Element) {
        if (element !== root) {
            val attributes = element.attributes
            for (i in 0 until attributes.length) {
                val attr = attributes.item(i) as? Attr ?: continue
                val name = attr.name
                if (name == "xmlns" || name.startsWith("xmlns:")) {
                    context.report(
                        ISSUE,
                        attr,
                        context.getValueLocation(attr),
                        "Redundant namespace declaration: `$name` should be declared on the root element"
                    )
                }
            }
        }

        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.ELEMENT_NODE) {
                checkElement(context, root, child as Element)
            }
        }
    }
}