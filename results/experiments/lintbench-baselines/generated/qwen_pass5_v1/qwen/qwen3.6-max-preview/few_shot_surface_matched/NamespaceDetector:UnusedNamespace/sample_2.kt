package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

class NamespaceDetector : ResourceXmlDetector(), XmlScanner {

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return
        val declaredNamespaces = mutableMapOf<String, Attr>()
        val usedPrefixes = mutableSetOf<String>()

        collectNamespaces(root, declaredNamespaces, usedPrefixes)

        for ((prefix, attr) in declaredNamespaces) {
            if (prefix !in usedPrefixes) {
                context.report(
                    ISSUE,
                    attr,
                    context.getLocation(attr),
                    "Unused namespace $prefix"
                )
            }
        }
    }

    private fun collectNamespaces(
        element: Element,
        declared: MutableMap<String, Attr>,
        used: MutableSet<String>
    ) {
        element.prefix?.takeIf { it.isNotEmpty() }?.let { used.add(it) }

        val attributes = element.attributes
        if (attributes != null) {
            for (i in 0 until attributes.length) {
                val attr = attributes.item(i) as Attr
                val name = attr.name
                if (name.startsWith("xmlns:")) {
                    val prefix = name.substring(6)
                    if (prefix.isNotEmpty()) {
                        declared[prefix] = attr
                    }
                } else {
                    attr.prefix?.takeIf { it.isNotEmpty() }?.let { used.add(it) }
                }
            }
        }

        var child: Node? = element.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE) {
                collectNamespaces(child as Element, declared, used)
            }
            child = child.nextSibling
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "UnusedNamespace",
            briefDescription = "Unused namespace",
            explanation = "Unused namespace declarations take up space and require processing that is not necessary.",
            category = Category.PERFORMANCE,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(NamespaceDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
        )
    }
}