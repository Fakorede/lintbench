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
        val root = document.documentElement ?: return

        val declared = mutableMapOf<String, MutableList<org.w3c.dom.Attr>>()
        val used = mutableSetOf<String>()

        fun checkElement(element: org.w3c.dom.Element) {
            val attributes = element.attributes
            if (attributes != null) {
                for (i in 0 until attributes.length) {
                    val attr = attributes.item(i) as org.w3c.dom.Attr
                    val prefix = attr.prefix
                    if (prefix == "xmlns") {
                        val localName = attr.localName
                        if (localName != null) {
                            declared.getOrPut(localName) { mutableListOf() }.add(attr)
                        }
                    } else if (attr.nodeName == "xmlns") {
                        // Default namespace, ignore
                    } else {
                        if (!prefix.isNullOrEmpty()) {
                            used.add(prefix)
                        }
                    }
                }
            }

            val elementPrefix = element.prefix
            if (!elementPrefix.isNullOrEmpty()) {
                used.add(elementPrefix)
            }

            val childNodes = element.childNodes
            if (childNodes != null) {
                for (i in 0 until childNodes.length) {
                    val child = childNodes.item(i)
                    if (child is org.w3c.dom.Element) {
                        checkElement(child)
                    }
                }
            }
        }

        checkElement(root)

        for ((prefix, attrs) in declared) {
            if (prefix !in used) {
                for (attr in attrs) {
                    context.report(
                        ISSUE,
                        attr,
                        context.getLocation(attr),
                        "Unused namespace `xmlns:$prefix`"
                    )
                }
            }
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "UnusedNamespace",
            briefDescription = "Unused namespace",
            explanation = "Unused namespace declarations take up space and require processing that is  not necessary",
            category = Category.PERFORMANCE,
            priority = 1,
            severity = Severity.WARNING,
            implementation = Implementation(NamespaceDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
        )
    }
}