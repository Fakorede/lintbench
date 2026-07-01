package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element

class NamespaceDetector : ResourceXmlDetector() {

    companion object {
        private val IMPLEMENTATION = Implementation(
            NamespaceDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "RedundantNamespace",
            briefDescription = "Redundant namespace",
            explanation = """
                In Android XML documents, only specify the namespace on the root/document \
                element. Namespace declarations elsewhere in the document are typically \
                accidental leftovers from copy/pasting XML from other files or documentation.
            """.trimIndent(),
            category = Category.PERFORMANCE,
            priority = 1,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return
        checkElement(context, root, isRoot = true)
    }

    private fun checkElement(context: XmlContext, element: Element, isRoot: Boolean) {
        if (!isRoot) {
            val attributes = element.attributes
            for (i in 0 until attributes.length) {
                val attr = attributes.item(i) as? Attr ?: continue
                val name = attr.name
                if (name.startsWith("xmlns:") || name == "xmlns") {
                    val fix = context.fix()
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
                        "Redundant namespace declaration",
                        fix
                    )
                }
            }
        }

        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is Element) {
                checkElement(context, child, isRoot = false)
            }
        }
    }
}