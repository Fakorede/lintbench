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
            explanation = "Unused namespace declarations take up space and require processing that is not necessary",
            category = Category.PERFORMANCE,
            priority = 1,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return
        val declarations = mutableListOf<Pair<String, Attr>>()
        val usedPrefixes = mutableSetOf<String>()

        collectNamespaces(root, declarations, usedPrefixes)

        for ((prefix, attr) in declarations) {
            if (prefix !in usedPrefixes) {
                context.report(
                    ISSUE,
                    context.getLocation(attr),
                    "Unused namespace declaration `$prefix`"
                )
            }
        }
    }

    private fun collectNamespaces(
        element: Element,
        declarations: MutableList<Pair<String, Attr>>,
        usedPrefixes: MutableSet<String>
    ) {
        val tagName = element.tagName
        val colonIndex = tagName.indexOf(':')
        if (colonIndex != -1) {
            usedPrefixes.add(tagName.substring(0, colonIndex))
        }

        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as Attr
            val attrName = attr.name
            if (attrName.startsWith("xmlns:")) {
                declarations.add(attrName.substring(6) to attr)
            } else {
                val attrColon = attrName.indexOf(':')
                if (attrColon != -1) {
                    usedPrefixes.add(attrName.substring(0, attrColon))
                }
            }
        }

        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.ELEMENT_NODE) {
                collectNamespaces(child as Element, declarations, usedPrefixes)
            }
        }
    }
}