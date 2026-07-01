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
                
                val elemPrefix = element.prefix
                if (!elemPrefix.isNullOrEmpty()) {
                    used.add(elemPrefix)
                } else {
                    val name = element.nodeName
                    val index = name.indexOf(':')
                    if (index != -1) {
                        used.add(name.substring(0, index))
                    }
                }

                val attributes = element.attributes
                for (i in 0 until attributes.length) {
                    val attr = attributes.item(i) as org.w3c.dom.Attr
                    val nodeName = attr.nodeName
                    if (nodeName.startsWith("xmlns:")) {
                        val prefix = nodeName.substring(6)
                        if (prefix.isNotEmpty()) {
                            declared[prefix] = attr
                        }
                    } else if (nodeName != "xmlns") {
                        val attrPrefix = attr.prefix
                        if (!attrPrefix.isNullOrEmpty()) {
                            used.add(attrPrefix)
                        } else {
                            val index = nodeName.indexOf(':')
                            if (index != -1) {
                                used.add(nodeName.substring(0, index))
                            }
                        }
                    }
                }
            }

            var child = node.firstChild
            while (child != null) {
                checkNode(child)
                child = child.nextSibling
            }
        }

        checkNode(document)

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