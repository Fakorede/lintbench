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

        // Collect all namespace declarations on the root element
        val namespaceDeclarations = mutableMapOf<String, Attr>()
        val attributes = root.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as Attr
            if (attr.name.startsWith("xmlns:")) {
                val prefix = attr.name.substring("xmlns:".length)
                namespaceDeclarations[prefix] = attr
            }
        }

        if (namespaceDeclarations.isEmpty()) return

        // Collect all prefixes actually used in the document
        val usedPrefixes = mutableSetOf<String>()
        collectUsedPrefixes(root, usedPrefixes)

        // Report unused namespace declarations
        for ((prefix, attr) in namespaceDeclarations) {
            if (prefix !in usedPrefixes) {
                context.report(
                    ISSUE,
                    root,
                    context.getLocation(attr),
                    "Unused namespace `${attr.name}`"
                )
            }
        }
    }

    private fun collectUsedPrefixes(node: Node, usedPrefixes: MutableSet<String>) {
        if (node.nodeType == Node.ELEMENT_NODE) {
            val element = node as Element

            // Check element prefix
            val elementPrefix = node.prefix
            if (elementPrefix != null && elementPrefix != "xmlns") {
                usedPrefixes.add(elementPrefix)
            }

            // Check attribute prefixes
            val attributes = element.attributes
            for (i in 0 until attributes.length) {
                val attr = attributes.item(i) as Attr
                val attrName = attr.name
                // Skip namespace declarations themselves
                if (attrName.startsWith("xmlns:") || attrName == "xmlns") continue
                val attrPrefix = attr.prefix
                if (attrPrefix != null && attrPrefix != "xmlns") {
                    usedPrefixes.add(attrPrefix)
                }
            }

            // Recurse into children
            var child = element.firstChild
            while (child != null) {
                collectUsedPrefixes(child, usedPrefixes)
                child = child.nextSibling
            }
        }
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "UnusedNamespace",
            briefDescription = "Unused namespace",
            explanation =
                "Unused namespace declarations take up space and require processing " +
                    "that is not necessary",
            category = Category.PERFORMANCE,
            priority = 1,
            severity = Severity.WARNING,
            implementation = Implementation(
                NamespaceDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}