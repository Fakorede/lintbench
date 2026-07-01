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

class NamespaceDetector : ResourceXmlDetector() {

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

    override fun getIssues(): List<Issue> = listOf(ISSUE)

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return
        val declarations = mutableMapOf<String, Attr>()
        val usedPrefixes = mutableSetOf<String>()

        collectNamespaces(root, declarations, usedPrefixes)

        for ((prefix, attr) in declarations) {
            if (prefix !in usedPrefixes && prefix != "xml") {
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
        declarations: MutableMap<String, Attr>,
        usedPrefixes: MutableSet<String>
    ) {
        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as Attr
            val attrName = attr.name
            if (attrName.startsWith("xmlns:")) {
                declarations[attrName.substring(6)] = attr
            } else {
                val colonIndex = attrName.indexOf(':')
                if (colonIndex > 0) {
                    usedPrefixes.add(attrName.substring(0, colonIndex))
                }
            }
        }

        val tagName = element.tagName
        val colonIndex = tagName.indexOf(':')
        if (colonIndex > 0) {
            usedPrefixes.add(tagName.substring(0, colonIndex))
        }

        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is Element) {
                collectNamespaces(child, declarations, usedPrefixes)
            }
        }
    }
}