package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Attr
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
            explanation = """
                In Android XML documents, namespace declarations should only be present
                on the root element. Namespace declarations on other elements are usually
                accidental leftovers from copying XML from other files or documentation
                and should be removed to avoid confusion and unnecessary parsing.
            """.trimIndent(),
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
                if (isNamespaceAttribute(attr)) {
                    context.report(
                        ISSUE,
                        context.getLocation(attr),
                        "Redundant namespace declaration: `${attr.name}`",
                    )
                }
            }
        }

        var child = element.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE) {
                checkElement(context, root, child as Element)
            }
            child = child.nextSibling
        }
    }

    private fun isNamespaceAttribute(attr: Attr): Boolean {
        return attr.name == "xmlns" || attr.name.startsWith("xmlns:")
    }
}