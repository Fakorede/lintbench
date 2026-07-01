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
import org.w3c.dom.Node

class NamespaceDetector : Detector(), XmlScanner {

    companion object {
        val ISSUE = Issue.create(
            id = "RedundantNamespace",
            briefDescription = "Redundant namespace",
            explanation = """
                In Android XML documents, only specify the namespace on the root/document element.
                Namespace declarations elsewhere in the document are typically accidental leftovers from copy/pasting XML from other files or documentation.
            """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(NamespaceDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
        )
    }

    override fun getApplicableNodeTypes(): IntArray? {
        return intArrayOf(Node.ELEMENT_NODE)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        if (element === context.document.documentElement) {
            return
        }

        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as? Attr ?: continue
            if (attr.isNsAttribute) {
                context.report(
                    ISSUE,
                    context.getLocation(attr),
                    "Redundant namespace declaration"
                )
            }
        }
    }

    private val Attr.isNsAttribute: Boolean
        get() = name == "xmlns" || prefix == "xmlns"
}