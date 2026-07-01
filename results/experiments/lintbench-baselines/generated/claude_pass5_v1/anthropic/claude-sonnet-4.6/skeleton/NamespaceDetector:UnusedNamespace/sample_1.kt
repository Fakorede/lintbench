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
            explanation = "Unused namespace declarations take up space and require processing that is not necessary",
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

        // Collect all namespace declarations from the root element
        val declaredNamespaces = mutableMapOf<String, Attr>()
        val attributes: NamedNodeMap = root.attributes ?: return

        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as? Attr ?: continue
            val name = attr.name ?: continue
            if (name.startsWith(XMLNS_PREFIX)) {
                val prefix = name.substring(XMLNS_PREFIX.length)
                if (prefix.isNotEmpty()) {
                    declaredNamespaces[prefix] = attr
                }
            }
        }

        if (declaredNamespaces.isEmpty()) {
            return
        }

        // Collect all used prefixes in the document
        val usedPrefixes = mutableSetOf<String>()
        collectUsedPrefixes(root, usedPrefixes, true)

        // Report unused namespaces
        for ((prefix, attr) in declaredNamespaces) {
            if (prefix !in usedPrefixes) {
                context.report(
                    ISSUE,
                    attr,
                    context.getLocation(attr),
                    "Unused namespace `${attr.name}`"
                )
            }
        }
    }

    private fun collectUsedPrefixes(node: Node, usedPrefixes: MutableSet<String>, isRoot: Boolean) {
        if (node.nodeType == Node.ELEMENT_NODE) {
            val element = node as Element

            // Check element prefix
            val elementPrefix = element.prefix
            if (!elementPrefix.isNullOrEmpty()) {
                usedPrefixes.add(elementPrefix)
            }

            // Check attribute prefixes
            val attributes = element.attributes
            if (attributes != null) {
                for (i in 0 until attributes.length) {
                    val attr = attributes.item(i) as? Attr ?: continue
                    val attrName = attr.name ?: continue

                    // Skip xmlns declarations themselves
                    if (attrName == XMLNS || attrName.startsWith(XMLNS_PREFIX)) {
                        continue
                    }

                    val attrPrefix = attr.prefix
                    if (!attrPrefix.isNullOrEmpty()) {
                        usedPrefixes.add(attrPrefix)
                    }
                }
            }

            // Also check attribute values for references like "?attr/..." or "@namespace/..."
            // that might use namespace prefixes in their values
            if (attributes != null) {
                for (i in 0 until attributes.length) {
                    val attr = attributes.item(i) as? Attr ?: continue
                    val attrName = attr.name ?: continue
                    if (attrName == XMLNS || attrName.startsWith(XMLNS_PREFIX)) {
                        continue
                    }
                    // Check if value references a namespace prefix via "prefix:something"
                    val value = attr.value ?: continue
                    checkValueForNamespacePrefix(value, usedPrefixes)
                }
            }

            // Recurse into children
            val children = element.childNodes
            for (i in 0 until children.length) {
                collectUsedPrefixes(children.item(i), usedPrefixes, false)
            }
        }
    }

    private fun checkValueForNamespacePrefix(value: String, usedPrefixes: MutableSet<String>) {
        // Look for patterns like "prefix:something" in attribute values
        val colonIndex = value.indexOf(':')
        if (colonIndex > 0) {
            val potentialPrefix = value.substring(0, colonIndex)
            // Simple heuristic: if it looks like a valid XML prefix (no spaces, special chars)
            if (potentialPrefix.all { it.isLetterOrDigit() || it == '_' || it == '-' || it == '.' }) {
                usedPrefixes.add(potentialPrefix)
            }
        }
    }
}