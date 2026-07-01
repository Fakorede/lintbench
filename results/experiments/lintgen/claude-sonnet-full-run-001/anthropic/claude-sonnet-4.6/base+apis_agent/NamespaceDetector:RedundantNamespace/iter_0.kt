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
import org.w3c.dom.NamedNodeMap

class NamespaceDetector : Detector(), XmlScanner {

    companion object {
        @JvmField
        val REDUNDANT_NAMESPACE: Issue = Issue.create(
            id = "RedundantNamespace",
            briefDescription = "Redundant namespace",
            explanation = """
                In Android XML documents, only specify the namespace on the root/document \
                element. Namespace declarations elsewhere in the document are typically \
                accidental leftovers from copy/pasting XML from other files or documentation.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                NamespaceDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )

        private const val XMLNS_PREFIX = "xmlns"
        private const val XMLNS_COLON = "xmlns:"
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return
        checkElementForRedundantNamespaces(context, root, isRoot = true)
    }

    private fun checkElementForRedundantNamespaces(
        context: XmlContext,
        element: Element,
        isRoot: Boolean
    ) {
        if (!isRoot) {
            val attributes: NamedNodeMap = element.attributes
            for (i in 0 until attributes.length) {
                val attr = attributes.item(i) as? Attr ?: continue
                val name = attr.name ?: continue
                if (name == XMLNS_PREFIX || name.startsWith(XMLNS_COLON)) {
                    val location = context.getLocation(attr)
                    context.report(
                        REDUNDANT_NAMESPACE,
                        element,
                        location,
                        "Redundant namespace declaration; already declared on the root element"
                    )
                }
            }
        }

        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is Element) {
                checkElementForRedundantNamespaces(context, child, isRoot = false)
            }
        }
    }
}