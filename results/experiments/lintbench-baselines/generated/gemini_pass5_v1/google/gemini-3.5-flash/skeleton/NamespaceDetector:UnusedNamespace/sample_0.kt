package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.LintFix
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Attr
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
        
        val declarations = mutableListOf<Pair<String, Attr>>()
        val used = mutableSetOf<String>()

        fun visit(node: Node) {
            if (node.nodeType == Node.ELEMENT_NODE) {
                val element = node as Element
                val tagName = element.tagName
                val colon = tagName.indexOf(':')
                if (colon != -1) {
                    used.add(tagName.substring(0, colon))
                }

                val attributes = element.attributes
                for (i in 0 until attributes.length) {
                    val attr = attributes.item(i) as Attr
                    val name = attr.name
                    if (name.startsWith("xmlns:")) {
                        val prefix = name.substring(6)
                        declarations.add(prefix to attr)
                    } else {
                        val attrColon = name.indexOf(':')
                        if (attrColon != -1) {
                            used.add(name.substring(0, attrColon))
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

        for ((prefix, attr) in declarations) {
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
                    ISSUE,
                    attr,
                    context.getLocation(attr),
                    "Unused namespace declaration $prefix",
                    fix
                )
            }
        }
    }
}