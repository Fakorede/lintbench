package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LintFix
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
            category = Category.PERFORMANCE,
            priority = 1,
            severity = Severity.WARNING,
            implementation = Implementation(
                NamespaceDetector::class.java,
                Scope.XML_FILE_SCOPE
            )
        )
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        val declared = mutableMapOf<String, MutableList<Attr>>()
        val used = mutableSetOf<String>()

        fun visit(node: Node) {
            if (node is Element) {
                val elementPrefix = node.prefix ?: run {
                    val name = node.nodeName
                    val idx = name.indexOf(':')
                    if (idx != -1) name.substring(0, idx) else ""
                }
                if (elementPrefix.isNotEmpty()) {
                    used.add(elementPrefix)
                }

                val attributes = node.attributes
                for (i in 0 until attributes.length) {
                    val attr = attributes.item(i) as Attr
                    val prefix = attr.prefix
                    if (prefix == "xmlns" || attr.nodeName.startsWith("xmlns:")) {
                        val declaredPrefix = attr.localName ?: attr.nodeName.substringAfter(':')
                        if (declaredPrefix.isNotEmpty()) {
                            declared.getOrPut(declaredPrefix) { mutableListOf() }.add(attr)
                        }
                    } else {
                        val p = if (!prefix.isNullOrEmpty()) prefix else {
                            val idx = attr.nodeName.indexOf(':')
                            if (idx != -1) attr.nodeName.substring(0, idx) else ""
                        }
                        if (p.isNotEmpty()) {
                            used.add(p)
                        }
                    }
                }

                val children = node.childNodes
                for (i in 0 until children.length) {
                    visit(children.item(i))
                }
            }
        }

        visit(document.documentElement ?: return)

        for ((prefix, attrs) in declared) {
            if (prefix !in used) {
                for (attr in attrs) {
                    val fix = LintFix.create()
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
                        "Unused namespace declaration `$prefix`",
                        fix
                    )
                }
            }
        }
    }
}