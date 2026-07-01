package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element

class NamespaceDetector : ResourceXmlDetector() {

    companion object {
        val ISSUE_UNUSED_NAMESPACE = Issue.create(
            id = "UnusedNamespace",
            briefDescription = "Unused namespace",
            explanation = "Unused namespace declarations take up space and require processing that is not necessary.",
            category = Category.PERFORMANCE,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                NamespaceDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }

    override fun getIssues(): List<Issue> = listOf(ISSUE_UNUSED_NAMESPACE)

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return
        val declared = mutableMapOf<String, Attr>()
        val used = mutableSetOf<String>()

        collectNamespaces(root, declared, used)

        for ((prefix, attr) in declared) {
            if (prefix !in used) {
                val message = "Unused namespace declaration: xmlns:$prefix"
                context.report(ISSUE_UNUSED_NAMESPACE, context.getLocation(attr), message)
            }
        }
    }

    private fun collectNamespaces(
        element: Element,
        declared: MutableMap<String, Attr>,
        used: MutableSet<String>
    ) {
        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as Attr
            val name = attr.name
            if (name.startsWith("xmlns:")) {
                declared[name.substring(6)] = attr
            } else if (name != "xmlns") {
                val prefix = attr.prefix
                if (prefix != null && prefix != "xmlns") {
                    used.add(prefix)
                }
            }
        }

        val elementPrefix = element.prefix
        if (elementPrefix != null) {
            used.add(elementPrefix)
        }

        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is Element) {
                collectNamespaces(child, declared, used)
            }
        }
    }
}