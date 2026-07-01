package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.w3c.dom.Element
import org.w3c.dom.Node

class NamespaceDetector : Detector(), XmlScanner {
    companion object {
        val ISSUE = Issue.create(
            id = "UnusedNamespace",
            briefDescription = "Unused namespace",
            explanation = "Unused namespace declarations take up space and require processing that is not necessary",
            category = Category.PERFORMANCE,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(NamespaceDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
        )
    }

    override fun getApplicableElements(): Collection<String>? = listOf("*")

    override fun visitElement(context: XmlContext, element: Element) {
        val attributes = element.attributes ?: return
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i)
            val name = attr.nodeName
            if (name.startsWith("xmlns:")) {
                val prefix = name.substring(6)
                if (!isPrefixUsed(element, prefix)) {
                    context.report(
                        ISSUE,
                        context.getLocation(attr),
                        "Unused namespace declaration `$name`"
                    )
                }
            }
        }
    }

    private fun isPrefixUsed(element: Element, prefix: String): Boolean {
        if (element.prefix == prefix) return true
        if (element.tagName.startsWith("$prefix:")) return true

        val attrs = element.attributes
        if (attrs != null) {
            for (i in 0 until attrs.length) {
                val attrName = attrs.item(i).nodeName
                if (attrName.startsWith("xmlns:")) continue
                if (attrName.startsWith("$prefix:")) return true
            }
        }

        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.ELEMENT_NODE) {
                if (isPrefixUsed(child as Element, prefix)) return true
            }
        }
        return false
    }
}