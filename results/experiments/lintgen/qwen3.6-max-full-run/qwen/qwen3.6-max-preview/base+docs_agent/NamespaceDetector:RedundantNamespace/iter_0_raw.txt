package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.w3c.dom.Element

class NamespaceDetector : Detector(), XmlScanner {

    override fun getApplicableElements(): Collection<String>? = ALL

    override fun visitElement(context: XmlContext, element: Element) {
        val root = element.ownerDocument?.documentElement ?: return
        if (element === root) return

        val attributes = element.attributes ?: return
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i)
            if (attr.name.startsWith("xmlns") || attr.namespaceURI == "http://www.w3.org/2000/xmlns/") {
                context.report(
                    ISSUE,
                    context.getLocation(attr),
                    "Redundant namespace declaration `${attr.name}`; move to root element or remove"
                )
            }
        }
    }

    companion object {
        val ISSUE = Issue.create(
            "RedundantNamespace",
            "Redundant namespace",
            "In Android XML documents, only specify the namespace on the root/document element. " +
            "Namespace declarations elsewhere in the document are typically accidental leftovers " +
            "from copy/pasting XML from other files or documentation.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            Implementation(NamespaceDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
        )
    }
}