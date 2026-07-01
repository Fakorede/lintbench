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
            explanation = "Unused namespace declarations take up space and require processing that is not necessary.",
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

        visit(root, declared, used)

        for ((prefix, attr) in declared) {
            if (prefix !in used) {
                val location = context.getLocation(attr)
                val message = "Unused namespace `$prefix`"

                val fix = fix()
                    .name("Remove namespace declaration")
                    .replace()
                    .range(location)
                    .with("")
                    .autoFix()
                    .build()

                context.report(
                    issue = ISSUE,
                    scope = attr,
                    location = location,
                    message = message,
                    quickfixData = fix
                )
            }
        }
    }

    private fun visit(node: Node, declared: MutableMap<String, Attr>, used: MutableSet<String>) {
        if (node.nodeType == Node.ELEMENT_NODE) {
            val element = node as Element
            val tagName = element.nodeName
            if (tagName.contains(':')) {
                used.add(tagName.substringBefore(':'))
            }

            val attributes = element.attributes
            if (attributes != null) {
                for (i in 0 until attributes.length) {
                    val attr = attributes.item(i) as Attr
                    val attrName = attr.nodeName
                    if (attrName.startsWith("xmlns:")) {
                        val prefix = attrName.substring(6)
                        declared[prefix] = attr
                    } else if (attrName.contains(':')) {
                        val prefix = attrName.substringBefore(':')
                        used.add(prefix)
                    }
                }
            }

            val children = element.childNodes
            if (children != null) {
                for (i in 0 until children.length) {
                    visit(children.item(i), declared, used)
                }
            }
        }
    }
}