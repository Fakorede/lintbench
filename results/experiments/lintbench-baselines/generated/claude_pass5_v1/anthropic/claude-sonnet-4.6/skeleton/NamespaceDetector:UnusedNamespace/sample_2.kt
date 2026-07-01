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
import org.w3c.dom.NamedNodeMap
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
            explanation = "Unused namespace declarations take up space and require processing that is " +
                "not necessary",
            category = Category.PERFORMANCE,
            priority = 1,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )

        private const val XMLNS_PREFIX = "xmlns:"
        private const val XMLNS = "xmlns"
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return

        // Collect all namespace declarations on the root element
        val declaredNamespaces = mutableMapOf<String, Node>() // prefix -> attribute node
        val attributes: NamedNodeMap = root.attributes ?: return

        for (i in 0 until attributes.length) {
            val attr = attributes.item(i)
            val name = attr.nodeName
            if (name.startsWith(XMLNS_PREFIX)) {
                val prefix = name.substring(XMLNS_PREFIX.length)
                if (prefix.isNotEmpty()) {
                    declaredNamespaces[prefix] = attr
                }
            } else if (name == XMLNS) {
                // Default namespace declaration — skip, not a prefixed namespace
            }
        }

        if (declaredNamespaces.isEmpty()) {
            return
        }

        // Collect all used namespace prefixes in the document
        val usedPrefixes = mutableSetOf<String>()
        collectUsedPrefixes(root, usedPrefixes, isRoot = true)

        // Report any declared namespace that is not used
        for ((prefix, attrNode) in declaredNamespaces) {
            if (prefix !in usedPrefixes) {
                context.report(
                    ISSUE,
                    root,
                    context.getLocation(attrNode),
                    "Unused namespace `$prefix`",
                )
            }
        }
    }

    /**
     * Recursively walks the element tree and collects all namespace prefixes
     * that are actually used (in element names or attribute names), excluding
     * namespace declaration attributes themselves.
     */
    private fun collectUsedPrefixes(node: Node, usedPrefixes: MutableSet<String>, isRoot: Boolean) {
        if (node.nodeType == Node.ELEMENT_NODE) {
            val element = node as Element

            // Check element's own prefix
            val elementPrefix = node.prefix
            if (!elementPrefix.isNullOrEmpty()) {
                usedPrefixes.add(elementPrefix)
            }

            // Check attribute prefixes
            val attrs = element.attributes
            if (attrs != null) {
                for (i in 0 until attrs.length) {
                    val attr = attrs.item(i)
                    val attrName = attr.nodeName

                    // Skip namespace declaration attributes themselves
                    if (attrName == XMLNS || attrName.startsWith(XMLNS_PREFIX)) {
                        continue
                    }

                    val attrPrefix = attr.prefix
                    if (!attrPrefix.isNullOrEmpty()) {
                        usedPrefixes.add(attrPrefix)
                    }
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