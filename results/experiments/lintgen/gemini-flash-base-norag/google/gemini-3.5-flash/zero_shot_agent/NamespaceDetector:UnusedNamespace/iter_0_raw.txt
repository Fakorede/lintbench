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
import java.util.EnumSet

class NamespaceDetector : Detector(), XmlScanner {

    override fun visitDocument(context: XmlContext, document: Document) {
        val declaredPrefixes = mutableMapOf<String, Attr>()
        val usedPrefixes = mutableSetOf<String>()

        fun visit(node: Node) {
            if (node is Element) {
                val elementPrefix = node.prefix ?: run {
                    val name = node.nodeName
                    val index = name.indexOf(':')
                    if (index != -1) name.substring(0, index) else null
                }
                if (elementPrefix != null) {
                    usedPrefixes.add(elementPrefix)
                }

                val attributes = node.attributes
                for (i in 0 until attributes.length) {
                    val attr = attributes.item(i) as Attr
                    val nodeName = attr.nodeName
                    if (nodeName.startsWith("xmlns:")) {
                        val prefix = nodeName.substring(6)
                        if (prefix.isNotEmpty()) {
                            declaredPrefixes[prefix] = attr
                        }
                    } else if (nodeName != "xmlns") {
                        val attrPrefix = attr.prefix ?: run {
                            val index = nodeName.indexOf(':')
                            if (index != -1) nodeName.substring(0, index) else null
                        }
                        if (attrPrefix != null) {
                            usedPrefixes.add(attrPrefix)
                        }
                    }
                }

                val childNodes = node.childNodes
                for (i in 0 until childNodes.length) {
                    visit(childNodes.item(i))
                }
            }
        }

        val root = document.documentElement
        if (root != null) {
            visit(root)
        }

        for ((prefix, attr) in declaredPrefixes) {
            if (prefix !in usedPrefixes) {
                val fix = fix()
                    .name("Remove namespace declaration")
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
            explanation = """
                Unused namespace declarations take up space and require processing that is not necessary.
                """.trimIndent(),
            category = Category.PERFORMANCE,
            priority = 1,
            severity = Severity.WARNING,
            implementation = Implementation(
                NamespaceDetector::class.java,
                EnumSet.of(Scope.MANIFEST, Scope.RESOURCE_FILE)
            )
        )
    }
}