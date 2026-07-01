package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
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
            explanation = "Unused namespace declarations take up space and require processing that is not necessary",
            category = Category.PERFORMANCE,
            priority = 1,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return

        // Collect all namespace declarations on the root element
        val declaredNamespaces = mutableMapOf<String, String>() // prefix -> uri
        val attributes = root.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i)
            val name = attr.nodeName
            if (name.startsWith("xmlns:")) {
                val prefix = name.substring("xmlns:".length)
                val uri = attr.nodeValue
                declaredNamespaces[prefix] = uri
            }
        }

        if (declaredNamespaces.isEmpty()) return

        // Collect all used namespace prefixes across the entire document
        val usedPrefixes = mutableSetOf<String>()
        collectUsedPrefixes(root, usedPrefixes)

        // Report namespaces that are declared but never used
        for ((prefix, _) in declaredNamespaces) {
            if (prefix !in usedPrefixes) {
                val attrNode = attributes.getNamedItem("xmlns:$prefix")
                if (attrNode != null) {
                    context.report(
                        ISSUE,
                        root,
                        context.getLocation(attrNode),
                        "Unused namespace `$prefix`"
                    )
                }
            }
        }
    }

    private fun collectUsedPrefixes(node: Node, usedPrefixes: MutableSet<String>) {
        if (node.nodeType == Node.ELEMENT_NODE) {
            val element = node as Element

            // Check the element's own name for a prefix
            val elementName = element.nodeName
            if (elementName.contains(':')) {
                val prefix = elementName.substringBefore(':')
                if (prefix != "xmlns") {
                    usedPrefixes.add(prefix)
                }
            }

            // Check all attributes for prefixes (excluding xmlns declarations themselves)
            val attributes = element.attributes
            for (i in 0 until attributes.length) {
                val attr = attributes.item(i)
                val attrName = attr.nodeName
                if (attrName.contains(':') && !attrName.startsWith("xmlns:")) {
                    val prefix = attrName.substringBefore(':')
                    usedPrefixes.add(prefix)
                }
            }

            // Recurse into child nodes
            val children = element.childNodes
            for (i in 0 until children.length) {
                collectUsedPrefixes(children.item(i), usedPrefixes)
            }
        }
    }
}