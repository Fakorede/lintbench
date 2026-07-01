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
        val declaredNamespaces = mutableMapOf<String, Attr>() // prefix -> attr node
        val rootAttributes: NamedNodeMap = root.attributes
        for (i in 0 until rootAttributes.length) {
            val attr = rootAttributes.item(i) as Attr
            val name = attr.name
            if (name.startsWith(XMLNS_PREFIX)) {
                val prefix = name.substring(XMLNS_PREFIX.length)
                declaredNamespaces[prefix] = attr
            }
        }

        if (declaredNamespaces.isEmpty()) {
            return
        }

        // Collect all prefixes actually used in the document
        val usedPrefixes = mutableSetOf<String>()
        collectUsedPrefixes(root, usedPrefixes, rootAttributes)

        // Report any declared namespaces that are not used
        for ((prefix, attr) in declaredNamespaces) {
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

    private fun collectUsedPrefixes(node: Node, usedPrefixes: MutableSet<String>, rootAttrs: NamedNodeMap) {
        if (node.nodeType == Node.ELEMENT_NODE) {
            val element = node as Element

            // Check element's own prefix
            val elementPrefix = element.prefix
            if (!elementPrefix.isNullOrEmpty()) {
                usedPrefixes.add(elementPrefix)
            }

            // Check all attributes on this element
            val attrs: NamedNodeMap = element.attributes
            for (i in 0 until attrs.length) {
                val attr = attrs.item(i) as Attr
                val attrName = attr.name
                // Skip xmlns declarations themselves
                if (attrName.startsWith(XMLNS_PREFIX)) {
                    continue
                }
                val attrPrefix = attr.prefix
                if (!attrPrefix.isNullOrEmpty()) {
                    usedPrefixes.add(attrPrefix)
                }
            }

            // Recurse into children
            val children = element.childNodes
            for (i in 0 until children.length) {
                collectUsedPrefixes(children.item(i), usedPrefixes, rootAttrs)
            }
        }
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "UnusedNamespace",
            briefDescription = "Unused namespace",
            explanation =
                "Unused namespace declarations take up space and require processing that is " +
                    "not necessary",
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