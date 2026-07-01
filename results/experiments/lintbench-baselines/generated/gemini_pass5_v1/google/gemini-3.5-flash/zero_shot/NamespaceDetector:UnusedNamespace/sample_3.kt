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
        val declaredPrefixes = mutableMapOf<String, MutableList<Attr>>()
        val usedPrefixes = mutableSetOf<String>()

        fun visit(node: Node) {
            if (node is Element) {
                val tagName = node.tagName
                if (tagName.contains(':')) {
                    val prefix = tagName.substringBefore(':')
                    usedPrefixes.add(prefix)
                }

                val attributes = node.attributes
                if (attributes != null) {
                    for (i in 0 until attributes.length) {
                        val attr = attributes.item(i) as Attr
                        val attrName = attr.nodeName
                        if (attrName.startsWith("xmlns:")) {
                            val prefix = attrName.substring(6)
                            declaredPrefixes.getOrPut(prefix) { mutableListOf() }.add(attr)
                        } else if (attrName.contains(':')) {
                            val prefix = attrName.substringBefore(':')
                            usedPrefixes.add(prefix)
                        }
                    }
                }

                val children = node.childNodes
                if (children != null) {
                    for (i in 0 until children.length) {
                        visit(children.item(i))
                    }
                }
            }
        }

        val root = document.documentElement ?: return
        visit(root)

        for ((prefix, attrs) in declaredPrefixes) {
            if (prefix !in usedPrefixes) {
                for (attr in attrs) {
                    val location = context.getLocation(attr)
                    val fix = fix()
                        .name("Remove unused namespace")
                        .replace()
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
                Scope.MANIFEST_AND_RESOURCE_SCOPE
            )
        )
    }
}