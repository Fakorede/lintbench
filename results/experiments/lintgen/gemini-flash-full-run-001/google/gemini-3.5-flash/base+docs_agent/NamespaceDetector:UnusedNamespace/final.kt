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
        val root = document.documentElement ?: return
        val declared = mutableMapOf<String, Attr>()
        val used = mutableSetOf<String>()

        fun visit(node: Node) {
            if (node is Element) {
                val elementPrefix = node.prefix
                if (!elementPrefix.isNullOrEmpty()) {
                    used.add(elementPrefix)
                } else {
                    val tag = node.tagName
                    val colon = tag.indexOf(':')
                    if (colon != -1) {
                        used.add(tag.substring(0, colon))
                    }
                }

                val attributes = node.attributes
                for (i in 0 until attributes.length) {
                    val attr = attributes.item(i) as Attr
                    val nodeName = attr.nodeName
                    if (nodeName.startsWith("xmlns:")) {
                        val prefix = nodeName.substring(6)
                        if (prefix.isNotEmpty()) {
                            declared[prefix] = attr
                        }
                    } else {
                        val attrPrefix = attr.prefix
                        if (!attrPrefix.isNullOrEmpty()) {
                            if (attrPrefix != "xmlns") {
                                used.add(attrPrefix)
                            }
                        } else {
                            val colon = nodeName.indexOf(':')
                            if (colon != -1) {
                                val prefix = nodeName.substring(0, colon)
                                if (prefix != "xmlns") {
                                    used.add(prefix)
                                }
                            }
                        }
                    }
                }

                var child = node.firstChild
                while (child != null) {
                    visit(child)
                    child = child.nextSibling
                }
            }
        }

        visit(root)

        for ((prefix, attr) in declared) {
            if (prefix !in used) {
                val location = context.getLocation(attr)
                val fix = fix()
                    .name("Remove unused namespace")
                    .replace()
                    .range(location)
                    .all()
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