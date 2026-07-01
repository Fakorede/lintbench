package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext

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
                In Android XML documents, only specify the namespace on the root/document
                element. Namespace declarations elsewhere in the document are typically
                accidental leftovers from copy/pasting XML from other files or documentation.
            """,
            category = Category.PERFORMANCE,
            priority = 1,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun visitDocument(context: XmlContext, document: org.w3c.dom.Document) {
        val root = document.documentElement ?: return
        val elements = document.getElementsByTagName("*")

        for (i in 0 until elements.length) {
            val element = elements.item(i) as? org.w3c.dom.Element ?: continue
            if (element === root) continue

            for (j in 0 until element.attributes.length) {
                val attr = element.attributes.item(j)
                if (attr.name == "xmlns" || attr.name.startsWith("xmlns:")) {
                    context.report(
                        ISSUE,
                        attr,
                        context.getLocation(attr),
                        "Redundant namespace declaration",
                    )
                }
            }
        }
    }
}