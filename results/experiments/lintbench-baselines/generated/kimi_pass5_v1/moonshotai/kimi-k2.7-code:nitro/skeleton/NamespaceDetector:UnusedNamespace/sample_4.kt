package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext

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
            explanation = "Namespace declarations take up space in XML files and require processing. "
                + "If a declared namespace is not actually used by any element or attribute in the file, "
                + "it should be removed to reduce file size and parsing overhead.",
            category = Category.PERFORMANCE,
            priority = 1,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun visitDocument(context: XmlContext, document: org.w3c.dom.Document) {
        val root = document.documentElement ?: return

        val declarations = mutableListOf<Pair<String, org.w3c.dom.Attr>>()
        val usedPrefixes = mutableSetOf<String>()
        val usedDefaultNamespaceUris = mutableSetOf<String>()

        collectFromNode(root, declarations, usedPrefixes, usedDefaultNamespaceUris)

        for ((prefix, attr) in declarations) {
            val namespaceUri = attr.value ?: ""
            val isUsed = if (prefix.isEmpty()) {
                usedDefaultNamespaceUris.contains(namespaceUri)
            } else {
                usedPrefixes.contains(prefix)
            }

            if (!isUsed) {
                val message = if (prefix.isEmpty()) {
                    "Unused namespace xmlns=\"$namespaceUri\""
                } else {
                    "Unused namespace xmlns:$prefix=\"$namespaceUri\""
                }
                context.report(ISSUE, context.getLocation(attr), message)
            }
        }
    }

    private fun collectFromNode(
        node: org.w3c.dom.Node,
        declarations: MutableList<Pair<String, org.w3c.dom.Attr>>,
        usedPrefixes: MutableSet<String>,
        usedDefaultNamespaceUris: MutableSet<String>,
    ) {
        if (node.nodeType != org.w3c.dom.Node.ELEMENT_NODE) {
            return
        }

        val element = node as org.w3c.dom.Element
        recordElementNamespaces(element, declarations, usedPrefixes, usedDefaultNamespaceUris)

        val children = element.childNodes
        for (i in 0 until children.length) {
            collectFromNode(children.item(i), declarations, usedPrefixes, usedDefaultNamespaceUris)
        }
    }

    private fun recordElementNamespaces(
        element: org.w3c.dom.Element,
        declarations: MutableList<Pair<String, org.w3c.dom.Attr>>,
        usedPrefixes: MutableSet<String>,
        usedDefaultNamespaceUris: MutableSet<String>,
    ) {
        val nodeName = element.nodeName
        val colonIndex = nodeName.indexOf(':')
        if (colonIndex == -1) {
            element.namespaceURI?.let { usedDefaultNamespaceUris.add(it) }
        } else {
            usedPrefixes.add(nodeName.substring(0, colonIndex))
        }

        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as org.w3c.dom.Attr
            val name = attr.name
            when {
                name == "xmlns" -> declarations.add("" to attr)
                name.startsWith("xmlns:") -> declarations.add(
                    name.substring("xmlns:".length) to attr
                )
                else -> {
                    val attrColonIndex = name.indexOf(':')
                    if (attrColonIndex != -1) {
                        usedPrefixes.add(name.substring(0, attrColonIndex))
                    }
                }
            }
        }
    }
}