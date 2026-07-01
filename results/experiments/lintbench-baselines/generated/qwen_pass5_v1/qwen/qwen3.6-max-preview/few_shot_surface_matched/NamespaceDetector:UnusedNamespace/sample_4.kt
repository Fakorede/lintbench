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
        val attributes = root.attributes ?: return

        val declaredPrefixes = mutableMapOf<String, Attr>()
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as Attr
            val name = attr.name
            if (name.startsWith("xmlns:")) {
                val prefix = name.substring(6)
                declaredPrefixes[prefix] = attr
            }
        }

        if (declaredPrefixes.isEmpty()) return

        val usedPrefixes = mutableSetOf<String>()
        collectUsedPrefixes(root, usedPrefixes)

        for ((prefix, attr) in declaredPrefixes) {
            if (prefix !in usedPrefixes) {
                context.report(
                    ISSUE,
                    attr,
                    context.getValueLocation(attr),
                    "Unused namespace declaration `$prefix`"
                )
            }
        }
    }

    private fun collectUsedPrefixes(node: Node, usedPrefixes: MutableSet<String>) {
        if (node.nodeType == Node.ELEMENT_NODE) {
            val element = node as Element
            element.prefix?.let { usedPrefixes.add(it) }

            val attrs = element.attributes
            if (attrs != null) {
                for (i in 0 until attrs.length) {
                    val attr = attrs.item(i) as Attr
                    val attrPrefix = attr.prefix
                    if (attrPrefix != null && attrPrefix != "xmlns") {
                        usedPrefixes.add(attrPrefix)
                    }
                }
            }
        }

        var child = node.firstChild
        while (child != null) {
            collectUsedPrefixes(child, usedPrefixes)
            child = child.nextSibling
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "UnusedNamespace",
            briefDescription = "Unused namespace",
            explanation = "Unused namespace declarations take up space and require processing that is not necessary",
            category = Category.PERFORMANCE,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(NamespaceDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
        )
    }
}