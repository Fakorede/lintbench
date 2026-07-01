package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.w3c.dom.Attr
import org.w3c.dom.Element

class NamespaceDetector : Detector(), XmlScanner {

    companion object {
        @JvmField
        val REDUNDANT_NAMESPACE = Issue.create(
            id = "RedundantNamespace",
            briefDescription = "Redundant namespace",
            explanation = """
                In Android XML documents, only specify the namespace on the root/document \
                element. Namespace declarations elsewhere in the document are typically \
                accidental leftovers from copy/pasting XML from other files or documentation.
            """,
            category = Category.CORRECTNESS,
            priority = 3,
            severity = Severity.WARNING,
            implementation = Implementation(
                NamespaceDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )

        private const val XMLNS_PREFIX = "xmlns:"
        private const val XMLNS = "xmlns"
    }

    override fun getApplicableAttributes(): Collection<String>? = null

    override fun visitDocument(context: XmlContext, document: org.w3c.dom.Document) {
        val root = document.documentElement ?: return
        checkElement(context, root, isRoot = true)
    }

    private fun checkElement(context: XmlContext, element: Element, isRoot: Boolean) {
        if (!isRoot) {
            val attributes = element.attributes ?: return
            for (i in 0 until attributes.length) {
                val attr = attributes.item(i) as? Attr ?: continue
                val name = attr.name ?: continue
                if (name == XMLNS || name.startsWith(XMLNS_PREFIX)) {
                    val location = context.getLocation(attr)
                    context.report(
                        REDUNDANT_NAMESPACE,
                        element,
                        location,
                        "Redundant namespace declaration; already declared on the root element"
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