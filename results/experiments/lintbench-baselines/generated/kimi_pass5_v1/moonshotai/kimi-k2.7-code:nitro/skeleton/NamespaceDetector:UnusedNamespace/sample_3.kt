package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceFolderType
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
        private const val XML_PREFIX = "xml"
        private const val XMLNS = "xmlns"

        private val IMPLEMENTATION = Implementation(
            NamespaceDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "UnusedNamespace",
            briefDescription = "Unused namespace",
            explanation = "Namespace declarations that are never referenced by any " +
                    "element or attribute in the document still take up space and require " +
                    "processing. Remove unused namespace declarations to reduce file " +
                    "size and parsing overhead.",
            category = Category.PERFORMANCE,
            priority = 1,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean = true

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return

        val declaredPrefixes = mutableMapOf<String, Attr>()
        val usedPrefixes = mutableSetOf<String>()
        val declaredDefaults = mutableMapOf<String, Attr>()
        val usedDefaults = mutableSetOf<String>()

        fun collect(node: Node) {
            if (node.nodeType != Node.ELEMENT_NODE) return
            val element = node as Element

            val attributes = element.attributes
            for (i in 0 until attributes.length) {
                val attr = attributes.item(i) as Attr
                when {
                    attr.name.startsWith("xmlns:") -> {
                        val prefix = attr.name.substring(6)
                        if (prefix != XML_PREFIX) {
                            declaredPrefixes[prefix] = attr
                        }
                    }
                    attr.name == XMLNS -> {
                        declaredDefaults[attr.value] = attr
                    }
                    attr.prefix != null -> {
                        usedPrefixes.add(attr.prefix)
                    }
                }
            }

            val elementPrefix = element.prefix
            if (elementPrefix != null) {
                usedPrefixes.add(elementPrefix)
            } else {
                usedDefaults.add(element.namespaceURI)
            }

            val children = element.childNodes
            for (i in 0 until children.length) {
                collect(children.item(i))
            }
        }

        collect(root)

        for ((prefix, attr) in declaredPrefixes) {
            if (prefix !in usedPrefixes) {
                context.report(
                    ISSUE,
                    context.getLocation(attr),
                    "Unused namespace \"xmlns:$prefix\"",
                )
            }
        }

        for ((uri, attr) in declaredDefaults) {
            if (uri !in usedDefaults) {
                context.report(
                    ISSUE,
                    context.getLocation(attr),
                    "Unused default namespace \"$uri\"",
                )
            }
        }
    }
}