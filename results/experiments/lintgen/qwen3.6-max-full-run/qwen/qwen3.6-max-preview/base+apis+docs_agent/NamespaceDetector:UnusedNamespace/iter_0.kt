package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.w3c.dom.Attr
import org.w3c.dom.Element

class NamespaceDetector : Detector(), XmlScanner {

    companion object {
        val ISSUE = Issue.create(
            id = "UnusedNamespace",
            briefDescription = "Unused namespace",
            explanation = "Unused namespace declarations take up space and require processing that is not necessary.",
            category = Category.PERFORMANCE,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(NamespaceDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
        )
    }

    override fun getApplicableElements(): Collection<String>? = null

    override fun visitElement(context: XmlContext, element: Element) {
        if (element.ownerDocument.documentElement != element) return

        val usedNamespaces = mutableSetOf<String>()
        collectUsedNamespaces(element, usedNamespaces)

        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as Attr
            val name = attr.nodeName
            if (name.startsWith("xmlns:") || name == "xmlns") {
                val uri = attr.nodeValue
                if (uri.isNotEmpty() && uri !in usedNamespaces) {
                    context.report(
                        ISSUE,
                        context.getLocation(attr),
                        "Unused namespace declaration"
                    )
                }
            }
        }
    }

    private fun collectUsedNamespaces(element: Element, used: MutableSet<String>) {
        element.namespaceURI?.let { used.add(it) }
        val attrs = element.attributes
        for (i in 0 until attrs.length) {
            val attr = attrs.item(i) as Attr
            attr.namespaceURI?.let { used.add(it) }
        }
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is Element) {
                collectUsedNamespaces(child, used)
            }
        }
    }
}