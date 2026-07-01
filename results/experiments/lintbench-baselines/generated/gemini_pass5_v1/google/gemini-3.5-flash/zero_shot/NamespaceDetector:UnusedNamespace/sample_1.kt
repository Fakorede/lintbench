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

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "UnusedNamespace",
            briefDescription = "Unused namespace",
            explanation = """
                Unused namespace declarations take up space and require processing that is not necessary.
                """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 1,
            severity = Severity.WARNING,
            implementation = Implementation(
                NamespaceDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return

        val declaredNamespaces = mutableMapOf<String, Attr>()
        val usedNamespaces = mutableSetOf<String>()

        fun collect(element: Element) {
            val tagName = element.tagName
            val elementColon = tagName.indexOf(':')
            if (elementColon != -1) {
                val prefix = tagName.substring(0, elementColon)
                usedNamespaces.add(prefix)
            }

            val attributes = element.attributes
            for (i in 0 until attributes.length) {
                val attr = attributes.item(i) as Attr
                val attrName = attr.nodeName
                if (attrName.startsWith("xmlns:")) {
                    val prefix = attrName.substring(6)
                    declaredNamespaces[prefix] = attr
                } else {
                    val attrColon = attrName.indexOf(':')
                    if (attrColon != -1) {
                        val prefix = attrName.substring(0, attrColon)
                        usedNamespaces.add(prefix)
                    }
                }
            }

            var child = element.firstChild
            while (child != null) {
                if (child.nodeType == Node.ELEMENT_NODE) {
                    collect(child as Element)
                }
                child = child.nextSibling
            }
        }

        collect(root)

        for ((prefix, attr) in declaredNamespaces) {
            if (prefix !in usedNamespaces) {
                val fix = fix()
                    .name("Remove unused namespace '$prefix'")
                    .replace()
                    .range(context.getLocation(attr))
                    .with("")
                    .autoFix()
                    .build()

                context.report(
                    ISSUE,
                    attr,
                    context.getLocation(attr),
                    "Unused namespace `xmlns:$prefix`",
                    fix
                )
            }
        }
    }
}