package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

class NamespaceDetector : Detector(), Detector.XmlScanner {

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return
        checkElement(context, root)
    }

    private fun checkElement(context: XmlContext, element: Element) {
        val declared = mutableListOf<Pair<String, Attr>>()

        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as? Attr ?: continue
            val name = attr.name
            if (name.startsWith("xmlns:")) {
                val prefix = name.substring(6)
                if (prefix.isNotEmpty() && prefix != "xml") {
                    declared.add(prefix to attr)
                }
            }
        }

        for ((prefix, attr) in declared) {
            if (!isPrefixUsed(element, prefix)) {
                context.report(
                    ISSUE,
                    attr,
                    context.getLocation(attr),
                    "Namespace prefix `$prefix` is declared but never used"
                )
            }
        }

        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.ELEMENT_NODE) {
                checkElement(context, child as Element)
            }
        }
    }

    private fun isPrefixUsed(element: Element, prefix: String): Boolean {
        if (element.prefix == prefix) return true

        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as? Attr ?: continue
            if (attr.prefix == prefix) return true
        }

        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.ELEMENT_NODE && isPrefixUsed(child as Element, prefix)) {
                return true
            }
        }

        return false
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "UnusedNamespace",
            briefDescription = "Unused namespace declaration",
            explanation = """
                Unused namespace declarations take up space and require processing that is not necessary.
                Remove any namespace declarations that are not used.
            """.trimIndent(),
            category = Category.PERFORMANCE,
            priority = 2,
            severity = Severity.WARNING,
            implementation = Implementation(
                NamespaceDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}