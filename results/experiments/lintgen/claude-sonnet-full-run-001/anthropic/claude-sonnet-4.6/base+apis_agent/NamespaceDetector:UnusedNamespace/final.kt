package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
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
        val attributes: NamedNodeMap = root.attributes ?: return

        // Build a map of prefix -> namespace URI for xmlns declarations
        val namespacePrefixes = mutableMapOf<String, Attr>()
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as? Attr ?: continue
            val name = attr.name ?: continue
            if (name.startsWith(XMLNS_PREFIX)) {
                val prefix = name.substring(XMLNS_PREFIX.length)
                if (prefix.isNotEmpty()) {
                    namespacePrefixes[prefix] = attr
                }
            }
        }

        if (namespacePrefixes.isEmpty()) {
            return
        }

        // Collect all used namespace URIs in the document
        val usedNamespaceUris = mutableSetOf<String>()
        collectUsedNamespaces(root, usedNamespaceUris, isRoot = true)

        // Check each declared namespace to see if it's used
        for ((prefix, attr) in namespacePrefixes) {
            val uri = attr.value ?: continue
            if (!usedNamespaceUris.contains(uri)) {
                context.report(
                    UNUSED,
                    attr,
                    context.getLocation(attr),
                    "Unused namespace `$prefix`"
                )
            }
        }
    }

    private fun collectUsedNamespaces(node: Node, usedUris: MutableSet<String>, isRoot: Boolean) {
        if (node.nodeType == Node.ELEMENT_NODE) {
            val element = node as Element

            // Check the element's own namespace
            val elementNs = element.namespaceURI
            if (!elementNs.isNullOrEmpty()) {
                usedUris.add(elementNs)
            }

            // Check all attributes
            val attrs = element.attributes
            if (attrs != null) {
                for (i in 0 until attrs.length) {
                    val attr = attrs.item(i) as? Attr ?: continue
                    val attrName = attr.name ?: continue

                    // Skip xmlns declarations themselves (they are declarations, not usages)
                    if (attrName.startsWith(XMLNS_PREFIX)) {
                        continue
                    }

                    val attrNs = attr.namespaceURI
                    if (!attrNs.isNullOrEmpty()) {
                        usedUris.add(attrNs)
                    }
                }
            }

            // Recurse into children
            var child = element.firstChild
            while (child != null) {
                collectUsedNamespaces(child, usedUris, isRoot = false)
                child = child.nextSibling
            }
        }
    }
}