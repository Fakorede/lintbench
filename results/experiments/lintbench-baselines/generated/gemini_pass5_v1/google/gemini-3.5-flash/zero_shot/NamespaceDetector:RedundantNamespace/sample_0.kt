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
        @JvmField
        val ISSUE = Issue.create(
            id = "RedundantNamespace",
            briefDescription = "Redundant namespace",
            explanation = """
                In Android XML documents, only specify the namespace on the root/document \
                element. Namespace declarations elsewhere in the document are typically \
                accidental leftovers from copy/pasting XML from other files or documentation.
                """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 3,
            severity = Severity.WARNING,
            implementation = Implementation(
                NamespaceDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
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
                val attribute = attributes.item(i) as Attr
                val name = attribute.nodeName
                if (name == "xmlns" || name.startsWith("xmlns:")) {
                    val fix = fix()
                        .name("Remove namespace declaration")
                        .replace()
                        .all()
                        .with("")
                        .autoFix()
                        .build()

                    context.report(
                        ISSUE,
                        attribute,
                        context.getLocation(attribute),
                        "Redundant namespace declaration",
                        fix
                    )
                }
            }
        }
        val childNodes = element.childNodes
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child is Element) {
                checkElement(context, child, isRoot = false)
            }
        }
    }
}