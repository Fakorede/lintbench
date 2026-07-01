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

class NamespaceDetector : Detector(), XmlScanner {

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return
        val declared = mutableMapOf<String, Attr>()
        val used = mutableSetOf<String>()

        fun visit(element: Element) {
            val tagName = element.tagName
            val tagColon = tagName.indexOf(':')
            if (tagColon > 0) {
                used.add(tagName.substring(0, tagColon))
            }

            val attributes = element.attributes
            if (attributes != null) {
                for (i in 0 until attributes.length) {
                    val attr = attributes.item(i) as Attr
                    val name = attr.nodeName
                    if (name.startsWith("xmlns:")) {
                        val prefix = name.substring(6)
                        declared[prefix] = attr
                    } else {
                        val attrColon = name.indexOf(':')
                        if (attrColon > 0) {
                            used.add(name.substring(0, attrColon))
                        }
                    }
                }
            }

            val childNodes = element.childNodes
            for (i in 0 until childNodes.length) {
                val child = childNodes.item(i)
                if (child is Element) {
                    visit(child)
                }
            }
        }

        visit(root)

        for ((prefix, attr) in declared) {
            if (prefix !in used) {
                val fix = fix()
                    .name("Remove unused namespace")
                    .replace()
                    .range(context.getLocation(attr))
                    .with("")
                    .autoFix()
                    .build()

                context.report(
                    ISSUE,
                    attr,
                    context.getLocation(attr),
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
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}