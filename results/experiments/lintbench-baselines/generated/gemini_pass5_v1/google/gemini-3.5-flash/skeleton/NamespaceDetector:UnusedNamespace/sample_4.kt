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

        fun visit(node: Node) {
            if (node.nodeType == Node.ELEMENT_NODE) {
                val element = node as Element
                val tagName = element.tagName
                val elementColon = tagName.indexOf(':')
                if (elementColon != -1) {
                    used.add(tagName.substring(0, elementColon))
                }

                val attributes = element.attributes
                for (i in 0 until attributes.length) {
                    val attr = attributes.item(i) as Attr
                    val attrName = attr.name
                    if (attrName.startsWith("xmlns:")) {
                        val prefix = attrName.substring(6)
                        if (prefix.isNotEmpty()) {
                            declared[prefix] = attr
                        }
                    } else if (attrName != "xmlns") {
                        val attrColon = attrName.indexOf(':')
                        if (attrColon != -1) {
                            used.add(attrName.substring(0, attrColon))
                        }
                    }
                }

                var child = element.firstChild
                while (child != null) {
                    visit(child)
                    child = child.nextSibling
                }
            }
        }

        visit(root)

        for ((prefix, attr) in declared) {
            if (prefix !in used) {
                val fix = fix()
                    .name("Remove namespace declaration")
                    .replace()
                    .range(context.getLocation(attr))
                    .all()
                    .with("")
                    .autoFix()
                    .build()

                context.report(
                    issue = ISSUE,
                    scope = attr,
                    location = context.getLocation(attr),
                    message = "Unused namespace declaration `$prefix`",
                    quickfixData = fix
                )
            }
        }
    }
}