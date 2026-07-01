package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner

class NamespaceDetector : ResourceXmlDetector(), XmlScanner {

    override fun visitDocument(context: XmlContext, document: org.w3c.dom.Document) {
        val declared = mutableMapOf<String, org.w3c.dom.Attr>()
        val used = mutableSetOf<String>()

        fun checkNode(node: org.w3c.dom.Node) {
            if (node.nodeType == org.w3c.dom.Node.ELEMENT_NODE) {
                val element = node as org.w3c.dom.Element
                val prefix = element.prefix
                if (!prefix.isNullOrEmpty()) {
                    used.add(prefix)
                }

                val attributes = element.attributes
                if (attributes != null) {
                    for (i in 0 until attributes.length) {
                        val attr = attributes.item(i) as org.w3c.dom.Attr
                        val name = attr.nodeName
                        if (name.startsWith("xmlns:")) {
                            val localName = attr.localName ?: name.substring(6)
                            declared[localName] = attr
                        } else {
                            val attrPrefix = attr.prefix
                            if (!attrPrefix.isNullOrEmpty()) {
                                used.add(attrPrefix)
                            }
                        }
                    }
                }

                var child = element.firstChild
                while (child != null) {
                    checkNode(child)
                    child = child.nextSibling
                }
            }
        }

        val root = document.documentElement
        if (root != null) {
            checkNode(root)
        }

        for ((prefix, attr) in declared) {
            if (prefix !in used) {
                context.report(
                    ISSUE,
                    attr,
                    context.getNameLocation(attr),
                    "Unused namespace $prefix"
                )
            }
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "UnusedNamespace",
            briefDescription = "Unused namespace",
            explanation = "Unused namespace declarations take up space and require processing that is not necessary",
            category = Category.PERFORMANCE,
            priority = 1,
            severity = Severity.WARNING,
            implementation = Implementation(
                NamespaceDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}