package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.TOOLS_URI
import com.android.SdkConstants.XMLNS_PREFIX
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.NamedNodeMap
import org.w3c.dom.Node

class NamespaceDetector : Detector(), XmlScanner {

    companion object {
        @JvmField
        val UNUSED = Issue.create(
            id = "UnusedNamespace",
            briefDescription = "Unused namespace",
            explanation = """
                Unused namespace declarations take up space and require processing that is \
                not necessary
                """,
            category = Category.PERFORMANCE,
            priority = 1,
            severity = Severity.WARNING,
            implementation = Implementation(
                NamespaceDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return

        // Collect all namespace declarations on the root element
        val declaredNamespaces = mutableMapOf<String, Attr>() // prefix -> attr
        val attrs: NamedNodeMap = root.attributes
        for (i in 0 until attrs.length) {
            val attr = attrs.item(i) as Attr
            val name = attr.name
            if (name.startsWith(XMLNS_PREFIX)) {
                val prefix = name.substring(XMLNS_PREFIX.length)
                declaredNamespaces[prefix] = attr
            }
        }

        if (declaredNamespaces.isEmpty()) return

        // Collect all used namespace URIs in the document
        val usedNamespaceUris = mutableSetOf<String>()
        collectUsedNamespaces(root, usedNamespaceUris, true)

        // Check each declared namespace to see if it's used
        for ((_, attr) in declaredNamespaces) {
            val uri = attr.value
            if (!usedNamespaceUris.contains(uri)) {
                context.report(
                    UNUSED,
                    root,
                    context.getLocation(attr),
                    "Unused namespace `${attr.name}`"
                )
            }
        }
    }

    private fun collectUsedNamespaces(node: Node, used: MutableSet<String>, isRoot: Boolean) {
        if (node.nodeType == Node.ELEMENT_NODE) {
            val element = node as Element

            // Check element's own namespace
            val elementNs = element.namespaceURI
            if (elementNs != null) {
                used.add(elementNs)
            }

            // Check attributes
            val attrs: NamedNodeMap = element.attributes
            for (i in 0 until attrs.length) {
                val attr = attrs.item(i) as Attr
                val attrName = attr.name
                // Skip xmlns declarations themselves
                if (attrName.startsWith(XMLNS_PREFIX)) {
                    continue
                }
                val attrNs = attr.namespaceURI
                if (attrNs != null) {
                    used.add(attrNs)
                }
            }

            // Recurse into children
            val children = element.childNodes
            for (i in 0 until children.length) {
                collectUsedNamespaces(children.item(i), used, false)
            }
        }
    }
}