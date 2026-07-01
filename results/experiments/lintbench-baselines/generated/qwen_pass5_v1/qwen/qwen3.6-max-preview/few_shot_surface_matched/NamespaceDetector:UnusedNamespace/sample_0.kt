package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

class NamespaceDetector : ResourceXmlDetector(), XmlScanner {

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return
        val declaredNamespaces = mutableMapOf<Attr, String>()
        val usedNamespaces = mutableSetOf<String>()

        collectNamespaces(root, declaredNamespaces, usedNamespaces)

        for ((attr, uri) in declaredNamespaces) {
            if (uri !in usedNamespaces) {
                context.report(
                    ISSUE,
                    attr,
                    context.getLocation(attr),
                    "Unused namespace declaration"
                )
            }
        }
    }

    private fun collectNamespaces(
        node: Node,
        declared: MutableMap<Attr, String>,
        used: MutableSet<String>
    ) {
        if (node.nodeType == Node.ELEMENT_NODE) {
            val element = node as Element
            element.namespaceURI?.let { used.add(it) }

            val attributes = element.attributes
            for (i in 0 until attributes.length) {
                val attr = attributes.item(i) as Attr
                val name = attr.name
                if (name == "xmlns" || name.startsWith("xmlns:")) {
                    declared[attr] = attr.value
                } else {
                    attr.namespaceURI?.let { used.add(it) }
                }
            }

            var child = element.firstChild
            while (child != null) {
                collectNamespaces(child, declared, used)
                child = child.nextSibling
            }
        }
    }

    companion object {
        @JvmField
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
}