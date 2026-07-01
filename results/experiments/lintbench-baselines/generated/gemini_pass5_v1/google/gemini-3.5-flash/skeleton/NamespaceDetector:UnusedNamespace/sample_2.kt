package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.ResourceXmlDetector
import org.w3c.dom.Document

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
            explanation = "Unused namespace declarations take up space and require processing that is not necessary.",
            category = Category.PERFORMANCE,
            priority = 1,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return
        val declared = mutableMapOf<String, org.w3c.dom.Attr>()
        val used = mutableSetOf<String>()

        fun visit(node: org.w3c.dom.Node) {
            if (node is org.w3c.dom.Element) {
                val tagName = node.tagName
                val colonIndex = tagName.indexOf(':')
                if (colonIndex > 0) {
                    used.add(tagName.substring(0, colonIndex))
                }

                val attributes = node.attributes
                for (i in 0 until attributes.length) {
                    val attr = attributes.item(i) as org.w3c.dom.Attr
                    val name = attr.name
                    if (name.startsWith("xmlns:")) {
                        val prefix = name.substring(6)
                        declared[prefix] = attr
                    } else {
                        val attrColonIndex = name.indexOf(':')
                        if (attrColonIndex > 0) {
                            used.add(name.substring(0, attrColonIndex))
                        }
                    }
                }

                var child = node.firstChild
                while (child != null) {
                    visit(child)
                    child = child.nextSibling
                }
            }
        }

        visit(root)

        for ((prefix, attr) in declared) {
            if (prefix !in used) {
                context.report(
                    ISSUE,
                    attr,
                    context.getLocation(attr),
                    "Unused namespace $prefix"
                )
            }
        }
    }
}