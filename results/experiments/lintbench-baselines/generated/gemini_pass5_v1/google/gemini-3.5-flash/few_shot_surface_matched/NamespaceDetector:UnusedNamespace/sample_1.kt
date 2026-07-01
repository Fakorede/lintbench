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
        val root = document.documentElement ?: return

        val declared = mutableMapOf<String, org.w3c.dom.Attr>()
        val used = mutableSetOf<String>()

        fun checkElement(element: org.w3c.dom.Element) {
            val attributes = element.attributes
            for (i in 0 until attributes.length) {
                val attr = attributes.item(i) as org.w3c.dom.Attr
                val name = attr.nodeName
                if (name.startsWith("xmlns:")) {
                    val prefix = name.substring(6)
                    declared[prefix] = attr
                } else {
                    val prefix = attr.prefix
                    if (prefix != null && prefix.isNotEmpty()) {
                        used.add(prefix)
                    } else {
                        val idx = name.indexOf(':')
                        if (idx != -1) {
                            used.add(name.substring(0, idx))
                        }
                    }
                }
            }

            val elementPrefix = element.prefix
            if (elementPrefix != null && elementPrefix.isNotEmpty()) {
                used.add(elementPrefix)
            } else {
                val tagName = element.tagName
                val idx = tagName.indexOf(':')
                if (idx != -1) {
                    used.add(tagName.substring(0, idx))
                }
            }

            val childNodes = element.childNodes
            for (i in 0 until childNodes.length) {
                val child = childNodes.item(i)
                if (child.nodeType == org.w3c.dom.Node.ELEMENT_NODE) {
                    checkElement(child as org.w3c.dom.Element)
                }
            }
        }

        checkElement(root)

        for ((prefix, attr) in declared) {
            if (prefix == "xml") continue
            if (!used.contains(prefix)) {
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
            explanation = """
                Unused namespace declarations take up space and require processing that is not necessary.
                """.trimIndent(),
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