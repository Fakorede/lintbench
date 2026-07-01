package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
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
            id = "UnusedNamespace",
            briefDescription = "Unused namespace",
            explanation = "Unused namespace declarations take up space and require processing that is not necessary.",
            category = Category.PERFORMANCE,
            priority = 1,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return
        val declarations = mutableMapOf<String, Attr>()
        val attrs = root.attributes
        for (i in 0 until attrs.length) {
            val attr = attrs.item(i) as Attr
            val name = attr.name
            if (name == "xmlns") {
                declarations[""] = attr
            } else if (name.startsWith("xmlns:")) {
                declarations[name.substring(6)] = attr
            }
        }

        if (declarations.isEmpty()) return

        val usedPrefixes = mutableSetOf<String>()
        collectUsedPrefixes(root, usedPrefixes)

        for ((prefix, attr) in declarations) {
            if (prefix !in usedPrefixes) {
                context.report(
                    ISSUE,
                    context.getLocation(attr),
                    "Unused namespace declaration `${attr.name}`"
                )
            }
        }
    }

    private fun collectUsedPrefixes(node: Node, usedPrefixes: MutableSet<String>) {
        if (node.nodeType == Node.ELEMENT_NODE) {
            val element = node as Element
            usedPrefixes.add(element.prefix ?: "")

            val elementAttrs = element.attributes
            for (i in 0 until elementAttrs.length) {
                val attr = elementAttrs.item(i) as Attr
                if (attr.name.startsWith("xmlns")) continue
                val attrPrefix = attr.prefix
                if (!attrPrefix.isNullOrEmpty()) {
                    usedPrefixes.add(attrPrefix)
                }
            }

            val children = node.childNodes
            for (i in 0 until children.length) {
                collectUsedPrefixes(children.item(i), usedPrefixes)
            }
        }
    }
}