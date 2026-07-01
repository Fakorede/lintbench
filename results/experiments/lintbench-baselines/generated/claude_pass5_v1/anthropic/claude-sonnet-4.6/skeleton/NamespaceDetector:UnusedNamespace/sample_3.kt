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

        // Collect all namespace declarations from the root element
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

        if (declaredNamespaces.isEmpty()) {
            return
        }

        // Collect all used namespace prefixes in the document
        val usedPrefixes = mutableSetOf<String>()
        collectUsedPrefixes(root, usedPrefixes, isRoot = true)

        // Report unused namespaces
        for ((prefix, _) in declaredNamespaces) {
            if (prefix !in usedPrefixes) {
                val attrName = "xmlns:$prefix"
                val attrNode = attributes.getNamedItem(attrName)
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

    private fun collectUsedPrefixes(node: Node, usedPrefixes: MutableSet<String>, isRoot: Boolean) {
        if (node.nodeType == Node.ELEMENT_NODE) {
            val element = node as Element

            // Check element's own prefix (but not the root element's xmlns declarations themselves)
            val elementPrefix = node.prefix
            if (!elementPrefix.isNullOrEmpty()) {
                usedPrefixes.add(elementPrefix)
            }

            // Check all attributes
            val attributes = element.attributes
            for (i in 0 until attributes.length) {
                val attr = attributes.item(i)
                val attrName = attr.nodeName

                // Skip namespace declarations themselves
                if (attrName.startsWith("xmlns:") || attrName == "xmlns") {
                    continue
                }

                val attrPrefix = attr.prefix
                if (!attrPrefix.isNullOrEmpty() && attrPrefix != "xmlns") {
                    usedPrefixes.add(attrPrefix)
                }
            }

            // Recurse into children
            val children = element.childNodes
            for (i in 0 until children.length) {
                collectUsedPrefixes(children.item(i), usedPrefixes, isRoot = false)
            }
        }
    }
}