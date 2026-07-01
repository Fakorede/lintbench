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
        @JvmField
        val ISSUE = Issue.create(
            "UnusedNamespace",
            "Unused namespace",
            "Unused namespace declarations take up space and require processing that is not necessary.",
            Category.CORRECTNESS,
            2,
            Severity.WARNING,
            Implementation(NamespaceDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
        )
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return
        val declarations = mutableMapOf<String, Attr>()
        val usedPrefixes = mutableSetOf<String>()

        collectNamespaces(root, declarations, usedPrefixes)

        for ((prefix, attr) in declarations) {
            if (prefix !in usedPrefixes) {
                val message = "Unused namespace declaration `$prefix`"
                context.report(ISSUE, attr, context.getLocation(attr), message)
            }
        }
    }

    private fun collectNamespaces(
        node: Node,
        declarations: MutableMap<String, Attr>,
        usedPrefixes: MutableSet<String>
    ) {
        if (node.nodeType == Node.ELEMENT_NODE) {
            val element = node as Element
            val tagName = element.tagName
            val colonIndex = tagName.indexOf(':')
            if (colonIndex > 0) {
                usedPrefixes.add(tagName.substring(0, colonIndex))
            }

            val attributes = element.attributes
            for (i in 0 until attributes.length) {
                val attr = attributes.item(i) as Attr
                val attrName = attr.name
                if (attrName.startsWith("xmlns:")) {
                    val prefix = attrName.substring(6)
                    declarations[prefix] = attr
                } else {
                    val colonIdx = attrName.indexOf(':')
                    if (colonIdx > 0) {
                        usedPrefixes.add(attrName.substring(0, colonIdx))
                    }
                }
            }
        }

        val children = node.childNodes
        for (i in 0 until children.length) {
            collectNamespaces(children.item(i), declarations, usedPrefixes)
        }
    }
}