package com.android.tools.lint.checks

import com.android.SdkConstants.XMLNS_PREFIX
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
import org.w3c.dom.NamedNodeMap
import org.w3c.dom.Node

class NamespaceDetector : ResourceXmlDetector() {

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return

        // Collect all namespace declarations defined on the root element
        val namespaceDeclarations = mutableMapOf<String, Attr>()
        val rootAttributes: NamedNodeMap = root.attributes
        for (i in 0 until rootAttributes.length) {
            val attr = rootAttributes.item(i) as Attr
            if (attr.name.startsWith(XMLNS_PREFIX)) {
                val prefix = attr.name.substring(XMLNS_PREFIX.length)
                if (prefix.isNotEmpty()) {
                    namespaceDeclarations[prefix] = attr
                }
            }
        }

        if (namespaceDeclarations.isEmpty()) return

        // Collect all prefixes actually used in the document
        val usedPrefixes = mutableSetOf<String>()
        collectUsedPrefixes(root, usedPrefixes, namespaceDeclarations.keys)

        // Report any declared namespaces that are never used
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

    private fun collectUsedPrefixes(node: Node, usedPrefixes: MutableSet<String>, declaredPrefixes: Set<String>) {
        if (node.nodeType == Node.ELEMENT_NODE) {
            val element = node as Element

            // Check element prefix
            val elementPrefix = node.prefix
            if (elementPrefix != null && elementPrefix in declaredPrefixes) {
                usedPrefixes.add(elementPrefix)
            }

            // Check attribute prefixes
            val attributes: NamedNodeMap = element.attributes
            for (i in 0 until attributes.length) {
                val attr = attributes.item(i) as Attr
                // Skip namespace declarations themselves
                if (attr.name.startsWith(XMLNS_PREFIX)) continue
                val attrPrefix = attr.prefix
                if (attrPrefix != null && attrPrefix in declaredPrefixes) {
                    usedPrefixes.add(attrPrefix)
                }
            }

            // Recurse into children
            val children = element.childNodes
            for (i in 0 until children.length) {
                collectUsedPrefixes(children.item(i), usedPrefixes, declaredPrefixes)
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