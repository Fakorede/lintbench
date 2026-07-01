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
        private const val XMLNS = "xmlns"
        private const val XMLNS_PREFIX = "xmlns:"
        private val IMPLEMENTATION = Implementation(
            NamespaceDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "UnusedNamespace",
            briefDescription = "Unused namespace",
            explanation = "Unused namespace declarations take up space and require processing that is not necessary. Remove any namespace declarations that are not referenced in the file.",
            category = Category.PERFORMANCE,
            priority = 1,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return

        val prefixed = mutableListOf<Pair<String, Attr>>()
        val defaults = mutableListOf<Attr>()
        val usedPrefixes = mutableSetOf<String>()
        var defaultUsed = false

        fun visit(node: Node) {
            if (node.nodeType != Node.ELEMENT_NODE) return
            val element = node as Element

            if (element.prefix.isNullOrEmpty()) {
                defaultUsed = true
            } else {
                usedPrefixes.add(element.prefix)
            }

            val attrs = element.attributes
            for (i in 0 until attrs.length) {
                val attr = attrs.item(i) as Attr
                val name = attr.name
                when {
                    name == XMLNS -> defaults.add(attr)
                    name.startsWith(XMLNS_PREFIX) -> {
                        val prefix = name.substring(XMLNS_PREFIX.length)
                        prefixed.add(prefix to attr)
                    }
                    else -> {
                        val attrPrefix = attr.prefix
                        if (!attrPrefix.isNullOrEmpty()) {
                            usedPrefixes.add(attrPrefix)
                        }
                    }
                }
            }

            val children = element.childNodes
            for (i in 0 until children.length) {
                visit(children.item(i))
            }
        }

        visit(root)

        for ((prefix, attr) in prefixed) {
            if (prefix !in usedPrefixes) {
                context.report(
                    ISSUE,
                    attr,
                    context.getLocation(attr),
                    "The namespace `$prefix` is never used"
                )
            }
        }

        for (attr in defaults) {
            if (!defaultUsed) {
                context.report(
                    ISSUE,
                    attr,
                    context.getLocation(attr),
                    "The default namespace is never used"
                )
            }
        }
    }
}