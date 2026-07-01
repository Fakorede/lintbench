package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

class NamespaceDetector : Detector(), Detector.XmlScanner {

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return
        val attributes = root.attributes ?: return

        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as Attr
            val attrName = attr.nodeName
            if (attrName.startsWith("xmlns:")) {
                val prefix = attrName.substring(6)
                if (!isPrefixUsedInTree(root, prefix)) {
                    context.report(
                        ISSUE_UNUSED_NAMESPACE,
                        context.getLocation(attr),
                        "Unused namespace `$prefix`"
                    )
                }
            }
        }
    }

    private fun isPrefixUsedInTree(element: Element, prefix: String): Boolean {
        val prefixMarker = "$prefix:"
        if (element.tagName.startsWith(prefixMarker)) return true

        val attrs = element.attributes
        if (attrs != null) {
            for (i in 0 until attrs.length) {
                if (attrs.item(i).nodeName.startsWith(prefixMarker)) return true
            }
        }

        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.ELEMENT_NODE && isPrefixUsedInTree(child as Element, prefix)) {
                return true
            }
        }
        return false
    }

    companion object {
        val ISSUE_UNUSED_NAMESPACE = Issue.create(
            id = "UnusedNamespace",
            briefDescription = "Unused namespace",
            explanation = "Unused namespace declarations take up space and require processing that is not necessary",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(NamespaceDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
        )
    }
}