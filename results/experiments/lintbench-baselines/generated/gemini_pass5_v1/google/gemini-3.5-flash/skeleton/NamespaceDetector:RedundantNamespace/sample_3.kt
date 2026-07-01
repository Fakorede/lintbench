package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
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
            explanation = "In Android XML documents, only specify the namespace on the root/document element. Namespace declarations elsewhere in the document are typically accidental leftovers from copy/pasting XML from other files or documentation.",
            category = Category.PERFORMANCE,
            priority = 1,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun visitDocument(context: XmlContext, document: org.w3c.dom.Document) {
        val root = document.documentElement ?: return
        checkElement(context, root, isRoot = true)
    }

    private fun checkElement(context: XmlContext, element: org.w3c.dom.Element, isRoot: Boolean) {
        if (!isRoot) {
            val attributes = element.attributes
            if (attributes != null) {
                for (i in 0 until attributes.length) {
                    val attribute = attributes.item(i) ?: continue
                    val name = attribute.nodeName
                    if (name != null && (name.startsWith("xmlns:") || name == "xmlns")) {
                        val location = context.getLocation(attribute)
                        context.report(
                            ISSUE,
                            attribute,
                            location,
                            "Redundant namespace declaration"
                        )
                    }
                }
            }
        }
        val childNodes = element.childNodes
        if (childNodes != null) {
            for (i in 0 until childNodes.length) {
                val child = childNodes.item(i)
                if (child is org.w3c.dom.Element) {
                    checkElement(context, child, isRoot = false)
                }
            }
        }
    }
}