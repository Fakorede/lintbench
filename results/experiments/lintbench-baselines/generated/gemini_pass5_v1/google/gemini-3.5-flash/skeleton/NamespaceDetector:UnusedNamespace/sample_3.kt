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

        checkNode(root, declared, used)

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

    private fun checkNode(node: Node, declared: MutableMap<String, Attr>, used: MutableSet<String>) {
        if (node.nodeType == Node.ELEMENT_NODE) {
            val element = node as Element
            val elemPrefix = getPrefix(element.tagName)
            if (elemPrefix != null) {
                used.add(elemPrefix)
            }

            val attributes = element.attributes
            for (i in 0 until attributes.length) {
                val attr = attributes.item(i) as Attr
                val name = attr.name
                if (name.startsWith("xmlns:")) {
                    val prefix = name.substring(6)
                    declared[prefix] = attr
                } else if (name != "xmlns") {
                    val attrPrefix = getPrefix(name)
                    if (attrPrefix != null) {
                        used.add(attrPrefix)
                    }
                }
            }
        }

        var child = node.firstChild
        while (child != null) {
            checkNode(child, declared, used)
            child = child.nextSibling
        }
    }

    private fun getPrefix(name: String): String? {
        val index = name.indexOf(':')
        return if (index != -1) name.substring(0, index) else null
    }
}