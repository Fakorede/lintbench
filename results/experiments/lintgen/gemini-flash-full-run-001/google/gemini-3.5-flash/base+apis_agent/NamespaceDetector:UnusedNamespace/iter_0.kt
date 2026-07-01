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

    override fun getApplicableFiles() = Scope.RESOURCE_FILE_SCOPE

    override fun visitDocument(context: XmlContext, document: Document) {
        val declared = mutableMapOf<String, Attr>()
        val used = mutableSetOf<String>()

        fun visit(node: Node) {
            if (node is Element) {
                val elemName = node.nodeName
                val colon = elemName.indexOf(':')
                if (colon != -1) {
                    used.add(elemName.substring(0, colon))
                }

                val attributes = node.attributes
                for (i in 0 until attributes.length) {
                    val attr = attributes.item(i) as Attr
                    val attrName = attr.nodeName
                    if (attrName.startsWith("xmlns:")) {
                        val prefix = attrName.substring(6)
                        declared[prefix] = attr
                    } else {
                        val attrColon = attrName.indexOf(':')
                        if (attrColon != -1) {
                            used.add(attrName.substring(0, attrColon))
                        }
                    }
                }
            }
            val childNodes = node.childNodes
            for (i in 0 until childNodes.length) {
                visit(childNodes.item(i))
            }
        }

        visit(document)

        for ((prefix, attr) in declared) {
            if (prefix !in used) {
                context.report(
                    ISSUE,
                    attr,
                    context.getLocation(attr),
                    "Unused namespace `$prefix`"
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