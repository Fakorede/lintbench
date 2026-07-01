package com.android.tools.lint.checks

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
import org.w3c.dom.Node

class NamespaceDetector : Detector(), XmlScanner {

    companion object {
        val ISSUE = Issue.create(
            id = "UnusedNamespace",
            briefDescription = "Unused namespace",
            explanation = "Unused namespace declarations take up space and require processing that is not necessary",
            category = Category.PERFORMANCE,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(NamespaceDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
        )
    }

    override fun getApplicableElements(): Collection<String>? = null

    override fun visitElement(context: XmlContext, element: Element) {
        if (element.parentNode !is Document) return

        val declaredNamespaces = mutableMapOf<String, Attr>()
        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as Attr
            val name = attr.name
            if (name.startsWith("xmlns:")) {
                val prefix = name.substring(6)
                if (prefix != "tools") {
                    declaredNamespaces[prefix] = attr
                }
            }
        }

        if (declaredNamespaces.isEmpty()) return

        val usedPrefixes = mutableSetOf<String>()
        collectUsedPrefixes(element, usedPrefixes)

        for ((prefix, attr) in declaredNamespaces) {
            if (prefix !in usedPrefixes) {
                context.report(
                    ISSUE,
                    context.getLocation(attr),
                    "Unused namespace $prefix"
                )
            }
        }
    }

    private fun collectUsedPrefixes(node: Node, usedPrefixes: MutableSet<String>) {
        if (node.nodeType == Node.ELEMENT_NODE) {
            val el = node as Element
            val tagName = el.tagName
            val colonIdx = tagName.indexOf(':')
            if (colonIdx != -1) {
                usedPrefixes.add(tagName.substring(0, colonIdx))
            }

            val attrs = el.attributes
            for (i in 0 until attrs.length) {
                val attrName = attrs.item(i).nodeName
                val attrColonIdx = attrName.indexOf(':')
                if (attrColonIdx != -1) {
                    val attrPrefix = attrName.substring(0, attrColonIdx)
                    if (attrPrefix != "xmlns") {
                        usedPrefixes.add(attrPrefix)
                    }
                }
            }
        }

        val children = node.childNodes
        for (i in 0 until children.length) {
            collectUsedPrefixes(children.item(i), usedPrefixes)
        }
    }
}