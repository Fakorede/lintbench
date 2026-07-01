package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Attr
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
            explanation = "Unused namespace declarations take up space and require " +
                "processing that is not necessary. Remove any namespace declarations " +
                "that are not used in this file.",
            category = Category.PERFORMANCE,
            priority = 1,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return
        val namespaces = mutableListOf<Attr>()
        val usedPrefixes = mutableSetOf<String>()

        collectNamespaces(root, namespaces)
        collectUsedPrefixes(root, usedPrefixes)

        for (attr in namespaces) {
            val prefix = attr.getDeclaredPrefix() ?: continue
            if (prefix == "xml" || prefix == "xmlns") continue
            if (prefix !in usedPrefixes) {
                context.report(
                    ISSUE,
                    attr,
                    context.getLocation(attr),
                    "Unused namespace '$prefix'"
                )
            }
        }
    }

    private fun collectNamespaces(node: Node, namespaces: MutableList<Attr>) {
        if (node.nodeType != Node.ELEMENT_NODE) return
        val element = node as Element
        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as Attr
            if (attr.isNamespaceDeclaration) {
                namespaces.add(attr)
            }
        }
        val children = element.childNodes
        for (i in 0 until children.length) {
            collectNamespaces(children.item(i), namespaces)
        }
    }

    private fun collectUsedPrefixes(node: Node, used: MutableSet<String>) {
        if (node.nodeType != Node.ELEMENT_NODE) return
        val element = node as Element
        val tagPrefix = element.tagName.substringNamespacePrefix()
        if (tagPrefix != null) {
            used.add(tagPrefix)
        }
        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as Attr
            val attrPrefix = attr.name.substringNamespacePrefix()
            if (attrPrefix != null && attrPrefix != "xmlns") {
                used.add(attrPrefix)
            }
        }
        val children = element.childNodes
        for (i in 0 until children.length) {
            collectUsedPrefixes(children.item(i), used)
        }
    }

    private val Attr.isNamespaceDeclaration: Boolean
        get() = name == "xmlns" || name.startsWith("xmlns:")

    private fun Attr.getDeclaredPrefix(): String? {
        return if (name.startsWith("xmlns:")) {
            name.substringAfter("xmlns:")
        } else {
            null
        }
    }

    private fun String.substringNamespacePrefix(): String? {
        val index = indexOf(':')
        return if (index > 0) substring(0, index) else null
    }
}