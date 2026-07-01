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

        // Collect all namespace declarations from the root element
        val declaredNamespaces = mutableMapOf<String, Attr>() // prefix -> attr node
        val attributes: NamedNodeMap = root.attributes ?: return

        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as? Attr ?: continue
            val name = attr.name ?: continue
            if (name.startsWith(XMLNS_PREFIX)) {
                val prefix = name.substring(XMLNS_PREFIX.length)
                if (prefix.isNotEmpty()) {
                    declaredNamespaces[prefix] = attr
                }
            } else if (name == XMLNS) {
                // Default namespace declaration — skip, not prefix-based
            }
        }

        if (declaredNamespaces.isEmpty()) {
            return
        }

        // Collect all used namespace prefixes in the entire document
        val usedPrefixes = mutableSetOf<String>()
        collectUsedPrefixes(root, usedPrefixes, isRoot = true)

        // Report any declared namespace that is never used
        for ((prefix, attr) in declaredNamespaces) {
            if (prefix !in usedPrefixes) {
                context.report(
                    ISSUE,
                    root,
                    context.getNameLocation(attr),
                    "Unused namespace `$prefix`",
                )
            }
        }
    }

    /**
     * Recursively walks the element tree and collects every namespace prefix
     * that is actually used (i.e. appears as a prefix on an element or
     * attribute name, excluding xmlns declarations themselves).
     */
    private fun collectUsedPrefixes(node: Node, used: MutableSet<String>, isRoot: Boolean) {
        if (node.nodeType == Node.ELEMENT_NODE) {
            val element = node as Element

            // Check the element's own prefix
            val elementPrefix = element.prefix
            if (!elementPrefix.isNullOrEmpty()) {
                used.add(elementPrefix)
            }

            // Check all attributes
            val attributes = element.attributes
            if (attributes != null) {
                for (i in 0 until attributes.length) {
                    val attr = attributes.item(i) as? Attr ?: continue
                    val attrName = attr.name ?: continue

                    // Skip namespace declarations themselves
                    if (attrName == XMLNS || attrName.startsWith(XMLNS_PREFIX)) {
                        continue
                    }

                    val attrPrefix = attr.prefix
                    if (!attrPrefix.isNullOrEmpty()) {
                        used.add(attrPrefix)
                    }

                    // Also scan attribute values for references like "prefix:something"
                    // (e.g. tools:ignore, app:layout_constraintTop_toTopOf values are
                    //  plain strings, but some values like style references use prefixes)
                    // We intentionally do NOT scan values — only structural usage matters.
                }
            }

            // Recurse into children
            var child = element.firstChild
            while (child != null) {
                collectUsedPrefixes(child, used, isRoot = false)
                child = child.nextSibling
            }
        }
    }
}