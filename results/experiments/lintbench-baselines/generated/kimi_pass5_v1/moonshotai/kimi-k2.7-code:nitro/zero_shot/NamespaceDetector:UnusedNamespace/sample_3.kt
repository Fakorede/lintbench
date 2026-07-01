package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.android.tools.lint.detector.api.XmlScannerConstants
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

class NamespaceDetector : Detector(), XmlScanner {

    override fun getApplicableElements(): Collection<String> = XmlScannerConstants.ALL

    override fun visitDocument(context: XmlContext, document: Document) {
        val declarations = mutableMapOf<String, Attr>()
        val usages = mutableSetOf<String>()
        collect(document, declarations, usages)

        for ((prefix, attr) in declarations) {
            if (prefix !in usages) {
                context.report(
                    ISSUE,
                    context.getLocation(attr),
                    "Unused namespace declaration: `$prefix`"
                )
            }
        }
    }

    private fun collect(
        node: Node,
        declarations: MutableMap<String, Attr>,
        usages: MutableSet<String>
    ) {
        if (node is Element) {
            val attrs = node.attributes
            for (i in 0 until attrs.length) {
                val attr = attrs.item(i) as Attr
                if (attr.name.startsWith(XMLNS_PREFIX)) {
                    val declaredPrefix = attr.localName ?: continue
                    declarations[declaredPrefix] = attr
                } else if (attr.prefix != null) {
                    usages.add(attr.prefix)
                }
            }

            val tagPrefix = node.prefix
            if (tagPrefix != null) {
                usages.add(tagPrefix)
            }
        }

        val children = node.childNodes
        for (i in 0 until children.length) {
            collect(children.item(i), declarations, usages)
        }
    }

    companion object {
        private const val XMLNS_PREFIX = "xmlns:"

        @JvmField
        val ISSUE = Issue.create(
            id = "UnusedNamespace",
            briefDescription = "Unused namespace",
            explanation = """
                Unused namespace declarations take up space and require processing that is not necessary.
                Remove any namespace declarations that are not referenced by elements or attributes in the file.
            """.trimIndent(),
            category = Category.PERFORMANCE,
            priority = 3,
            severity = Severity.WARNING,
            implementation = Implementation(
                NamespaceDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}