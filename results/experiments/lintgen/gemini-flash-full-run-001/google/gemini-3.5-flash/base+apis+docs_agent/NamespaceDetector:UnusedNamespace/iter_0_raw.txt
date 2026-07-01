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

    override fun visitDocument(context: XmlContext, document: Document) {
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
                    val name = attr.nodeName
                    if (name.startsWith("xmlns:")) {
                        val prefix = name.substring(6)
                        declared[prefix] = attr
                    } else {
                        val colon = name.indexOf(':')
                        if (colon != -1) {
                            used.add(name.substring(0, colon))
                        }
                    }
                }
            }

            val children = node.childNodes
            for (i in 0 until children.length) {
                visit(children.item(i))
            }
        }

        visit(document)

        for ((prefix, attr) in declared) {
            if (prefix !in used) {
                val location = context.getLocation(attr)
                val fix = fix()
                    .name("Remove namespace declaration")
                    .replace()
                    .range(location)
                    .with("")
                    .autoFix()
                    .build()

                context.report(
                    ISSUE,
                    attr,
                    location,
                    "Unused namespace `$prefix`",
                    fix
                )
            }
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "UnusedNamespace",
            briefDescription = "Unused namespace",
            explanation = "Unused namespace declarations take up space and require processing that is not necessary",
            category = Category.PERFORMANCE,
            priority = 1,
            severity = Severity.WARNING,
            implementation = Implementation(
                NamespaceDetector::class.java,
                Scope.MANIFEST_AND_RESOURCE_SCOPE
            )
        )
    }
}