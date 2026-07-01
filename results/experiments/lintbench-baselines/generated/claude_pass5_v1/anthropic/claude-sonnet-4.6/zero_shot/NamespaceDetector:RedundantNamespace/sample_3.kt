package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Attr
import org.w3c.dom.Element

class NamespaceDetector : LayoutDetector() {

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
                Scope.ALL_RESOURCES_SCOPE
            )
        )

        private const val XMLNS_PREFIX = "xmlns:"
        private const val XMLNS = "xmlns"
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val document = element.ownerDocument ?: return
        val root = document.documentElement ?: return

        // Only check non-root elements
        if (element === root) {
            return
        }

        val attributes = element.attributes ?: return
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as? Attr ?: continue
            val name = attr.name ?: continue
            if (name.startsWith(XMLNS_PREFIX) || name == XMLNS) {
                context.report(
                    REDUNDANT_NAMESPACE,
                    attr,
                    context.getLocation(attr),
                    "Redundant namespace declaration; already declared on the root element"
                )
            }
        }
    }
}