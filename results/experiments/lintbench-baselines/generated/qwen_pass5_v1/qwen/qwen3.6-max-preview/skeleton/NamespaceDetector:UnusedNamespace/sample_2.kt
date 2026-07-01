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
        val declared = mutableMapOf<String, Attr>()
        val used = mutableSetOf<String>()

        fun traverse(node: Node) {
            if (node.nodeType == Node.ELEMENT_NODE) {
                val element = node as Element
                element.prefix?.let { used.add(it) }

                val attributes = element.attributes
                for (i in 0 until attributes.length) {
                    val attr = attributes.item(i) as Attr
                    val name = attr.name
                    if (name.startsWith("xmlns:")) {
                        declared[name.substring(6)] = attr
                    } else {
                        attr.prefix?.let { used.add(it) }
                    }
                }

                var child = node.firstChild
                while (child != null) {
                    traverse(child)
                    child = child.nextSibling
                }
            }
        }

        traverse(root)

        for ((prefix, attr) in declared) {
            if (prefix !in used) {
                context.report(
                    ISSUE,
                    context.getLocation(attr),
                    "Unused namespace $prefix"
                )
            }
        }
    }
}