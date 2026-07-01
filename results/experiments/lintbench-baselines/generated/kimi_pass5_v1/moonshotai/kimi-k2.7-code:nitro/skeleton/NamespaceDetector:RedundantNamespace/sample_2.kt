package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext

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
            explanation = "In Android XML documents, namespaces should only be declared on the root element. "
                + "Namespace declarations elsewhere are redundant, increase file size, and are typically accidental leftovers from copied XML.",
            category = Category.PERFORMANCE,
            priority = 1,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun visitDocument(context: XmlContext, document: org.w3c.dom.Document) {
        val root = document.documentElement ?: return
        checkElement(context, root, root)
    }

    private fun checkElement(
        context: XmlContext,
        root: org.w3c.dom.Element,
        element: org.w3c.dom.Element
    ) {
        if (element !== root) {
            val attributes = element.attributes
            for (i in 0 until attributes.length) {
                val attr = attributes.item(i) as? org.w3c.dom.Attr ?: continue
                if (isNamespaceDeclaration(attr)) {
                    context.report(
                        ISSUE,
                        attr,
                        context.getLocation(attr),
                        "Redundant namespace declaration \"${attr.name}\"; move it to the root element"
                    )
                }
            }
        }

        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == org.w3c.dom.Node.ELEMENT_NODE) {
                checkElement(context, root, child as org.w3c.dom.Element)
            }
        }
    }

    private fun isNamespaceDeclaration(attr: org.w3c.dom.Attr): Boolean =
        attr.name == "xmlns" || attr.name.startsWith("xmlns:")
}